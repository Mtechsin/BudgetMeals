package com.budgetmeals.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetmeals.app.data.BudgetRepository
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class BackupState(
    val folderUri: Uri? = null,
    val folderDisplayName: String? = null,
    val lastBackupTime: LocalDateTime? = null,
    val lastBackupStatus: String = "NO_FOLDER",
    val errorMessage: String? = null,
    val isAutoBackupEnabled: Boolean = false,
) {
    val needsFolderReselection: Boolean
        get() = lastBackupStatus == "PERMISSION_LOST"
}

class BackupManager(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun readState(): BackupState {
        val uriStr = prefs.getString(KEY_FOLDER_URI, null)
        val uri = uriStr?.let { Uri.parse(it) }
        val name = prefs.getString(KEY_FOLDER_NAME, null)
        val timeStr = prefs.getString(KEY_LAST_BACKUP_TIME, null)
        val time = timeStr?.let { runCatching { LocalDateTime.parse(it, FORMATTER) }.getOrNull() }
        val status = prefs.getString(KEY_LAST_BACKUP_STATUS, if (uri != null) "IDLE" else "NO_FOLDER") ?: "NO_FOLDER"
        val error = prefs.getString(KEY_LAST_BACKUP_ERROR, null)
        val autoEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, uri != null)

        return BackupState(
            folderUri = uri,
            folderDisplayName = name,
            lastBackupTime = time,
            lastBackupStatus = status,
            errorMessage = error,
            isAutoBackupEnabled = autoEnabled,
        )
    }

    fun setBackupFolder(treeUri: Uri, displayName: String?) {
        prefs.edit()
            .putString(KEY_FOLDER_URI, treeUri.toString())
            .putString(KEY_FOLDER_NAME, displayName)
            .putString(KEY_LAST_BACKUP_STATUS, "IDLE")
            .remove(KEY_LAST_BACKUP_ERROR)
            .putBoolean(KEY_AUTO_ENABLED, true)
            .apply()

        scheduleDailyAutoBackup()
        refreshState()
    }

    fun scheduleDailyAutoBackup() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_DAILY_AUTO_BACKUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun scheduleDebouncedBackup() {
        val uriStr = prefs.getString(KEY_FOLDER_URI, null) ?: return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            WORK_DEBOUNCED_BACKUP,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun performAutoBackup(): Boolean = withContext(Dispatchers.IO) {
        val uriStr = prefs.getString(KEY_FOLDER_URI, null) ?: return@withContext true
        val treeUri = Uri.parse(uriStr)
        val folder = DocumentFile.fromTreeUri(context, treeUri)

        if (folder == null || !folder.canWrite()) {
            recordFailure("PERMISSION_LOST", "Choose backup folder again")
            return@withContext false
        }

        var newFile: DocumentFile? = null
        try {
            val repository = BudgetRepository(context)
            val now = LocalDateTime.now()
            val fileName = "BudgetMeals_backup_auto_${now.format(FILE_TIMESTAMP)}.zip"
            newFile = folder.createFile("application/zip", fileName)
                ?: error("Could not create backup file in folder")

            context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                BudgetBackupArchive.createArchive(repository, os)
            } ?: error("Could not open output stream for backup file")

            pruneAutomaticBackups(folder, maxToKeep = 7)
            recordSuccess(now)
            true
        } catch (e: Exception) {
            runCatching { newFile?.delete() }
            recordFailure("FAILED", e.message ?: "Backup failed")
            false
        }
    }

    suspend fun performManualBackup(): Result<String> = withContext(Dispatchers.IO) {
        val uriStr = prefs.getString(KEY_FOLDER_URI, null)
            ?: return@withContext Result.failure(IllegalStateException("Choose a backup folder first."))
        val treeUri = Uri.parse(uriStr)
        val folder = DocumentFile.fromTreeUri(context, treeUri)

        if (folder == null || !folder.canWrite()) {
            recordFailure("PERMISSION_LOST", "Choose backup folder again")
            return@withContext Result.failure(IllegalStateException("Choose backup folder again."))
        }

        var newFile: DocumentFile? = null
        try {
            val repository = BudgetRepository(context)
            val now = LocalDateTime.now()
            val fileName = "BudgetMeals_backup_manual_${now.format(FILE_TIMESTAMP)}.zip"
            newFile = folder.createFile("application/zip", fileName)
                ?: error("Could not create manual backup file")

            context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                BudgetBackupArchive.createArchive(repository, os)
            } ?: error("Could not open output stream for manual backup file")

            recordSuccess(now)
            Result.success("Backup saved: $fileName")
        } catch (e: Exception) {
            runCatching { newFile?.delete() }
            recordFailure("FAILED", e.message ?: "Backup failed")
            Result.failure(e)
        }
    }

    private fun pruneAutomaticBackups(folder: DocumentFile, maxToKeep: Int = 7) {
        try {
            val autoBackups = folder.listFiles()
                .filter {
                    val name = it.name.orEmpty()
                    name.startsWith("BudgetMeals_backup_auto_") && name.endsWith(".zip")
                }
                .sortedByDescending { it.name.orEmpty() }

            val validBackups = mutableListOf<DocumentFile>()
            val failedBackups = mutableListOf<DocumentFile>()
            for (file in autoBackups) {
                val isValid = runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        BudgetBackupArchive.previewArchive(input).isValid
                    } ?: false
                }.getOrDefault(false)
                if (isValid) validBackups += file else failedBackups += file
            }
            failedBackups.forEach { runCatching { it.delete() } }
            if (validBackups.size > maxToKeep) {
                for (oldFile in validBackups.drop(maxToKeep)) {
                    oldFile.delete()
                }
            }
        } catch (_: Exception) {
            // Prune error does not invalidate successful backup
        }
    }

    fun previewBackup(inputStream: InputStream): BackupPreview {
        return BudgetBackupArchive.previewArchive(inputStream)
    }

    fun restoreBackup(inputStream: InputStream): Boolean {
        val repository = BudgetRepository(context)
        return BudgetBackupArchive.restoreArchive(context, repository, inputStream)
    }

    private fun recordSuccess(time: LocalDateTime) {
        prefs.edit()
            .putString(KEY_LAST_BACKUP_TIME, time.format(FORMATTER))
            .putString(KEY_LAST_BACKUP_STATUS, "SUCCESS")
            .remove(KEY_LAST_BACKUP_ERROR)
            .apply()
        refreshState()
    }

    private fun recordFailure(status: String, error: String) {
        prefs.edit()
            .putString(KEY_LAST_BACKUP_STATUS, status)
            .putString(KEY_LAST_BACKUP_ERROR, error)
            .apply()
        refreshState()
    }

    private fun refreshState() {
        _state.value = readState()
    }

    companion object {
        private const val WORK_DAILY_AUTO_BACKUP = "budget_daily_auto_backup"
        private const val WORK_DEBOUNCED_BACKUP = "budget_debounced_backup"

        private const val KEY_FOLDER_URI = "backup_folder_uri"
        private const val KEY_FOLDER_NAME = "backup_folder_name"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_LAST_BACKUP_STATUS = "last_backup_status"
        private const val KEY_LAST_BACKUP_ERROR = "last_backup_error"
        private const val KEY_AUTO_ENABLED = "auto_backup_enabled"

        private val FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private val FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}

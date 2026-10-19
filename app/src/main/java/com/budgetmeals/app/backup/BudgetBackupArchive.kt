package com.budgetmeals.app.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.AppThemeMode
import com.budgetmeals.app.data.BudgetRepository
import com.budgetmeals.app.data.BudgetSettings
import com.budgetmeals.app.notifications.ReminderScheduler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class BackupPreview(
    val createdAt: LocalDateTime?,
    val recordCounts: Map<String, Int>,
    val isValid: Boolean,
    val errorMessage: String? = null,
) {
    val exportedAt: LocalDateTime get() = createdAt ?: LocalDateTime.now()
    val expensesCount: Int get() = recordCounts["expenses"] ?: 0
    val stockCount: Int get() = recordCounts["stock"] ?: 0
    val mealsCount: Int get() = recordCounts["meal_templates"] ?: 0
    val plansCount: Int get() = recordCounts["day_plans"] ?: 0
}

/**
 * Handles creation, validation, recovery staging, and direct table restoration
 * of versioned portable BudgetMeals backup archives.
 */
object BudgetBackupArchive {

    private const val ARCHIVE_VERSION = 1
    private val ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    val REQUIRED_TABLES = listOf(
        "categories",
        "food_catalog",
        "stock",
        "expenses",
        "meal_templates",
        "meal_logs",
        "meal_consumptions",
        "shopping",
        "spares",
        "day_plans",
        "day_closures",
    )

    fun createArchive(repository: BudgetRepository, outputStream: OutputStream) {
        repository.persistenceLock.readLock().lock()
        try {
            val db = repository.database.readableDatabase
            val zip = ZipOutputStream(outputStream)
            val tableContents = mutableMapOf<String, ByteArray>()
            val recordCounts = mutableMapOf<String, Int>()
            val checksums = mutableMapOf<String, String>()

            for (table in REQUIRED_TABLES) {
                val jsonArray = readTableAsJson(db, table)
                val bytes = jsonArray.toString().toByteArray(StandardCharsets.UTF_8)
                tableContents[table] = bytes
                recordCounts[table] = jsonArray.length()
                checksums["$table.json"] = sha256Hex(bytes)
            }

            // Settings
            val settings = repository.readSettings()
            val settingsJson = settingsToJson(settings)
            val settingsBytes = settingsJson.toString().toByteArray(StandardCharsets.UTF_8)
            tableContents["settings"] = settingsBytes
            checksums["settings.json"] = sha256Hex(settingsBytes)

            // Meta
            val meta = JSONObject().apply {
                put("archive_version", ARCHIVE_VERSION)
                put("created_at", LocalDateTime.now().format(ISO_FORMATTER))
                put("counts", JSONObject(recordCounts as Map<*, *>))
                put("checksums", JSONObject(checksums as Map<*, *>))
            }
            val metaBytes = meta.toString().toByteArray(StandardCharsets.UTF_8)

            // Write to ZIP
            zip.putNextEntry(ZipEntry("meta.json"))
            zip.write(metaBytes)
            zip.closeEntry()

            for ((table, bytes) in tableContents) {
                zip.putNextEntry(ZipEntry("$table.json"))
                zip.write(bytes)
                zip.closeEntry()
            }

            zip.finish()
        } finally {
            repository.persistenceLock.readLock().unlock()
        }
    }

    fun previewArchive(inputStream: InputStream): BackupPreview {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            var metaJson: JSONObject? = null
            val fileContents = mutableMapOf<String, ByteArray>()

            while (entry != null) {
                val name = entry.name
                val bytes = zip.readBytes()
                if (name == "meta.json") {
                    metaJson = JSONObject(String(bytes, StandardCharsets.UTF_8))
                } else if (name.endsWith(".json")) {
                    fileContents[name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            if (metaJson == null) {
                return BackupPreview(null, emptyMap(), false, "Missing meta.json in backup archive.")
            }

            val version = metaJson.optInt("archive_version", -1)
            if (version !in 1..ARCHIVE_VERSION) {
                return BackupPreview(null, emptyMap(), false, "Unsupported backup archive version: $version.")
            }

            val createdAtStr = metaJson.optString("created_at")
            val createdAt = runCatching { LocalDateTime.parse(createdAtStr, ISO_FORMATTER) }.getOrNull()
            val countsJson = metaJson.optJSONObject("counts")
            val checksumsJson = metaJson.optJSONObject("checksums")
                ?: return BackupPreview(createdAt, emptyMap(), false, "Corrupted archive: missing checksums in meta.json.")

            val counts = mutableMapOf<String, Int>()
            if (countsJson != null) {
                for (key in countsJson.keys()) {
                    counts[key] = countsJson.getInt(key)
                }
            }

            // Must contain settings.json
            if (!fileContents.containsKey("settings.json")) {
                return BackupPreview(createdAt, counts, false, "Corrupted archive: missing settings.json.")
            }

            // Must contain EVERY table in REQUIRED_TABLES
            for (table in REQUIRED_TABLES) {
                val fileName = "$table.json"
                if (!fileContents.containsKey(fileName)) {
                    return BackupPreview(createdAt, counts, false, "Corrupted archive: missing required table $fileName.")
                }
            }

            // Verify checksums of settings.json and every required table
            val requiredFiles = REQUIRED_TABLES.map { "$it.json" } + "settings.json"
            for (fileName in requiredFiles) {
                val expectedSha = checksumsJson.optString(fileName)
                if (expectedSha.isNullOrBlank()) {
                    return BackupPreview(createdAt, counts, false, "Missing checksum for $fileName in meta.json.")
                }
                val actualBytes = fileContents[fileName]
                    ?: return BackupPreview(createdAt, counts, false, "Missing $fileName in archive.")
                val actualSha = sha256Hex(actualBytes)
                if (!expectedSha.equals(actualSha, ignoreCase = true)) {
                    return BackupPreview(createdAt, counts, false, "Checksum verification failed for $fileName.")
                }
            }

            BackupPreview(createdAt, counts, true, null)
        } catch (e: Exception) {
            BackupPreview(null, emptyMap(), false, e.message ?: "Could not read backup file.")
        }
    }

    fun restoreArchive(context: Context, repository: BudgetRepository, inputStream: InputStream): Boolean {
        // Read and validate archive contents first into memory
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val tableJsons = mutableMapOf<String, JSONArray>()

        val unzippedFiles = mutableMapOf<String, ByteArray>()
        while (entry != null) {
            val name = entry.name
            val bytes = zip.readBytes()
            unzippedFiles[name] = bytes
            zip.closeEntry()
            entry = zip.nextEntry
        }

        val metaBytes = unzippedFiles["meta.json"] ?: error("Invalid archive: missing meta.json")
        val metaJson = JSONObject(String(metaBytes, StandardCharsets.UTF_8))
        val version = metaJson.optInt("archive_version", -1)
        check(version in 1..ARCHIVE_VERSION) { "Unsupported archive version: $version" }

        val checksumsJson = metaJson.optJSONObject("checksums") ?: error("Invalid archive: missing checksums in meta.json")

        // Must contain settings.json
        val settingsBytes = unzippedFiles["settings.json"] ?: error("Corrupted archive: missing settings.json")
        val settingsJson = JSONObject(String(settingsBytes, StandardCharsets.UTF_8))

        // Must contain EVERY table in REQUIRED_TABLES
        for (table in REQUIRED_TABLES) {
            val fileName = "$table.json"
            val tableBytes = unzippedFiles[fileName] ?: error("Corrupted archive: missing required table $fileName")
            tableJsons[table] = JSONArray(String(tableBytes, StandardCharsets.UTF_8))
        }

        // Verify checksums of settings.json and every required table
        val requiredFiles = REQUIRED_TABLES.map { "$it.json" } + "settings.json"
        for (fileName in requiredFiles) {
            val expectedSha = checksumsJson.optString(fileName)
            check(!expectedSha.isNullOrBlank()) { "Missing checksum for $fileName in meta.json" }
            val actualBytes = unzippedFiles[fileName] ?: error("Missing $fileName in archive")
            val actualSha = sha256Hex(actualBytes)
            check(expectedSha.equals(actualSha, ignoreCase = true)) {
                "Checksum mismatch on $fileName"
            }
        }

        // Acquire write lock to prevent UI or background workers from altering DB during restore
        repository.persistenceLock.writeLock().lock()
        val recoveryDir = File(context.cacheDir, "backup_recovery").apply { mkdirs() }
        val recoveryCopy = File(recoveryDir, "recovery_pre_restore.zip")
        val recoveryJournal = File(recoveryDir, "recovery_journal.json")

        try {
            // Stage recovery copy of current database
            recoveryJournal.writeText("""{"status":"STAGING"}""", StandardCharsets.UTF_8)
            recoveryCopy.outputStream().use { fos ->
                createArchive(repository, fos)
            }
            recoveryJournal.writeText("""{"status":"STAGED"}""", StandardCharsets.UTF_8)

            val db = repository.database.writableDatabase
            db.beginTransaction()
            try {
                // Clear existing records directly
                for (table in REQUIRED_TABLES.asReversed()) {
                    db.delete(table, null, null)
                }

                // Insert directly from backup JSON without triggering business logic
                for (table in REQUIRED_TABLES) {
                    val array = tableJsons[table] ?: error("Missing table $table during insertion")
                    for (i in 0 until array.length()) {
                        val rowObj = array.getJSONObject(i)
                        val values = jsonObjectToContentValues(rowObj)
                        db.insertOrThrow(table, null, values)
                    }
                }

                // Restore settings directly without triggering template price recomputation
                restoreSettings(repository, settingsJson)

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            // Restore succeeded: mark completed and clean up recovery
            recoveryJournal.writeText("""{"status":"COMPLETED"}""", StandardCharsets.UTF_8)
            runCatching {
                recoveryCopy.delete()
                recoveryJournal.delete()
            }

            // Rebuild alarms / reminders
            val restoredSettings = repository.readSettings()
            ReminderScheduler.sync(context, restoredSettings)

            return true
        } catch (e: Exception) {
            // Attempt emergency rollback from recovery copy if journal was STAGED
            var rollbackSucceeded = false
            try {
                if (recoveryCopy.exists()) {
                    recoveryCopy.inputStream().use { recoveryStream ->
                        restoreDirectly(context, repository, recoveryStream)
                    }
                }
                rollbackSucceeded = true
            } catch (_: Exception) {
                // Journal retained for investigation
            }
            if (rollbackSucceeded) {
                runCatching { recoveryCopy.delete() }
                runCatching { recoveryJournal.delete() }
            }
            throw e
        } finally {
            repository.persistenceLock.writeLock().unlock()
        }
    }

    fun checkAndPerformStartupRecovery(context: Context, repository: BudgetRepository) {
        val recoveryDir = File(context.cacheDir, "backup_recovery")
        val recoveryJournal = File(recoveryDir, "recovery_journal.json")
        val recoveryCopy = File(recoveryDir, "recovery_pre_restore.zip")
        if (!recoveryJournal.exists()) return

        repository.persistenceLock.writeLock().lock()
        try {
            val journalText = runCatching { recoveryJournal.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return
            val status = runCatching { JSONObject(journalText).optString("status") }.getOrNull()
            if (status == "STAGED") {
                if (recoveryCopy.exists()) {
                    recoveryCopy.inputStream().use { stream ->
                        restoreDirectly(context, repository, stream)
                    }
                }
            }
            recoveryCopy.delete()
            recoveryJournal.delete()
        } catch (_: Exception) {
        } finally {
            repository.persistenceLock.writeLock().unlock()
        }
    }

    private fun restoreDirectly(context: Context, repository: BudgetRepository, inputStream: InputStream) {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val tableJsons = mutableMapOf<String, JSONArray>()
        var settingsJson: JSONObject? = null

        while (entry != null) {
            val name = entry.name
            val bytes = zip.readBytes()
            val tableName = name.removeSuffix(".json")
            if (tableName == "settings") {
                settingsJson = JSONObject(String(bytes, StandardCharsets.UTF_8))
            } else if (tableName in REQUIRED_TABLES) {
                tableJsons[tableName] = JSONArray(String(bytes, StandardCharsets.UTF_8))
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        val db = repository.database.writableDatabase
        db.beginTransaction()
        try {
            for (table in REQUIRED_TABLES.asReversed()) {
                db.delete(table, null, null)
            }
            for (table in REQUIRED_TABLES) {
                val array = tableJsons[table] ?: continue
                for (i in 0 until array.length()) {
                    val rowObj = array.getJSONObject(i)
                    val values = jsonObjectToContentValues(rowObj)
                    db.insertOrThrow(table, null, values)
                }
            }
            if (settingsJson != null) {
                restoreSettings(repository, settingsJson)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun readTableAsJson(db: SQLiteDatabase, table: String): JSONArray {
        val array = JSONArray()
        db.rawQuery("SELECT * FROM $table", null).use { cursor ->
            val colNames = cursor.columnNames
            while (cursor.moveToNext()) {
                val obj = JSONObject()
                for (i in colNames.indices) {
                    val name = colNames[i]
                    if (cursor.isNull(i)) {
                        obj.put(name, JSONObject.NULL)
                    } else {
                        when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(name, cursor.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> obj.put(name, cursor.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING -> obj.put(name, cursor.getString(i))
                            android.database.Cursor.FIELD_TYPE_BLOB -> obj.put(name, cursor.getBlob(i))
                            else -> obj.put(name, cursor.getString(i))
                        }
                    }
                }
                array.put(obj)
            }
        }
        return array
    }

    private fun jsonObjectToContentValues(obj: JSONObject): ContentValues {
        val values = ContentValues()
        for (key in obj.keys()) {
            val v = obj.get(key)
            if (v == null || v == JSONObject.NULL) {
                values.putNull(key)
            } else when (v) {
                is Int -> values.put(key, v)
                is Long -> values.put(key, v)
                is Double -> values.put(key, v)
                is Boolean -> values.put(key, if (v) 1 else 0)
                is String -> values.put(key, v)
                else -> values.put(key, v.toString())
            }
        }
        return values
    }

    private fun settingsToJson(s: BudgetSettings): JSONObject = JSONObject().apply {
        put("monthlyFoodBudget", s.monthlyFoodBudget)
        put("dailyFoodBudget", s.dailyFoodBudget)
        put("weeklyFoodLimit", s.weeklyFoodLimit)
        put("kosharyDay", s.kosharyDay.name)
        put("kosharyPrice", s.kosharyPrice)
        put("autoSaveSpares", s.autoSaveSpares)
        put("remindersEnabled", s.remindersEnabled)
        put("shoppingDay", s.shoppingDay.name)
        put("lastSparesAutoSaveDate", s.lastSparesAutoSaveDate?.toString() ?: JSONObject.NULL)
        put("themeMode", s.themeMode.name)
        put("foodPreferences", s.foodPreferences)
        put("foodExclusions", s.foodExclusions)
        put("habitMessagesEnabled", s.habitMessagesEnabled)
        put("breakfastStart", s.breakfastStart.toString())
        put("breakfastEnd", s.breakfastEnd.toString())
        put("lunchStart", s.lunchStart.toString())
        put("lunchEnd", s.lunchEnd.toString())
        put("dinnerStart", s.dinnerStart.toString())
        put("dinnerEnd", s.dinnerEnd.toString())
    }

    private fun restoreSettings(repository: BudgetRepository, json: JSONObject) {
        val bStart = json.optString("breakfastStart").takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(6, 0)
        val bEnd = json.optString("breakfastEnd").takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(11, 0)
        val lStart = json.optString("lunchStart").takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(12, 0)
        val lEnd = json.optString("lunchEnd").takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(17, 0)
        val dStart = json.optString("dinnerStart").takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(18, 0)
        val dEnd = json.optString("dinnerEnd").takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(23, 0)

        val settings = BudgetSettings(
            monthlyFoodBudget = json.optDouble("monthlyFoodBudget", 3_000.0),
            dailyFoodBudget = json.optDouble("dailyFoodBudget", 100.0),
            weeklyFoodLimit = json.optDouble("weeklyFoodLimit", 750.0),
            kosharyDay = runCatching { DayOfWeek.valueOf(json.optString("kosharyDay")) }.getOrDefault(DayOfWeek.WEDNESDAY),
            kosharyPrice = json.optDouble("kosharyPrice", 60.0),
            autoSaveSpares = json.optBoolean("autoSaveSpares", true),
            remindersEnabled = json.optBoolean("remindersEnabled", true),
            shoppingDay = runCatching { DayOfWeek.valueOf(json.optString("shoppingDay")) }.getOrDefault(DayOfWeek.FRIDAY),
            lastSparesAutoSaveDate = json.optString("lastSparesAutoSaveDate").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            themeMode = runCatching { AppThemeMode.valueOf(json.optString("themeMode")) }.getOrDefault(AppThemeMode.SYSTEM),
            foodPreferences = json.optString("foodPreferences", ""),
            foodExclusions = json.optString("foodExclusions", ""),
            habitMessagesEnabled = json.optBoolean("habitMessagesEnabled", true),
            breakfastStart = bStart,
            breakfastEnd = bEnd,
            lunchStart = lStart,
            lunchEnd = lEnd,
            dinnerStart = dStart,
            dinnerEnd = dEnd,
        )
        repository.restoreSettingsDirectly(settings)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}

package com.budgetmeals.app.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = BackupManager(applicationContext)
        val success = manager.performAutoBackup()
        return if (success) Result.success() else Result.retry()
    }
}

package com.budgetmeals.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun shareExport(
    context: Context,
    content: String,
    fileNamePrefix: String,
    extension: String,
    mimeType: String,
    subject: String,
    chooserTitle: String,
) {
    val uri = withContext(Dispatchers.IO) {
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDirectory, "$fileNamePrefix-${LocalDate.now()}.$extension")
        exportFile.writeText(content)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
    }
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            chooserTitle,
        ),
    )
}

package com.mtgscanner.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Hands a file to another app through Android's share sheet (via the app's FileProvider). */
object ShareFile {
    private fun dir(context: Context) = File(context.cacheDir, "shared").apply { mkdirs() }

    /** A safe file name from [name], like "Trade binder" → "Trade-binder". */
    fun fileName(name: String, ext: String): String =
        name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "grimoire" }.take(60) + ".$ext"

    suspend fun html(context: Context, name: String, html: String) {
        val file = withContext(Dispatchers.IO) { File(dir(context), fileName(name, "html")).apply { writeText(html) } }
        send(context, file, "text/html", name)
    }

    private fun send(context: Context, file: File, type: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(title, uri)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

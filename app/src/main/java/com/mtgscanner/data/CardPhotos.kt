package com.mtgscanner.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos of your own copies, kept in app storage (filesDir/photos).
 * The app has no FileProvider, so the camera writes into a temporary MediaStore entry
 * (Android 10+, no permission needed) that is copied in and then deleted.
 */
object CardPhotos {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** Copies the picture at [uri] into app storage; returns the saved file's path, or null on failure. */
    suspend fun save(ctx: Context, uri: Uri, cardId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(dir(ctx), "card-$cardId-${System.currentTimeMillis()}.jpg")
            val input = ctx.contentResolver.openInputStream(uri) ?: return@runCatching null
            input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            if (out.length() == 0L) { out.delete(); null } else out.absolutePath
        }.getOrNull()
    }

    /** Deletes a saved photo, only if it lives in our photos folder. */
    suspend fun delete(ctx: Context, path: String): Unit = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        runCatching {
            val f = File(path)
            if (f.parentFile?.canonicalPath == dir(ctx).canonicalPath) f.delete()
        }
    }

    /** Whether the camera can be used (needs Android 10+ for a permission-free target). */
    val canUseCamera: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** A temporary target the camera app can write to, or null when not possible. */
    fun cameraTarget(ctx: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "mtg-card-${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MTG Scanner")
            }
            ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    /** Removes a temporary camera target made by [cameraTarget]. */
    suspend fun discard(ctx: Context, uri: Uri): Unit = withContext(Dispatchers.IO) {
        runCatching { ctx.contentResolver.delete(uri, null, null) }
    }
}

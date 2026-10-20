package com.mtgscanner.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Photos of your own copies, kept in app storage (filesDir/photos).
 * The app has no FileProvider, so the camera writes into a temporary MediaStore entry
 * (Android 10+, no permission needed) that is copied in and then deleted.
 */
object CardPhotos {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** Longest side of a saved photo: plenty to see wear on a card, a fraction of a camera's full size. */
    private const val MAX_SIDE = 1600

    /**
     * Saves the picture at [uri] into app storage, scaled down to [MAX_SIDE] and turned upright.
     * Returns the saved file's path, or null on failure.
     */
    suspend fun save(ctx: Context, uri: Uri, cardId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(dir(ctx), "card-$cardId-${System.currentTimeMillis()}.jpg")
            val small = runCatching { downscaled(ctx, uri) }.getOrNull()
            if (small != null) {
                out.outputStream().use { o -> small.compress(Bitmap.CompressFormat.JPEG, 88, o) }
                small.recycle()
            } else {
                // Couldn't decode it here; keep the original rather than nothing.
                val input = ctx.contentResolver.openInputStream(uri) ?: return@runCatching null
                input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
            if (out.length() == 0L) { out.delete(); null } else out.absolutePath
        }.getOrNull()
    }

    /** The picture at [uri] decoded at most [MAX_SIDE] pixels on its longest side, rotated as its EXIF data says. */
    private fun downscaled(ctx: Context, uri: Uri): Bitmap? {
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val degrees = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        if (degrees == 0 && scale >= 1f) return decoded
        val m = Matrix().apply { postScale(scale, scale); postRotate(degrees.toFloat()) }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (result !== decoded) decoded.recycle()
        return result
    }

    /** Degrees to turn a photo upright, from its EXIF orientation. */
    fun exifRotation(input: InputStream): Int = runCatching {
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }.getOrDefault(0)

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

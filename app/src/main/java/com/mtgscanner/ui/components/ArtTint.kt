package com.mtgscanner.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The main color of a card's art, worked out from a tiny copy of the picture. Kept free of Android so it can be tested. */
object ArtTint {
    /** Hue (0..360) and saturation (0..1) of the art's strongest color, or null for grey or very dark art. */
    fun pick(pixels: IntArray): Pair<Float, Float>? {
        var x = 0.0
        var y = 0.0
        var weight = 0.0
        var satSum = 0.0
        for (p in pixels) {
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val v = max
            val s = if (max == 0f) 0f else (max - min) / max
            // Near-black and washed-out pixels say little about the art's color.
            if (v < 0.18f || s < 0.15f) continue
            val h = hue(r, g, b, max, min)
            val w = (s * v).toDouble()
            val rad = Math.toRadians(h.toDouble())
            x += cos(rad) * w
            y += sin(rad) * w
            weight += w
            satSum += s * w
        }
        // Too little color: leave the page its own colors.
        if (weight < pixels.size * 0.04) return null
        val h = ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
        return h to (satSum / weight).toFloat().coerceIn(0f, 1f)
    }

    private fun hue(r: Float, g: Float, b: Float, max: Float, min: Float): Float {
        val d = max - min
        if (d == 0f) return 0f
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return if (h < 0) h + 360f else h
    }

    /** A soft color in the art's hue, dark enough for light text on it. */
    fun color(hue: Float, sat: Float): Color = Color.hsv(hue, (sat * 0.8f).coerceIn(0.25f, 0.6f), 0.42f)

    const val PX = 24
}

/** Tints already worked out, by picture link; grey art is remembered too, so it isn't worked out again. */
private object TintCache {
    private val cache = LruCache<String, Any>(120)
    private val NONE = Any()

    fun cached(url: String): Color? = cache.get(url) as? Color
    fun known(url: String): Boolean = cache.get(url) != null
    fun put(url: String, c: Color?) { cache.put(url, c ?: NONE) }
}

/**
 * The tint of [imageUrl]'s art (best with an art crop), or null while it loads, for grey art, or when it can't load.
 * Use it for headers and tabs; buttons keep the accent.
 */
@Composable
fun rememberArtTint(imageUrl: String?): Color? {
    val url = imageUrl ?: return null
    val context = LocalContext.current
    var tint by remember(url) { mutableStateOf(TintCache.cached(url)) }
    LaunchedEffect(url) {
        if (TintCache.known(url)) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(ArtTint.PX, ArtTint.PX).allowHardware(false).build()
        val drawable = (com.mtgscanner.util.suspendRunCatching { context.imageLoader.execute(request) }.getOrNull() as? SuccessResult)?.drawable
        val found = drawable?.let { d ->
            withContext(Dispatchers.Default) {
                val bmp = Bitmap.createBitmap(ArtTint.PX, ArtTint.PX, Bitmap.Config.ARGB_8888)
                try {
                    d.setBounds(0, 0, ArtTint.PX, ArtTint.PX)
                    d.draw(Canvas(bmp))
                    val px = IntArray(ArtTint.PX * ArtTint.PX)
                    bmp.getPixels(px, 0, ArtTint.PX, 0, 0, ArtTint.PX, ArtTint.PX)
                    ArtTint.pick(px)?.let { (h, s) -> ArtTint.color(h, s) }
                } finally {
                    bmp.recycle()
                }
            }
        }
        // A picture that didn't load (offline) isn't remembered as grey: the next visit tries again.
        if (drawable != null) TintCache.put(url, found)
        tint = found
    }
    return tint
}

/** The page background with a little of [tint] in it; the background itself without one. */
fun tintedBackground(tint: Color?, amount: Float = 0.28f): Color = if (tint == null) Palette.Background else lerp(Palette.Background, tint, amount)

package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Motion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.ui.components.animationsOff
import com.mtgscanner.ui.theme.Palette
import kotlin.math.exp

/** How long a card's last seen corners still count; after that the frame glides back to the guide. */
private const val FRESH_MS = 900L

/**
 * Where the frame on screen is right now, in window pixels: read when a card is added, so it flies off from the
 * spot (and at the tilt) the card was seen.
 */
class TrackedCard {
    @Volatile internal var points: FloatArray? = null
    @Volatile internal var origin: Offset = Offset.Zero
    @Volatile internal var onCard: Boolean = false

    /** The card's own upright box (window pixels) and tilt in degrees, or null when the frame isn't on a card. */
    fun pose(): Pair<Rect, Float>? {
        val p = points ?: return null
        if (!onCard) return null
        val (w, h, angle) = TrackMath.cardPose(p)
        val cx = (p[0] + p[2] + p[4] + p[6]) / 4f + origin.x
        val cy = (p[1] + p[3] + p[5] + p[7]) / 4f + origin.y
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f) to angle
    }
}

/**
 * The card-shaped frame over the camera. It rests in the middle as a guide; when the scanner sees a card it glides
 * onto the card's corners and follows it, and goes back when the card leaves. Turns accent when a card is
 * recognized and flashes when one is added. [guide] gets the resting frame's spot (for hints).
 */
@Composable
internal fun TrackingFrame(
    found: Boolean, flash: Int, paused: Boolean, tracked: TrackedCard, guide: Modifier, flashColor: Color = Palette.Accent,
) {
    val ctx = LocalContext.current
    val still = remember(ctx) { animationsOff(ctx) }
    val seen by CardTrack.seen.collectAsStateWithLifecycle()
    val color by animateColorAsState(
        when {
            paused -> Color.White.copy(alpha = 0.25f)
            found -> Palette.Accent
            else -> Color.White.copy(alpha = 0.55f)
        },
        label = "frame",
    )
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash > 0) {
            flashing = true
            kotlinx.coroutines.delay(220)
            flashing = false
        }
    }
    val fill by animateFloatAsState(if (flashing) 0.25f else 0f, tween(Motion.base, easing = Motion.ease), label = "flash")

    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val seenNow by rememberUpdatedState(seen)
    val pausedNow by rememberUpdatedState(paused)
    // Where the frame should be: on the card while it's fresh, else the guide in the middle.
    fun target(now: Long): Pair<FloatArray, Boolean>? {
        val sz = size
        if (sz.width <= 0f) return null
        val s = seenNow
        if (s != null && !pausedNow && now - s.at < FRESH_MS) {
            return TrackMath.clockwise(TrackMath.toView(s.points, s.aspect, sz.width, sz.height)) to true
        }
        val w = sz.width * 0.66f
        val h = w * 88f / 63f
        val l = (sz.width - w) / 2f
        val t = (sz.height - h) / 2f
        return floatArrayOf(l, t, l + w, t, l + w, t + h, l, t + h) to false
    }
    // Read only while drawing, so each step redraws the frame without recomposing the screen.
    var shown by remember { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 16f else (t - last) / 1_000_000f
                last = t
                val (goal, onCard) = target(android.os.SystemClock.elapsedRealtime()) ?: return@withFrameNanos
                tracked.onCard = onCard
                val cur = shown
                if (cur != null && (0 until 8).all { kotlin.math.abs(goal[it] - cur[it]) < 0.5f }) return@withFrameNanos
                // Eases toward the goal (about 90 ms to cover most of the way), so it glides rather than jitters.
                val k = if (still || cur == null) 1f else (1f - exp(-dt / 90f))
                val next = FloatArray(8) { i -> if (cur == null) goal[i] else cur[i] + (goal[i] - cur[i]) * k }
                shown = next
                tracked.points = next
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The resting spot, for hints; drawn by the canvas below.
        Box(guide.align(Alignment.Center).fillMaxWidth(0.66f).aspectRatio(63f / 88f))
        Canvas(
            Modifier.fillMaxSize().onGloballyPositioned {
                size = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                tracked.origin = it.positionInWindow()
            },
        ) {
            val p = shown ?: return@Canvas
            val path = Path().apply {
                moveTo(p[0], p[1]); lineTo(p[2], p[3]); lineTo(p[4], p[5]); lineTo(p[6], p[7]); close()
            }
            if (fill > 0f) drawPath(path, flashColor.copy(alpha = fill))
            drawPath(
                path, if (flashing) flashColor else color,
                style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(10.dp.toPx())),
            )
        }
    }
}

package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Motion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import com.mtgscanner.ui.theme.CardShape

/**
 * App-wide "hold to zoom" for card images. A long press on a card shows it big over a dimmed
 * screen; drag it away (or tap outside, or Back) to close. Drawn by [CardZoomHost] at the app root.
 */
object CardZoom {
    class Shown(val urls: List<String>, val underlay: String?, val key: Int)

    var shown by mutableStateOf<Shown?>(null)
        private set

    /** How far the big card has been dragged from the middle, in px. */
    internal var offset by mutableStateOf(Offset.Zero)

    /** Bumped when a drag that began on the small image ends; the host then settles the card. */
    internal var releases by mutableIntStateOf(0)
    internal var releaseVelocity = Velocity.Zero
    private var count = 0

    /** Shows [imageUrl] big, in Scryfall's large size when it can, falling back to [fallbackUrl]. */
    fun show(imageUrl: String?, fallbackUrl: String? = null) {
        val big = largeCardImage(imageUrl)
        val urls = listOfNotNull(big, scryfallImageFallback(big), imageUrl, largeCardImage(fallbackUrl), fallbackUrl).distinct()
        if (urls.isEmpty()) return
        offset = Offset.Zero
        shown = Shown(urls, imageUrl ?: fallbackUrl, ++count)
        com.mtgscanner.util.Haptics.tick()
    }

    fun hide() {
        shown = null
        offset = Offset.Zero
    }

    internal fun dragBy(delta: Offset) {
        if (shown != null) offset += delta
    }

    internal fun release(velocity: Velocity) {
        if (shown == null) return
        releaseVelocity = velocity
        releases++
    }
}

private val SIZE_IN_PATH = Regex("""/(small|normal)/(front|back)/""")
private val SIZE_IN_QUERY = Regex("""version=(small|normal)""")

/** The "large" Scryfall image for a small or normal Scryfall image link; other links unchanged. */
fun largeCardImage(url: String?): String? {
    if (url == null || "scryfall" !in url) return url
    return url.replace(SIZE_IN_PATH, "/large/\$2/").replace(SIZE_IN_QUERY, "version=large")
}

/**
 * Tap and hold to show this card big ([CardZoom]). Keeping the finger down and dragging moves the
 * big card, so one motion can peek and throw it away.
 *
 * Without [onTap], taps go to whatever is clickable underneath (a row, a tile) and a long press
 * only zooms. With [onTap], the image takes its own taps, so a parent's long press (like starting
 * to pick cards) doesn't also fire when the image is held.
 */
fun Modifier.cardZoom(url: String?, fallback: String? = null, onTap: (() -> Unit)? = null): Modifier =
    if (url == null && fallback == null) this else this then CardZoomElement(url, fallback, onTap)

/** A plain modifier node, not `composed`: grids put one on every picture. */
private data class CardZoomElement(val url: String?, val fallback: String?, val onTap: (() -> Unit)?) : ModifierNodeElement<CardZoomNode>() {
    override fun create() = CardZoomNode(url, fallback, onTap)
    override fun update(node: CardZoomNode) = node.update(url, fallback, onTap)
}

private class CardZoomNode(
    private var url: String?,
    private var fallback: String?,
    private var onTap: (() -> Unit)?,
) : DelegatingNode(), PointerInputModifierNode {
    private val input = delegate(SuspendingPointerInputModifierNode { zoomGesture() })

    fun update(url: String?, fallback: String?, onTap: (() -> Unit)?) {
        // A new picture, or a change between taking taps and passing them on, restarts the gesture.
        val restart = url != this.url || fallback != this.fallback || (onTap != null) != (this.onTap != null)
        this.url = url
        this.fallback = fallback
        this.onTap = onTap
        if (restart) input.resetPointerInputHandler()
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) =
        input.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = input.onCancelPointerInput()

    private suspend fun PointerInputScope.zoomGesture() {
        val exclusive = onTap != null
        awaitEachGesture {
            val down = awaitFirstDown()
            if (exclusive) down.consume()
            var held = false
            val up = try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            } catch (e: PointerEventTimeoutCancellationException) {
                held = true
                null
            }
            if (!held) {
                if (up != null && exclusive) {
                    up.consume()
                    onTap?.invoke()
                }
                return@awaitEachGesture
            }
            CardZoom.show(url, fallback)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            // Keep this finger: its moves drag the big card, and nothing underneath scrolls or clicks.
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                val delta = change.positionChange()
                if (delta != Offset.Zero) CardZoom.dragBy(delta)
                tracker.addPosition(change.uptimeMillis, change.position)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
            CardZoom.release(tracker.calculateVelocity())
        }
    }
}

/** The big card over a dimmed screen. Put once at the app root, above everything else. */
@Composable
fun CardZoomHost() {
    val shown = CardZoom.shown ?: return
    val scope = rememberCoroutineScope()
    val appear = remember(shown.key) { Animatable(0f) }
    val settling = remember(shown.key) { arrayOfNulls<Job>(1) }
    var closing by remember(shown.key) { mutableStateOf(false) }
    val density = LocalDensity.current
    val throwDistance = with(density) { 110.dp.toPx() }
    val throwSpeed = with(density) { 1200.dp.toPx() }
    val farAway = with(density) { 1600.dp.toPx() }

    LaunchedEffect(shown.key) {
        appear.animateTo(1f, tween(Motion.base, easing = FastOutSlowInEasing))
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            appear.animateTo(0f, tween(Motion.fast))
            CardZoom.hide()
        }
    }

    // Past a short distance (or a quick flick) the card flies off and closes; otherwise it springs back.
    fun settle(velocity: Velocity) {
        if (closing) return
        val start = CardZoom.offset
        val dist = hypot(start.x, start.y)
        val speed = hypot(velocity.x, velocity.y)
        settling[0]?.cancel()
        if (dist > throwDistance || (speed > throwSpeed && dist > throwDistance / 4)) {
            closing = true
            val dir = if (speed > throwSpeed) Offset(velocity.x / speed, velocity.y / speed) else Offset(start.x / dist, start.y / dist)
            settling[0] = scope.launch {
                animate(0f, 1f, animationSpec = tween(Motion.base)) { t, _ -> CardZoom.offset = start + dir * (farAway * t) }
                CardZoom.hide()
            }
        } else {
            settling[0] = scope.launch {
                animate(0f, 1f, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) { t, _ ->
                    CardZoom.offset = start * (1f - t)
                }
            }
        }
    }

    // A drag that began on the small image (finger still down from the long press) ends here.
    LaunchedEffect(shown.key) {
        var seen = CardZoom.releases
        snapshotFlow { CardZoom.releases }.collect { r ->
            if (r != seen) {
                seen = r
                settle(CardZoom.releaseVelocity)
            }
        }
    }

    BackHandler { close() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(shown.key) { detectTapGestures(onTap = { close() }) }
            .pointerInput(shown.key) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { settling[0]?.cancel(); tracker.resetTracking() },
                    onDragEnd = { settle(tracker.calculateVelocity()) },
                    onDragCancel = { settle(Velocity.Zero) },
                    onDrag = { change, delta ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!closing) CardZoom.dragBy(delta)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val w = min(min(maxWidth * 0.86f, maxHeight * 0.86f * (63f / 88f)), 480.dp)
        val offset = CardZoom.offset
        val fade = 1f - (hypot(offset.x, offset.y) / (throwDistance * 3f)).coerceIn(0f, 0.85f)
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = (appear.value * fade).coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.82f)),
        )
        Box(
            Modifier
                .size(w, w * (88f / 63f))
                .graphicsLayer {
                    val o = CardZoom.offset
                    val s = 0.35f + 0.65f * appear.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value.coerceIn(0f, 1f)
                    translationX = o.x
                    translationY = o.y
                    rotationZ = (o.x / max(size.width, 1f)) * 12f
                }
                .clip(CardShape)
                .background(Palette.SurfaceHigh)
                // Taps on the card itself don't close it; drags still move it.
                .pointerInput(shown.key) { detectTapGestures { } },
        ) {
            // The small image is usually cached already, so it shows at once while the big one loads.
            if (shown.underlay != null) {
                AsyncImage(shown.underlay, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
            var index by remember(shown.key) { mutableIntStateOf(0) }
            if (index < shown.urls.size) {
                AsyncImage(
                    model = shown.urls[index],
                    contentDescription = "Card, enlarged",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onError = { index++ },
                )
            }
        }
    }
}

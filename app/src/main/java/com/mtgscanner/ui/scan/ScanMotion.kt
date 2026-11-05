package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.animationsOff
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.theme.Palette
import kotlin.math.roundToInt
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.CardShape

private val Fan = FanLayout()

/**
 * The cards scanned this session, fanned like a solitaire column: the newest in front, a little lower
 * and to the right, older ones showing just their name bar. Shows the last [FanLayout.max]. Tap to
 * open the scan list. Lines with keys above [startKey] (added since the screen opened) slide in.
 */
@Composable
fun SessionFan(session: List<ScanEntry>, startKey: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (session.isEmpty()) return
    val ctx = LocalContext.current
    val still = remember(ctx) { animationsOff(ctx) }
    val shown = remember(session) { session.take(Fan.max).reversed() }
    val count = session.sumOf { it.count }
    val height by animateFloatAsState(Fan.height(shown.size), tween(if (still) 0 else 200), label = "fanH")
    val width by animateFloatAsState(Fan.width(shown.size), tween(if (still) 0 else 200), label = "fanW")
    Box(
        modifier
            .size(width.dp, height.dp)
            .semantics { contentDescription = "Scan list, $count cards" }
            .clickable(onClickLabel = "Open scan list", onClick = onClick)
            // Fade the bottom edge so the front card tucks away instead of being cut off.
            .graphicsLayer { clip = true; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        shown.forEachIndexed { i, e ->
            key(e.key) {
                val slot by animateFloatAsState(i.toFloat(), tween(if (still) 0 else 220), label = "slot")
                val enter = remember { Animatable(if (e.key > startKey && !still) 0f else 1f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
                FanCard(
                    e,
                    Modifier
                        .offset { IntOffset(Fan.slotX(slot).dp.roundToPx(), Fan.slotY(slot).dp.roundToPx()) }
                        .graphicsLayer {
                            val p = enter.value
                            // Slides in from the right and settles into place.
                            translationX = (1f - p) * 36.dp.toPx()
                            translationY = (1f - p) * -10.dp.toPx()
                            alpha = p
                        },
                )
            }
        }
    }
}

@Composable
private fun FanCard(e: ScanEntry, modifier: Modifier) {
    val shape = CardShape
    Box(
        modifier
            .size(Fan.cardWidth.dp, (Fan.cardWidth * 88f / 63f).dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Palette.ArtBase),
    ) {
        // Drawn name bar, seen while the picture loads or when it can't.
        Text(
            e.card.name,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Space.s2, vertical = Space.s1),
        )
        AsyncImage(
            model = e.card.imageUrl ?: e.card.smallImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        if (e.count > 1) {
            Text(
                "×${e.count}",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Space.s1, end = Space.s1)
                    .clip(RoundedCornerShape(Corners.Small))
                    .background(Palette.Gold)
                    .padding(horizontal = Space.s1),
            )
        }
    }
}

/** One card on its way to its box: where it starts and ends (window coordinates) and the label it lands on. */
data class Flight(val id: Int, val imageUrl: String?, val from: Rect, val to: Rect, val label: String, val color: Int, val angle: Float = 0f)

/**
 * After an add, the card's picture shrinks from the frame toward the scan pile, and a small chip there
 * says which box it went to; then both fade. About a second in all. It never catches touches, so
 * scanning on is never held up.
 */
@Composable
fun AddFlightOverlay(flight: Flight?, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val f = flight ?: return@Box
        key(f.id) { FlightOf(f, origin) }
    }
}

@Composable
private fun FlightOf(f: Flight, origin: Offset) {
    val ctx = LocalContext.current
    val still = remember(ctx) { animationsOff(ctx) }
    val move = remember { Animatable(if (still) 1f else 0f) }
    val chip = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!still) move.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        chip.animateTo(1f, tween(140))
        kotlinx.coroutines.delay(750)
        chip.animateTo(0f, tween(220, easing = LinearEasing))
    }
    val from = f.from.translate(-origin.x, -origin.y)
    val to = f.to.translate(-origin.x, -origin.y)
    val density = LocalDensity.current
    val endScale = (to.height * 0.9f / from.height).coerceIn(0.08f, 1f)

    if (!still) {
        val widthDp = with(density) { from.width.toDp() }
        CardImage(
            f.imageUrl,
            Modifier
                .offset { IntOffset(from.left.roundToInt(), from.top.roundToInt()) }
                .width(widthDp)
                .graphicsLayer {
                    val p = move.value
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    val scale = 1f + (endScale - 1f) * p
                    scaleX = scale
                    scaleY = scale
                    translationX = (to.center.x - from.center.x) * p
                    translationY = (to.center.y - from.center.y) * p
                    // Starts at the card's own tilt, ending in a slight tilt, like a card being tossed.
                    rotationZ = f.angle + (-8f - f.angle) * p
                    alpha = if (p < 0.65f) 1f else 1f - (p - 0.65f) / 0.35f
                },
        )
    }

    // The box label, just above where the card landed.
    var chipSize by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { 8.dp.toPx() }
    Row(
        Modifier
            .offset {
                val x = (to.center.x - chipSize.width / 2f).coerceAtLeast(gap)
                val y = (to.top - chipSize.height - gap).coerceAtLeast(0f)
                IntOffset(x.roundToInt(), y.roundToInt())
            }
            .onSizeChanged { chipSize = it }
            .graphicsLayer {
                alpha = chip.value
                val s = 0.85f + 0.15f * chip.value
                scaleX = s
                scaleY = s
            }
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(Corners.Small))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.dp, boxColor(f.color).copy(alpha = 0.8f), RoundedCornerShape(Corners.Small))
            .padding(horizontal = Space.s3, vertical = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(boxColor(f.color)))
        Spacer(Modifier.width(Space.s2))
        Text(f.label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

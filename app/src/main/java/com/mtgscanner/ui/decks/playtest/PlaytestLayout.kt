package com.mtgscanner.ui.decks.playtest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.mtgscanner.ui.theme.Motion
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Space

/** Where each card of a row sits; pure, for tests. */
object FitMath {
    /**
     * The step from one card's left edge to the next: the card's width and a [gap] when [count] cards fit in
     * [available], or less so they overlap and the last one ends at the edge.
     */
    fun step(count: Int, card: Float, gap: Float, available: Float): Float {
        if (count <= 1) return card + gap
        val full = card + gap
        if (count * card + (count - 1) * gap <= available) return full
        return ((available - card) / (count - 1)).coerceAtLeast(0f)
    }
}

/**
 * Cards side by side at [cardWidth] (a card is 1.4 times as tall), overlapping like a held hand when they don't all
 * fit, so the whole row always shows without scrolling. Later cards sit on top of earlier ones.
 */
@Composable
fun <T> FitRow(
    items: List<T>, cardWidth: Dp, modifier: Modifier = Modifier,
    /** Each card's identity, so a card that moves along the row slides there and a new one fades in. */
    id: (T) -> Any,
    /** Draw order: a card being dragged goes over the rest. */
    z: (T) -> Float = { 0f },
    card: @Composable (T) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val step = FitMath.step(items.size, cardWidth.value, Space.s1.value, maxWidth.value).dp
        Box(Modifier.fillMaxWidth().height(cardWidth * 1.4f)) {
            items.forEachIndexed { i, item ->
                key(id(item)) {
                    val x by animateDpAsState(step * i, tween(Motion.base, easing = Motion.ease), label = "slide")
                    // A card that just arrived grows in from a little smaller.
                    val shown = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { shown.animateTo(1f, tween(Motion.base, easing = Motion.ease)) }
                    Box(
                        Modifier.offset(x = x).zIndex(z(item)).graphicsLayer {
                            alpha = shown.value
                            val k = 0.85f + 0.15f * shown.value
                            scaleX = k; scaleY = k
                        },
                    ) { card(item) }
                }
            }
        }
    }
}

package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette

/*
 * Gray shapes shown while something loads, in place of a spinner, so the screen keeps the
 * shape of what's coming. The default color is Palette.Surface (a step lighter than the page);
 * on a sheet or card that is already Surface, pass Palette.SurfaceHigh.
 */

/** A slow, faint pulse shared by the shapes in one placeholder. */
@Composable
private fun pulse(): State<Float> =
    rememberInfiniteTransition(label = "placeholder").animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "placeholderPulse",
    )

/** [count] rounded bars the height of a list row. */
@Composable
fun PlaceholderRows(
    count: Int = 6,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 56.dp,
    color: Color = Palette.Surface,
) {
    val a = pulse()
    Column(
        modifier.fillMaxWidth().graphicsLayer { alpha = a.value },
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        repeat(count) {
            Box(Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(Corners.Medium)).background(color))
        }
    }
}

/** [count] card-sized boxes, [columns] to a row. */
@Composable
fun PlaceholderCards(
    count: Int = 6,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    color: Color = Palette.Surface,
) {
    val a = pulse()
    val perRow = columns.coerceAtLeast(1)
    Column(
        modifier.fillMaxWidth().graphicsLayer { alpha = a.value },
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        repeat((count + perRow - 1) / perRow) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                repeat(perRow) { col ->
                    val shown = row * perRow + col < count
                    Box(
                        Modifier.weight(1f).aspectRatio(63f / 88f)
                            .then(if (shown) Modifier.clip(CardShape).background(color) else Modifier),
                    )
                }
            }
        }
    }
}

/** One rounded block of [height], for a chart, header or panel. */
@Composable
fun PlaceholderBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = Palette.Surface,
) {
    val a = pulse()
    Box(
        modifier.fillMaxWidth().height(height).graphicsLayer { alpha = a.value }
            .clip(RoundedCornerShape(Corners.Medium)).background(color),
    )
}

/** A loading card page: the card, then a few rows. */
@Composable
fun PlaceholderPage(modifier: Modifier = Modifier, color: Color = Palette.Surface) {
    Column(modifier.fillMaxWidth()) {
        PlaceholderBlock(320.dp, color = color)
        Spacer(Modifier.height(Space.s3))
        PlaceholderRows(3, color = color)
    }
}

/** The app's one loading circle: thin, round-ended, in the accent, at a calm size. */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 28.dp, color: Color = Palette.Muted) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier.size(size),
        color = color,
        strokeWidth = 2.5.dp,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        trackColor = color.copy(alpha = 0.15f),
    )
}

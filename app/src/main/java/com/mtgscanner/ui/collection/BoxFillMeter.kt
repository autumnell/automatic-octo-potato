package com.mtgscanner.ui.collection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette

/** The color a box's count and meter take: its own color, gold when nearly full, red when full. */
fun fillColor(state: BoxFill, normal: Color): Color = when (state) {
    BoxFill.OK -> normal
    BoxFill.NEAR -> Palette.Gold
    BoxFill.FULL -> Palette.Danger
}

/** A box's fill as a row of segments, like slots in a shelf. */
@Composable
fun BoxFillMeter(cards: Int, capacity: Int, color: Color, modifier: Modifier = Modifier, thickness: androidx.compose.ui.unit.Dp = 5.dp) {
    if (capacity <= 0) return
    val state = BoxFillMath.state(cards, capacity)
    val n = BoxFillMath.segments(capacity)
    val lit = BoxFillMath.lit(cards, capacity, n)
    val on = fillColor(state, color)
    val off = Palette.Outline.copy(alpha = 0.7f)
    Canvas(modifier.fillMaxWidth().height(thickness).semantics { contentDescription = "$cards of $capacity" }) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val r = CornerRadius(1.5.dp.toPx())
        for (i in 0 until n) {
            drawRoundRect(
                color = if (i < lit) on else off,
                topLeft = Offset(i * (w + gap), 0f),
                size = Size(w, size.height),
                cornerRadius = r,
            )
        }
    }
}

package com.mtgscanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette

/**
 * A small filled line chart. Needs at least two values. With [onScrub], a finger on the chart marks the nearest
 * point and reports its index (null when the finger lifts), so the caller can show that day's value.
 */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier, line: Color = Palette.Accent, onScrub: ((Int?) -> Unit)? = null) {
    if (values.size < 2) return
    var at by remember(values) { mutableStateOf<Int?>(null) }
    val report by rememberUpdatedState(onScrub)
    val touch = if (onScrub == null) Modifier else Modifier.pointerInput(values) {
        fun index(x: Float) = ChartMath.indexAt(x, size.width.toFloat(), values.size)
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            at = index(down.position.x).also { report?.invoke(it) }
            while (true) {
                val e = awaitPointerEvent()
                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                ch.consume()
                val i = index(ch.position.x)
                if (i != at) { at = i; report?.invoke(i); com.mtgscanner.util.Haptics.step() }
            }
            at = null
            report?.invoke(null)
        }
    }
    Canvas(modifier.then(touch)) {
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0.0001 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        fun yOf(v: Double) = (size.height - ((v - min) / span * size.height * 0.85f) - size.height * 0.075f).toFloat()
        val path = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yOf(v)
            if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        at?.let { i ->
            val x = i * stepX
            drawLine(Palette.Muted, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(line, radius = 4.5.dp.toPx(), center = Offset(x, yOf(values[i])))
        }
    }
}

/** Pure math for [LineChart], so it can be tested. */
object ChartMath {
    /** The point nearest [x] on a chart [width] wide with [count] evenly spaced points. */
    fun indexAt(x: Float, width: Float, count: Int): Int {
        if (count < 2 || width <= 0f) return 0
        return Math.round(x / width * (count - 1)).coerceIn(0, count - 1)
    }
}

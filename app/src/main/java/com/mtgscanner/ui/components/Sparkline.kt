package com.mtgscanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette

/** Sparkline math, kept free of Compose so it can be tested. */
object Spark {
    /** Each point's height from 0 (lowest) to 1 (highest); a flat line sits in the middle. */
    fun heights(points: List<Double>): List<Float> {
        if (points.isEmpty()) return emptyList()
        val lo = points.min()
        val hi = points.max()
        val span = hi - lo
        return if (span <= 1e-9) points.map { 0.5f } else points.map { ((it - lo) / span).toFloat() }
    }

    /** 1 up, -1 down, 0 flat (within half a cent or 0.5%). */
    fun trend(points: List<Double>): Int {
        if (points.size < 2) return 0
        val d = points.last() - points.first()
        val tol = maxOf(0.005, kotlin.math.abs(points.first()) * 0.005)
        return when {
            d > tol -> 1
            d < -tol -> -1
            else -> 0
        }
    }
}

/** A tiny price line: green when it ended up, red when down. Nothing with fewer than two points. */
@Composable
fun PriceSparkline(points: List<Double>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val heights = remember(points) { Spark.heights(points) }
    val color = when (Spark.trend(points)) {
        1 -> Palette.Success
        -1 -> Palette.Danger
        else -> Palette.Muted
    }
    Canvas(modifier.size(width = 36.dp, height = 14.dp)) {
        val stroke = 1.4.dp.toPx()
        val pad = stroke
        val w = size.width
        val h = size.height - pad * 2
        val step = w / (heights.size - 1)
        val path = Path()
        heights.forEachIndexed { i, y ->
            val x = i * step
            val py = pad + h * (1f - y)
            if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // A dot on today's price.
        drawCircle(color, radius = stroke * 1.1f, center = Offset(w - 0.5f, pad + h * (1f - heights.last())))
    }
}

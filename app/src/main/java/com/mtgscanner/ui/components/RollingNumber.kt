package com.mtgscanner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * A number that counts to its new value instead of jumping: from 0 the first time it shows, then from
 * the old value whenever [value] changes. [format] turns the number in between into text (it gets the
 * exact [value] once the count ends). Use a style with tabular figures (PriceStyle) so digits don't jiggle.
 * Coming back to a screen shows the last value straight away; it only counts again when the value moved.
 */
@Composable
fun RollingText(value: Double, format: (Double) -> String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    // What the count last settled on; survives leaving and returning to the screen.
    var settled by rememberSaveable { mutableDoubleStateOf(0.0) }
    var from by remember { mutableDoubleStateOf(settled) }
    var to by remember { mutableDoubleStateOf(settled) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (value == to && progress.value >= 1f) return@LaunchedEffect
        // Start from wherever the count is now, so a change mid-count carries on smoothly.
        val now = from + (to - from) * progress.value
        from = now
        to = value
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = RollingMath.durationMillis(now, value), easing = FastOutSlowInEasing))
        settled = value
    }

    val shown = if (progress.value >= 1f) to else from + (to - from) * progress.value
    // Shrinks to fit rather than cutting off a large value in a wide font.
    AutoSizeText(format(shown), style = style, color = color, modifier = modifier)
}

/** Timing for [RollingText]: longer for bigger jumps, but never slow. */
object RollingMath {
    fun durationMillis(from: Double, to: Double): Int {
        val jump = kotlin.math.abs(to - from)
        if (jump == 0.0) return 0
        // About 450ms for small changes, up to 1100ms for a jump of thousands.
        val scale = kotlin.math.log10(jump + 1.0).coerceIn(0.0, 4.0) / 4.0
        return (450 + 650 * scale).toInt()
    }
}

/** A money total in the user's currency (like formatMoneyShort) that counts to its new value. */
@Composable
fun RollingMoney(usd: Double, eur: Double, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val euros = com.mtgscanner.ui.theme.AppSettings.currency == com.mtgscanner.util.PriceCurrency.EUR
    RollingText(if (euros) eur else usd, { v -> if (euros) formatEur(v) else formatUsd(v) }, style, color, modifier)
}

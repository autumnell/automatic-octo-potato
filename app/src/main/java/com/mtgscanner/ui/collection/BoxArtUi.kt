package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.components.formatMoneyShort
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BoxStats
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.cardPriceShort
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.scan.SetIcon
import java.util.Locale
import kotlin.math.abs

/** Colors for boxes without their own color, picked by position so neighbours differ. */
private val derivedColors = listOf(
    Color(0xFFF2C14E), Color(0xFF5B9BE0), Color(0xFF4CC38A), Color(0xFFE0703D),
    Color(0xFF9B7BE0), Color(0xFF3FB8AF), Color(0xFFE07BB0), Color(0xFFB8B3AC),
)

/** Each box's stripe and ring color: its own color when set, else one from a fixed set. */
fun boxStripeColors(boxes: List<BoxSummary>): Map<Long, Color> {
    var next = 0
    return boxes.associate { b ->
        b.id to if (b.color != 0) Color(b.color) else derivedColors[next++ % derivedColors.size]
    }
}

/** Box value changes come from dollar price history, so they're hidden when prices show in euros. */
fun boxChangeShown(): Boolean = AppSettings.currency != com.mtgscanner.util.PriceCurrency.EUR

/**
 * "+$61.66 (+36.7%) since added"; the percent is left out when unknown. The change is measured from the prices when
 * the cards were added, and unslop asks every change to say what it's against.
 */
fun boxChangeLabel(change: Double, percent: Double?): String {
    val sign = if (change < 0) "−" else "+"
    val pct = percent?.let { " ($sign${String.format(Locale.US, "%.1f", abs(it))}%)" }.orEmpty()
    return "$sign${formatUsd(abs(change))}$pct since added"
}

/** Green for gains, red for losses; [onDark] uses the bright shades that read on card art. */
fun boxChangeColor(change: Double, onDark: Boolean = false): Color = when {
    change > 0.005 -> if (onDark) Palette.OnDarkSuccess else Palette.Success
    change < -0.005 -> if (onDark) Palette.OnDarkDanger else Palette.Danger
    else -> if (onDark) Palette.OnDarkMuted else Palette.Muted
}

/**
 * Donut of the collection split by box, with the total in the middle. Arcs follow value, or
 * card count when prices are hidden.
 */
@Composable
fun ValueRing(
    boxes: List<BoxSummary>,
    stats: Map<Long, BoxStats>,
    colors: Map<Long, Color>,
    totalCards: Int,
    totalValue: Double,
    modifier: Modifier = Modifier,
    /** [totalValue] in euros. */
    totalEur: Double = 0.0,
    /** The biggest boxes beside the ring. */
    legend: Boolean = true,
) {
    val prices = AppSettings.showPrices
    val weights = boxes.map { b -> b to if (prices) b.value else b.cards.toDouble() }.filter { it.second > 0 }
    val sum = weights.sumOf { it.second }
    val then = stats.values.sumOf { it.valueThen }
    val change = stats.values.sumOf { it.change }
    val track = Palette.SurfaceHigh
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2),
        horizontalArrangement = if (legend) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(156.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                if (sum > 0) {
                    // A small gap between arcs when there are several.
                    val gap = if (weights.size > 1) 2f else 0f
                    var start = -90f
                    weights.forEach { (b, w) ->
                        val sweep = (w / sum * 360.0).toFloat()
                        if (sweep - gap > 0.3f) {
                            drawArc(colors[b.id] ?: Palette.Accent, start + gap / 2, sweep - gap, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                        }
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = Space.s6)) {
                if (prices) {
                    Text(formatMoneyShort(totalValue, totalEur), style = MaterialTheme.typography.titleLarge.merge(TabularNums), fontWeight = FontWeight.Medium, maxLines = 1)
                    if (then > 0 && boxChangeShown()) {
                        Text(
                            boxChangeLabel(change, null),
                            style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                            color = boxChangeColor(change),
                            maxLines = 1,
                        )
                    }
                    Text("$totalCards cards", style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Palette.Muted)
                } else {
                    Text("$totalCards", style = MaterialTheme.typography.headlineMedium.merge(TabularNums), fontWeight = FontWeight.Medium)
                    Text("cards", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
            }
        }
        if (legend) Spacer(Modifier.width(Space.s4))
        // The biggest few boxes, as a legend.
        if (legend) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            val top = weights.sortedByDescending { it.second }.take(5)
            if (top.isEmpty()) Text("No cards yet", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            top.forEach { (b, w) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[b.id] ?: Palette.Accent))
                    Spacer(Modifier.width(Space.s2))
                    Text(b.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Space.s2))
                    Text("${(w / sum * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Palette.Muted)
                }
            }
        }
    }
}

/** Pick the card shown behind a box. */
@Composable
fun CoverPickerDialog(
    boxName: String,
    cards: List<CollectionCard>,
    currentId: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = cards.filter { it.imageUrl != null }.sortedByDescending { it.unitPrice() ?: 0.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Cover for $boxName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            if (sorted.isEmpty()) {
                Text("No cards with art yet", color = Palette.Muted)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(sorted, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small))
                                .background(if (c.id == currentId) Palette.SurfaceHigh else Color.Transparent)
                                .clickable { onPick(c.id) }.padding(Space.s2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.width(36.dp))
                            Spacer(Modifier.width(Space.s3))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SetIcon(c.setCode, size = 12.dp, tint = Palette.Muted)
                                    Spacer(Modifier.width(Space.s1))
                                    Text(c.setCode.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                                }
                            }
                            if (AppSettings.showPrices) Text(cardPriceShort(c), style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Palette.Muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("Most valuable") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The value ring folded into one thin bar: a segment per box in its colour, by value (or by cards when
 * prices are hidden). Tapping it opens the full ring.
 */
@Composable
fun ValueBar(boxes: List<BoxSummary>, colors: Map<Long, Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val prices = AppSettings.showPrices
    val weights = boxes.map { b -> b to if (prices) b.value else b.cards.toDouble() }.filter { it.second > 0 }
    val sum = weights.sumOf { it.second }
    if (sum <= 0) return
    // Labelled so the thin bar reads as a split by box, not an empty progress bar.
    Row(modifier.clickable(onClick = onClick).padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        Text(if (prices) "Value by box" else "Cards by box", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
        Spacer(Modifier.width(Space.s2))
        // Drawn rather than laid out with weights, so every box gets its segment however small.
        val track = Palette.SurfaceHigh
        val fallback = Palette.Accent
        Canvas(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))) {
            drawRect(track)
            val gap = 2.dp.toPx()
            var x = 0f
            weights.forEach { (b, w) ->
                val width = size.width * (w / sum).toFloat()
                drawRect(colors[b.id] ?: fallback, topLeft = Offset(x, 0f), size = Size((width - gap).coerceAtLeast(1f), size.height))
                x += width
            }
        }
    }
}

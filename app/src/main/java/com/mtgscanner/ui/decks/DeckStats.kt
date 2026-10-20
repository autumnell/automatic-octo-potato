package com.mtgscanner.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.ui.components.Mana
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Card data looked up for decks, kept for the app's lifetime. */
private val cardCache = ConcurrentHashMap<String, DeckCardInfo>()

/** Names Scryfall answered but couldn't find (typos), so they aren't asked for again this session. */
private val unknownNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

/** True when Scryfall has said it has no card by this name. */
fun deckCardUnknown(name: String): Boolean = DeckStatsMath.key(name) in unknownNames

/**
 * Card data for deck card names, keyed by [DeckStatsMath.key]: from your collection first,
 * then Scryfall (by name, in batches). Names Scryfall can't find, or all of them when offline, are left out.
 */
suspend fun CollectionRepository.deckCardInfo(names: Collection<String>): Map<String, DeckCardInfo> {
    val wanted = names.associateBy { DeckStatsMath.key(it) }.filterKeys { it.isNotEmpty() }
    val out = HashMap<String, DeckCardInfo>()
    val owned = observeAll().first().groupBy { DeckStatsMath.key(it.name) }
    val missing = mutableListOf<String>()
    for ((k, name) in wanted) {
        val cached = cardCache[k]
        val mine = owned[k]?.firstOrNull()
        when {
            cached != null -> out[k] = cached
            mine != null -> {
                val info = DeckCardInfo(
                    name = mine.name, cmc = mine.cmc, manaCost = mine.manaCost, typeLine = mine.typeLine,
                    colors = mine.colors, colorIdentity = mine.colorIdentity, oracleText = mine.oracleText,
                    imageUrl = mine.imageUrl,
                )
                cardCache[k] = info
                out[k] = info
            }
            k in unknownNames -> {}
            else -> missing += name
        }
    }
    if (missing.isNotEmpty()) {
        val found = runCatching { api.identify(missing.map { n -> buildJsonObject { put("name", n.substringBefore(" // ")) } }) }.getOrNull()
        missing.zip(found ?: emptyList()).forEach { (n, c) ->
            if (c == null) unknownNames += DeckStatsMath.key(n)
            if (c != null) {
                val info = DeckCardInfo(
                    name = c.name, cmc = c.cmc, manaCost = c.effectiveManaCost, typeLine = c.effectiveTypeLine,
                    colors = c.effectiveColors.joinToString(""), colorIdentity = c.colorIdentity.joinToString(""),
                    oracleText = c.effectiveOracleText, imageUrl = c.imageUrl,
                )
                val k = DeckStatsMath.key(n)
                cardCache[k] = info
                out[k] = info
            }
        }
    }
    return out
}

fun StatColor.color(): Color = when (this) {
    StatColor.White -> MtgColors.W
    StatColor.Blue -> MtgColors.U
    StatColor.Black -> MtgColors.B
    StatColor.Red -> MtgColors.R
    StatColor.Green -> MtgColors.G
    StatColor.Multi -> MtgColors.Multi
    StatColor.Colorless -> MtgColors.C
}

/** Mana curve, mana costs and mana production for a deck. */
@Composable
fun DeckStatsView(stats: DeckStats?, failed: Boolean) {
    if (stats == null) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            if (failed) Text("No card data. Check your connection.", color = Palette.Muted)
            else CircularProgressIndicator(color = Palette.Accent)
        }
        return
    }
    var showColorless by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            StatCard("Mana values", "Avg ${String.format(Locale.US, "%.2f", stats.averageMv)}") {
                if (stats.nonland == 0) Text("No spells", color = Palette.Muted)
                else {
                    Curve(stats)
                    Spacer(Modifier.height(12.dp))
                    Legend(StatColor.entries.map { it to (stats.byColor[it] ?: 0) }.filter { it.second > 0 }, percent = false)
                }
            }
        }
        item {
            StatCard("Mana costs", null, action = {
                Text("Colorless", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = showColorless,
                    onCheckedChange = { showColorless = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent),
                )
            }) {
                val slices = StatColor.entries
                    .filter { showColorless || it != StatColor.Colorless }
                    .map { it to (stats.pips[it] ?: 0) }.filter { it.second > 0 }
                PieWithLegend(slices, "pips")
            }
        }
        item {
            StatCard("Mana production", null) {
                val slices = StatColor.entries.map { it to (stats.production[it] ?: 0) }.filter { it.second > 0 }
                PieWithLegend(slices, "sources")
            }
        }
        item {
            val bits = buildList {
                add("${stats.nonland} spells")
                add("${stats.lands} lands")
                if (stats.unknown > 0) add("${stats.unknown} not found")
            }
            Text(
                bits.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    trailing: String?,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = Palette.Surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleSmall, color = Palette.AccentText)
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Stacked bars for mana values 0..7+, colored by card color, with the count on top. */
@Composable
private fun Curve(stats: DeckStats) {
    val max = stats.curve.maxOf { it.values.sum() }.coerceAtLeast(1)
    val track = Palette.SurfaceHigh
    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.curve.forEachIndexed { mv, groups ->
            val total = groups.values.sum()
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (total > 0) "$total" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp)) {
                    val r = CornerRadius(4.dp.toPx())
                    drawRoundRect(track, size = size, cornerRadius = r, alpha = 0.35f)
                    var top = size.height
                    for (g in StatColor.entries) {
                        val n = groups[g] ?: 0
                        if (n == 0) continue
                        val h = size.height * n / max
                        top -= h
                        drawRoundRect(g.color(), topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = r)
                    }
                }
                Text(if (mv == 7) "7+" else "$mv", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun PieWithLegend(slices: List<Pair<StatColor, Int>>, unit: String) {
    if (slices.isEmpty()) {
        Text("None", color = Palette.Muted)
        return
    }
    val total = slices.sumOf { it.second }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 22.dp.toPx()
                val gap = if (slices.size > 1) 2f else 0f
                var start = -90f
                for ((c, n) in slices) {
                    val sweep = 360f * n / total
                    drawArc(
                        color = c.color(),
                        startAngle = start + gap / 2,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.titleLarge)
                Text(unit, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            for ((c, n) in slices) LegendRow(c, n, "${(100.0 * n / total).toInt()}%")
        }
    }
}

@Composable
private fun Legend(items: List<Pair<StatColor, Int>>, percent: Boolean) {
    val total = items.sumOf { it.second }.coerceAtLeast(1)
    Column {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (c, n) ->
                    Box(Modifier.weight(1f)) { LegendRow(c, n, if (percent) "${100 * n / total}%" else null) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendRow(c: StatColor, n: Int, extra: String?) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (c == StatColor.Multi) Box(Modifier.size(16.dp).background(c.color(), CircleShape))
        else Mana.Symbol(c.letter.toString(), 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(c.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$n", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (extra != null) {
            Spacer(Modifier.width(6.dp))
            Text(extra, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

package com.mtgscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.tagList
import com.mtgscanner.ui.theme.Palette

/** One face of a card as printed: name, cost, type, rules, flavor and power/toughness. */
data class FaceText(
    val name: String,
    val manaCost: String,
    val typeLine: String,
    val rules: String,
    val flavor: String? = null,
    val stats: String? = null,
)

/** What the card text block shows. [legalities] maps Scryfall format keys to "legal", "not_legal", "banned" or "restricted". */
data class CardTextInfo(
    val faces: List<FaceText>,
    val watermark: String? = null,
    val artist: String? = null,
    val legalities: Map<String, String> = emptyMap(),
)

private fun stats(power: String?, toughness: String?, loyalty: String?): String? = when {
    power != null && toughness != null -> "$power/$toughness"
    loyalty != null -> "Loyalty: $loyalty"
    else -> null
}

fun ScryfallCard.textInfo(): CardTextInfo {
    val faces = cardFaces?.takeIf { it.size > 1 && it.any { f -> !f.oracleText.isNullOrBlank() } }?.map { f ->
        FaceText(f.name, f.manaCost.orEmpty(), f.typeLine.orEmpty(), f.oracleText.orEmpty(), f.flavorText, stats(f.power, f.toughness, f.loyalty))
    } ?: listOf(
        FaceText(name, effectiveManaCost, effectiveTypeLine, effectiveOracleText, flavorText, stats(power, toughness, loyalty)),
    )
    return CardTextInfo(faces, watermark, artist, legalities)
}

/** What can be shown without a connection, from the saved card. */
fun CollectionCard.textInfo(): CardTextInfo {
    val legal = tagList().filter { it.startsWith("legal:") }.map { it.removePrefix("legal:") }.toSet()
    return CardTextInfo(
        faces = listOf(FaceText(name, manaCost, typeLine, oracleText)),
        artist = artist,
        legalities = listOf("standard", "pioneer", "modern", "commander", "pauper").associateWith { if (it in legal) "legal" else "not_legal" },
    )
}

private val FORMATS = listOf(
    "standard" to "Standard", "alchemy" to "Alchemy",
    "pioneer" to "Pioneer", "historic" to "Historic",
    "modern" to "Modern", "brawl" to "Brawl",
    "legacy" to "Legacy", "standardbrawl" to "Std. Brawl",
    "vintage" to "Vintage", "timeless" to "Timeless",
    "commander" to "Commander", "pauper" to "Pauper",
    "oathbreaker" to "Oathbreaker", "penny" to "Penny",
)

/** Card text laid out like Scryfall's: one panel, each part split by a thin line, then a legality grid. */
@Composable
fun CardTextBlock(info: CardTextInfo, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column {
            info.faces.forEachIndexed { i, f ->
                if (i > 0) Divider(thick = true)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    ManaCost(f.manaCost, size = 18.dp)
                }
                Divider()
                Text(f.typeLine, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (f.rules.isNotBlank() || f.flavor != null) {
                    Divider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        f.rules.split('\n').filter { it.isNotBlank() }.forEachIndexed { k, para ->
                            if (k > 0) Spacer(Modifier.height(6.dp))
                            RulesText(para)
                        }
                        f.flavor?.let {
                            if (f.rules.isNotBlank()) Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Palette.Muted)
                        }
                    }
                }
                f.stats?.let {
                    Divider()
                    Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            info.watermark?.let {
                Divider()
                Line("Watermark: ", it.replaceFirstChar(Char::uppercase))
            }
            info.artist?.let {
                Divider()
                Line("Illustrated by ", it)
            }
            if (info.legalities.isNotEmpty()) {
                Divider(thick = true)
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FORMATS.filter { it.first in info.legalities }.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (key, label) -> Legality(info.legalities[key].orEmpty(), label, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider(thick: Boolean = false) =
    HorizontalDivider(color = Palette.Outline, thickness = if (thick) 2.dp else 1.dp)

@Composable
private fun Line(label: String, value: String) {
    Text(
        buildAnnotatedString {
            append(label)
            pushStyle(androidx.compose.ui.text.SpanStyle(color = Palette.Accent))
            append(value)
            pop()
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Legality(status: String, format: String, modifier: Modifier) {
    val (label, bg) = when (status) {
        "legal" -> "LEGAL" to Palette.Success
        "banned" -> "BANNED" to Palette.Danger
        "restricted" -> "RESTRICTED" to Color(0xFF2F7FB8)
        else -> "NOT LEGAL" to Palette.SurfaceHigh
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(76.dp).clip(RoundedCornerShape(6.dp)).background(bg).padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
                color = if (status == "legal" || status == "banned" || status == "restricted") Color.White else Palette.Muted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(format, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

private val SYMBOL = Regex("""\{([^}]+)\}""")

/** Rules text with {T}, {2}, {U} and friends drawn as symbols, and reminder text in italics. */
@Composable
private fun RulesText(text: String) {
    val symbols = SYMBOL.findAll(text).map { it.value }.distinct().toList()
    val annotated = buildAnnotatedString {
        var last = 0
        var inReminder = false
        fun plain(s: String) {
            // Reminder text sits in parentheses and is shown in italics, like on Scryfall.
            s.forEach { ch ->
                if (ch == '(' && !inReminder) { inReminder = true; pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)) }
                append(ch)
                if (ch == ')' && inReminder) { inReminder = false; pop() }
            }
        }
        SYMBOL.findAll(text).forEach { m ->
            plain(text.substring(last, m.range.first))
            appendInlineContent(m.value, m.value)
            last = m.range.last + 1
        }
        plain(text.substring(last))
        if (inReminder) pop()
    }
    val inline = symbols.associateWith { sym ->
        InlineTextContent(Placeholder(1.05.em, 1.05.em, PlaceholderVerticalAlign.TextCenter)) {
            if (sym == "{T}" || sym == "{Q}") {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(if (sym == "{T}") "↷" else "↶", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                ManaCost(sym, size = 15.dp)
            }
        }
    }
    Text(annotated, inlineContent = inline, style = MaterialTheme.typography.bodyMedium)
}

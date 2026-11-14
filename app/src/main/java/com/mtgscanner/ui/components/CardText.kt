package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.tagList
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

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
        // No tags at all means the card was saved without its legalities (matched from the on-phone card list), not
        // that it's banned everywhere: say nothing rather than "not legal" five times.
        legalities = if (legal.isEmpty()) emptyMap()
        else listOf("standard", "pioneer", "modern", "commander", "pauper").associateWith { if (it in legal) "legal" else "not_legal" },
    )
}

/** A panel with a hairline border, like Scryfall's text box: rows inside it are split by [Palette.Rule] lines. */
@Composable
fun RuledPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Corners.Medium)
    Surface(modifier.fillMaxWidth().border(1.dp, Palette.Rule, shape), shape = shape, color = Palette.Surface) {
        Column(content = content)
    }
}

/**
 * Card text laid out like Scryfall's: one bordered panel, each part split by a thin line. Legality has its own tab
 * on the card page (CardInfoTabs), which reads [CardTextInfo.legalities].
 * [heading] sets the name in the heading face, for the card page where it is the page's title. Holding the name
 * calls [onHoldName].
 */
@Composable
fun CardTextBlock(info: CardTextInfo, modifier: Modifier = Modifier, heading: Boolean = false, onHoldName: (() -> Unit)? = null) {
    RuledPanel(modifier) {
        info.faces.forEachIndexed { i, f ->
            if (i > 0) Divider(thick = true)
            Row(
                Modifier.fillMaxWidth()
                    .then(if (onHoldName != null) Modifier.pointerInput(onHoldName) { detectTapGestures(onLongPress = { onHoldName() }) } else Modifier)
                    .padding(horizontal = Space.s4, vertical = Space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    f.name, style = if (heading) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ManaCost(f.manaCost, size = if (heading) 20.dp else 18.dp)
            }
            Divider()
            Text(f.typeLine, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2))
            if (f.rules.isNotBlank() || f.flavor != null) {
                Divider()
                Column(Modifier.padding(horizontal = Space.s4, vertical = Space.s3)) {
                    f.rules.split('\n').filter { it.isNotBlank() }.forEachIndexed { k, para ->
                        if (k > 0) Spacer(Modifier.height(Space.s2))
                        RulesText(para)
                    }
                    f.flavor?.let {
                        if (f.rules.isNotBlank()) Spacer(Modifier.height(Space.s2))
                        Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Palette.Muted)
                    }
                }
            }
            f.stats?.let {
                Divider()
                Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2))
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
    }
}

@Composable
private fun Divider(thick: Boolean = false) =
    HorizontalDivider(color = Palette.Rule, thickness = if (thick) 2.dp else 1.dp)

@Composable
private fun Line(label: String, value: String) {
    Text(
        buildAnnotatedString {
            append(label)
            pushStyle(androidx.compose.ui.text.SpanStyle(color = Palette.Text))
            append(value)
            pop()
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
    )
}

private val SYMBOL = Regex("""\{([^}]+)\}""")

/** Rules text with {T}, {2}, {U} and friends drawn as symbols, and reminder text in italics. */
@Composable
private fun RulesText(text: String) {
    val symbols = remember(text) { SYMBOL.findAll(text).map { it.value }.distinct().toList() }
    val annotated = remember(text) { buildAnnotatedString {
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
    } }
    // The placeholder grows with the system font size, so the symbol does too.
    val symbolSize = 15.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale
    val inline = remember(symbols, symbolSize) {
        symbols.associateWith { sym ->
            InlineTextContent(Placeholder(1.05.em, 1.05.em, PlaceholderVerticalAlign.TextCenter)) {
                ManaCost(sym, size = symbolSize)
            }
        }
    }
    Text(annotated, inlineContent = inline, style = MaterialTheme.typography.bodyMedium)
}

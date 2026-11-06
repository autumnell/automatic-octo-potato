package com.mtgscanner.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.PlexMonoText

// The Collection screens' price-guide look: hairline rules, square-ish corners, numbers in Plex Mono.

/** Hairline between rows and around the stats strip. */
// The hairline used between rows and cells.
val RuleColor: Color get() = Palette.Rule

/** 3dp corners, as in the mockups. */
// Small UI corners (cards keep CardShape).
val LookShape = RoundedCornerShape(Corners.Small)

/** Plex Mono at [size], for prices, changes and set codes. */
fun monoStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = PlexMonoText, fontSize = size, lineHeight = size * 1.3f, fontWeight = weight)

/** One day's price per point for a finish: the foil price for foils, falling back to the regular one. */
fun historyPairs(points: List<PricePoint>, foil: Boolean): List<Pair<String, Double>> =
    points.mapNotNull { p -> (if (foil) p.usdFoil ?: p.usd else p.usd)?.let { p.day to it } }

/** A 1dp rule across the width. */
@Composable
fun RuleLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(RuleColor))
}

/** An 8dp dot in the card's color identity: gold for more than one color, gray for none. */
@Composable
fun ColorDot(identity: String, modifier: Modifier = Modifier) {
    val letters = identity.uppercase().filter { it in "WUBRG" }.toSet()
    val color = when (letters.size) {
        0 -> MtgColors.C
        1 -> MtgColors.barFor(letters.first())
        else -> MtgColors.Multi
    }
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/**
 * The collection's totals on one quiet line: "1,234 cards · $5,678 · +$12.40 this week". [value] and [week] are null
 * when prices are off, and then only the card count shows. With [cardCount] / [valueNumber] the numbers
 * count to their new values instead of jumping.
 */
@Composable
fun StatsStrip(
    cards: String, value: String?, week: String?, modifier: Modifier = Modifier,
    cardCount: Int? = null,
    valueNumber: Double? = null,
    valueFormat: ((Double) -> String)? = null,
) {
    val style = monoStyle(14.sp)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (cardCount != null) {
            com.mtgscanner.ui.components.RollingText(cardCount.toDouble(), { "%,d".format(it.toLong()) }, style, Palette.Text)
        } else Text(cards, style = style, color = Palette.Text, maxLines = 1)
        Text(" cards", style = style, color = Palette.Muted, maxLines = 1)
        if (value != null) {
            Text("  ·  ", style = style, color = Palette.Muted)
            if (valueNumber != null && valueFormat != null) {
                com.mtgscanner.ui.components.RollingText(valueNumber, valueFormat, style, Palette.Text)
            } else Text(value, style = style, color = Palette.Text, maxLines = 1)
        }
        if (week != null) {
            Text("  ·  ", style = style, color = Palette.Muted)
            Text(week, style = style, color = Palette.Text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(" this week", style = style, color = Palette.Muted, maxLines = 1)
        }
    }
}

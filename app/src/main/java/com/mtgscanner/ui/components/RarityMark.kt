package com.mtgscanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The five rarity colors a set symbol comes in. */
enum class RarityTier(val label: String) {
    COMMON("Common"), UNCOMMON("Uncommon"), RARE("Rare"), MYTHIC("Mythic"), SPECIAL("Special");

    companion object {
        /** Scryfall's rarity word to its tier; null or blank shows nothing. Bonus and anything new count as special. */
        fun of(rarity: String?): RarityTier? = when (rarity?.trim()?.lowercase()) {
            null, "" -> null
            "common" -> COMMON
            "uncommon" -> UNCOMMON
            "rare" -> RARE
            "mythic", "mythic rare" -> MYTHIC
            else -> SPECIAL
        }
    }
}

/** Top and bottom of the symbol's metallic fill, and its rim. */
private fun shades(t: RarityTier): Triple<Color, Color, Color> = when (t) {
    // Black like a real common symbol, with a light rim so it shows on the dark theme.
    RarityTier.COMMON -> Triple(Color(0xFF45464A), Color(0xFF111113), Color(0xFF8E9096))
    RarityTier.UNCOMMON -> Triple(Color(0xFFE6EFF5), Color(0xFF7F99A8), Color(0xFF1E2328))
    RarityTier.RARE -> Triple(Color(0xFFF7E2A2), Color(0xFFB1872A), Color(0xFF2A2010))
    RarityTier.MYTHIC -> Triple(Color(0xFFFBB860), Color(0xFFCC3B1B), Color(0xFF2B120A))
    RarityTier.SPECIAL -> Triple(Color(0xFFDCC2F2), Color(0xFF7346A8), Color(0xFF1E1228))
}

/**
 * A small diamond in the set-symbol color of [rarity]: black for common, silver, gold,
 * orange-red for mythic and purple for special. Nothing for an unknown rarity.
 */
@Composable
fun RarityMark(rarity: String?, modifier: Modifier = Modifier) {
    val tier = RarityTier.of(rarity) ?: return
    val (top, bottom, rim) = shades(tier)
    Canvas(modifier.size(10.dp).semantics { contentDescription = tier.label }) {
        val w = size.width
        val h = size.height
        // A slightly tall diamond reads as a symbol rather than a dot.
        val inset = 0.6.dp.toPx()
        val path = Path().apply {
            moveTo(w / 2, inset)
            lineTo(w - inset, h / 2)
            lineTo(w / 2, h - inset)
            lineTo(inset, h / 2)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(top, bottom), start = Offset(w * 0.3f, 0f), end = Offset(w * 0.7f, h)))
        drawPath(path, rim, style = Stroke(width = 0.9.dp.toPx()))
    }
}

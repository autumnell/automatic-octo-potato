package com.mtgscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtgscanner.R

/**
 * The Mana icon font by Andrew Gioia (SIL OFL 1.1, https://mana.andrewgioia.com): the real
 * Magic symbols for mana, tap, card types and more. Glyph codes come from its mana.css.
 */
object Mana {
    val Family = FontFamily(Font(R.font.mana))

    /** A symbol's glyph; hybrids have a second glyph for the lower-right half. */
    data class Glyph(val main: String, val second: String? = null)

    private val glyphs: Map<String, Glyph> = mapOf(
        "w" to Glyph("\ue600"),
        "u" to Glyph("\ue601"),
        "b" to Glyph("\ue602"),
        "r" to Glyph("\ue603"),
        "g" to Glyph("\ue604"),
        "c" to Glyph("\ue904"),
        "x" to Glyph("\ue615"),
        "y" to Glyph("\ue616"),
        "z" to Glyph("\ue617"),
        "s" to Glyph("\ue619"),
        "e" to Glyph("\ue907"),
        "tap" to Glyph("\ue61a"),
        "untap" to Glyph("\ue61b"),
        "p" to Glyph("\ue618"),
        "chaos" to Glyph("\ue61d"),
        "infinity" to Glyph("\ue903"),
        "1-2" to Glyph("\ue902"),
        "l" to Glyph("\uea2d"),
        "d" to Glyph("\uea2e"),
        "h" to Glyph("\ue618"),
        "tk" to Glyph("\ue9c4"),
        "paw" to Glyph("\uea32"),
        "0" to Glyph("\ue605"),
        "1" to Glyph("\ue606"),
        "2" to Glyph("\ue607"),
        "3" to Glyph("\ue608"),
        "4" to Glyph("\ue609"),
        "5" to Glyph("\ue60a"),
        "6" to Glyph("\ue60b"),
        "7" to Glyph("\ue60c"),
        "8" to Glyph("\ue60d"),
        "9" to Glyph("\ue60e"),
        "10" to Glyph("\ue60f"),
        "11" to Glyph("\ue610"),
        "12" to Glyph("\ue611"),
        "13" to Glyph("\ue612"),
        "14" to Glyph("\ue613"),
        "15" to Glyph("\ue614"),
        "16" to Glyph("\ue62a"),
        "17" to Glyph("\ue62b"),
        "18" to Glyph("\ue62c"),
        "19" to Glyph("\ue62d"),
        "20" to Glyph("\ue62e"),
        "100" to Glyph("\ue900"),
        "1000000" to Glyph("\ue901"),
        "wu" to Glyph("\ue600", "\ue601"),
        "wb" to Glyph("\ue600", "\ue602"),
        "ub" to Glyph("\ue601", "\ue602"),
        "ur" to Glyph("\ue601", "\ue603"),
        "br" to Glyph("\ue602", "\ue603"),
        "bg" to Glyph("\ue602", "\ue604"),
        "rw" to Glyph("\ue603", "\ue600"),
        "rg" to Glyph("\ue603", "\ue604"),
        "gw" to Glyph("\ue604", "\ue600"),
        "gu" to Glyph("\ue604", "\ue601"),
        "2w" to Glyph("\ue607", "\ue600"),
        "2u" to Glyph("\ue607", "\ue601"),
        "2b" to Glyph("\ue607", "\ue602"),
        "2r" to Glyph("\ue607", "\ue603"),
        "2g" to Glyph("\ue607", "\ue604"),
        "cw" to Glyph("\ue904", "\ue600"),
        "cu" to Glyph("\ue904", "\ue601"),
        "cb" to Glyph("\ue904", "\ue602"),
        "cr" to Glyph("\ue904", "\ue603"),
        "cg" to Glyph("\ue904", "\ue604"),
        "wp" to Glyph("\ue618"),
        "up" to Glyph("\ue618"),
        "bp" to Glyph("\ue618"),
        "rp" to Glyph("\ue618"),
        "gp" to Glyph("\ue618"),
        "wup" to Glyph("\ue618", "\ue618"),
        "wbp" to Glyph("\ue618", "\ue618"),
        "ubp" to Glyph("\ue618", "\ue618"),
        "urp" to Glyph("\ue618", "\ue618"),
        "brp" to Glyph("\ue618", "\ue618"),
        "bgp" to Glyph("\ue618", "\ue618"),
        "rwp" to Glyph("\ue618", "\ue618"),
        "rgp" to Glyph("\ue618", "\ue618"),
        "gwp" to Glyph("\ue618", "\ue618"),
        "gup" to Glyph("\ue618", "\ue618"),
        "artifact" to Glyph("\ue61e"),
        "creature" to Glyph("\ue61f"),
        "enchantment" to Glyph("\ue620"),
        "instant" to Glyph("\ue621"),
        "land" to Glyph("\ue622"),
        "planeswalker" to Glyph("\ue623"),
        "sorcery" to Glyph("\ue624"),
        "battle" to Glyph("\ue9d1"),
        "multicolor" to Glyph("\ue985"),
        "dfc-day" to Glyph("\ue906"),
        "dfc-night" to Glyph("\ue905"),
        "ability-day-night" to Glyph("\ue9a8"),
        "commander" to Glyph("\ue9c6"),
        "loyalty-up" to Glyph("\ue627"),
        "loyalty-down" to Glyph("\ue625"),
        "loyalty-zero" to Glyph("\ue626"),
        "loyalty-start" to Glyph("\ue628"),
    )

    /** The glyph for a font class name like "w", "2u", "tap" or "dfc-day". */
    fun glyph(name: String): Glyph? = glyphs[name]

    /** Font class name for a Scryfall symbol without braces: "W/U" → "wu", "T" → "tap". */
    fun keyFor(symbol: String): String = when (val s = symbol.uppercase()) {
        "T" -> "tap"
        "Q" -> "untap"
        "½", "1/2" -> "1-2"
        "∞" -> "infinity"
        "PW" -> "planeswalker"
        else -> s.replace("/", "").lowercase()
    }

    // Circle colors from mana.css, so symbols look like they do on the cards.
    val White = Color(0xFFF0F2C0)
    val Blue = Color(0xFFB5CDE3)
    val Black = Color(0xFFACA29A)
    val Red = Color(0xFFDB8664)
    val Green = Color(0xFF93B483)
    val Colorless = Color(0xFFBEB9B2)
    private val Ink = Color(0xFF111111)

    private fun colorOf(c: Char): Color = when (c) {
        'w' -> White; 'u' -> Blue; 'b' -> Black; 'r' -> Red; 'g' -> Green; else -> Colorless
    }

    private val MANA_KEY = Regex("[wubrgc2]{1,2}p?")

    /** The circle's two colors: top-left and bottom-right (the same for a single color). */
    fun colors(key: String): Pair<Color, Color> {
        if (!MANA_KEY.matches(key)) return Colorless to Colorless
        val k = key.removeSuffix("p")
        return when {
            k.length == 2 && k[0] in "2c" -> Colorless to colorOf(k[1])
            k.length == 2 -> colorOf(k[0]) to colorOf(k[1])
            else -> colorOf(k[0]).let { it to it }
        }
    }

    /** True when the font can draw this Scryfall symbol (without braces). */
    fun has(symbol: String): Boolean = glyph(keyFor(symbol)) != null

    /** One mana symbol drawn in a colored circle; check [has] first. */
    @Composable
    fun Symbol(symbol: String, size: Dp = 16.dp) {
        val key = keyFor(symbol)
        val g = glyph(key) ?: return
        val (top, bottom) = colors(key)
        val bg = if (top == bottom) Modifier.background(top)
        else Modifier.background(
            Brush.linearGradient(
                0f to top, 0.5f to top, 0.5f to bottom, 1f to bottom,
                start = Offset.Zero, end = Offset.Infinite,
            ),
        )
        val density = LocalDensity.current
        Box(Modifier.size(size).clip(CircleShape).then(bg), contentAlignment = Alignment.Center) {
            if (g.second == null) {
                Text(g.main, fontFamily = Family, color = Ink, fontSize = with(density) { (size * 0.72f).toSp() }, textAlign = TextAlign.Center, maxLines = 1)
            } else {
                val small = with(density) { (size * 0.42f).toSp() }
                Text(g.main, fontFamily = Family, color = Ink, fontSize = small, maxLines = 1, modifier = Modifier.align(BiasAlignment(-0.5f, -0.5f)))
                Text(g.second, fontFamily = Family, color = Ink, fontSize = small, maxLines = 1, modifier = Modifier.align(BiasAlignment(0.5f, 0.5f)))
            }
        }
    }

    /** A bare glyph (no circle), tinted, for icons like day and night. */
    @Composable
    fun Icon(name: String, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
        val g = glyph(name) ?: return
        val density = LocalDensity.current
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Text(g.main, fontFamily = Family, color = color, fontSize = with(density) { size.toSp() }, maxLines = 1)
        }
    }
}

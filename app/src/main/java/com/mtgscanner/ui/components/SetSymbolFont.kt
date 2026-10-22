package com.mtgscanner.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mtgscanner.R
import com.mtgscanner.ui.theme.Palette

/**
 * The Keyrune icon font by Andrew Gioia (fonts under SIL OFL 1.1, https://keyrune.andrewgioia.com):
 * a symbol for every Magic set. Glyph codes are in [SetSymbolGlyphs].
 */
object SetSymbolFont {
    val Family = FontFamily(Font(R.font.keyrune))

    private val Silver = Color(0xFF9AA6B2)
    private val Gold = Color(0xFFC9A54F)
    private val Mythic = Color(0xFFE0582B)
    private val Special = Color(0xFF9B6FCF)

    /** The symbol's color for a rarity: plain text color for common or none. */
    fun colorFor(rarity: String?): Color = when (RarityTier.of(rarity)) {
        RarityTier.UNCOMMON -> Silver
        RarityTier.RARE -> Gold
        RarityTier.MYTHIC -> Mythic
        RarityTier.SPECIAL -> Special
        RarityTier.COMMON, null -> Palette.Text
    }
}

/** A set's symbol, tinted by [rarity]; draws nothing for a set the font doesn't know. */
@Composable
fun SetSymbol(code: String?, rarity: String? = null, size: TextUnit = 15.sp, modifier: Modifier = Modifier) {
    val glyph = SetSymbolGlyphs.glyph(code) ?: return
    Text(
        glyph,
        fontFamily = SetSymbolFont.Family,
        fontSize = size,
        lineHeight = size,
        color = SetSymbolFont.colorFor(rarity),
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { },
    )
}

/** The set symbol in its rarity color, or the plain rarity mark when the font has no symbol for the set. */
@Composable
fun SetMark(code: String?, rarity: String?) {
    if (SetSymbolGlyphs.glyph(code) != null) SetSymbol(code, rarity) else RarityMark(rarity)
}

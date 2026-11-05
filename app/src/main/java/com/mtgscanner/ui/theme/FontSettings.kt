package com.mtgscanner.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mtgscanner.R

/**
 * The app's two fonts (both SIL OFL, bundled): Fraunces, a soft old-style serif, for headings and the big value, Lexend for
 * everything else, prices included. Fixed by design (design/Design.md); there is no font picker. [scale] sizes each
 * against the heading sizes in Theme.kt; [spacing] is the letter spacing headings get.
 */
enum class AppFont(val label: String, val scale: Float, val spacing: Double, val family: () -> FontFamily) {
    FRAUNCES("Fraunces", 0.8f, 0.0, { Fraunces }),
    LEXEND("Lexend", 0.86f, 0.0, { Lexend }),
}

val Fraunces = FontFamily(
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    // No 700: weights stop at 600 (design/Design.md). Anything asking for Bold gets SemiBold, not a faked bold.
    Font(R.font.fraunces_semibold, FontWeight.Bold),
)

/** Which font goes where. */
object FontSettings {
    val heading = AppFont.FRAUNCES
    val body = AppFont.LEXEND
    val price = AppFont.LEXEND
}

package com.mtgscanner.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mtgscanner.R

/**
 * The fonts the app can use (all SIL OFL, bundled). [scale] evens out how big each one looks at the same size,
 * with Cormorant (which runs small) as 1.0; [spacing] is the letter spacing headings get in it.
 */
enum class AppFont(val label: String, val scale: Float, val spacing: Double, val family: () -> FontFamily) {
    CORMORANT("Cormorant", 1f, 0.4, { Cormorant }),
    CINZEL("Cinzel", 0.8f, 0.6, { Cinzel }),
    FRAUNCES("Fraunces", 0.88f, 0.0, { Fraunces }),
    LEXEND("Lexend", 0.86f, 0.0, { Lexend }),
    INTER("Inter", 0.86f, -0.2, { Inter }),
    MANROPE("Manrope", 0.86f, 0.0, { Manrope }),
    SPACE_GROTESK("Space Grotesk", 0.86f, 0.0, { SpaceGrotesk }),
    PIXELIFY("Pixelify", 0.88f, 0.4, { Pixelify }),
    PLEX_MONO("Plex Mono", 0.8f, 0.0, { PlexMono });

    companion object {
        val headings = listOf(CORMORANT, CINZEL, FRAUNCES, LEXEND, INTER, MANROPE, SPACE_GROTESK, PIXELIFY)
        val body = listOf(LEXEND, INTER, MANROPE, SPACE_GROTESK)
        val prices = listOf(CORMORANT, CINZEL, FRAUNCES, LEXEND, INTER, MANROPE, SPACE_GROTESK, PIXELIFY, PLEX_MONO)

        /** A saved name back to a font in [allowed], or [fallback] if it's missing or not allowed there. */
        fun parse(name: String?, allowed: List<AppFont>, fallback: AppFont): AppFont =
            entries.firstOrNull { it.name == name }?.takeIf { it in allowed } ?: fallback
    }
}

val Cinzel = FontFamily(
    Font(R.font.cinzel_semibold, FontWeight.Medium),
    Font(R.font.cinzel_semibold, FontWeight.SemiBold),
    Font(R.font.cinzel_bold, FontWeight.Bold),
)
val Fraunces = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_bold, FontWeight.Bold),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)
val Pixelify = FontFamily(
    Font(R.font.pixelify_regular, FontWeight.Normal),
    Font(R.font.pixelify_medium, FontWeight.Medium),
    Font(R.font.pixelify_semibold, FontWeight.SemiBold),
    Font(R.font.pixelify_bold, FontWeight.Bold),
)
val PlexMono = FontFamily(
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

/** Which font goes where, from Settings › Look. Defaults are the current look until changed. */
object FontSettings {
    private var prefs: SharedPreferences? = null

    var heading by mutableStateOf(AppFont.CORMORANT)
        private set
    var body by mutableStateOf(AppFont.LEXEND)
        private set
    var price by mutableStateOf(AppFont.CORMORANT)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs = p
        heading = AppFont.parse(p.getString("fontHeading", null), AppFont.headings, AppFont.CORMORANT)
        body = AppFont.parse(p.getString("fontBody", null), AppFont.body, AppFont.LEXEND)
        price = AppFont.parse(p.getString("fontPrice", null), AppFont.prices, AppFont.CORMORANT)
    }

    fun updateHeading(f: AppFont) { heading = f; prefs?.edit()?.putString("fontHeading", f.name)?.apply() }
    fun updateBody(f: AppFont) { body = f; prefs?.edit()?.putString("fontBody", f.name)?.apply() }
    fun updatePrice(f: AppFont) { price = f; prefs?.edit()?.putString("fontPrice", f.name)?.apply() }

    fun reset() {
        updateHeading(AppFont.CORMORANT); updateBody(AppFont.LEXEND); updatePrice(AppFont.CORMORANT)
    }
}

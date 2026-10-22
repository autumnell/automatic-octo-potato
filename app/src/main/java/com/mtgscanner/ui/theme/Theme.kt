package com.mtgscanner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.R

/**
 * The app's colors. Every value is read live from [ThemeSettings], so switching light/dark mode
 * or the accent color in Settings recolors the whole app immediately.
 */
object Palette {
    /** Whether the phone itself is in dark mode; only used when the theme follows the system. */
    var systemDark by mutableStateOf(true)

    val dark: Boolean
        get() = when (ThemeSettings.mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
        }

    /** Dark mode on pure black (Settings › Look). */
    val black: Boolean get() = dark && ThemeSettings.trueBlack

    val Accent: Color get() = ThemeSettings.activeAccent
    val AccentDeep: Color get() = lerp(Accent, Color(0xFF6E1630), 0.38f)
    /**
     * The accent for text (prices, selected labels). In light mode it is darkened until it reaches
     * about 4.5:1 contrast on the light background; in dark mode it is the accent itself.
     */
    val AccentText: Color get() {
        if (dark) return Accent
        val accent = Accent
        val background = Background
        // Read on every recomposition; the contrast search only needs redoing when the colors change.
        if (accent != accentTextFor) {
            accentTextCache = readableOn(accent, background)
            accentTextFor = accent
        }
        return accentTextCache
    }
    private var accentTextFor: Color = Color.Unspecified
    private var accentTextCache: Color = Color.Unspecified
    /** Text/icon color that reads well on top of the accent. */
    val OnAccent: Color get() = if (Accent.luminance() > 0.45f) Color(0xFF141414) else Color.White

    val Background: Color get() = when { black -> Color.Black; dark -> Color(0xFF111214); else -> Color(0xFFF4F4F7) }
    val Surface: Color get() = when { black -> Color(0xFF0D0D0F); dark -> Color(0xFF1A1B1F); else -> Color(0xFFFFFFFF) }
    val SurfaceHigh: Color get() = when { black -> Color(0xFF1A1B1E); dark -> Color(0xFF24262C); else -> Color(0xFFE9EAEF) }
    val BoxCard: Color get() = when { black -> Color(0xFF10121A); dark -> Color(0xFF1E2233); else -> Color(0xFFE4E8F6) }
    val NavBar: Color get() = when { black -> Color.Black; dark -> Color(0xFF09090A); else -> Color(0xFFFFFFFF) }
    val Gold: Color get() = if (dark) Color(0xFFF2C14E) else Color(0xFFB07D12)
    /** [Gold] for text: darker in light mode so it reads on light panels (about 4.6:1 or better). */
    val GoldText: Color get() = if (dark) Gold else Color(0xFF876009)
    val Info: Color get() = if (dark) Color(0xFF5B9BE0) else Color(0xFF2F6FB8)
    val Text: Color get() = if (dark) Color(0xFFEDEDEF) else Color(0xFF17181C)
    val Muted: Color get() = if (dark) Color(0xFF9A9BA3) else Color(0xFF666873)
    /** Default icon tint: a quiet gray, a touch softer than [Muted]. The accent is kept for value, selection and the main action. */
    val Icon: Color get() = if (dark) Color(0xFF8E9098) else Color(0xFF6E707B)
    val Outline: Color get() = when { black -> Color(0xFF26272C); dark -> Color(0xFF34363D); else -> Color(0xFFD3D5DC) }
    /** Hairline between plain sections: fainter than [Outline]. */
    val Hairline: Color get() = Outline.copy(alpha = if (dark) 0.7f else 0.8f)
    val Danger: Color get() = if (dark) Color(0xFFE5534B) else Color(0xFFC62F28)
    val Success: Color get() = if (dark) Color(0xFF4CC38A) else Color(0xFF1E8E5A)

    val AccentGradient: Brush get() = Brush.horizontalGradient(listOf(Accent, AccentDeep))
    /** Background for hero panels: the box color warmed with a little of the accent. */
    val HeroGradient: Brush get() = Brush.linearGradient(listOf(lerp(BoxCard, Accent, if (dark) 0.22f else 0.18f), BoxCard))
}

/** WCAG contrast ratio between two colors (1..21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** [color] darkened toward black just enough to reach [ratio] contrast on a light [background]. */
fun readableOn(color: Color, background: Color, ratio: Float = 4.6f): Color {
    var t = 0f
    var c = color
    while (t < 1f && contrastRatio(c, background) < ratio) {
        t += 0.04f
        c = lerp(color, Color.Black, t.coerceAtMost(1f))
    }
    return c
}

val Lexend = FontFamily(
    Font(R.font.lexend_light, FontWeight.Light),
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
    Font(R.font.lexend_bold, FontWeight.Bold),
)

/**
 * Cormorant SC (SIL OFL): a sharp engraved serif in small caps, for headings, the big value and prices.
 * A Latin subset; anything it lacks falls back to the system font.
 */
val Cormorant = FontFamily(
    Font(R.font.cormorant_sc_semibold, FontWeight.Medium),
    Font(R.font.cormorant_sc_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_sc_bold, FontWeight.Bold),
)

/** Lining figures, so numbers in headings sit on the line instead of Cormorant's old-style digits. */
private const val HEADING_FIGURES = "lnum"
/** Lining and tabular figures: every digit the same width, so prices line up and counters don't jiggle. */
private const val PRICE_FIGURES = "lnum, tnum"
/** Tabular figures alone, for labels (see [TabularNums]). */
private const val TABULAR = "tnum"

private fun scheme(): ColorScheme {
    val p = Palette
    val common = if (p.dark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = p.Accent,
        onPrimary = p.OnAccent,
        primaryContainer = lerp(p.Surface, p.Accent, 0.25f),
        onPrimaryContainer = p.AccentText,
        secondary = p.Info,
        onSecondary = Color.White,
        secondaryContainer = lerp(p.Surface, p.Accent, 0.18f),
        onSecondaryContainer = p.AccentText,
        tertiary = p.Gold,
        background = p.Background,
        onBackground = p.Text,
        surface = p.Surface,
        onSurface = p.Text,
        surfaceVariant = p.SurfaceHigh,
        onSurfaceVariant = p.Muted,
        surfaceContainer = p.Surface,
        surfaceContainerLow = p.Surface,
        surfaceContainerLowest = p.Background,
        surfaceContainerHigh = p.SurfaceHigh,
        surfaceContainerHighest = p.SurfaceHigh,
        outline = p.Outline,
        outlineVariant = p.Outline,
        error = p.Danger,
    )
}

private fun base(family: FontFamily, size: Int, weight: FontWeight, line: Int = (size * 1.35f).toInt(), spacing: Double = 0.0, figures: String? = null) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp, fontFeatureSettings = figures)

/** Heading sizes are set for Cormorant, which runs small; other fonts come down by their [AppFont.scale]. */
private fun heading(font: AppFont, size: Int, weight: FontWeight = FontWeight.Bold): TextStyle {
    val s = size * font.scale
    return TextStyle(
        fontFamily = font.family(), fontWeight = weight, fontSize = s.sp, lineHeight = (s * 1.2f).sp,
        letterSpacing = font.spacing.sp, fontFeatureSettings = HEADING_FIGURES,
    )
}

private fun typography(headingFont: AppFont, bodyFont: AppFont): Typography {
    val f = bodyFont.family()
    return Typography(
        displaySmall = heading(headingFont, 40),
        headlineLarge = heading(headingFont, 34),
        headlineMedium = heading(headingFont, 30),
        headlineSmall = heading(headingFont, 26),
        titleLarge = heading(headingFont, 23),
        titleMedium = base(f, 16, FontWeight.SemiBold),
        titleSmall = base(f, 14, FontWeight.Medium),
        bodyLarge = base(f, 16, FontWeight.Normal),
        bodyMedium = base(f, 14, FontWeight.Normal),
        bodySmall = base(f, 12, FontWeight.Normal),
        // Labels carry counts, badges and small prices: tabular figures so they line up in lists.
        labelLarge = base(f, 14, FontWeight.Medium, spacing = 0.3, figures = TABULAR),
        labelMedium = base(f, 12, FontWeight.Medium, spacing = 0.4, figures = TABULAR),
        labelSmall = base(f, 11, FontWeight.Medium, spacing = 0.5, figures = TABULAR),
    )
}

/** Every style made [factor] times larger, for big-text mode. */
private fun Typography.scaled(factor: Float): Typography {
    fun TextStyle.up() = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
    return Typography(
        displaySmall = displaySmall.up(), headlineLarge = headlineLarge.up(), headlineMedium = headlineMedium.up(),
        headlineSmall = headlineSmall.up(), titleLarge = titleLarge.up(), titleMedium = titleMedium.up(),
        titleSmall = titleSmall.up(), bodyLarge = bodyLarge.up(), bodyMedium = bodyMedium.up(), bodySmall = bodySmall.up(),
        labelLarge = labelLarge.up(), labelMedium = labelMedium.up(), labelSmall = labelSmall.up(),
    )
}

private fun priceStyle(font: AppFont, big: Boolean): TextStyle {
    val k = font.scale * (if (big) 1.2f else 1f)
    return TextStyle(
        fontFamily = font.family(), fontWeight = FontWeight.Bold, fontSize = (18 * k).sp, lineHeight = (22 * k).sp,
        letterSpacing = 0.2.sp, fontFeatureSettings = PRICE_FIGURES,
    )
}

/** Built styles, kept so switching screens doesn't rebuild them. Keyed by font choices and big text. */
private val typographyCache = HashMap<Triple<AppFont, AppFont, Boolean>, Typography>()
private val priceCache = HashMap<Pair<AppFont, Boolean>, TextStyle>()

private fun currentTypography(): Typography {
    val key = Triple(FontSettings.heading, FontSettings.body, AppSettings.bigText)
    return typographyCache.getOrPut(key) {
        val t = typography(key.first, key.second)
        if (key.third) t.scaled(1.2f) else t
    }
}

/**
 * For prices and other numbers: the chosen price font with tabular lining figures. 18sp (for Cormorant); change
 * the size with `PriceStyle.copy(fontSize = …)`. Bigger in big-text mode. No color: pass one (usually [Palette.AccentText]).
 */
val PriceStyle: TextStyle
    get() = priceCache.getOrPut(FontSettings.price to AppSettings.bigText) { priceStyle(FontSettings.price, AppSettings.bigText) }

/** The two corner sizes: [Small] for chips, pills and small controls; [Medium] for cards, sheets, panels and dialogs. */
object Corners {
    val Small = 6.dp
    val Medium = 12.dp
}

/** A real Magic card's corner, for card images (about 5% of the card's shorter side). */
val CardShape = RoundedCornerShape(percent = 5)

/** Tabular figures for counts and quantities in lists: `style.merge(TabularNums)`. */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(Corners.Small),
    small = RoundedCornerShape(Corners.Small),
    medium = RoundedCornerShape(Corners.Medium),
    large = RoundedCornerShape(Corners.Medium),
    extraLarge = RoundedCornerShape(Corners.Medium),
)

@Composable
fun MtgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = currentTypography(), shapes = shapes, content = content)
}

/** Colors for the five Magic colors plus colorless/multicolor, used for chips and mana symbols. */
object MtgColors {
    val W = Color(0xFFF8F1D8)
    val U = Color(0xFF4A90D9)
    val B = Color(0xFF6B6275)
    val R = Color(0xFFE0533D)
    val G = Color(0xFF2E9E5B)
    val C = Color(0xFFB8B3AC)
    val Multi = Color(0xFFD4AF37)

    fun forLetter(c: Char): Color = when (c.uppercaseChar()) {
        'W' -> W; 'U' -> U; 'B' -> B; 'R' -> R; 'G' -> G; else -> C
    }

    /** White for bars and slices on the app background: a deeper cream in light mode, where [W] vanishes. */
    val WOnSurface: Color get() = if (Palette.dark) W else Color(0xFFDCCB92)

    /** [forLetter] for bars and slices drawn on the app background (see [WOnSurface]). */
    fun barFor(c: Char): Color = if (c.uppercaseChar() == 'W') WOnSurface else forLetter(c)

    /** A thin edge for light slices in light mode, so white keeps its shape next to the background. */
    val SliceEdge: Color get() = Color.Black.copy(alpha = 0.22f)

    fun rarity(r: String): Color = when (r) {
        "common" -> Color(0xFFB4B4B4)
        "uncommon" -> Color(0xFFA7C4D6)
        "rare" -> Color(0xFFE2BE5A)
        "mythic" -> Color(0xFFF0712C)
        else -> Color(0xFFB07FD9)
    }
}

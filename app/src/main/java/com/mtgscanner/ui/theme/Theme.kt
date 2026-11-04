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
import androidx.compose.ui.graphics.toArgb
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

    /** The accent. The default red is a touch deeper in light mode, as in the "Small step" look. */
    val Accent: Color get() {
        val a = ThemeSettings.activeAccent
        return if (!dark && a.toArgb() == ThemeSettings.defaultAccent.toArgb()) ThemeSettings.defaultAccentLight else a
    }
    val AccentDeep: Color get() = lerp(Accent, Color(0xFF4A1512), 0.38f)
    /**
     * The accent for text (selected labels, links). Moved toward white (dark mode) or black (light mode) until it
     * reaches about 4.5:1 contrast on the background, so a deep accent such as the default red still reads.
     */
    val AccentText: Color get() {
        val accent = Accent
        val background = Background
        // Read on every recomposition; the contrast search only needs redoing when the colors change.
        if (accent != accentTextFor || background != accentTextOn) {
            accentTextCache = readableOn(accent, background)
            accentTextFor = accent
            accentTextOn = background
        }
        return accentTextCache
    }
    private var accentTextFor: Color = Color.Unspecified
    private var accentTextOn: Color = Color.Unspecified
    private var accentTextCache: Color = Color.Unspecified
    /**
     * Text/icon color for a solid accent fill (buttons, badges): white when it reads (4.5:1) or reads better,
     * otherwise near-black. Works for any accent, including a custom one, in light and dark mode.
     */
    val OnAccent: Color get() = onFill(Accent)

    // Warm "Small step" palette: ink-brown darks, paper lights.
    val Background: Color get() = when { black -> Color.Black; dark -> Color(0xFF100F0E); else -> Color(0xFFE7E2D5) }
    /** Panels (row groups, sheets, dialogs): a clear step above [Background] in both modes. */
    val Surface: Color get() = when { black -> Color(0xFF151412); dark -> Color(0xFF1E1C19); else -> Color(0xFFFDFCF8) }
    val SurfaceHigh: Color get() = when { black -> Color(0xFF221F1C); dark -> Color(0xFF2B2824); else -> Color(0xFFDCD6C8) }
    /** Box tiles: a warm panel a step above the background. */
    val BoxCard: Color get() = when { black -> Color(0xFF171513); dark -> Color(0xFF221F1B); else -> Color(0xFFFAF7F0) }
    val NavBar: Color get() = when { black -> Color.Black; dark -> Color(0xFF080707); else -> Color(0xFFFDFCF8) }
    val Gold: Color get() = if (dark) Color(0xFFF2C14E) else Color(0xFFA87610)
    /** [Gold] for text: darker in light mode so it reads on light panels (about 4.6:1 or better). */
    val GoldText: Color get() = if (dark) Gold else Color(0xFF7C5807)
    val Info: Color get() = if (dark) Color(0xFF5B9BE0) else Color(0xFF2F6FB8)
    val Text: Color get() = if (dark) Color(0xFFECE8E1) else Color(0xFF1C1B19)
    /** Secondary text: 4.6:1 or better on [Background], [Surface] and [SurfaceHigh] in both modes. */
    val Muted: Color get() = if (dark) Color(0xFFA29C92) else Color(0xFF5E5A52)
    /** Default icon tint: a quiet warm gray, a touch softer than [Muted]. */
    val Icon: Color get() = if (dark) Color(0xFF99938A) else Color(0xFF67625A)
    /** Secondary text and icons on always-dark surfaces (camera, scrims over art, game screen), in both modes. */
    val OnDarkMuted: Color get() = Color(0xFFB4B5BB)
    /** Borders of inputs, outlined buttons and chips. */
    val Outline: Color get() = when { black -> Color(0xFF34302A); dark -> Color(0xFF423D36); else -> Color(0xFFB0A999) }
    /** The 1dp line under list rows, tab rows and the bottom bar's top edge. */
    val Rule: Color get() = when { black -> Color(0xFF2A2722); dark -> Color(0xFF332F2A); else -> Color(0xFFCAC4B5) }
    /** Hairline between plain sections: the same as [Rule]. */
    val Hairline: Color get() = Rule
    val Danger: Color get() = if (dark) Color(0xFFE5534B) else Color(0xFFC62F28)
    val Success: Color get() = if (dark) Color(0xFF4CC38A) else Color(0xFF1E8E5A)

    val AccentGradient: Brush get() = Brush.horizontalGradient(listOf(Accent, AccentDeep))
}

/** WCAG contrast ratio between two colors (1..21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

private val InkOnLight = Color(0xFF141414)

/** White or near-black, whichever reads on a solid [fill]; white wins whenever it reaches 4.5:1. */
fun onFill(fill: Color): Color {
    val white = contrastRatio(Color.White, fill)
    return if (white >= 4.5f || white >= contrastRatio(InkOnLight, fill)) Color.White else InkOnLight
}

/**
 * [color] moved just enough to reach [ratio] contrast on [background]: toward black on a light background,
 * toward white on a dark one.
 */
fun readableOn(color: Color, background: Color, ratio: Float = 4.6f): Color {
    val target = if (background.luminance() > 0.4f) Color.Black else Color.White
    var t = 0f
    var c = color
    while (t < 1f && contrastRatio(c, background) < ratio) {
        t += 0.04f
        c = lerp(color, target, t.coerceAtMost(1f))
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
/** Lining tabular figures for labels (see [TabularNums]). */
private const val TABULAR = PRICE_FIGURES

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
        // Left at Material's defaults these are lilac: snackbars (inverse*), their action text, raised
        // surfaces and some containers came out purple against the warm palette.
        surfaceTint = Color.Transparent,
        surfaceBright = p.SurfaceHigh,
        surfaceDim = p.Background,
        inverseSurface = if (p.dark) lerp(p.SurfaceHigh, Color.White, 0.07f) else Color(0xFF2B2824),
        inverseOnSurface = if (p.dark) p.Text else Color(0xFFF2EEE8),
        inversePrimary = if (p.dark) p.AccentText else lerp(p.Accent, Color.White, 0.5f),
        tertiaryContainer = lerp(p.Surface, p.Gold, 0.22f),
        onTertiaryContainer = p.Text,
        errorContainer = lerp(p.Surface, p.Danger, 0.22f),
        onErrorContainer = p.Text,
        scrim = Color.Black,
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
        // Smaller headings are a step lighter so a title next to a bold price doesn't stack two bolds.
        headlineSmall = heading(headingFont, 26, FontWeight.SemiBold),
        titleLarge = heading(headingFont, 23, FontWeight.SemiBold),
        titleMedium = base(f, 16, FontWeight.Medium),
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

/**
 * The two corner sizes: [Small] for chips and small controls; [Medium] for panels, sheets and dialogs.
 * Both are a near-square 3dp in the "Small step" look; card images keep [CardShape].
 */
object Corners {
    val Small = 3.dp
    val Medium = 3.dp
}

/** A real Magic card's corner, for card images (about 5% of the card's shorter side). */
val CardShape = RoundedCornerShape(percent = 5)

/** Tabular (and lining) figures for prices, counts and quantities in lists: `style.merge(TabularNums)`. Lining keeps Cormorant's digits on the line. */
val TabularNums = TextStyle(fontFeatureSettings = "lnum, tnum")

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

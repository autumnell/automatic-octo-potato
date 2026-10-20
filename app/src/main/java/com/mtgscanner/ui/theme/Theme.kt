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

    val Accent: Color get() = ThemeSettings.accent
    val AccentDeep: Color get() = lerp(Accent, Color(0xFF6E1630), 0.38f)
    /** Text/icon color that reads well on top of the accent. */
    val OnAccent: Color get() = if (Accent.luminance() > 0.45f) Color(0xFF141414) else Color.White

    val Background: Color get() = if (dark) Color(0xFF111214) else Color(0xFFF4F4F7)
    val Surface: Color get() = if (dark) Color(0xFF1A1B1F) else Color(0xFFFFFFFF)
    val SurfaceHigh: Color get() = if (dark) Color(0xFF24262C) else Color(0xFFE9EAEF)
    val BoxCard: Color get() = if (dark) Color(0xFF1E2233) else Color(0xFFE4E8F6)
    val NavBar: Color get() = if (dark) Color(0xFF09090A) else Color(0xFFFFFFFF)
    val Gold: Color get() = if (dark) Color(0xFFF2C14E) else Color(0xFFB07D12)
    val Info: Color get() = if (dark) Color(0xFF5B9BE0) else Color(0xFF2F6FB8)
    val Text: Color get() = if (dark) Color(0xFFEDEDEF) else Color(0xFF17181C)
    val Muted: Color get() = if (dark) Color(0xFF9A9BA3) else Color(0xFF666873)
    val Outline: Color get() = if (dark) Color(0xFF34363D) else Color(0xFFD3D5DC)
    val Danger: Color get() = if (dark) Color(0xFFE5534B) else Color(0xFFC62F28)
    val Success: Color get() = if (dark) Color(0xFF4CC38A) else Color(0xFF1E8E5A)

    val AccentGradient: Brush get() = Brush.horizontalGradient(listOf(Accent, AccentDeep))
    /** Background for hero panels: the box color warmed with a little of the accent. */
    val HeroGradient: Brush get() = Brush.linearGradient(listOf(lerp(BoxCard, Accent, if (dark) 0.22f else 0.18f), BoxCard))
}

val Lexend = FontFamily(
    Font(R.font.lexend_light, FontWeight.Light),
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
    Font(R.font.lexend_bold, FontWeight.Bold),
)

private fun scheme(): ColorScheme {
    val p = Palette
    val common = if (p.dark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = p.Accent,
        onPrimary = p.OnAccent,
        primaryContainer = lerp(p.Surface, p.Accent, 0.25f),
        onPrimaryContainer = p.Accent,
        secondary = p.Info,
        onSecondary = Color.White,
        secondaryContainer = lerp(p.Surface, p.Accent, 0.18f),
        onSecondaryContainer = p.Accent,
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

private fun base(size: Int, weight: FontWeight, line: Int = (size * 1.35f).toInt(), spacing: Double = 0.0) =
    TextStyle(fontFamily = Lexend, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val typography = Typography(
    displaySmall = base(34, FontWeight.SemiBold),
    headlineLarge = base(30, FontWeight.SemiBold),
    headlineMedium = base(26, FontWeight.SemiBold),
    headlineSmall = base(22, FontWeight.SemiBold),
    titleLarge = base(20, FontWeight.SemiBold),
    titleMedium = base(16, FontWeight.SemiBold),
    titleSmall = base(14, FontWeight.Medium),
    bodyLarge = base(16, FontWeight.Normal),
    bodyMedium = base(14, FontWeight.Normal),
    bodySmall = base(12, FontWeight.Normal),
    labelLarge = base(14, FontWeight.Medium, spacing = 0.3),
    labelMedium = base(12, FontWeight.Medium, spacing = 0.4),
    labelSmall = base(11, FontWeight.Medium, spacing = 0.5),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MtgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = typography, shapes = shapes, content = content)
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

    fun rarity(r: String): Color = when (r) {
        "common" -> Color(0xFFB4B4B4)
        "uncommon" -> Color(0xFFA7C4D6)
        "rare" -> Color(0xFFE2BE5A)
        "mythic" -> Color(0xFFF0712C)
        else -> Color(0xFFB07FD9)
    }
}

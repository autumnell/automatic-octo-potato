package com.mtgscanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFFB9A3FF),
    secondary = Color(0xFF4FE3C1),
    background = Color(0xFF121212),
    surface = Color(0xFF1A1A1F),
)
private val Light = lightColorScheme(
    primary = Color(0xFF5B3FC4),
    secondary = Color(0xFF00876B),
)

@Composable
fun MtgTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Colors for the five Magic colors plus colorless/multicolor, used for chips and mana symbols. */
object MtgColors {
    val W = Color(0xFFF8F1D8)
    val U = Color(0xFF4A90D9)
    val B = Color(0xFF4B4453)
    val R = Color(0xFFE0533D)
    val G = Color(0xFF2E9E5B)
    val C = Color(0xFFB8B3AC)
    val Multi = Color(0xFFD4AF37)

    fun forLetter(c: Char): Color = when (c.uppercaseChar()) {
        'W' -> W; 'U' -> U; 'B' -> B; 'R' -> R; 'G' -> G; else -> C
    }

    fun rarity(r: String): Color = when (r) {
        "common" -> Color(0xFF9E9E9E)
        "uncommon" -> Color(0xFFA7C4D6)
        "rare" -> Color(0xFFD4AF37)
        "mythic" -> Color(0xFFE8642C)
        else -> Color(0xFFB07FD9)
    }
}

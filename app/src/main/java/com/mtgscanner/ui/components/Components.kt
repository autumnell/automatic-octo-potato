package com.mtgscanner.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import java.text.NumberFormat
import java.util.Locale

private val usd = NumberFormat.getCurrencyInstance(Locale.US)
fun formatUsd(v: Double?): String = if (v == null) "--" else usd.format(v)
fun formatUsd(v: String?): String = formatUsd(v?.toDoubleOrNull())

/** Draws a mana cost like "{2}{U}{U}" as small colored circles. */
@Composable
fun ManaCost(cost: String, size: Dp = 16.dp) {
    if (cost.isBlank()) return
    val symbols = Regex("""\{([^}]+)\}|(//)""").findAll(cost).map { it.groupValues[1].ifEmpty { "//" } }.toList()
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        symbols.forEach { sym ->
            if (sym == "//") {
                Text("//", fontSize = 11.sp, color = Palette.Muted)
            } else {
                val letters = sym.filter { it in "WUBRG" }
                val bg = when {
                    letters.length > 1 -> MtgColors.Multi
                    letters.length == 1 -> MtgColors.forLetter(letters[0])
                    else -> MtgColors.C
                }
                val fg = if (bg == MtgColors.W || bg == MtgColors.C || bg == MtgColors.Multi) Color.Black else Color.White
                Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Text(
                        sym.replace("/", ""),
                        color = fg,
                        fontSize = (size.value * 0.55f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun RarityDot(rarity: String, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MtgColors.rarity(rarity)))
}

/** A soft pulsing block shown while an image loads. */
@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.background(Palette.SurfaceHigh.copy(alpha = a)))
}

/** A card image with rounded corners like a real card, and a placeholder while loading. */
@Composable
fun CardImage(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val shape = RoundedCornerShape(corner)
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(63f / 88f).clip(shape),
        loading = { Shimmer(Modifier.fillMaxSize()) },
        error = { Box(Modifier.fillMaxSize().background(Palette.SurfaceHigh)) },
    )
}

/** Big orange gradient button, the main action on a screen. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Palette.AccentGradient else Brush.horizontalGradient(listOf(Palette.SurfaceHigh, Palette.SurfaceHigh)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Palette.OnAccent else Palette.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), color = if (enabled) Palette.OnAccent else Palette.Muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Rounded dark tile with a label and a big orange number, like a dashboard stat. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, icon: ImageVector? = null) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Palette.Accent, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** Dark rounded pill with an icon, used for Sort / Group / Filter buttons. */
@Composable
fun ToolChip(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (active) Palette.Accent.copy(alpha = 0.16f) else Palette.SurfaceHigh,
        border = if (active) BorderStroke(1.dp, Palette.Accent.copy(alpha = 0.6f)) else null,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) Palette.Accent else Palette.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Accent else Palette.Text)
        }
    }
}

/** Small selectable pill used in filter rows. */
@Composable
fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent.copy(alpha = 0.18f) else Palette.SurfaceHigh)
            .border(1.dp, if (selected) Palette.Accent else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.Accent else Palette.Text)
    }
}

@Composable
fun ColorPip(letter: Char, size: Dp = 12.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MtgColors.forLetter(letter)).border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape))
}

/** Little gradient "FOIL" tag. */
@Composable
fun FoilTag(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7DD3FC), Color(0xFFC4B5FD), Color(0xFFF9A8D4), Color(0xFFFDE68A))))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("FOIL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
    }
}

/** A card in a grid: the image with a quantity badge and its price underneath. */
@Composable
fun CardTile(
    imageUrl: String?,
    name: String,
    price: String,
    modifier: Modifier = Modifier,
    quantity: Int? = null,
    foil: Boolean = false,
    onClick: () -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box {
            CardImage(imageUrl, Modifier.fillMaxWidth(), corner = 9.dp)
            if (quantity != null && quantity > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text("×$quantity", color = Color.White, style = MaterialTheme.typography.labelMedium) }
            }
            if (foil) FoilTag(Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(price, style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
    }
}

fun boxIcon(kind: String): ImageVector = when (kind) {
    "binder" -> Icons.AutoMirrored.Filled.MenuBook
    "deck" -> Icons.Filled.Style
    "wishlist" -> Icons.Filled.AutoAwesome
    "trade" -> Icons.Filled.SwapHoriz
    else -> Icons.Filled.Inventory2
}

/** Centered message for empty screens. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Palette.Surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

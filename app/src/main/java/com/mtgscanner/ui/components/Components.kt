package com.mtgscanner.ui.components

import androidx.compose.animation.core.tween
import com.mtgscanner.ui.theme.Motion
import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil.compose.AsyncImage
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums
import java.text.NumberFormat
import java.util.Locale

private val usd = NumberFormat.getCurrencyInstance(Locale.US)
fun formatUsd(v: Double?): String = if (v == null) "--" else usd.format(v)
fun formatUsd(v: String?): String = formatUsd(v?.toDoubleOrNull())

private val MANA_SYMBOL = Regex("""\{([^}]+)\}|(//)""")

/** Draws a mana cost like "{2}{U}{U}" as small colored circles. */
@Composable
fun ManaCost(cost: String, size: Dp = 16.dp) {
    if (cost.isBlank()) return
    val symbols = remember(cost) { MANA_SYMBOL.findAll(cost).map { it.groupValues[1].ifEmpty { "//" } }.toList() }
    // Symbol text is sized in dp so it always fits its circle, even with a large system font.
    val density = LocalDensity.current
    val symbolText = with(density) { (size * 0.55f).toSp() }
    val slashText = with(density) { 11.dp.toSp() }
    // Read aloud as words ("2, blue, blue"), not the symbols' letters.
    val spoken = remember(cost) { "Mana cost " + com.mtgscanner.util.ManaSpeech.of(cost) }
    Row(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Space.s1), verticalAlignment = Alignment.CenterVertically,
    ) {
        symbols.forEach { sym ->
            if (sym == "//") {
                Text("//", fontSize = slashText, color = Palette.Muted)
            } else if (Mana.has(sym)) {
                Mana.Symbol(sym, size)
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
                        fontSize = symbolText,
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

/**
 * A card image with a real card's corners ([CardShape]), and a plain placeholder while loading.
 * Uses AsyncImage (no subcomposition) so long lists and grids stay smooth.
 */
@Composable
fun CardImage(
    url: String?,
    modifier: Modifier = Modifier,
    /** Tap and hold shows the card big (see [cardZoom]). */
    zoom: Boolean = false,
    /** With [zoom]: the image handles taps itself, so a parent's own long press doesn't also fire. */
    onTap: (() -> Unit)? = null,
    /** Shown in grey, like a card you don't have. */
    grey: Boolean = false,
) {
    val shape = CardShape
    val fill = Palette.SurfaceHigh
    val placeholder = remember(fill) { ColorPainter(fill) }
    // If the stored link fails, ask Scryfall for the card's current image instead.
    val backup = remember(url) { scryfallImageFallback(url) }
    var useBackup by remember(url) { mutableStateOf(false) }
    AsyncImage(
        model = if (useBackup) backup else url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(63f / 88f).clip(shape).background(fill)
            .then(if (zoom) Modifier.cardZoom(url, onTap = onTap) else Modifier),
        placeholder = placeholder,
        error = placeholder,
        onError = { if (!useBackup && backup != null) useBackup = true },
        colorFilter = if (grey) GreyFilter else null,
    )
}

/** Takes the color out of a picture. */
private val GreyFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) },
)

private val SCRYFALL_IMAGE = Regex("""/(small|normal|large|png|art_crop|border_crop)/(front|back)/[0-9a-f]/[0-9a-f]/([0-9a-f-]{36})""")

/** Scryfall's always-current image link for the card in a cards.scryfall.io image URL. */
fun scryfallImageFallback(url: String?): String? {
    val m = SCRYFALL_IMAGE.find(url ?: return null) ?: return null
    val (size, face, id) = m.destructured
    return "https://api.scryfall.com/cards/$id?format=image&version=$size" + if (face == "back") "&face=back" else ""
}

/**
 * The main button on a screen (one per screen, design/Design.md): flat, filled with the accent. [hero] is kept for
 * old callers and looks the same; the design system bans gradient buttons.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    hero: Boolean = false,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Corners.Medium))
            .then(
                when {
                    !enabled -> Modifier.background(Palette.SurfaceHigh)
                    else -> Modifier.background(Palette.Accent)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Palette.OnAccent else Palette.Icon, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s2))
        }
        Text(text, color = if (enabled) Palette.OnAccent else Palette.Faint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Rounded tile with a label and a big number. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, icon: ImageVector? = null) {
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(Space.s4), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s2))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            }
            Spacer(Modifier.height(Space.s2))
            Text(value, style = MaterialTheme.typography.headlineSmall.merge(TabularNums), color = Palette.Text, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** Small outlined button with an icon, used for Sort / Group / Filter. Active: a filled panel with a text-colored edge. */
@Composable
fun ToolChip(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, compact: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Corners.Small),
        color = Palette.SurfaceHigh,
        border = if (active) BorderStroke(1.dp, Palette.Text) else null,
    ) {
        // Compact chips share a row with other controls, so they keep less padding around the label.
        Row(
            Modifier.padding(horizontal = if (compact) Space.s3 else Space.s4, vertical = if (compact) Space.s2 else Space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (active) Palette.Text else Palette.Icon, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(if (compact) Space.s1 else Space.s2))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
        }
    }
}

/** Small selectable chip used in filter rows: outlined; selected is filled with a text-colored edge (no accent). */
@Composable
fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(Corners.Small))
            .background(Palette.SurfaceHigh)
            .border(1.dp, if (selected) Palette.Text else Color.Transparent, RoundedCornerShape(Corners.Small))
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.s2))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text, fontWeight = if (selected) FontWeight.SemiBold else null)
    }
}

@Composable
fun ColorPip(letter: Char, size: Dp = 12.dp) {
    // The real mana symbol, a little larger so it reads at chip size.
    if (letter.uppercaseChar() in "WUBRGC") { Mana.Symbol(letter.uppercase(), size + 4.dp); return }
    if (letter.uppercaseChar() == 'M') { Mana.MultiSymbol(size + 4.dp); return }
    Box(Modifier.size(size).clip(CircleShape).background(MtgColors.forLetter(letter)).border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape))
}

/** Small "Foil" tag: a thin silver outline, no fill. */
@Composable
fun FoilTag(modifier: Modifier = Modifier) {
    val silver = Color(0xFFB8BCC4)
    Box(
        modifier
            .border(1.dp, silver, RoundedCornerShape(Corners.Small))
            .padding(horizontal = Space.s2, vertical = 1.dp),
    ) {
        Text("Foil", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = silver)
    }
}

/** A card in a grid: the image with a quantity badge and its price underneath. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CardTile(
    imageUrl: String?,
    name: String,
    price: String,
    modifier: Modifier = Modifier,
    quantity: Int? = null,
    foil: Boolean = false,
    /** Long press, used to start picking several cards. */
    onLongClick: (() -> Unit)? = null,
    /** Shown with a check mark and an outline while picking cards. */
    selected: Boolean = false,
    /** Copies already in the collection; shows a small badge when above zero. */
    owned: Int = 0,
    /** Extra modifier for the picture alone, e.g. to grow it into the card page. */
    imageModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) Palette.Accent.copy(alpha = 0.22f) else Color.Transparent, tween(Motion.fast, easing = Motion.ease), label = "tileSelected")
    Column(
        modifier
            .pressScale(press)
            .clip(RoundedCornerShape(Corners.Small))
            .background(tint)
            .combinedClickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick)
            .padding(Space.s1),
    ) {
        Box {
            // Holding the picture shows it big; with a tile long press (picking), the rest of the tile keeps it.
            CardImage(imageUrl, Modifier.fillMaxWidth().then(imageModifier), zoom = true, onTap = if (onLongClick != null) onClick else null)
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(Space.s2).size(24.dp).clip(CircleShape).background(Palette.Accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, "Selected", tint = Palette.OnAccent, modifier = Modifier.size(18.dp)) }
            }
            if (quantity != null && quantity > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.s2)
                        .clip(RoundedCornerShape(Corners.Small))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = Space.s2, vertical = Space.s1),
                ) { Text("×$quantity", color = Color.White, style = MaterialTheme.typography.labelMedium) }
            }
            if (foil) FoilTag(Modifier.align(Alignment.BottomStart).padding(Space.s2))
            if (owned > 0 && !selected) OwnedBadge(owned, Modifier.align(Alignment.TopStart).padding(Space.s2))
        }
        Spacer(Modifier.height(Space.s1))
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (AppSettings.showPrices) Text(price, style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Palette.Text)
    }
}

/** Small "✓ 3" badge marking a card you already own. */
@Composable
fun OwnedBadge(count: Int, modifier: Modifier = Modifier, foil: Int = 0) {
    // Foils are said apart: "2 · 1 foil", or "1 foil" when every copy is.
    val text = when {
        foil <= 0 -> "$count"
        foil >= count -> "$count foil"
        else -> "$count · $foil foil"
    }
    Row(
        modifier
            .clip(RoundedCornerShape(Corners.Small))
            .background(Palette.Accent)
            .padding(horizontal = Space.s2, vertical = Space.s1)
            .semantics(mergeDescendants = true) { contentDescription = "You own $text" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Check, null, tint = Palette.OnAccent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(Space.s1))
        Text(text, color = Palette.OnAccent, style = MaterialTheme.typography.labelSmall)
    }
}

/** " · $12.34", or nothing when prices are hidden. */
fun priceSuffix(v: Double?): String = if (AppSettings.showPrices) " · ${formatUsd(v)}" else ""

fun boxIcon(kind: String): ImageVector = when (kind) {
    "binder" -> Icons.AutoMirrored.Outlined.MenuBook
    "deck" -> Icons.Outlined.Style
    "wishlist" -> Icons.Outlined.FavoriteBorder
    "trade" -> Icons.Outlined.SwapHoriz
    else -> Icons.Outlined.Inventory2
}

/**
 * Older call shape for empty screens. Draws the same quiet [EmptyState] as everywhere else (the icon is no
 * longer shown), with [body] as one muted line and [action] under it.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    EmptyState(title, modifier, body = body, content = action)
}

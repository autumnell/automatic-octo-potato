package com.mtgscanner.ui.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.game.GameState
import com.mtgscanner.ui.components.ManaIcons
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space

/**
 * The back of a player's panel, shown when they tap ⋯ and the panel flips over: commander damage from each
 * opponent, laid out like the table itself so each tile sits where that player sits, then poison, commander tax
 * and rad. Tap to add one, hold to take one back. [rotation] is how far the panel is turned to face its player.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerBack(
    g: GameState, i: Int, modifier: Modifier,
    onCommander: (from: Int, partner: Boolean, delta: Int) -> Unit,
    onPoison: (Int) -> Unit,
    onCounter: (kind: String, delta: Int) -> Unit,
    onPlayer: () -> Unit,
    onDone: () -> Unit,
    rotation: Int = 0,
) {
    val p = g.players[i]
    val showDamage = g.format.commanderDamage && g.players.size > 1
    val counters = buildList {
        add(BackCounter("Poison", p.poison, ManaIcons.Poison, warn = p.poison >= g.format.poisonLimit - 3, onChange = onPoison))
        if (g.format.usesCommander) {
            add(BackCounter("Tax", p.commanderTax, ManaIcons.Commander) { d -> onCounter("Commander cast", d) })
            if (g.partners) add(BackCounter("Partner", p.partnerTax, ManaIcons.Commander) { d -> onCounter("Partner cast", d) })
        }
        add(BackCounter("Rad", p.rad, ManaIcons.Rad) { d -> onCounter("Rad", d) })
    }
    // The table's shape as this player sees it from their seat: the phone's shape, turned with them.
    val config = LocalConfiguration.current
    val phone = config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)
    val turned = (rotation % 360 + 360) % 360
    val ratio = if (turned == 90 || turned == 270) 1f / phone else phone
    Column(modifier.fillMaxSize().background(Palette.StagePanel).padding(horizontal = Space.s3, vertical = Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onPlayer, modifier = Modifier.size(BACK_BUTTON)) { Icon(Icons.Outlined.Person, "${p.name}: name, color, art and more", tint = Color.White) }
            IconButton(onClick = onDone, modifier = Modifier.size(BACK_BUTTON)) { Icon(Icons.Outlined.Check, "Done", tint = Color.White) }
        }
        if (showDamage) DamageLabel()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val width = maxWidth
            val height = maxHeight
            // A short, wide panel (most seats at a table of 3 or 4) puts the table map and the counters side by
            // side; a taller one stacks them and scrolls if it has to.
            val wide = width > height * 1.2f
            val map: @Composable (Modifier) -> Unit = { m -> SeatMap(g, i, turned, ratio, m, onCommander) }
            if (wide) Row(Modifier.fillMaxSize()) {
                if (showDamage) {
                    map(Modifier.heightIn(max = height).widthIn(max = (width - COUNTERS_WIDTH).coerceAtLeast(MAP_MIN / 2)))
                    Spacer(Modifier.width(Space.s3))
                }
                Column(
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.s1, Alignment.CenterVertically),
                ) { counters.forEach { BackStepper(it) } }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (showDamage) {
                    map(Modifier.heightIn(max = (height * 0.6f).coerceAtLeast(MAP_MIN)).widthIn(max = width))
                    Spacer(Modifier.height(Space.s3))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    counters.forEach { BackStepper(it) }
                }
            }
        }
    }
}

/** The back's icon buttons and counter buttons: smaller than usual so a short panel still fits them. */
private val BACK_BUTTON = 36.dp

/** Room kept for the counters beside the table map: label, −, value, +. */
private val COUNTERS_WIDTH = 168.dp

/** The table map never shrinks below this on a tall panel. */
private val MAP_MIN = 120.dp

/** A counter on the back of a panel. */
private class BackCounter(val label: String, val value: Int, val icon: ImageVector, val warn: Boolean = false, val onChange: (Int) -> Unit)

@Composable
private fun DamageLabel() {
    Text("Commander damage", color = Palette.OnDarkMuted, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(Space.s1))
}

/**
 * A small picture of the table in its real layout: every other player's seat is a damage tile in their color,
 * this player's own seat says You. It's drawn the way the phone lies on the table, so each tile points at the
 * player it belongs to, and the words in it are turned to face whoever is reading. [ratio] is its width over
 * height as the reader sees it.
 */
@Composable
private fun SeatMap(
    g: GameState, i: Int, turned: Int, ratio: Float, modifier: Modifier,
    onCommander: (from: Int, partner: Boolean, delta: Int) -> Unit,
) {
    val p = g.players[i]
    Box(modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)) {
        Box(Modifier.rotateLayout(360 - turned).fillMaxSize()) {
            TableSeats(g.players.size, g.layout, SEAT_GAP) { seat, _, m ->
                if (seat == i) Box(
                    m.clip(RoundedCornerShape(Corners.Small)).border(1.dp, Palette.OnDarkMuted.copy(alpha = 0.5f), RoundedCornerShape(Corners.Small)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("You", color = Palette.OnDarkMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.rotateLayout(turned))
                } else {
                    val o = g.players[seat]
                    val color = PLAYER_COLORS[o.color % PLAYER_COLORS.size]
                    Box(m) {
                        // With partners, the seat holds two tiles: the commander and the partner.
                        Row(Modifier.rotateLayout(turned).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(SEAT_GAP * 2)) {
                            (if (g.partners) listOf(false, true) else listOf(false)).forEach { partner ->
                                DamageTile(
                                    name = o.name, partner = partner, color = color, damage = p.damageFrom(seat, partner),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onChange = { d -> onCommander(seat, partner, d) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Half the space between seats on the table map; on the 4dp grid once both sides add up. */
private val SEAT_GAP = 2.dp

/** Commander damage from one opponent: their color, their name, the number. Tap +1, hold −1. Red at 21. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DamageTile(name: String, partner: Boolean, color: Color, damage: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val lethal = damage >= GameState.COMMANDER_LIMIT
    val shape = RoundedCornerShape(Corners.Small)
    BoxWithConstraints(
        modifier.clip(shape).background(color)
            .border(if (lethal) 3.dp else 0.dp, if (lethal) Palette.Danger else Color.Transparent, shape)
            .semantics(mergeDescendants = true) { contentDescription = "Commander damage from $name${if (partner) " (partner)" else ""}: $damage" }
            .combinedClickable(
                onClickLabel = "+1 from $name", onLongClickLabel = "−1 from $name",
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(1) },
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onChange(-1) },
            )
            .padding(Space.s1),
        contentAlignment = Alignment.Center,
    ) {
        // A small seat shows just the number; the color says whose it is. A roomier one adds the name.
        val roomy = maxHeight >= 48.dp && maxWidth >= 48.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (roomy) Text(
                if (partner) "Partner" else name, color = Color.White, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text("$damage", color = Color.White, fontSize = if (roomy) 22.sp else 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** A small counter on one line: its icon over its name, then − value +. Tax shows the tax; its buttons count casts. */
@Composable
private fun BackStepper(c: BackCounter) {
    val haptics = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(c.icon, null, tint = Palette.OnDarkMuted, modifier = Modifier.size(14.dp))
            Text(c.label, color = Palette.OnDarkMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.s1))
        listOf(-1, 1).forEach { sign ->
            if (sign > 0) Text(
                "${c.value}", color = if (c.warn && c.value > 0) Palette.Danger else Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.width(32.dp),
            )
            Box(
                Modifier.size(BACK_BUTTON).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                    .semantics { contentDescription = "${if (sign < 0) "Less" else "More"} ${c.label}" }
                    .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); c.onChange(sign) },
                contentAlignment = Alignment.Center,
            ) { Text(if (sign < 0) "−" else "+", color = Color.White, fontSize = 20.sp) }
        }
    }
}

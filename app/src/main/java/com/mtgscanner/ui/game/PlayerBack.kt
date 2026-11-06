package com.mtgscanner.ui.game

import androidx.compose.foundation.ExperimentalFoundationApi
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

/** One commander that can hit a player: whose it is, and whether it's their partner. */
internal data class DamageSource(val from: Int, val partner: Boolean)

/** The commanders that can hit player [i]: every other player's, and their partners' when partners count. Pure, for tests. */
internal fun damageSources(players: Int, i: Int, partners: Boolean): List<DamageSource> =
    (0 until players).filter { it != i }.flatMap { from ->
        listOf(DamageSource(from, false)) + if (partners) listOf(DamageSource(from, true)) else emptyList()
    }

/**
 * The back of a player's panel, shown when they tap ⋯ and the panel flips over: commander damage from each
 * opponent in that opponent's color, then poison, commander tax and rad. Tap to add one, hold to take one back.
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
) {
    val p = g.players[i]
    val sources = if (g.format.commanderDamage) damageSources(g.players.size, i, g.partners) else emptyList()
    val counters = buildList {
        add(BackCounter("Poison", p.poison, ManaIcons.Poison, warn = p.poison >= g.format.poisonLimit - 3, onChange = onPoison))
        if (g.format.usesCommander) {
            add(BackCounter("Tax", p.commanderTax, ManaIcons.Commander) { d -> onCounter("Commander cast", d) })
            if (g.partners) add(BackCounter("Partner", p.partnerTax, ManaIcons.Commander) { d -> onCounter("Partner cast", d) })
        }
        add(BackCounter("Rad", p.rad, ManaIcons.Rad) { d -> onCounter("Rad", d) })
    }
    Column(modifier.fillMaxSize().background(Palette.StagePanel).padding(horizontal = Space.s3, vertical = Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onPlayer, modifier = Modifier.size(BACK_BUTTON)) { Icon(Icons.Outlined.Person, "${p.name}: name, color, art and more", tint = Color.White) }
            IconButton(onClick = onDone, modifier = Modifier.size(BACK_BUTTON)) { Icon(Icons.Outlined.Check, "Done", tint = Color.White) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val width = maxWidth
            val cols = sources.size.coerceIn(1, 3)
            val rows = (sources.size + cols - 1) / cols
            // A short, wide panel (most seats at a table of 3 or 4) puts the damage tiles and the counters side
            // by side so nothing is cut off; a taller one stacks them and scrolls if it has to.
            val wide = maxWidth >= 300.dp && maxWidth > maxHeight * 1.3f && rows <= 2
            if (wide) Row(Modifier.fillMaxSize()) {
                if (sources.isNotEmpty()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        DamageLabel()
                        DamageGrid(g, p, sources, cols, Modifier.weight(1f), fill = true, onCommander = onCommander)
                    }
                    Spacer(Modifier.width(Space.s3))
                }
                Column(
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.s1, Alignment.CenterVertically),
                ) { counters.forEach { BackStepper(it) } }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (sources.isNotEmpty()) {
                    DamageLabel()
                    DamageGrid(g, p, sources, (width.value / 96f).toInt().coerceIn(1, 5), Modifier, fill = false, onCommander = onCommander)
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

/** A counter on the back of a panel. */
private class BackCounter(val label: String, val value: Int, val icon: ImageVector, val warn: Boolean = false, val onChange: (Int) -> Unit)

@Composable
private fun DamageLabel() {
    Text("Commander damage", color = Palette.OnDarkMuted, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(Space.s1))
}

/** The damage tiles in rows of [cols]; with [fill] the rows share the height they're given. */
@Composable
private fun DamageGrid(
    g: GameState, p: com.mtgscanner.game.Player, sources: List<DamageSource>, cols: Int, modifier: Modifier, fill: Boolean,
    onCommander: (from: Int, partner: Boolean, delta: Int) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        sources.chunked(cols).forEach { row ->
            Row(if (fill) Modifier.weight(1f) else Modifier, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                row.forEach { s ->
                    val o = g.players[s.from]
                    DamageTile(
                        name = o.name + if (s.partner) " · partner" else "",
                        color = PLAYER_COLORS[o.color % PLAYER_COLORS.size],
                        damage = p.damageFrom(s.from, s.partner),
                        modifier = Modifier.weight(1f).then(if (fill) Modifier.fillMaxHeight() else Modifier),
                        onChange = { d -> onCommander(s.from, s.partner, d) },
                    )
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Commander damage from one opponent: their color, their name, the number. Tap +1, hold −1. Red at 21. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DamageTile(name: String, color: Color, damage: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val lethal = damage >= GameState.COMMANDER_LIMIT
    val shape = RoundedCornerShape(Corners.Small)
    Column(
        modifier.heightIn(min = 56.dp).clip(shape).background(color)
            .border(if (lethal) 3.dp else 0.dp, if (lethal) Palette.Danger else Color.Transparent, shape)
            .semantics(mergeDescendants = true) { contentDescription = "Commander damage from $name: $damage" }
            .combinedClickable(
                onClickLabel = "+1 from $name", onLongClickLabel = "−1 from $name",
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(1) },
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onChange(-1) },
            )
            .padding(horizontal = Space.s2, vertical = Space.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$damage", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
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

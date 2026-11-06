package com.mtgscanner.ui.decks.playtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.QuietButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums

/** Counters offered on every permanent; any other kind can be typed in. */
private val COUNTER_KINDS = listOf(Piece.PLUS, Piece.MINUS, "Loyalty", "Charge", "Shield", "Stun")

/**
 * Everything one card can do where it is: play it from hand, tap, flip, counters, copy as a token, or move it
 * to any other zone (top or bottom of the library).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CardActionsSheet(s: Side, side: Int, zone: Zone, p: Piece, vm: PlaytestViewModel, onDismiss: () -> Unit) {
    fun move(to: Zone, bottom: Boolean = false) {
        vm.act { Playtest.move(it, side, p.id, to, bottom) }
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.Top) {
                CardImage(p.card.imageUrl, Modifier.width(112.dp), zoom = p.card.imageUrl != null)
                Spacer(Modifier.width(Space.s3))
                Column(Modifier.weight(1f)) {
                    Text(p.card.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (p.card.typeLine.isNotBlank()) Text(p.card.typeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Text(zone.label + if (p.card.token) " · token" else "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    if (zone == Zone.COMMAND && s.commanderCasts > 0) {
                        Text("Tax ${2 * s.commanderCasts}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
            }
            Spacer(Modifier.height(Space.s3))

            // The main action for where the card is.
            when (zone) {
                Zone.HAND, Zone.COMMAND -> GradientButton(
                    if (p.card.isPermanent || p.card.typeLine.isBlank()) "Play" else "Cast",
                    { move(if (p.card.isPermanent || p.card.typeLine.isBlank()) Zone.BATTLEFIELD else Zone.GRAVEYARD) },
                    Modifier.fillMaxWidth(),
                )
                Zone.BATTLEFIELD -> Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    QuietButton(onClick = { vm.act { Playtest.toggleTap(it, side, p.id) }; onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Corners.Medium)) {
                        Text(if (p.tapped) "Untap" else "Tap")
                    }
                    QuietButton(onClick = { vm.act { Playtest.flip(it, side, p.id) }; onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Corners.Medium)) {
                        Text(if (p.faceDown) "Turn face up" else "Turn face down")
                    }
                }
                else -> {}
            }

            if (zone == Zone.BATTLEFIELD) {
                Spacer(Modifier.height(Space.s3))
                Text("Counters", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                (COUNTER_KINDS + p.counters.keys.filter { it !in COUNTER_KINDS }).forEach { kind ->
                    CounterRow(kind, p.counters[kind] ?: 0) { d -> vm.act { Playtest.counter(it, side, p.id, kind, d) } }
                }
                var custom by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        custom, { custom = it }, singleLine = true, placeholder = { Text("Other counter") },
                        shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(), modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val k = custom.trim()
                        if (k.isNotEmpty()) vm.act { Playtest.counter(it, side, p.id, k, 1) }
                        custom = ""
                    }, enabled = custom.isNotBlank()) { Text("Add") }
                }
                TextButton(onClick = { vm.act { Playtest.token(it, side, p.card) }; onDismiss() }) { Text("Make a token copy") }
            }

            Spacer(Modifier.height(Space.s3))
            Text("Move to", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Spacer(Modifier.height(Space.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                listOf(Zone.BATTLEFIELD, Zone.HAND, Zone.GRAVEYARD, Zone.EXILE, Zone.COMMAND).filter { it != zone }.forEach { z ->
                    SelectPill(z.label, false, { move(z) })
                }
                SelectPill("Top of library", false, { move(Zone.LIBRARY) })
                SelectPill("Bottom of library", false, { move(Zone.LIBRARY, bottom = true) })
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

@Composable
private fun CounterRow(kind: String, n: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(kind, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(-1) }, enabled = n > 0) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$n", style = MaterialTheme.typography.titleMedium.merge(TabularNums), modifier = Modifier.width(32.dp))
        TextButton(onClick = { onChange(1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Text, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Text)

/** The cards in one zone (the library from the top down); tap one for its actions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ZoneSheet(s: Side, side: Int, zone: Zone, vm: PlaytestViewModel, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val cards = s[zone]
    // The library stays hidden until you choose to look, as when you search it in a real game.
    var reveal by remember(zone) { mutableStateOf(zone != Zone.LIBRARY) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("${s.name} · ${zone.label} (${cards.size})", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (zone == Zone.LIBRARY) {
                Spacer(Modifier.height(Space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    SelectPill("Draw", false, { vm.act { Playtest.draw(it, side) } })
                    SelectPill("Mill 1", false, { cards.firstOrNull()?.let { c -> vm.act { Playtest.move(it, side, c.id, Zone.GRAVEYARD) } } })
                    SelectPill("Exile top", false, { cards.firstOrNull()?.let { c -> vm.act { Playtest.move(it, side, c.id, Zone.EXILE) } } })
                    SelectPill("Shuffle", false, { vm.shuffle(side); reveal = false })
                    if (!reveal) SelectPill("Search", false, { reveal = true })
                }
            }
            Spacer(Modifier.height(Space.s3))
            if (cards.isEmpty()) Text("Empty", color = Palette.Muted, modifier = Modifier.padding(vertical = Space.s4))
            if (reveal) {
                cards.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        row.forEach { c ->
                            Column(Modifier.weight(1f)) {
                                PieceTile(c, 76.dp, onTap = { onPick(c.id) }, onHold = { onPick(c.id) })
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

/** Common tokens one tap away, and any other token by name, type and power/toughness. */
private val PRESETS = listOf(
    PlayCard("Treasure", typeLine = "Token Artifact — Treasure"),
    PlayCard("Clue", typeLine = "Token Artifact — Clue"),
    PlayCard("Food", typeLine = "Token Artifact — Food"),
    PlayCard("Blood", typeLine = "Token Artifact — Blood"),
    PlayCard("Soldier", typeLine = "Token Creature — Soldier", power = "1", toughness = "1"),
    PlayCard("Spirit", typeLine = "Token Creature — Spirit", power = "1", toughness = "1"),
    PlayCard("Goblin", typeLine = "Token Creature — Goblin", power = "1", toughness = "1"),
    PlayCard("Saproling", typeLine = "Token Creature — Saproling", power = "1", toughness = "1"),
    PlayCard("Zombie", typeLine = "Token Creature — Zombie", power = "2", toughness = "2"),
    PlayCard("Beast", typeLine = "Token Creature — Beast", power = "3", toughness = "3"),
    PlayCard("Angel", typeLine = "Token Creature — Angel", power = "4", toughness = "4"),
    PlayCard("Dragon", typeLine = "Token Creature — Dragon", power = "5", toughness = "5"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TokenSheet(onMake: (PlayCard, Int) -> Unit, onDismiss: () -> Unit) {
    var count by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var power by remember { mutableStateOf("") }
    var toughness by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Make tokens", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (count > 1) count-- }, enabled = count > 1) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("×$count", style = MaterialTheme.typography.titleMedium.merge(TabularNums))
                TextButton(onClick = { if (count < 20) count++ }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.height(Space.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                PRESETS.forEach { c ->
                    val pt = if (c.power != null) " ${c.power}/${c.toughness}" else ""
                    SelectPill(c.name + pt, false, { onMake(c, count) })
                }
            }
            Spacer(Modifier.height(Space.s4))
            Text("Another token", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Spacer(Modifier.height(Space.s2))
            OutlinedTextField(
                name, { name = it }, singleLine = true, placeholder = { Text("Name, like Elf Warrior") },
                shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.s2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                OutlinedTextField(
                    power, { power = it.take(3) }, singleLine = true, placeholder = { Text("Power") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    toughness, { toughness = it.take(3) }, singleLine = true, placeholder = { Text("Toughness") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(), modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.s3))
            GradientButton(
                "Make $count",
                {
                    val creature = power.isNotBlank() || toughness.isNotBlank()
                    onMake(
                        PlayCard(
                            name.trim(), typeLine = if (creature) "Token Creature" else "Token",
                            power = power.trim().ifBlank { null } ?: if (creature) "0" else null,
                            toughness = toughness.trim().ifBlank { null } ?: if (creature) "0" else null,
                        ),
                        count,
                    )
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            )
            Spacer(Modifier.height(Space.s6))
        }
    }
}

/** What's happened this game, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogSheet(log: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("What happened", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Space.s2))
            log.asReversed().forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Space.s1)) }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

package com.mtgscanner.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.CommanderRules
import com.mtgscanner.data.CommanderSlot
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DecklistParser

/** How many cards a deck of [format] plays: 100 for most commander formats, 60 otherwise; null with no format. */
internal fun deckTarget(format: String): Int? {
    val f = DeckFormats.of(format)
    return when {
        f.id.isEmpty() -> null
        f.id in setOf("oathbreaker", "brawl", "standardbrawl") -> 60
        f.commander -> 100
        else -> 60
    }
}

/**
 * The top of edit mode, as one panel: the name (typed in place), the format, the card count against the format's
 * size, and for commander formats the commander slots. Empty optional slots (partner, companion) sit on one line
 * of small buttons instead of a panel each.
 */
@Composable
fun DeckEditHeader(ui: DeckUi, vm: DeckViewModel, modifier: Modifier = Modifier) {
    val commander = DeckFormats.isCommander(ui.format)
    val leads = remember(ui.commanders) { ui.commanders.all.map { DeckNames.key(it) }.toSet() }
    val mainCount = remember(ui.text, leads) {
        DecklistParser.parse(ui.text).filter { it.section == "Main" && DeckNames.key(it.name) !in leads }.sumOf { it.quantity }
    }
    // The commanders count toward a commander deck's 100; the companion doesn't.
    val count = mainCount + if (commander) ui.commanders.commanders.size else 0
    val target = deckTarget(ui.format)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).background(Palette.Surface)) {
        NameField(ui.name, vm::setName)
        HorizontalDivider(color = Palette.Rule)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Space.s1, end = Space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatMenu(ui.format, vm::setFormat)
            Spacer(Modifier.weight(1f))
            Text(
                if (target != null) "$count / $target cards" else if (count == 1) "1 card" else "$count cards",
                style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = Palette.Muted,
            )
        }
        if (target != null) CountBar(count, target, Modifier.padding(start = Space.s3, end = Space.s3, bottom = Space.s3))
        if (commander) {
            HorizontalDivider(color = Palette.Rule)
            EditSlots(ui, vm)
        }
    }
}

/** The deck name, typed straight into the panel in the heading face. */
@Composable
private fun NameField(name: String, onName: (String) -> Unit) {
    val style = MaterialTheme.typography.titleLarge
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s3, vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (name.isEmpty()) Text("Deck name", style = style, color = Palette.Faint, maxLines = 1)
            BasicTextField(
                value = name, onValueChange = onName, singleLine = true,
                textStyle = style.copy(color = Palette.Text), cursorBrush = SolidColor(Palette.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(Icons.Outlined.Edit, null, tint = Palette.Faint, modifier = Modifier.padding(start = Space.s2).size(18.dp))
    }
}

/** The format as a small menu button: common formats first, then the rest, then None. */
@Composable
private fun FormatMenu(format: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = DeckFormats.of(format)
    Box {
        TextButton(onClick = { open = true }) {
            Text(if (current.id.isEmpty()) "No format" else current.label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            Icon(Icons.Outlined.ArrowDropDown, "Change format", tint = Palette.Icon, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val (common, rest) = DeckFormats.ALL.partition { it.common }
            (common + rest).forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.label, color = if (f.id == current.id) Palette.Text else Palette.Muted) },
                    onClick = { open = false; onPick(f.id) },
                )
            }
            HorizontalDivider(color = Palette.Rule)
            DropdownMenuItem(text = { Text("No format", color = Palette.Muted) }, onClick = { open = false; onPick("") })
        }
    }
}

/** A thin bar for the card count, full at the format's size. */
@Composable
private fun CountBar(count: Int, target: Int, modifier: Modifier = Modifier) {
    val fraction = (count.toFloat() / target).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh)) {
        Box(
            Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(Corners.Small))
                .background(if (count > target) Palette.Danger else Palette.Text),
        )
    }
}

/**
 * The commander slots inside the header panel: the commander (and a partner or signature spell once set) as rows
 * with their card, and empty optional slots as small add buttons on one line.
 */
@Composable
private fun EditSlots(ui: DeckUi, vm: DeckViewModel) {
    val c = ui.commanders
    val firstName = c.commanders.firstOrNull()
    val first = firstName?.let { ui.leadInfo[it.lowercase()] }
    val second = c.commanders.getOrNull(1)
    val oath = ui.format == "oathbreaker"
    val image = { n: String -> ui.cardInfo[DeckNames.key(n)]?.imageUrl }
    SlotLine(slotLabel(CommanderSlot.COMMANDER, ui.format, null), firstName, firstName?.let(image), { vm.openPicker(CommanderSlot.COMMANDER) }) {
        vm.clearSlot(CommanderSlot.COMMANDER)
    }
    val secondAllowed = second != null || (first != null && CommanderRules.allowsSecond(first))
    val extras = buildList {
        if (secondAllowed) add(CommanderSlot.SECOND to second)
        if (oath) add(CommanderSlot.SIGNATURE to c.signature)
        add(CommanderSlot.COMPANION to c.companion)
    }
    extras.filter { it.second != null }.forEach { (slot, name) ->
        HorizontalDivider(color = Palette.Rule)
        SlotLine(slotLabel(slot, ui.format, first), name, name?.let(image), { vm.openPicker(slot) }) { vm.clearSlot(slot) }
    }
    val empty = extras.filter { it.second == null }.map { it.first }
    if (empty.isNotEmpty()) {
        HorizontalDivider(color = Palette.Rule)
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
            empty.forEach { slot ->
                TextButton(onClick = { vm.openPicker(slot) }) {
                    Icon(Icons.Outlined.Add, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s1))
                    Text(slotLabel(slot, ui.format, first), style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                }
            }
        }
    }
}

/** One slot: its card, the slot's name over the card's, and × to clear it. Empty, it asks for a card. */
@Composable
private fun SlotLine(label: String, value: String?, imageUrl: String?, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 56.dp).padding(start = Space.s3, end = Space.s1, top = Space.s2, bottom = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value != null) CardImage(imageUrl, Modifier.width(32.dp), zoom = imageUrl != null)
        else Box(
            Modifier.width(32.dp).aspectRatio(0.716f).clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Add, null, tint = Palette.Icon, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(
                value ?: "Pick a ${label.lowercase()}", style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) Palette.AccentText else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (value != null) IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Clear $label", tint = Palette.Icon) }
    }
}

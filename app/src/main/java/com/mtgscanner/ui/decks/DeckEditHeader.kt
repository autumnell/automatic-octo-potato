package com.mtgscanner.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
 * The top of edit mode, one panel cut into cells: name and format with the card count on the first row, then
 * commander and companion (and a partner or signature spell where the format has one), two to a row.
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
        CellRow {
            NameCell(ui.name, vm::setName, Modifier.weight(1f))
            CellRule()
            FormatCell(ui.format, count, target, vm::setFormat, Modifier.weight(1f))
        }
        if (commander) {
            val c = ui.commanders
            val first = c.commanders.firstOrNull()?.let { ui.leadInfo[it.lowercase()] }
            val second = c.commanders.getOrNull(1)
            val image = { n: String -> ui.cardInfo[DeckNames.key(n)]?.imageUrl }
            val slots = buildList {
                add(CommanderSlot.COMMANDER to c.commanders.firstOrNull())
                add(CommanderSlot.COMPANION to c.companion)
                if (second != null || (first != null && CommanderRules.allowsSecond(first))) add(CommanderSlot.SECOND to second)
                if (ui.format == "oathbreaker") add(CommanderSlot.SIGNATURE to c.signature)
            }
            slots.chunked(2).forEach { pair ->
                HorizontalDivider(color = Palette.Rule)
                CellRow {
                    pair.forEachIndexed { i, (slot, name) ->
                        if (i > 0) CellRule()
                        SlotCell(
                            slotLabel(slot, ui.format, first), name, name?.let(image), Modifier.weight(1f),
                            onOpen = { vm.openPicker(slot) }, onClear = { vm.clearSlot(slot) },
                        )
                    }
                    if (pair.size == 1) { CellRule(); Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A row of cells that share its height, so the rules between them run top to bottom. */
@Composable
private fun CellRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), content = content)
}

@Composable
private fun CellRule() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Rule))
}

/** The deck name, typed straight into its cell in the heading face. */
@Composable
private fun NameCell(name: String, onName: (String) -> Unit, modifier: Modifier) {
    val style = MaterialTheme.typography.titleMedium
    Column(modifier.padding(Space.s3)) {
        Text("Deck name", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Box(Modifier.fillMaxWidth().padding(top = Space.s1)) {
            if (name.isEmpty()) Text("Untitled", style = style, color = Palette.Faint, maxLines = 1)
            BasicTextField(
                value = name, onValueChange = onName, maxLines = 2,
                textStyle = style.copy(color = Palette.Text), cursorBrush = SolidColor(Palette.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The format (tap the cell to change it) over the card count and a thin bar toward the format's size. */
@Composable
private fun FormatCell(format: String, count: Int, target: Int?, onPick: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = DeckFormats.of(format)
    Box(modifier) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().clickable { open = true }.padding(Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (current.id.isEmpty()) "No format" else current.label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Icon(Icons.Outlined.ArrowDropDown, "Change format", tint = Palette.Icon, modifier = Modifier.size(18.dp))
            }
            Text(
                if (target != null) "$count / $target" else if (count == 1) "1 card" else "$count cards",
                style = MaterialTheme.typography.titleMedium.merge(TabularNums), modifier = Modifier.padding(top = Space.s1),
            )
            if (target != null) CountBar(count, target, Modifier.padding(top = Space.s2))
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

/** One slot as a cell: its card, the slot's name over the card's, and × to clear it. Empty, it offers Add. */
@Composable
private fun SlotCell(label: String, value: String?, imageUrl: String?, modifier: Modifier, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier.fillMaxHeight().clickable(onClick = onOpen).heightIn(min = 56.dp).padding(start = Space.s3, top = Space.s2, bottom = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value != null) {
            CardImage(imageUrl, Modifier.width(28.dp), zoom = imageUrl != null)
            Spacer(Modifier.width(Space.s2))
        }
        Column(Modifier.weight(1f)) {
            // Shrinks a little at big text, where the picture and × leave "Commander" too little room.
            com.mtgscanner.ui.components.AutoSizeText(label, MaterialTheme.typography.labelMedium, color = Palette.Muted, minScale = 0.8f)
            Text(
                value ?: "Add", style = MaterialTheme.typography.bodyMedium,
                color = if (value == null) Palette.AccentText else Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (value != null) IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Clear $label", tint = Palette.Icon, modifier = Modifier.size(18.dp)) }
        else Icon(Icons.Outlined.Add, null, tint = Palette.Icon, modifier = Modifier.padding(end = Space.s3).size(18.dp))
    }
}

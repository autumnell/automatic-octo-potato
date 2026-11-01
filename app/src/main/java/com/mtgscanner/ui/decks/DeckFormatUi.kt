package com.mtgscanner.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgscanner.data.CommanderRules
import com.mtgscanner.data.CommanderSlot
import com.mtgscanner.data.DeckCommanders
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.data.PairKind
import com.mtgscanner.data.RulesCard
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.components.PlaceholderRows

/** A card offered in the commander picker. */
data class PickOption(val name: String, val typeLine: String, val imageUrl: String?, val rules: RulesCard)

fun DeckCardInfo.rules() = RulesCard(name, typeLine, oracleText, colorIdentity, legalities)
fun DeckCardInfo.option() = PickOption(name, typeLine, imageUrl, rules())
fun ScryfallCard.option() = PickOption(
    name, effectiveTypeLine, smallImageUrl ?: imageUrl,
    RulesCard(name, effectiveTypeLine, effectiveOracleText, colorIdentity.joinToString(""), legalities),
)

/** Common formats as pills, the rest under "More…". */
@Composable
fun FormatPicker(format: String, onPick: (String) -> Unit) {
    var more by remember { mutableStateOf(false) }
    val common = DeckFormats.ALL.filter { it.common }
    val current = DeckFormats.of(format)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        common.forEach { f -> SelectPill(f.label, f.id == current.id, { onPick(f.id) }) }
        if (!current.common) SelectPill(current.label, true, { more = true })
        SelectPill(DeckFormats.NONE.label, current.id.isEmpty(), { onPick("") })
        Box {
            SelectPill("More…", false, { more = true })
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DeckFormats.ALL.filterNot { it.common }.forEach { f ->
                    DropdownMenuItem(text = { Text(f.label) }, onClick = { more = false; onPick(f.id) })
                }
            }
        }
    }
}

/** Name of a slot, as the picker and editor show it. */
fun slotLabel(slot: CommanderSlot, format: String, first: RulesCard?): String = when (slot) {
    CommanderSlot.COMMANDER -> if (format == "oathbreaker") "Oathbreaker" else "Commander"
    CommanderSlot.SECOND -> when (first?.let { CommanderRules.pairKind(it) }) {
        PairKind.BACKGROUND -> "Background"
        PairKind.DOCTORS_COMPANION -> "Doctor"
        PairKind.DOCTOR -> "Doctor's companion"
        else -> "Partner"
    }
    CommanderSlot.COMPANION -> "Companion"
    CommanderSlot.SIGNATURE -> "Signature spell"
}

/** The commander slots a format has: commander, a partner when the first allows one, companion, signature spell. */
@Composable
fun CommanderSlots(
    format: String,
    commanders: DeckCommanders,
    leadInfo: Map<String, RulesCard>,
    onOpen: (CommanderSlot) -> Unit,
    onClear: (CommanderSlot) -> Unit,
) {
    val firstName = commanders.commanders.firstOrNull()
    val first = firstName?.let { leadInfo[it.lowercase()] }
    val second = commanders.commanders.getOrNull(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SlotRow(slotLabel(CommanderSlot.COMMANDER, format, null), firstName, { onOpen(CommanderSlot.COMMANDER) }, { onClear(CommanderSlot.COMMANDER) })
        if (second != null || (first != null && CommanderRules.allowsSecond(first))) {
            SlotRow(slotLabel(CommanderSlot.SECOND, format, first), second, { onOpen(CommanderSlot.SECOND) }, { onClear(CommanderSlot.SECOND) })
        }
        if (format == "oathbreaker") {
            SlotRow(slotLabel(CommanderSlot.SIGNATURE, format, null), commanders.signature, { onOpen(CommanderSlot.SIGNATURE) }, { onClear(CommanderSlot.SIGNATURE) })
        }
        SlotRow(slotLabel(CommanderSlot.COMPANION, format, null), commanders.companion, { onOpen(CommanderSlot.COMPANION) }, { onClear(CommanderSlot.COMPANION) })
    }
}

@Composable
private fun SlotRow(label: String, value: String?, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh)
            .clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(
                value ?: "Set", style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) Palette.AccentText else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (value != null) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Clear", tint = Palette.Icon) }
        } else {
            Icon(Icons.Outlined.Add, null, tint = Palette.Icon, modifier = Modifier.padding(12.dp).size(20.dp))
        }
    }
}

/** Picks a card for a commander slot: the deck's own cards that fit, or a Scryfall search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommanderPickerSheet(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    loading: Boolean,
    options: List<PickOption>,
    onPick: (PickOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text("Search Scryfall") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Palette.Icon) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Clear") } },
                shape = RoundedCornerShape(Corners.Medium),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(if (query.isBlank()) "From this deck" else "Search results", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        }
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            when {
                loading -> PlaceholderRows(6, Modifier.padding(top = 8.dp), rowHeight = 48.dp, color = Palette.SurfaceHigh)
                options.isEmpty() -> Text(
                    if (query.isBlank()) "None in this deck" else "No matches",
                    color = Palette.Muted, modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(options, key = { it.name }) { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(o) }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardImage(o.imageUrl, Modifier.width(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(o.typeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

/**
 * Under the deck's name: commander art, the format and commanders, and what breaks the format's rules.
 * [onEdhrec] opens the commander's EDHREC page; null hides the button.
 */
@Composable
fun DeckHeader(
    format: String, commanders: DeckCommanders, art: List<String>, issues: CommanderRules.Issues, onEdhrec: (() -> Unit)? = null,
    /** The deck's salt (Commander decks), from EDHREC's scores. */
    salt: com.mtgscanner.data.DeckSalt? = null,
) {
    val f = DeckFormats.of(format)
    if (f.id.isEmpty() && commanders.isEmpty) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (f.commander) art.take(2).forEach { url ->
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 64.dp, height = 48.dp).clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            val who = if (f.commander) commanders.commanders.joinToString(" & ") else ""
            Text(
                listOf(f.label.takeIf { f.id.isNotEmpty() }, who.ifEmpty { null }).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            DeckIssuesLine(issues)
            if (f.commander && salt != null) {
                Text(salt.label, style = MaterialTheme.typography.bodySmall.merge(com.mtgscanner.ui.theme.TabularNums), color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onEdhrec != null && f.commander) {
            TextButton(onClick = onEdhrec) {
                Text("EDHREC")
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Confirms "Add to collection": which cards. They're kept in the deck, not in a box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeckSheet(
    counts: Pair<Int, Int>?,
    busy: Boolean,
    onAdd: (onlyMissing: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var onlyMissing by remember { mutableStateOf(true) }
    val n = counts?.let { if (onlyMissing) it.second else it.first }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Add to collection", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text("Cards", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Only missing" + (counts?.let { " (${it.second})" } ?: ""), onlyMissing, { onlyMissing = true })
                SelectPill("All" + (counts?.let { " (${it.first})" } ?: ""), !onlyMissing, { onlyMissing = false })
            }
            Spacer(Modifier.height(10.dp))
            Text("Kept in this deck", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.height(20.dp))
            if (busy) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
            } else {
                GradientButton(
                    text = when (n) { null -> "Add"; 1 -> "Add 1 card"; else -> "Add $n cards" },
                    icon = Icons.Outlined.Add,
                    onClick = { onAdd(onlyMissing) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = n != null && n > 0,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

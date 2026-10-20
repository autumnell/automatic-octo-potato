package com.mtgscanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BOX_PRESETS
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.theme.Palette

/**
 * Sheet for creating or editing a box: its name, what kind it is, and rules for what belongs
 * in it (colors, rarity, borderless and other printings, foil, card type, price, set).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BoxEditorSheet(
    title: String,
    initialName: String = "",
    initialKind: String = "box",
    initialRules: BoxRules = BoxRules(),
    showPresets: Boolean = initialName.isEmpty(),
    onSave: (name: String, kind: String, rules: BoxRules) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }
    var rules by remember { mutableStateOf(initialRules) }
    var minText by remember { mutableStateOf(initialRules.minPrice?.let(::plain) ?: "") }
    var maxText by remember { mutableStateOf(initialRules.maxPrice?.let(::plain) ?: "") }
    var setsText by remember { mutableStateOf(initialRules.sets.joinToString(", ")) }

    fun current() = rules.copy(
        minPrice = minText.replace(',', '.').toDoubleOrNull(),
        maxPrice = maxText.replace(',', '.').toDoubleOrNull(),
        sets = setsText.split(',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet(),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }

            if (showPresets) {
                Label("Start from")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BOX_PRESETS.forEach { p ->
                        SelectPill(p.name, selected = name == p.name && rules == p.rules, onClick = {
                            name = p.name; kind = p.kind; rules = p.rules
                            minText = p.rules.minPrice?.let(::plain) ?: ""
                            maxText = p.rules.maxPrice?.let(::plain) ?: ""
                            setsText = ""
                        }, leading = { Icon(boxIcon(p.kind), null, Modifier.width(16.dp), tint = Palette.Muted) })
                    }
                }
            }

            Label("Name")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Red rares, Trade binder") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
            )

            Label("Kind")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardBox.KINDS.forEach { k ->
                    SelectPill(kindLabel(k), kind == k, { kind = k }, leading = { Icon(boxIcon(k), null, Modifier.width(16.dp)) })
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("What goes in it", style = MaterialTheme.typography.titleMedium)
            Text(
                "When scanning with Auto-sort, each card goes into the first box whose rules fit. Leave everything off for a box that takes any card.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )

            Label("Colors")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("W", "U", "B", "R", "G", "M", "C").forEach { c ->
                    SelectPill(BoxRules.COLOR_NAMES.getValue(c), c in rules.colors, { rules = rules.copy(colors = rules.colors.toggle(c)) },
                        leading = if (c in "WUBRG") ({ ColorPip(c[0]) }) else null)
                }
            }

            Label("Rarity")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("common", "uncommon", "rare", "mythic").forEach { r ->
                    SelectPill(r.replaceFirstChar(Char::uppercase), r in rules.rarities, { rules = rules.copy(rarities = rules.rarities.toggle(r)) },
                        leading = { RarityDot(r, 8.dp) })
                }
            }

            Label("Printing")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxRules.TREATMENT_NAMES.forEach { (k, label) ->
                    SelectPill(label, k in rules.treatments, { rules = rules.copy(treatments = rules.treatments.toggle(k)) })
                }
            }

            Label("Finish")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("any" to "Any", "foil" to "Foil only", "nonfoil" to "Non-foil only").forEach { (k, label) ->
                    SelectPill(label, rules.finish == k, { rules = rules.copy(finish = k) })
                }
            }

            Label("Card type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxRules.TYPES.forEach { t ->
                    SelectPill(t, t in rules.types, { rules = rules.copy(types = rules.types.toggle(t)) })
                }
            }

            Label("Price per card (USD)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minText, onValueChange = { minText = it }, label = { Text("At least") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = maxText, onValueChange = { maxText = it }, label = { Text("Under") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            Label("Sets")
            OutlinedTextField(
                value = setsText, onValueChange = { setsText = it },
                placeholder = { Text("Set codes, e.g. MH3, BLB") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
            )

            Spacer(Modifier.height(14.dp))
            Text(current().describe(), style = MaterialTheme.typography.bodyMedium, color = Palette.Accent)
            Spacer(Modifier.height(14.dp))
            GradientButton(
                if (initialName.isEmpty()) "Create box" else "Save",
                { onSave(name.trim(), kind, current()) },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

fun kindLabel(kind: String) = when (kind) {
    "binder" -> "Binder"
    "deck" -> "Deck"
    "trade" -> "Trade binder"
    else -> "Box"
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Outline,
    cursorColor = Palette.Accent,
)

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

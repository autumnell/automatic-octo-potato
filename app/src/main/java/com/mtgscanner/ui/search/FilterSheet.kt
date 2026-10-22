package com.mtgscanner.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.collection.CardColors
import com.mtgscanner.ui.collection.ColorMatch
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** Scryfall filters: printings, legality, types, stats, language and sort. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: SearchFilters,
    onChange: (SearchFilters) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val f = filters
    var moreFormats by remember { mutableStateOf(SearchFilters.MORE_FORMATS.any { it.second == f.format }) }
    var otherType by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(SearchFilters()) }) { Text("Reset", color = Palette.Muted) }
            }

            ToggleRow("Group printings", f.groupPrintings) { onChange(f.copy(groupPrintings = it)) }
            ToggleRow("Tokens & art cards", f.tokens) { onChange(f.copy(tokens = it)) }

            Heading("Format")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = SearchFilters.MAIN_FORMATS + if (moreFormats) SearchFilters.MORE_FORMATS else emptyList()
                list.forEach { (label, key) ->
                    SelectPill(label, f.format == key, {
                        onChange(if (f.format == key) f.copy(format = null) else f.copy(format = key))
                    })
                }
                SelectPill(if (moreFormats) "Less" else "More…", false, { moreFormats = !moreFormats })
            }

            Heading("Color")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardColors.PIPS.forEach { c ->
                    val on = c in f.colors
                    SelectPill(COLOR_NAMES[c] ?: c, on, { onChange(f.copy(colors = if (on) f.colors - c else f.colors + c)) }, leading = { ColorPip(c[0]) })
                }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorMatch.entries.forEach { m -> SelectPill(m.label, f.colorMatch == m, { onChange(f.copy(colorMatch = m)) }) }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Colors", !f.byIdentity, { onChange(f.copy(byIdentity = false)) })
                SelectPill("Identity", f.byIdentity, { onChange(f.copy(byIdentity = true)) })
            }

            Heading("Type")
            Text("Tap: is · again: not", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            TypeGroup("Supertype", SearchFilters.SUPERTYPES, f, onChange)
            TypeGroup("Card type", SearchFilters.CARD_TYPES, f, onChange)
            val known = (SearchFilters.SUPERTYPES + SearchFilters.CARD_TYPES + SearchFilters.SUBTYPES).map { it.lowercase() }.toSet()
            TypeGroup("Subtype", SearchFilters.SUBTYPES + f.types.map { it.word }.filter { it.lowercase() !in known }, f, onChange)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallField(otherType, { otherType = it }, "Other type", Modifier.weight(1f), onDone = {
                    if (otherType.isNotBlank()) { onChange(f.copy(types = f.types + TypeTerm(otherType.trim()))); otherType = "" }
                })
                Spacer(Modifier.width(8.dp))
                SelectPill("Add", false, {
                    if (otherType.isNotBlank()) { onChange(f.copy(types = f.types + TypeTerm(otherType.trim()))); otherType = "" }
                }, leading = { Icon(Icons.Filled.Add, null, Modifier.size(14.dp), tint = Palette.Icon) })
            }

            Heading("Stats")
            SearchFilters.STATS.forEach { stat ->
                val t = f.stat(stat)
                Text(SearchFilters.statLabel(stat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SearchFilters.OPS.forEach { op ->
                            SelectPill(op.replace("<=", "≤").replace(">=", "≥"), t.op == op, { onChange(f.withStat(t.copy(op = op))) })
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // Text keyboard so "-" and "*" can be typed.
                    SmallField(
                        t.value,
                        { v -> onChange(f.withStat(t.copy(value = SearchFilters.cleanStatValue(v)))) },
                        "# or *",
                        Modifier.width(72.dp),
                    )
                }
            }

            Heading("Language & sort")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown(
                    "Lang: " + (SearchFilters.LANGUAGES.firstOrNull { it.second == f.lang }?.first ?: f.lang),
                    SearchFilters.LANGUAGES,
                ) { onChange(f.copy(lang = it)) }
                Dropdown(
                    "Sort: " + (SearchFilters.ORDERS.firstOrNull { it.second == f.order }?.first ?: f.order),
                    SearchFilters.ORDERS,
                ) { onChange(f.copy(order = it)) }
            }

            Spacer(Modifier.size(16.dp))
            GradientButton("Search", onApply, Modifier.fillMaxWidth())
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.AccentText, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Palette.Text else Palette.Muted, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeGroup(title: String, words: List<String>, f: SearchFilters, onChange: (SearchFilters) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        words.forEach { w ->
            val term = f.types.firstOrNull { it.word.equals(w, ignoreCase = true) }
            TypeChip(w, term) { onChange(f.cycleType(w)) }
        }
    }
}

/** A type chip with three states: off, IS (accent) and NOT (red). */
@Composable
private fun TypeChip(word: String, term: TypeTerm?, onClick: () -> Unit) {
    val color = when {
        term == null -> Color.Transparent
        term.not -> Palette.Danger
        else -> Palette.Accent
    }
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(Corners.Small))
            .background(if (term == null) Palette.SurfaceHigh else color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(Corners.Small))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (term != null) {
            Text(if (term.not) "NOT" else "IS", style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.width(6.dp))
        }
        Text(word, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
    }
}

@Composable
private fun SmallField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    onDone: () -> Unit = {},
) {
    Box(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
            cursorBrush = SolidColor(Palette.Accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Dropdown(label: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SelectPill(label, false, { open = true }, leading = null)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, key) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(key) })
            }
        }
    }
}

private val COLOR_NAMES = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless")

package com.mtgscanner.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.BOX_PRESETS
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.Divider
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.data.db.facts
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette

/** What the box editor produces. */
data class BoxDraft(
    val name: String = "",
    val kind: String = "box",
    val color: Int = 0,
    val rules: BoxRules = BoxRules(),
    /** Most cards it holds; 0 = no limit. */
    val capacity: Int = 0,
    /** A [Divider] key; empty = no dividers. */
    val divider: String = "",
)

/** A box's label color; 0 means the accent color. */
fun boxColor(color: Int): Color = if (color == 0) Palette.Accent else Color(color)

/**
 * Step-by-step sheet for making or editing a box:
 * 1. start from a preset or blank, 2. name, kind and color, 3. rules, each in its own card.
 * A live preview shows which of your cards fit, and warns when another box takes them first.
 * When editing, it opens on step 3 and step 1 is skipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxEditorSheet(
    title: String,
    initial: BoxDraft? = null,
    /** The box being edited, so the preview knows its place in the Auto-sort order. */
    editingId: Long? = null,
    onSave: (BoxDraft) -> Unit,
    onDismiss: () -> Unit,
    /** Shows the dividers and capacity fields; the caller must save [BoxDraft.capacity] and [BoxDraft.divider]. */
    layout: Boolean = false,
) {
    val isNew = initial == null
    var draft by remember { mutableStateOf(initial ?: BoxDraft()) }
    var step by remember { mutableIntStateOf(if (isNew) 1 else 3) }
    var minText by remember { mutableStateOf(draft.rules.minPrice?.let(::plain) ?: "") }
    var maxText by remember { mutableStateOf(draft.rules.maxPrice?.let(::plain) ?: "") }
    var setsText by remember { mutableStateOf(draft.rules.sets.joinToString(", ")) }
    var capText by remember { mutableStateOf(draft.capacity.takeIf { it > 0 }?.toString() ?: "") }

    fun rules() = draft.rules.copy(
        minPrice = minText.replace(',', '.').toDoubleOrNull(),
        maxPrice = maxText.replace(',', '.').toDoubleOrNull(),
        sets = setsText.split(',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet(),
    )
    fun startFrom(d: BoxDraft) {
        draft = d
        minText = d.rules.minPrice?.let(::plain) ?: ""
        maxText = d.rules.maxPrice?.let(::plain) ?: ""
        setsText = d.rules.sets.joinToString(", ")
        step = 2
    }

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
            StepBar(step, showStart = isNew, canJump = draft.name.isNotBlank() || !isNew) { step = it }
            Spacer(Modifier.height(8.dp))

            when (step) {
                1 -> StartStep(onPick = ::startFrom)
                2 -> DetailsStep(draft, onChange = { draft = it })
                else -> RulesStep(
                    rules = rules(),
                    onRules = { draft = draft.copy(rules = it) },
                    minText = minText, maxText = maxText, setsText = setsText,
                    onMin = { minText = it }, onMax = { maxText = it }, onSets = { setsText = it },
                )
            }

            if (step == 3 && layout) {
                LayoutCards(
                    divider = Divider.of(draft.divider),
                    onDivider = { draft = draft.copy(divider = it.key) },
                    capText = capText,
                    onCap = { v -> capText = v.filter { it.isDigit() }.take(5) },
                )
            }

            if (step == 3) {
                Spacer(Modifier.height(14.dp))
                LivePreview(rules(), editingId)
            }

            Spacer(Modifier.height(16.dp))
            when (step) {
                1 -> Unit
                2 -> GradientButton("Next: what goes in it", { step = 3 }, Modifier.fillMaxWidth(), enabled = draft.name.isNotBlank())
                else -> GradientButton(
                    if (isNew) "Create box" else "Save",
                    { onSave(draft.copy(name = draft.name.trim(), rules = rules(), capacity = capText.toIntOrNull() ?: 0)) },
                    Modifier.fillMaxWidth(),
                    enabled = draft.name.isNotBlank(),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StepBar(step: Int, showStart: Boolean, canJump: Boolean, onStep: (Int) -> Unit) {
    val steps = buildList {
        if (showStart) add(1 to "Start")
        add(2 to "Name")
        add(3 to "Rules")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (n, label) ->
            val done = n < step
            val reachable = n <= step || canJump
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(enabled = reachable) { onStep(n) }.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (n == step) Palette.Accent else if (done) Palette.Accent.copy(alpha = 0.25f) else Palette.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Filled.Check, null, tint = Palette.Accent, modifier = Modifier.size(14.dp))
                    else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (n == step) Color.Black else Palette.Muted)
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (n == step) Palette.Text else Palette.Muted)
            }
        }
    }
}

/** Step 1: a ready-made box, or a blank one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartStep(onPick: (BoxDraft) -> Unit) {
    Text("Pick a starting point.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    Spacer(Modifier.height(12.dp))
    Surface(
        onClick = { onPick(BoxDraft()) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Palette.SurfaceHigh,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckBoxOutlineBlank, null, tint = Palette.Accent)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Blank box", style = MaterialTheme.typography.titleSmall)
                Text("Name it and set your own rules", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BOX_PRESETS.forEach { p ->
            Surface(
                onClick = { onPick(BoxDraft(p.name, p.kind, 0, p.rules)) },
                shape = RoundedCornerShape(14.dp),
                color = Palette.SurfaceHigh,
            ) {
                Column(Modifier.width(150.dp).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(boxIcon(p.kind), null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    Text(p.rules.describe(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
                }
            }
        }
    }
}

/** Step 2: name, kind (which sets the icon) and label color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(draft: BoxDraft, onChange: (BoxDraft) -> Unit) {
    Label("Name")
    OutlinedTextField(
        value = draft.name,
        onValueChange = { onChange(draft.copy(name = it)) },
        placeholder = { Text("e.g. Red rares, Trade binder") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
    )

    Label("Kind")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardBox.KINDS.forEach { k ->
            SelectPill(kindLabel(k), draft.kind == k, { onChange(draft.copy(kind = k)) }, leading = { Icon(boxIcon(k), null, Modifier.size(16.dp)) })
        }
    }

    Label("Color")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardBox.COLORS.forEach { c ->
            val selected = draft.color == c
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(boxColor(c))
                    .border(if (selected) 3.dp else 0.dp, if (selected) Palette.Text else Color.Transparent, CircleShape)
                    .clickable { onChange(draft.copy(color = c)) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, "Selected", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Surface(shape = RoundedCornerShape(16.dp), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(boxIcon(draft.kind), null, tint = boxColor(draft.color), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(draft.name.ifBlank { "Your box" }, style = MaterialTheme.typography.titleLarge, color = boxColor(draft.color))
        }
    }
}

/** Step 3: one collapsible card per rule, each with a one-line summary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesStep(
    rules: BoxRules,
    onRules: (BoxRules) -> Unit,
    minText: String,
    maxText: String,
    setsText: String,
    onMin: (String) -> Unit,
    onMax: (String) -> Unit,
    onSets: (String) -> Unit,
) {
    var open by remember { mutableStateOf<String?>(null) }
    fun toggle(key: String) { open = if (open == key) null else key }

    Text(
        "Cards must match every rule. No rules = any card.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.Muted,
    )
    Spacer(Modifier.height(8.dp))

    RuleCard("Colors", rules.colors.takeIf { it.isNotEmpty() }?.joinToString(", ") { BoxRules.COLOR_NAMES[it] ?: it }, open == "colors", { toggle("colors") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("W", "U", "B", "R", "G", "M", "C").forEach { c ->
                SelectPill(BoxRules.COLOR_NAMES.getValue(c), c in rules.colors, { onRules(rules.copy(colors = rules.colors.toggle(c))) },
                    leading = if (c in "WUBRG") ({ ColorPip(c[0]) }) else null)
            }
        }
    }
    RuleCard("Rarity", rules.rarities.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }, open == "rarity", { toggle("rarity") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("common", "uncommon", "rare", "mythic").forEach { r ->
                SelectPill(r.replaceFirstChar(Char::uppercase), r in rules.rarities, { onRules(rules.copy(rarities = rules.rarities.toggle(r))) },
                    leading = { RarityDot(r, 8.dp) })
            }
        }
    }
    RuleCard("Printing", rules.treatments.takeIf { it.isNotEmpty() }?.joinToString(", ") { BoxRules.TREATMENT_NAMES[it] ?: it }, open == "printing", { toggle("printing") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxRules.TREATMENT_NAMES.forEach { (k, label) ->
                SelectPill(label, k in rules.treatments, { onRules(rules.copy(treatments = rules.treatments.toggle(k))) })
            }
        }
    }
    val finishSummary = when (rules.finish) { "foil" -> "Foil only"; "nonfoil" -> "Non-foil only"; else -> null }
    RuleCard("Finish", finishSummary, open == "finish", { toggle("finish") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("any" to "Any", "foil" to "Foil only", "nonfoil" to "Non-foil only").forEach { (k, label) ->
                SelectPill(label, rules.finish == k, { onRules(rules.copy(finish = k)) })
            }
        }
    }
    RuleCard("Card type", rules.types.takeIf { it.isNotEmpty() }?.joinToString(", "), open == "type", { toggle("type") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxRules.TYPES.forEach { t -> SelectPill(t, t in rules.types, { onRules(rules.copy(types = rules.types.toggle(t))) }) }
        }
    }
    if (AppSettings.showPrices || minText.isNotEmpty() || maxText.isNotEmpty()) {
        val priceSummary = BoxRules(minPrice = rules.minPrice, maxPrice = rules.maxPrice).takeIf { !it.isCatchAll }?.describe()
        RuleCard("Price per card", priceSummary, open == "price", { toggle("price") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minText, onValueChange = onMin, label = { Text("At least $") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = maxText, onValueChange = onMax, label = { Text("Under $") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
    RuleCard("Sets", rules.sets.takeIf { it.isNotEmpty() }?.joinToString(", "), open == "sets", { toggle("sets") }) {
        OutlinedTextField(
            value = setsText, onValueChange = onSets,
            placeholder = { Text("Set codes, e.g. MH3, BLB") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
        )
    }
}

/** How the box is laid out inside: dividers and how many cards fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutCards(divider: Divider, onDivider: (Divider) -> Unit, capText: String, onCap: (String) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    Label("Inside the box")
    RuleCard("Dividers", divider.takeIf { it != Divider.NONE }?.label, open == "div", { open = if (open == "div") null else "div" }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Divider.entries.forEach { d -> SelectPill(d.label, d == divider, { onDivider(d) }) }
        }
    }
    RuleCard("Capacity", capText.toIntOrNull()?.takeIf { it > 0 }?.let { "$it cards" }, open == "cap", { open = if (open == "cap") null else "cap" }) {
        OutlinedTextField(
            value = capText, onValueChange = onCap,
            placeholder = { Text("No limit") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun RuleCard(title: String, summary: String?, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Palette.SurfaceHigh,
        border = if (summary != null) androidx.compose.foundation.BorderStroke(1.dp, Palette.Accent.copy(alpha = 0.5f)) else null,
    ) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(summary ?: "Any", style = MaterialTheme.typography.bodySmall, color = if (summary != null) Palette.AccentText else Palette.Muted, maxLines = 1)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Collapse" else "Expand", tint = Palette.Muted)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
            }
        }
    }
}

/** "N of your cards fit", with thumbnails, and a warning when boxes earlier in the Auto-sort order take some first. */
@Composable
private fun LivePreview(rules: BoxRules, editingId: Long?) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val cards by remember { repo.observeAll() }.collectAsState(initial = null)
    val boxes by remember { repo.observeBoxes() }.collectAsState(initial = emptyList())
    val all = cards ?: return
    val facts = remember(all) { all.map { it.facts() } }
    val result = remember(rules, facts, boxes, editingId) {
        val position = boxes.indexOfFirst { it.id == editingId }.let { if (it < 0) boxes.size else it }
        val ahead = (if (rules.isCatchAll) boxes else boxes.take(position)).filter { it.id != editingId }
        BoxRules.overlap(rules, ahead.map { it to it.boxRules() }, facts)
    }
    val fitCount = result.fits.sumOf { all[it].quantity }
    val takenCount = result.takenBy.sumOf { (_, idx) -> idx.sumOf { all[it].quantity } }

    Surface(shape = RoundedCornerShape(16.dp), color = Palette.Background, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Preview", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Text(rules.describe(), style = MaterialTheme.typography.bodyMedium, color = Palette.AccentText)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    all.isEmpty() -> "Your collection is empty, so nothing to preview yet."
                    fitCount == 0 -> "None of your cards fit these rules yet."
                    rules.isCatchAll -> "Takes any card. ${fitCount - takenCount} of your cards don't fit another box."
                    else -> "$fitCount of your cards fit this box."
                },
                style = MaterialTheme.typography.titleSmall,
            )
            val thumbs = result.fits.map { all[it] }.distinctBy { it.scryfallId }.take(14)
            if (thumbs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(thumbs, key = { it.id }) { c -> CardImage(c.smallImageUrl, Modifier.height(64.dp), corner = 4.dp) }
                }
            }
            if (result.takenBy.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val names = result.takenBy.take(3).joinToString(", ") { (b, idx) -> "${b.name} (${idx.sumOf { all[it].quantity }})" }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, null, tint = Palette.Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$takenCount of these go to $names first, because ${if (result.takenBy.size == 1) "that box is" else "those boxes are"} checked " +
                            "earlier in Auto-sort. You can change the order under Collection › Auto-sort order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Gold,
                    )
                }
            }
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

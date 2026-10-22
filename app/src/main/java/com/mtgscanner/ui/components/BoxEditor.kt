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
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.BOX_PRESETS
import com.mtgscanner.data.BOX_TEMPLATES
import com.mtgscanner.data.BoxTemplate
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.Divider
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.data.db.facts
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

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
    /** When set, step 1 also offers whole systems of boxes made in one tap. */
    onTemplate: ((BoxTemplate) -> Unit)? = null,
    /** When set, "Try it first" shows where every card would go before anything is saved. */
    onTry: ((BoxDraft) -> Unit)? = null,
) {
    val isNew = initial == null
    var draft by remember { mutableStateOf(initial ?: BoxDraft()) }
    var step by remember { mutableIntStateOf(if (isNew) 1 else 3) }
    var minText by remember { mutableStateOf(draft.rules.minPrice?.let(::plain) ?: "") }
    var maxText by remember { mutableStateOf(draft.rules.maxPrice?.let(::plain) ?: "") }
    var setsText by remember { mutableStateOf(draft.rules.sets.joinToString(", ")) }
    var capText by remember { mutableStateOf(draft.capacity.takeIf { it > 0 }?.toString() ?: "") }
    var nums by remember { mutableStateOf(numTexts(draft.rules)) }

    fun rules() = draft.rules.copy(
        minPrice = minText.replace(',', '.').toDoubleOrNull(),
        maxPrice = maxText.replace(',', '.').toDoubleOrNull(),
        sets = setsText.split(',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet(),
        minCmc = nums["minCmc"]?.toIntOrNull(),
        maxCmc = nums["maxCmc"]?.toIntOrNull(),
        minPower = nums["minPower"]?.toIntOrNull(),
        maxPower = nums["maxPower"]?.toIntOrNull(),
        minToughness = nums["minToughness"]?.toIntOrNull(),
        maxToughness = nums["maxToughness"]?.toIntOrNull(),
    )
    fun startFrom(d: BoxDraft) {
        draft = d
        minText = d.rules.minPrice?.let(::plain) ?: ""
        maxText = d.rules.maxPrice?.let(::plain) ?: ""
        setsText = d.rules.sets.joinToString(", ")
        nums = numTexts(d.rules)
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
                1 -> StartStep(onPick = ::startFrom, onTemplate = onTemplate)
                2 -> DetailsStep(draft, onChange = { draft = it })
                else -> RulesStep(
                    rules = rules(),
                    onRules = { draft = draft.copy(rules = it) },
                    minText = minText, maxText = maxText, setsText = setsText,
                    onMin = { minText = it }, onMax = { maxText = it }, onSets = { setsText = it },
                    nums = nums, onNum = { k, v -> nums = nums + (k to v) },
                )
            }

            if (step == 3 && layout) {
                LayoutCards(
                    divider = Divider.of(draft.divider),
                    // Keeps a second level and number order set from the box menu's Dividers.
                    onDivider = { draft = draft.copy(divider = if (it == Divider.NONE) "" else com.mtgscanner.data.BoxLayout.of(draft.divider).copy(first = it).key) },
                    capText = capText,
                    onCap = { v -> capText = v.filter { it.isDigit() }.take(5) },
                )
            }

            if (step == 3) {
                Spacer(Modifier.height(14.dp))
                LivePreview(rules(), editingId)
            }

            if (step == 3) {
                // A range whose low end is above its high end lets no card in.
                rangeWarnings(rules()).forEach { w ->
                    Text(w, style = MaterialTheme.typography.bodySmall, color = Palette.GoldText, modifier = Modifier.padding(top = 8.dp))
                }
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
            if (step == 3 && onTry != null) {
                TextButton(
                    onClick = { onTry(draft.copy(name = draft.name.trim(), rules = rules(), capacity = capText.toIntOrNull() ?: 0)) },
                    enabled = draft.name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Try it first") }
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
                Modifier.clip(RoundedCornerShape(Corners.Small)).clickable(enabled = reachable) { onStep(n) }.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (n == step) Palette.Accent else if (done) Palette.Accent.copy(alpha = 0.25f) else Palette.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Filled.Check, null, tint = Palette.AccentText, modifier = Modifier.size(14.dp))
                    else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (n == step) Palette.OnAccent else Palette.Muted)
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
private fun StartStep(onPick: (BoxDraft) -> Unit, onTemplate: ((BoxTemplate) -> Unit)? = null) {
    if (onTemplate != null) {
        Text("Whole system", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BOX_TEMPLATES.forEach { t ->
                Surface(onClick = { onTemplate(t) }, shape = RoundedCornerShape(Corners.Medium), color = Palette.SurfaceHigh) {
                    Column(Modifier.width(150.dp).padding(12.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, color = Palette.AccentText, maxLines = 1)
                        Text(t.blurb, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("One box", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.height(8.dp))
    } else {
        Text("Pick a starting point.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Spacer(Modifier.height(12.dp))
    }
    Surface(
        onClick = { onPick(BoxDraft()) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Corners.Medium),
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
                onClick = { onPick(BoxDraft(p.name, p.kind, 0, p.rules, divider = p.divider)) },
                shape = RoundedCornerShape(Corners.Medium),
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
        shape = RoundedCornerShape(Corners.Medium),
        colors = fieldColors(),
    )

    Label("Kind")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardBox.KINDS.forEach { k ->
            SelectPill(kindLabel(k), draft.kind == k, { onChange(draft.copy(kind = k)) }, leading = { Icon(boxIcon(k), null, Modifier.size(16.dp)) })
        }
    }

    Label("Color")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardBox.COLORS.forEachIndexed { i, c ->
            val selected = draft.color == c
            val fill = boxColor(c)
            // A 48dp tap target around the 34dp swatch.
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, onClick = { onChange(draft.copy(color = c)) })
                    .semantics { contentDescription = if (c == 0) "Accent color" else "Color ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(if (selected) 3.dp else 0.dp, if (selected) Palette.Text else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, null, tint = if (fill.luminance() > 0.45f) Color(0xFF141414) else Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
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
    nums: Map<String, String>,
    onNum: (String, String) -> Unit,
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
                    leading = { ColorPip(c[0]) })
            }
        }
    }
    RuleCard("Rarity", rules.rarities.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }, open == "rarity", { toggle("rarity") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("common", "uncommon", "rare", "mythic").forEach { r ->
                SelectPill(r.replaceFirstChar(Char::uppercase), r in rules.rarities, { onRules(rules.copy(rarities = rules.rarities.toggle(r))) },
                    leading = { RarityMark(r) })
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
            BoxRules.TYPES.forEach { t -> SelectPill(t, t in rules.types, { onRules(rules.copy(types = rules.types.toggle(t))) }, leading = { Mana.Icon(t.lowercase(), Palette.Text, 16.dp) }) }
        }
    }
    if (AppSettings.showPrices || minText.isNotEmpty() || maxText.isNotEmpty()) {
        val priceSummary = BoxRules(minPrice = rules.minPrice, maxPrice = rules.maxPrice).takeIf { !it.isCatchAll }?.describe()
        RuleCard("Price per card", priceSummary, open == "price", { toggle("price") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minText, onValueChange = onMin, label = { Text("At least $") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = maxText, onValueChange = onMax, label = { Text("Under $") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
    RuleCard("Sets", rules.sets.takeIf { it.isNotEmpty() }?.joinToString(", "), open == "sets", { toggle("sets") }) {
        OutlinedTextField(
            value = setsText, onValueChange = onSets,
            placeholder = { Text("Set codes, e.g. MH3, BLB") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(),
        )
    }
    val idSummary = BoxRules(identity = rules.identity, identityMode = rules.identityMode).takeIf { rules.identity.isNotEmpty() }?.describe()
    RuleCard("Color identity", idSummary, open == "identity", { toggle("identity") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("W", "U", "B", "R", "G", "C").forEach { c ->
                SelectPill(BoxRules.COLOR_NAMES.getValue(c), c in rules.identity, { onRules(rules.copy(identity = rules.identity.toggle(c))) },
                    leading = { ColorPip(c[0]) })
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("exact" to "Exactly", "within" to "Fits in deck").forEach { (k, label) ->
                SelectPill(label, rules.identityMode == k, { onRules(rules.copy(identityMode = k)) })
            }
        }
    }
    StatRule("Mana value", "Cmc", rules.minCmc, rules.maxCmc, nums, onNum, open == "cmc", { toggle("cmc") })
    StatRule("Power", "Power", rules.minPower, rules.maxPower, nums, onNum, open == "power", { toggle("power") })
    StatRule("Toughness", "Toughness", rules.minToughness, rules.maxToughness, nums, onNum, open == "tough", { toggle("tough") })
    RuleCard("Format", rules.formats.takeIf { it.isNotEmpty() }?.joinToString(", ") { BoxRules.FORMAT_NAMES[it] ?: it }, open == "format", { toggle("format") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxRules.FORMAT_NAMES.forEach { (k, label) ->
                SelectPill(label, k in rules.formats, { onRules(rules.copy(formats = rules.formats.toggle(k))) })
            }
        }
    }
    RuleCard("Reserved List", if (rules.reserved) "Only" else null, open == "reserved", { toggle("reserved") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectPill("Any", !rules.reserved, { onRules(rules.copy(reserved = false)) })
            SelectPill("Reserved only", rules.reserved, { onRules(rules.copy(reserved = true)) })
        }
    }
    RuleCard("Tokens & art cards", if (rules.tokens) "Only" else null, open == "tokens", { toggle("tokens") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectPill("Any", !rules.tokens, { onRules(rules.copy(tokens = false)) })
            SelectPill("Tokens & art only", rules.tokens, { onRules(rules.copy(tokens = true)) })
        }
    }
}

/** A from–to number rule, like mana value 0 to 2. [key] names the text fields ("minCmc", "maxCmc"). */
@Composable
private fun StatRule(
    title: String, key: String, min: Int?, max: Int?,
    nums: Map<String, String>, onNum: (String, String) -> Unit,
    expanded: Boolean, onToggle: () -> Unit,
) {
    val summary = when {
        min != null && max != null -> if (min == max) "$min" else "$min–$max"
        min != null -> "$min+"
        max != null -> "≤$max"
        else -> null
    }
    RuleCard(title, summary, expanded, onToggle) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("min$key" to "From", "max$key" to "To").forEach { (k, label) ->
                OutlinedTextField(
                    value = nums[k].orEmpty(), onValueChange = { v -> onNum(k, v.filter { it.isDigit() }.take(2)) },
                    label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

/** Short warnings for ranges no card can fit, like price at least $5 and under $2. */
fun rangeWarnings(r: BoxRules): List<String> = buildList {
    // Price: at least is inclusive and under is exclusive, so equal values match nothing too.
    if (r.minPrice != null && r.maxPrice != null && r.minPrice >= r.maxPrice) add("Price: \"At least\" must be below \"Under\".")
    fun stat(name: String, min: Int?, max: Int?) {
        if (min != null && max != null && min > max) add("$name: From is above To.")
    }
    stat("Mana value", r.minCmc, r.maxCmc)
    stat("Power", r.minPower, r.maxPower)
    stat("Toughness", r.minToughness, r.maxToughness)
}

private fun numTexts(r: BoxRules): Map<String, String> = mapOf(
    "minCmc" to r.minCmc, "maxCmc" to r.maxCmc,
    "minPower" to r.minPower, "maxPower" to r.maxPower,
    "minToughness" to r.minToughness, "maxToughness" to r.maxToughness,
).mapValues { it.value?.toString().orEmpty() }

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
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun RuleCard(title: String, summary: String?, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(Corners.Medium),
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

    Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.Background, modifier = Modifier.fillMaxWidth()) {
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
                    items(thumbs, key = { it.id }) { c -> CardImage(c.smallImageUrl, Modifier.height(64.dp)) }
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
                        color = Palette.GoldText,
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

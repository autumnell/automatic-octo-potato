package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Warning
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
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.data.db.facts
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

@Composable
internal fun StepBar(step: Int, showStart: Boolean, canJump: Boolean, onStep: (Int) -> Unit) {
    val steps = buildList {
        if (showStart) add(1 to "Start")
        add(2 to "Name")
        add(3 to "Rules")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (n, label) ->
            val done = n < step
            val reachable = n <= step || canJump
            Row(
                Modifier.clip(RoundedCornerShape(Corners.Small)).clickable(enabled = reachable) { onStep(n) }.padding(end = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (n == step) Palette.Accent else if (done) Palette.Accent.copy(alpha = 0.25f) else Palette.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Outlined.Check, null, tint = Palette.AccentText, modifier = Modifier.size(18.dp))
                    else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (n == step) Palette.OnAccent else Palette.Muted)
                }
                Spacer(Modifier.width(Space.s2))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (n == step) Palette.Text else Palette.Muted)
            }
        }
    }
}

/** Step 1: a ready-made box, or a blank one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StartStep(onPick: (BoxDraft) -> Unit, onTemplate: ((BoxTemplate) -> Unit)? = null) {
    if (onTemplate != null) {
        Text("Whole system", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.height(Space.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            BOX_TEMPLATES.forEach { t ->
                Surface(onClick = { onTemplate(t) }, shape = RoundedCornerShape(Corners.Medium), color = Palette.SurfaceHigh) {
                    Column(Modifier.width(150.dp).padding(Space.s3)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1)
                        Text(t.blurb, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.s4))
        Text("One box", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.height(Space.s2))
    } else {
        Text("Pick a starting point", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Spacer(Modifier.height(Space.s3))
    }
    Surface(
        onClick = { onPick(BoxDraft()) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Corners.Medium),
        color = Palette.SurfaceHigh,
    ) {
        Row(Modifier.padding(Space.s4), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckBoxOutlineBlank, null, tint = Palette.Icon)
            Spacer(Modifier.width(Space.s3))
            Column {
                Text("Blank box", style = MaterialTheme.typography.titleSmall)
                Text("Name it and set your own rules", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
    Spacer(Modifier.height(Space.s3))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        BOX_PRESETS.forEach { p ->
            Surface(
                onClick = { onPick(BoxDraft(p.name, p.kind, 0, p.rules, divider = p.divider)) },
                shape = RoundedCornerShape(Corners.Medium),
                color = Palette.SurfaceHigh,
            ) {
                Column(Modifier.width(150.dp).padding(Space.s3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(boxIcon(p.kind), null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s2))
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
internal fun DetailsStep(draft: BoxDraft, onChange: (BoxDraft) -> Unit) {
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        CardBox.KINDS.forEach { k ->
            SelectPill(kindLabel(k), draft.kind == k, { onChange(draft.copy(kind = k)) }, leading = { Icon(boxIcon(k), null, Modifier.size(18.dp)) })
        }
    }

    Label("Color")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s1), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
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
                    if (selected) Icon(Icons.Outlined.Check, null, tint = if (fill.luminance() > 0.45f) Color(0xFF141414) else Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    Spacer(Modifier.height(Space.s4))
    Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.s4), verticalAlignment = Alignment.CenterVertically) {
            Icon(boxIcon(draft.kind), null, tint = boxColor(draft.color), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(Space.s3))
            Text(draft.name.ifBlank { "Your box" }, style = MaterialTheme.typography.titleLarge, color = boxColor(draft.color))
        }
    }
}

/** Step 3: one collapsible card per rule, each with a one-line summary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RulesStep(
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
    Spacer(Modifier.height(Space.s2))

    RuleCard("Colors", rules.colors.takeIf { it.isNotEmpty() }?.joinToString(", ") { BoxRules.COLOR_NAMES[it] ?: it }, open == "colors", { toggle("colors") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            listOf("W", "U", "B", "R", "G", "M", "C").forEach { c ->
                SelectPill(BoxRules.COLOR_NAMES.getValue(c), c in rules.colors, { onRules(rules.copy(colors = rules.colors.toggle(c))) },
                    leading = { ColorPip(c[0]) })
            }
        }
    }
    RuleCard("Rarity", rules.rarities.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }, open == "rarity", { toggle("rarity") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            listOf("common", "uncommon", "rare", "mythic").forEach { r ->
                SelectPill(r.replaceFirstChar(Char::uppercase), r in rules.rarities, { onRules(rules.copy(rarities = rules.rarities.toggle(r))) },
                    leading = { RarityMark(r) })
            }
        }
    }
    RuleCard("Printing", rules.treatments.takeIf { it.isNotEmpty() }?.joinToString(", ") { BoxRules.TREATMENT_NAMES[it] ?: it }, open == "printing", { toggle("printing") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            BoxRules.TREATMENT_NAMES.forEach { (k, label) ->
                SelectPill(label, k in rules.treatments, { onRules(rules.copy(treatments = rules.treatments.toggle(k))) })
            }
        }
    }
    val finishSummary = when (rules.finish) { "foil" -> "Foil only"; "nonfoil" -> "Non-foil only"; else -> null }
    RuleCard("Finish", finishSummary, open == "finish", { toggle("finish") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            listOf("any" to "Any", "foil" to "Foil only", "nonfoil" to "Non-foil only").forEach { (k, label) ->
                SelectPill(label, rules.finish == k, { onRules(rules.copy(finish = k)) })
            }
        }
    }
    RuleCard("Card type", rules.types.takeIf { it.isNotEmpty() }?.joinToString(", "), open == "type", { toggle("type") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            BoxRules.TYPES.forEach { t -> SelectPill(t, t in rules.types, { onRules(rules.copy(types = rules.types.toggle(t))) }, leading = { Mana.Icon(t.lowercase(), Palette.Text, 16.dp) }) }
        }
    }
    if (AppSettings.showPrices || minText.isNotEmpty() || maxText.isNotEmpty()) {
        val priceSummary = BoxRules(minPrice = rules.minPrice, maxPrice = rules.maxPrice).takeIf { !it.isCatchAll }?.describe()
        RuleCard("Price per card", priceSummary, open == "price", { toggle("price") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            listOf("W", "U", "B", "R", "G", "C").forEach { c ->
                SelectPill(BoxRules.COLOR_NAMES.getValue(c), c in rules.identity, { onRules(rules.copy(identity = rules.identity.toggle(c))) },
                    leading = { ColorPip(c[0]) })
            }
        }
        Spacer(Modifier.height(Space.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            listOf("exact" to "Exactly", "within" to "Fits in deck").forEach { (k, label) ->
                SelectPill(label, rules.identityMode == k, { onRules(rules.copy(identityMode = k)) })
            }
        }
    }
    StatRule("Mana value", "Cmc", rules.minCmc, rules.maxCmc, nums, onNum, open == "cmc", { toggle("cmc") })
    StatRule("Power", "Power", rules.minPower, rules.maxPower, nums, onNum, open == "power", { toggle("power") })
    StatRule("Toughness", "Toughness", rules.minToughness, rules.maxToughness, nums, onNum, open == "tough", { toggle("tough") })
    RuleCard("Format", rules.formats.takeIf { it.isNotEmpty() }?.joinToString(", ") { BoxRules.FORMAT_NAMES[it] ?: it }, open == "format", { toggle("format") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            BoxRules.FORMAT_NAMES.forEach { (k, label) ->
                SelectPill(label, k in rules.formats, { onRules(rules.copy(formats = rules.formats.toggle(k))) })
            }
        }
    }
    RuleCard("Reserved List", if (rules.reserved) "Only" else null, open == "reserved", { toggle("reserved") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            SelectPill("Any", !rules.reserved, { onRules(rules.copy(reserved = false)) })
            SelectPill("Reserved only", rules.reserved, { onRules(rules.copy(reserved = true)) })
        }
    }
    RuleCard("Tokens & art cards", if (rules.tokens) "Only" else null, open == "tokens", { toggle("tokens") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
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

internal fun numTexts(r: BoxRules): Map<String, String> = mapOf(
    "minCmc" to r.minCmc, "maxCmc" to r.maxCmc,
    "minPower" to r.minPower, "maxPower" to r.maxPower,
    "minToughness" to r.minToughness, "maxToughness" to r.maxToughness,
).mapValues { it.value?.toString().orEmpty() }

/** How the box is laid out inside: the card order, where it's kept, its rows and how many cards fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayoutCards(
    sortKey: String, onSortKey: (String) -> Unit,
    location: String, onLocation: (String) -> Unit,
    rows: Int, onRows: (Int) -> Unit,
    capText: String, onCap: (String) -> Unit,
) {
    var open by remember { mutableStateOf<String?>("order") }
    fun toggle(k: String) { open = if (open == k) null else k }
    Label("Inside the box")
    RuleCard("Card order", sortSummary(sortKey), open == "order", { toggle("order") }) {
        SortPicker(sortKey, onSortKey)
    }
    val placeSummary = listOfNotNull(location.trim().ifEmpty { null }, rows.takeIf { it > 1 }?.let { "$it rows" }).joinToString(" · ").ifEmpty { null }
    RuleCard("Where it's kept", placeSummary, open == "place", { toggle("place") }) {
        OutlinedTextField(
            value = location, onValueChange = { onLocation(it.take(60)) },
            placeholder = { Text("Closet, top shelf") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), colors = fieldColors(),
        )
        Spacer(Modifier.height(Space.s3))
        Text("Rows of cards", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
        Spacer(Modifier.height(Space.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            listOf(1, 2, 3, 4, 5, 6, 8).forEach { n -> SelectPill(n.toString(), rows == n, { onRows(n) }) }
        }
    }
    RuleCard("Capacity", capText.toIntOrNull()?.takeIf { it > 0 }?.let { "$it cards" }, open == "cap", { toggle("cap") }) {
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
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.s1),
        shape = RoundedCornerShape(Corners.Medium),
        color = Palette.SurfaceHigh,
        border = if (summary != null) androidx.compose.foundation.BorderStroke(1.dp, Palette.Accent.copy(alpha = 0.5f)) else null,
    ) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = Space.s4, vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(summary ?: "Any", style = MaterialTheme.typography.bodySmall, color = if (summary != null) Palette.Text else Palette.Muted, maxLines = 1)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse" else "Expand", tint = Palette.Icon)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s4)) { content() }
            }
        }
    }
}

/** "N of your cards fit", with thumbnails, and a warning when boxes earlier in the Auto-sort order take some first. */
@Composable
internal fun LivePreview(rules: BoxRules, editingId: Long?) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val cards by remember { repo.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val boxes by remember { repo.observeBoxes() }.collectAsStateWithLifecycle(initialValue = emptyList())
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
        Column(Modifier.padding(Space.s4)) {
            Text("Preview", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Text(rules.describe(), style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            Spacer(Modifier.height(Space.s2))
            Text(
                when {
                    all.isEmpty() -> "Your collection is empty, so nothing to preview yet"
                    fitCount == 0 -> "None of your cards fit these rules yet"
                    rules.isCatchAll -> "Takes any card. ${fitCount - takenCount} of your cards don't fit another box."
                    else -> "$fitCount of your cards fit this box"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            val thumbs = result.fits.map { all[it] }.distinctBy { it.scryfallId }.take(14)
            if (thumbs.isNotEmpty()) {
                Spacer(Modifier.height(Space.s2))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    items(thumbs, key = { it.id }) { c -> CardImage(c.smallImageUrl, Modifier.height(64.dp)) }
                }
            }
            if (result.takenBy.isNotEmpty()) {
                Spacer(Modifier.height(Space.s3))
                val names = result.takenBy.take(3).joinToString(", ") { (b, idx) -> "${b.name} (${idx.sumOf { all[it].quantity }})" }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Warning, null, tint = Palette.Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s2))
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

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(Space.s4))
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    Spacer(Modifier.height(Space.s2))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Outline,
    cursorColor = Palette.Accent,
)

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

internal fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

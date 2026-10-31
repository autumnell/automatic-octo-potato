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
    /** A [com.mtgscanner.data.BoxLayout] key: dividers and card order; empty = none. */
    val divider: String = "",
    /** Where the box is kept, like "Closet, top shelf". */
    val location: String = "",
    /** Rows of cards in the box; 1 for a single row. */
    val rows: Int = 1,
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
    /** Shows the card order, place and capacity fields; the caller must save them (see [BoxDraft]). */
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
        minPrice = com.mtgscanner.util.PriceInput.parse(minText),
        maxPrice = com.mtgscanner.util.PriceInput.parse(maxText),
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
                    sortKey = draft.divider, onSortKey = { draft = draft.copy(divider = it) },
                    location = draft.location, onLocation = { draft = draft.copy(location = it) },
                    rows = draft.rows, onRows = { draft = draft.copy(rows = it) },
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

fun kindLabel(kind: String) = when (kind) {
    "binder" -> "Binder"
    "deck" -> "Deck"
    "trade" -> "Trade binder"
    else -> "Box"
}

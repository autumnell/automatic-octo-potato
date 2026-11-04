package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import com.mtgscanner.data.db.placeName
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.mtgscanner.ui.components.pinchToResize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.boxIcon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.mtgscanner.data.BoxRules
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.mtgscanner.ui.components.CollapsingHeader
import com.mtgscanner.ui.components.flashOnce
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import androidx.compose.material.icons.outlined.Style
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.SetMark
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.unitEur
import com.mtgscanner.util.PriceCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mtgscanner.ui.components.foilShimmer
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.ToolChip
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.cardPriceShort
import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.mtgscanner.data.Binder
import com.mtgscanner.ui.detail.CardDetailScreen
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.TabularNums

/** Top bar while picking cards: how many, and what to do with them. */
@Composable
internal fun SelectionBar(count: Int, boxes: List<CardBox>, decks: List<Deck>, vm: BrowseViewModel, onTag: () -> Unit, onEdit: () -> Unit, value: String?, onCopy: () -> Unit) {
    var moveMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(end = Space.s1, top = Space.s1, bottom = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection) { Icon(Icons.Outlined.Close, "Stop picking") }
        Text(
            if (value != null) "$count selected · $value" else "$count selected",
            style = MaterialTheme.typography.titleMedium.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = vm::selectAll) { Icon(Icons.Outlined.SelectAll, "Select all") }
        Box {
            IconButton(onClick = { moveMenu = true }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "Move to box") }
            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                boxes.forEach { b ->
                    DropdownMenuItem(
                        text = { Text(b.name) },
                        leadingIcon = { Icon(boxIcon(b.kind), null, tint = boxColor(b.color)) },
                        onClick = { moveMenu = false; vm.moveSelected(b.id) },
                    )
                }
                if (decks.isNotEmpty()) {
                    DecksHeader(Modifier.padding(horizontal = Space.s3, vertical = Space.s2))
                    decks.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.name) },
                            leadingIcon = { Icon(Icons.Outlined.Style, null, tint = Palette.Icon) },
                            onClick = { moveMenu = false; vm.moveSelectedToDeck(d.id) },
                        )
                    }
                }
            }
        }
        IconButton(onClick = vm::deleteSelected) { Icon(Icons.Outlined.DeleteOutline, "Remove from collection", tint = Palette.Danger) }
        Box {
            IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, "More") }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text("Add a tag") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                    onClick = { more = false; onTag() },
                )
                DropdownMenuItem(
                    text = { Text("For sale") }, leadingIcon = { Icon(Icons.Outlined.Sell, null) },
                    onClick = { more = false; vm.sellSelected() },
                )
                DropdownMenuItem(text = { Text(if (count == 1) "Copy name" else "Copy names") }, onClick = { more = false; onCopy() })
                DropdownMenuItem(text = { Text("Condition, language, foil") }, onClick = { more = false; onEdit() })
                DropdownMenuItem(text = { Text("Stay in this box") }, onClick = { more = false; vm.pinSelected(true) })
                DropdownMenuItem(text = { Text("Let re-sort move") }, onClick = { more = false; vm.pinSelected(false) })
            }
        }
    }
}

/** Condition, language and finish for every picked card; anything left unpicked stays as it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BulkEditDialog(onApply: (String?, String?, Boolean?, Double?, String?) -> Unit, onDismiss: () -> Unit) {
    var paidText by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    val paid = com.mtgscanner.util.PriceInput.parse(paidText)
    var cond by remember { mutableStateOf<String?>(null) }
    var lang by remember { mutableStateOf<String?>(null) }
    var foil by remember { mutableStateOf<Boolean?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Edit cards") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Condition", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(Space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    CARD_CONDITIONS.forEach { (c, _) -> SelectPill(c, cond == c, { cond = if (cond == c) null else c }) }
                }
                Spacer(Modifier.height(Space.s3))
                Text("Language", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(Space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    CARD_LANGUAGES.forEach { (code, label) -> SelectPill(label, lang == code, { lang = if (lang == code) null else code }) }
                }
                Spacer(Modifier.height(Space.s3))
                Text("Finish", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(Space.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    SelectPill("Foil", foil == true, { foil = if (foil == true) null else true })
                    SelectPill("Non-foil", foil == false, { foil = if (foil == false) null else false })
                }
                Spacer(Modifier.height(Space.s3))
                // After buying a lot: what each card cost and who it came from.
                OutlinedTextField(
                    paidText, { paidText = it }, singleLine = true,
                    label = { Text("Paid each") }, placeholder = { Text("Leave as is") },
                    isError = paidText.isNotBlank() && paid == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.s2))
                OutlinedTextField(
                    from, { from = it }, singleLine = true,
                    label = { Text("From") }, placeholder = { Text("e.g. Local game store") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val fromOrNull = from.trim().ifEmpty { null }
            val ok = (paidText.isBlank() || paid != null) && (cond != null || lang != null || foil != null || paid != null || fromOrNull != null)
            TextButton(onClick = { onApply(cond, lang, foil, paid, fromOrNull) }, enabled = ok) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


@Composable
fun NameDialog(title: String, hint: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(hint) }) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Type a new tag, or tap one you've used before. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagDialog(existing: List<String>, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var tag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Add a tag") },
        text = {
            Column {
                OutlinedTextField(tag, { tag = it }, singleLine = true, placeholder = { Text("e.g. For Kenji's deck, Lent out") })
                if (existing.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s3))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        existing.forEach { t -> SelectPill(t, false, { onDone(t) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(tag) }, enabled = tag.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BoxPickerDialog(
    title: String,
    boxes: List<CardBox>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    decks: List<Deck> = emptyList(),
    onPickDeck: (Long) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            // Long box lists scroll instead of running off the dialog.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (boxes.isEmpty() && decks.isEmpty()) Text("Make another box first.", color = Palette.Muted)
                boxes.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { onPick(b.id) }.padding(vertical = Space.s3, horizontal = Space.s1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(boxIcon(b.kind), null, tint = boxColor(b.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.s3))
                        Text(b.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (decks.isNotEmpty()) {
                    DecksHeader(Modifier.padding(top = Space.s3, bottom = Space.s1, start = Space.s1))
                    decks.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { onPickDeck(d.id) }.padding(vertical = Space.s3, horizontal = Space.s1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Style, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(Space.s3))
                            Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Decks", above the decks in a Move picker. */
@Composable
private fun DecksHeader(modifier: Modifier = Modifier) {
    Text("Decks", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = modifier)
}

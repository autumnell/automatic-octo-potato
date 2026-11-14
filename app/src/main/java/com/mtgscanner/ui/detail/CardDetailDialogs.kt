package com.mtgscanner.ui.detail

import com.mtgscanner.ui.theme.Space
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.CardPhotos
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.changePrinting
import com.mtgscanner.data.db.CardEvent
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.isDeckSlot
import com.mtgscanner.data.db.placeName
import com.mtgscanner.data.autoBoxFor
import com.mtgscanner.data.deckSlotId
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.userTagList
import com.mtgscanner.ui.collection.TagDialog
import com.mtgscanner.ui.components.LineChart
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.tagList
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardTextBlock
import com.mtgscanner.ui.components.textInfo
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.nearMintEur
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.foilShimmer
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.SetSymbol
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.cardTotal
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.PlaceholderPage

@Composable
internal fun PhotoViewer(path: String, onRemove: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
            Column(Modifier.padding(Space.s2)) {
                AsyncImage(
                    model = java.io.File(path),
                    contentDescription = "Your photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(Corners.Small)).clickable(onClick = onDismiss),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRemove) { Text("Remove photo", color = Palette.Danger) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onReplace) { Text("Replace") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

/** Pick how many copies and what's different about them; they become their own row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitDialog(c: CollectionCard, onSplit: (SplitChange) -> Unit, onDismiss: () -> Unit) {
    var qty by remember { mutableIntStateOf(1) }
    var condition by remember { mutableStateOf(c.condition) }
    var foil by remember { mutableStateOf(c.foil) }
    var lang by remember { mutableStateOf(c.lang.ifBlank { "en" }.lowercase()) }
    val change = SplitChange(qty, condition, foil, lang)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Split copies") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(qty, onChange = { qty = it.coerceIn(1, c.quantity - 1) })
                    Spacer(Modifier.width(Space.s3))
                    Text("of ${c.quantity}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Muted)
                }
                Spacer(Modifier.height(Space.s3))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    CONDITIONS.forEach { (code, _) -> SelectPill(code, condition == code, onClick = { condition = code }) }
                    SelectPill("Foil", foil, onClick = { foil = !foil })
                }
                Spacer(Modifier.height(Space.s3))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    LANGUAGES.forEach { (code, _) -> SelectPill(code.uppercase(), lang == code, onClick = { lang = code }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSplit(change) }, enabled = SplitMath.valid(c, change)) { Text("Split") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Parses "$1.50", "1,50" or "1.5"; blank is null. Returns false for the outer value when not a number. */
internal fun parseAmount(text: String): Pair<Boolean, Double?> {
    if (text.isBlank()) return true to null
    val v = com.mtgscanner.util.PriceInput.parse(text) ?: return false to null
    return true to v
}

@Composable
internal fun AmountDialog(title: String, initial: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "") }
    val parsed = parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(12) },
                placeholder = { Text("$ · blank = none") },
                singleLine = true,
                isError = !parsed.first,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(parsed.second) }, enabled = parsed.first) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val SOURCES = listOf("Pack", "Trade", "Store", "Gift")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FromDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("From") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    SOURCES.forEach { s -> SelectPill(s, text.equals(s, ignoreCase = true), onClick = { text = if (text.equals(s, ignoreCase = true)) "" else s }) }
                }
                Spacer(Modifier.height(Space.s3))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    placeholder = { Text("Or type…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun NotesDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf(initial) }
    // Notes save only on Save: ask before dropping typed changes.
    var leaving by remember { mutableStateOf(false) }
    val close: () -> Unit = { if (notes != initial) leaving = true else onDismiss() }
    AlertDialog(
        onDismissRequest = close,
        containerColor = Palette.Surface,
        title = { Text("Notes") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Deck, binder page, trades…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(notes) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        containerColor = Palette.Surface,
        title = { Text("Discard note changes?") },
        confirmButton = { TextButton(onClick = { leaving = false; onDismiss() }) { Text("Discard changes", color = Palette.Danger) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep editing") } },
    )
}

@Composable
internal fun GradedDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Graded") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(20) },
                placeholder = { Text("PSA 9 · blank = none") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.mtgscanner.ui.collection

import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.components.formatMoneyShort
import com.mtgscanner.data.db.totalEur
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.BoxArt
import com.mtgscanner.data.BoxChange
import com.mtgscanner.data.planBoxes
import com.mtgscanner.data.saveBoxChanges
import com.mtgscanner.data.undoBoxChanges
import com.mtgscanner.data.BoxCovers
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.BoxStats
import com.mtgscanner.data.BoxLayout
import com.mtgscanner.data.BoxTemplate
import com.mtgscanner.data.RuleCheck
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.Found
import com.mtgscanner.data.LastChecked
import com.mtgscanner.data.LastCheckedMath
import com.mtgscanner.data.findCard
import com.mtgscanner.ui.home.SortItem
import com.mtgscanner.ui.home.cardsToSort
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.BoxDraft
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.panelRow
import com.mtgscanner.ui.decks.DecksTab
import com.mtgscanner.ui.components.StatTile
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DeleteBoxDialog(box: BoxSummary, others: List<BoxSummary>, onDismiss: () -> Unit, onDelete: (Long?) -> Unit) {
    // -1 means "delete the cards".
    var choice by remember { mutableStateOf(CollectionViewModel.TO_ANYTHING) }
    var confirmForever by remember { mutableStateOf(false) }
    val anything = others.firstOrNull { it.kind != "trade" && it.kind != "deck" && BoxRules.fromJson(it.rules).isCatchAll }
    if (confirmForever) {
        AlertDialog(
            onDismissRequest = { confirmForever = false },
            containerColor = Palette.Surface,
            title = { Text("Permanently delete ${box.cards} cards?") },
            text = { Text("${box.name} and every card in it will be removed from your collection.") },
            confirmButton = { TextButton(onClick = { onDelete(null) }) { Text("Delete forever", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmForever = false }) { Text("Keep them") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Delete ${box.name}?") },
        text = {
            // Scrolls with many boxes, so every choice (and "Delete them permanently") can be reached.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (box.cards == 0) {
                    Text("It's empty.")
                } else {
                    Text("What should happen to its ${box.cards} cards?")
                    Spacer(Modifier.height(8.dp))
                    Choice(
                        if (anything != null) "Move them to ${anything.name} (takes any card)" else "Move them to a new Anything box",
                        choice == CollectionViewModel.TO_ANYTHING,
                    ) { choice = CollectionViewModel.TO_ANYTHING }
                    if (others.isNotEmpty()) {
                        Choice("Sort them into my other boxes by their rules", choice == CardBox.AUTO_SORT) { choice = CardBox.AUTO_SORT }
                        others.filter { it.id != anything?.id }.forEach { o -> Choice("Move them to ${o.name}", choice == o.id) { choice = o.id } }
                    }
                    Choice("Delete them permanently", choice == -1L) { choice = -1L }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    box.cards == 0 -> onDelete(CardBox.AUTO_SORT)
                    choice == -1L -> confirmForever = true
                    else -> onDelete(choice)
                }
            }) { Text("Delete box", color = Palette.Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Sort a box after the fact, like an Anything box everything was scanned into. */
@Composable
internal fun SortBoxDialog(box: BoxSummary, hasOtherRules: Boolean, onOthers: () -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Sort ${box.name}") },
        text = {
            Column {
                Text(
                    "Cards that fit nothing stay here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Surface(onClick = onNew, shape = RoundedCornerShape(Corners.Medium), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Into a new box", style = MaterialTheme.typography.titleSmall)
                        Text("Set up a box with rules, and the cards that fit move into it", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
                if (hasOtherRules) {
                    Spacer(Modifier.height(8.dp))
                    Surface(onClick = onOthers, shape = RoundedCornerShape(Corners.Medium), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Into my other boxes", style = MaterialTheme.typography.titleSmall)
                            Text("Each card goes to the first box whose rules fit, in Auto-sort order", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A small stepper for how many copies of a wishlist card you want. */
@Composable
internal fun WantedDialog(item: WishlistItem, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var n by remember(item.id) { mutableIntStateOf(item.wanted.coerceAtLeast(1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(item.name) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                com.mtgscanner.ui.components.RepeatIconButton(onClick = { if (n > 1) n-- }, enabled = n > 1, repeat = n > 1) { Icon(Icons.Outlined.Remove, "Fewer") }
                Text("Want $n", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                com.mtgscanner.ui.components.RepeatIconButton(onClick = { n++ }) { Icon(Icons.Outlined.Add, "More") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The order of cards in a box: a ready-made order, or up to three levels of dividers and the order behind them. */
@Composable
internal fun LayoutDialog(box: BoxSummary, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember(box.divider) { mutableStateOf(BoxLayout.of(box.divider).key) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Card order in ${box.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                com.mtgscanner.ui.components.SortPicker(key) { key = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

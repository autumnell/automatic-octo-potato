package com.mtgscanner.ui.collection

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(f: Filters, ui: BrowseUi, vm: BrowseViewModel, saved: List<SavedSearch>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (f.activeCount > 0) TextButton(onClick = vm::clearFilters) { Text("Clear all") }
            }
            FilterGroup("Color") {
                // Pair keys from Home and an old "Multicolor" pick show only while picked, so they can be cleared.
                val legacy = f.colors.filter { it == CardColors.LEGACY_MULTI || com.mtgscanner.ui.home.ColorPairs.isPairKey(it) }
                (COLOR_FILTERS + legacy).forEach { col ->
                    val label = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless", "M" to "Multicolor")[col]
                        ?: com.mtgscanner.ui.home.ColorPairs.name(col)
                    val pip: (@Composable () -> Unit)? = if (col in listOf("W", "U", "B", "R", "G", "C", "M")) {
                        { ColorPip(col[0]) }
                    } else null
                    SelectPill(label, col in f.colors, onClick = { vm.update { it.copy(colors = it.colors.toggle(col)) } }, leading = pip)
                }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorMatch.entries.forEach { m ->
                    SelectPill(m.label, f.colorMatch == m, onClick = { vm.update { it.copy(colorMatch = m) } })
                }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Colors", !f.byIdentity, onClick = { vm.update { it.copy(byIdentity = false) } })
                SelectPill("Identity", f.byIdentity, onClick = { vm.update { it.copy(byIdentity = true) } })
            }
            FilterGroup("Rarity") {
                RARITIES.forEach { r ->
                    SelectPill(r.replaceFirstChar(Char::uppercase), r in f.rarities, onClick = { vm.update { it.copy(rarities = it.rarities.toggle(r)) } },
                        leading = { RarityMark(r) })
                }
                SelectPill("Foil only", f.foilOnly, onClick = { vm.update { it.copy(foilOnly = !it.foilOnly) } })
            }
            FilterGroup("Type") {
                CARD_TYPES.forEach { t ->
                    SelectPill(t, t in f.types, onClick = { vm.update { it.copy(types = it.types.toggle(t)) } })
                }
            }
            if (ui.userTags.isNotEmpty()) {
                FilterGroup("Your tags") {
                    ui.userTags.forEach { t ->
                        SelectPill(t, t in f.userTags, onClick = { vm.update { it.copy(userTags = it.userTags.toggle(t)) } })
                    }
                }
            }
            if (ui.treatments.isNotEmpty()) {
                FilterGroup("Printing") {
                    ui.treatments.forEach { t ->
                        SelectPill(BoxRules.TREATMENT_NAMES[t] ?: t, t in f.treatments, onClick = { vm.update { it.copy(treatments = it.treatments.toggle(t)) } })
                    }
                }
            }
            if (ui.sets.isNotEmpty()) {
                FilterGroup("Set") {
                    SelectPill("Any", f.set == null, onClick = { vm.update { it.copy(set = null) } })
                    ui.sets.forEach { (code, name) ->
                        SelectPill(name, f.set == code, onClick = { vm.update { it.copy(set = if (it.set == code) null else code) } })
                    }
                }
            }
            if (ui.decks.isNotEmpty() || f.deckBox != null) {
                FilterGroup("Deck") {
                    SelectPill("Any", f.deckBox == null, onClick = { vm.update { it.copy(deckBox = null) } })
                    ui.decks.forEach { (id, name) ->
                        SelectPill(name, f.deckBox == id, onClick = { vm.update { it.copy(deckBox = if (it.deckBox == id) null else id) } })
                    }
                }
            }
            if (saved.isNotEmpty()) {
                Text("Saved searches", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                saved.forEach { sv ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sv.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).clickable { vm.applySaved(sv); onDismiss() }.padding(vertical = 8.dp))
                        IconButton(onClick = { vm.deleteSaved(sv.name) }) { Icon(Icons.Outlined.DeleteOutline, "Delete saved search", tint = Palette.Icon) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

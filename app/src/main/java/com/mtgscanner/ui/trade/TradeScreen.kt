package com.mtgscanner.ui.trade

import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.components.formatMoney
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.mtgscanner.ui.decks.FromPhotoButton
import com.mtgscanner.ui.decks.appendList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CardKingdom
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.DecklistParser
import com.mtgscanner.util.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.ruleBelow
import com.mtgscanner.ui.components.panelRow

/** Trade from a trade binder: give cards from the binder, get any card, then apply it to your collection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(binderId: Long, onBack: () -> Unit, onSellList: () -> Unit = {}) {
    val vm: TradeViewModel = viewModel(key = "trade$binderId", factory = AppViewModels.Factory)
    LaunchedEffect(binderId) { vm.setBinder(binderId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val collection by vm.collection.collectAsStateWithLifecycle()
    val binderName by vm.binderName.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<String?>(null) } // "give" or "get"
    var confirm by remember { mutableStateOf(false) }
    var wants by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) } // key of a get-side card
    var showPrintings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    Haptics.Attach()

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(Space.s1), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text(
                    if (binderName.isEmpty()) "Trade" else "Trade · $binderName",
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { wants = true }) { Text("Wants") }
                IconButton(onClick = onSellList) { Icon(Icons.Outlined.Sell, "Sell list", tint = Palette.Text) }
                if (ui.give.isNotEmpty() || ui.get.isNotEmpty()) TextButton(onClick = vm::clear) { Text("Clear") }
            }
            if (AppSettings.showPrices && (ui.give.isNotEmpty() || ui.get.isNotEmpty())) {
                Surface(Modifier.fillMaxWidth().padding(horizontal = Space.s4), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
                    Column(Modifier.padding(Space.s4)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("You give ${formatMoney(ui.giveValue, ui.giveEur)}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums))
                                Text("You get ${formatMoney(ui.getValue, ui.getEur)}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums))
                            }
                            ChangeText(ui.getValue - ui.giveValue, ui.giveValue, MaterialTheme.typography.titleMedium)
                        }
                        if (ui.giveValue + ui.getValue > 0) {
                            Spacer(Modifier.height(Space.s3))
                            FairnessMeter(ui.giveValue, ui.getValue)
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                item { SideHeader("You give", onAdd = { picking = "give" }) }
                items(ui.give, key = { it.key }) { TradeRow(it, vm, first = it === ui.give.first(), last = it === ui.give.last(), modifier = Modifier.animateItem()) }
                item { SideHeader("You get", onAdd = { picking = "get" }) }
                items(ui.get, key = { it.key }) { g ->
                    TradeRow(g, vm, first = g === ui.get.first(), last = g === ui.get.last(), onEdit = { editing = g.key; showPrintings = false }, modifier = Modifier.animateItem())
                }
                if (ui.give.isEmpty() && ui.get.isEmpty()) {
                    item {
                        if (collection.isEmpty()) {
                            EmptyState("Binder is empty", action = "Add a card you get", onAction = { picking = "get" })
                        } else {
                            EmptyState("Start a trade", action = "Pick from binder", onAction = { picking = "give" })
                        }
                    }
                }
            }
            GradientButton(
                "Apply to my collection",
                { confirm = true },
                Modifier.fillMaxWidth().padding(Space.s4).navigationBarsPadding(),
                enabled = !ui.busy && (ui.give.isNotEmpty() || ui.get.isNotEmpty()),
            )
        }
        com.mtgscanner.ui.components.SlimSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (picking != null) {
        ModalBottomSheet(onDismissRequest = { picking = null }, containerColor = Palette.Surface) {
            var q by remember { mutableStateOf("") }
            Column(Modifier.padding(horizontal = Space.s4)) {
                SearchField(q, { q = it; if (picking == "get") vm.suggest(it) }, placeholder = if (picking == "give") "Search binder" else "Search any card")
            }
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                if (picking == "give") {
                    val matches = collection.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }.take(60)
                    if (matches.isEmpty()) item { Text(if (collection.isEmpty()) "Binder is empty" else "No match", color = Palette.Muted, modifier = Modifier.padding(Space.s6)) }
                    items(matches, key = { it.id }) { c ->
                        PickRow(c.name, "${c.setCode} · ×${c.quantity}", c.smallImageUrl) { vm.give(c); picking = null }
                    }
                } else {
                    if (ui.suggestions.isEmpty()) item { Text("Type a card name", color = Palette.Muted, modifier = Modifier.padding(Space.s6)) }
                    items(ui.suggestions) { name -> PickRow(name, null, null) { vm.get(name); picking = null } }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
    val edited = ui.get.firstOrNull { it.key == editing }
    if (edited != null && !showPrintings) {
        GetCardSheet(
            edited,
            onPrintings = { vm.loadPrintings(edited); showPrintings = true },
            onChange = { foil, cond, lang -> vm.setDetails(edited.key, foil, cond, lang) },
            onDismiss = { editing = null },
        )
    }
    if (edited != null && showPrintings) {
        val printings by vm.printings.collectAsStateWithLifecycle()
        PrintingsSheet(printings, edited.card?.id, { vm.setPrinting(edited.key, it); showPrintings = false }, { showPrintings = false })
    }
    if (wants) WantListSheet(collection, onAddAll = { vm.giveAll(it); wants = false }, onDismiss = { wants = false })
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.Surface,
            title = { Text("Apply this trade?") },
            text = { Text("Updates your collection both ways") },
            confirmButton = { TextButton(onClick = { vm.apply(); confirm = false }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
    if (ui.busy) {
        // Blocks taps underneath while a card is looked up or the trade is saved.
        Box(
            Modifier.fillMaxSize()
                .background(Palette.Background.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            contentAlignment = Alignment.Center,
        ) { com.mtgscanner.ui.components.Spinner() }
    }
}

@Composable
private fun SideHeader(title: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s2, top = Space.s3), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s1))
            Text("Add card")
        }
    }
}

@Composable
private fun TradeRow(item: TradeItem, vm: TradeViewModel, first: Boolean, last: Boolean, onEdit: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    // Swipe left to take the card out of the trade.
    com.mtgscanner.ui.components.SwipeActions(
        start = null,
        end = com.mtgscanner.ui.components.SwipeAction("Remove", Icons.Outlined.Close, Palette.Danger) { vm.setQty(item, 0) },
        modifier = modifier.padding(horizontal = Space.s4),
    ) {
        Row(
            Modifier.fillMaxWidth().panelRow(first, last)
                .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier).padding(start = Space.s3, end = Space.s1, top = Space.s2, bottom = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardImage(item.imageUrl, Modifier.width(40.dp), zoom = true, onTap = onEdit)
            Spacer(Modifier.width(Space.s3))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val prices = if (AppSettings.showPrices) " · ${formatMoney(item.price, item.eur)}" + item.buyPrice?.let { " · CK buys ${formatUsd(it)}" }.orEmpty() else ""
                Text((if (item.card != null) item.getLabel() else item.set) + prices, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            QuantityStepper(item.qty, onChange = { vm.setQty(item, it.coerceAtMost(item.owned?.quantity ?: 99)) })
            IconButton(onClick = { vm.setQty(item, 0) }) { Icon(Icons.Outlined.Close, "Remove", tint = Palette.Icon) }
        }
    }
}

/** How even the trade is: a bar split by value, with a short verdict. */
@Composable
private fun FairnessMeter(give: Double, get: Double) {
    val total = give + get
    val gap = kotlin.math.abs(get - give) / maxOf(give, get)
    val (label, color) = when {
        gap <= 0.1 -> "Fair" to Palette.Success
        gap <= 0.25 -> (if (get > give) "Leans your way" else "Leans their way") to Palette.Gold
        else -> (if (get > give) "Uneven, you gain" else "Uneven, you lose") to Palette.Danger
    }
    Column {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh)) {
            Row(Modifier.fillMaxSize()) {
                if (give > 0) Box(Modifier.weight((give / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Palette.Info))
                if (get > 0) Box(Modifier.weight((get / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(color))
            }
            // The middle mark: an even trade splits the bar here.
            Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(Palette.Text))
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.s1)) {
            Text("Give", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            // Gold is too faint for text in light mode; the bar keeps it.
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (color == Palette.Gold) Palette.Text else color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("Get", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

@Composable
internal fun PickRow(title: String, sub: String?, image: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.s4).ruleBelow().padding(vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
        if (image != null) { CardImage(image, Modifier.width(36.dp)); Spacer(Modifier.width(Space.s3)) }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
        }
    }
}

package com.mtgscanner.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
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
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.Undo
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A card on one side of a trade. [owned] is set for cards you give away. */
data class TradeItem(val key: String, val name: String, val set: String, val imageUrl: String?, val price: Double?, val qty: Int, val owned: CollectionCard? = null, val card: ScryfallCard? = null) {
    /** What Card Kingdom pays for one copy, when known. */
    val buyPrice: Double? get() = owned?.let { CardKingdom.buyPrice(it.scryfallId, it.foil) } ?: card?.let { CardKingdom.buyPrice(it.id, false) }
}

data class TradeUi(
    val give: List<TradeItem> = emptyList(),
    val get: List<TradeItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
) {
    val giveValue: Double get() = give.sumOf { (it.price ?: 0.0) * it.qty }
    val getValue: Double get() = get.sumOf { (it.price ?: 0.0) * it.qty }
}

class TradeViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(TradeUi())
    val ui: StateFlow<TradeUi> = _ui
    private val binderId = MutableStateFlow(-1L)

    /** The trade binder's cards: what you can give. */
    val collection: StateFlow<List<CollectionCard>> = combine(repo.observeAll(), binderId) { all, id -> all.filter { it.boxId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val binderName: StateFlow<String> = combine(repo.observeBoxes(), binderId) { boxes, id -> boxes.firstOrNull { it.id == id }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init { CardKingdom.init(repo.appContext) }

    fun setBinder(id: Long) { binderId.value = id }
    private var suggestJob: Job? = null

    fun give(c: CollectionCard) {
        _ui.update { u ->
            if (u.give.any { it.owned?.id == c.id }) u
            else u.copy(give = u.give + TradeItem("o${c.id}", c.name, c.setCode, c.smallImageUrl, c.unitPrice(), 1, owned = c))
        }
        Haptics.tick()
    }

    /** Puts every matched want-list card on the give side, with at least the wanted count. */
    fun giveAll(matches: List<Pair<CollectionCard, Int>>) {
        _ui.update { u ->
            var give = u.give
            matches.forEach { (c, n) ->
                val qty = n.coerceAtMost(c.quantity)
                give = if (give.any { it.owned?.id == c.id }) give.map { if (it.owned?.id == c.id) it.copy(qty = maxOf(it.qty, qty)) else it }
                else give + TradeItem("o${c.id}", c.name, c.setCode, c.smallImageUrl, c.unitPrice(), qty, owned = c)
            }
            u.copy(give = give, message = "Added ${matches.sumOf { it.second }} to give")
        }
        Haptics.tick()
    }

    fun suggest(text: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(250)
            val s = runCatching { repo.api.autocomplete(text) }.getOrDefault(emptyList())
            _ui.update { it.copy(suggestions = s) }
        }
    }

    /** Makes every added row's key unique, even after rows are removed. */
    private var nextKey = 0L

    fun get(name: String) {
        _ui.update { it.copy(busy = true, suggestions = emptyList()) }
        viewModelScope.launch {
            val card = runCatching { repo.api.named(name) }.getOrNull()
            if (card != null) Haptics.tick()
            _ui.update { u ->
                if (card == null) u.copy(busy = false, message = "Couldn't find $name")
                else u.copy(
                    busy = false,
                    get = u.get + TradeItem("s${card.id}-${nextKey++}", card.name, card.set.uppercase(), card.smallImageUrl, card.prices.usd?.toDoubleOrNull(), 1, card = card),
                )
            }
        }
    }

    fun setQty(item: TradeItem, qty: Int) {
        _ui.update { u ->
            fun List<TradeItem>.upd() = if (qty <= 0) filterNot { it.key == item.key } else map { if (it.key == item.key) it.copy(qty = qty) else it }
            u.copy(give = u.give.upd(), get = u.get.upd())
        }
        if (qty <= 0) Haptics.tick()
    }

    /**
     * Takes given cards out of the collection and adds the received ones, auto-sorted into boxes.
     * Cards that fit no box go into this trade binder. Offers Undo for the whole trade.
     */
    fun apply() {
        val u = _ui.value
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching {
                // Work out every destination before changing anything.
                val binder = binderId.value.takeIf { it >= 0 && repo.box(it) != null }
                var leftover: Long? = binder
                var unsorted = 0
                val adds = u.get.mapNotNull { item ->
                    val c = item.card ?: return@mapNotNull null
                    val box = repo.pickBox(c, false) ?: run {
                        unsorted += item.qty
                        leftover ?: repo.anythingBox().also { leftover = it }
                    }
                    Triple(c, item.qty, box)
                }
                val gives = u.give.mapNotNull { g -> g.owned?.let { o -> o.id to minOf(g.qty, o.quantity) } }
                val before = repo.snapshot(gives.map { it.first })
                gives.forEach { (id, n) -> repo.remove(id, n) }
                val added = adds.map { (c, n, box) -> repo.add(c, false, n, box) to n }
                val where = leftover?.let { repo.box(it)?.name } ?: "a box"
                Undo.offer(if (unsorted > 0) "Trade saved. $unsorted fit no box, put in $where" else "Trade saved") {
                    added.forEach { (id, n) -> repo.remove(id, n) }
                    repo.restore(before)
                }
            }
            Haptics.tick()
            _ui.value = if (result.isSuccess) TradeUi() else u.copy(busy = false, message = "Couldn't save the trade")
        }
    }

    fun clear() = _ui.update { TradeUi() }
    fun messageShown() = _ui.update { it.copy(message = null) }
}

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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    Haptics.Attach()

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    if (binderName.isEmpty()) "Trade" else "Trade · $binderName",
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { wants = true }) { Text("Wants") }
                IconButton(onClick = onSellList) { Icon(Icons.Filled.Sell, "Sell list", tint = Palette.Text) }
                if (ui.give.isNotEmpty() || ui.get.isNotEmpty()) TextButton(onClick = vm::clear) { Text("Clear") }
            }
            if (AppSettings.showPrices && (ui.give.isNotEmpty() || ui.get.isNotEmpty())) {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp), color = Palette.Surface) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("You give ${formatUsd(ui.giveValue)}", style = MaterialTheme.typography.bodyMedium)
                                Text("You get ${formatUsd(ui.getValue)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            ChangeText(ui.getValue - ui.giveValue, ui.giveValue, MaterialTheme.typography.titleMedium)
                        }
                        if (ui.giveValue + ui.getValue > 0) {
                            Spacer(Modifier.height(10.dp))
                            FairnessMeter(ui.giveValue, ui.getValue)
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                item { SideHeader("You give", onAdd = { picking = "give" }) }
                items(ui.give, key = { it.key }) { TradeRow(it, vm) }
                item { SideHeader("You get", onAdd = { picking = "get" }) }
                items(ui.get, key = { it.key }) { TradeRow(it, vm) }
                if (ui.give.isEmpty() && ui.get.isEmpty()) {
                    item {
                        if (collection.isEmpty()) {
                            EmptyState(Icons.Filled.SwapHoriz, "Binder is empty", "Add what you'd get.", action = { GradientButton("Add card", { picking = "get" }, icon = Icons.Filled.Add) })
                        } else {
                            EmptyState(Icons.Filled.SwapHoriz, "Start a trade", "Add cards to each side.", action = { GradientButton("Pick from binder", { picking = "give" }, icon = Icons.Filled.Add) })
                        }
                    }
                }
            }
            GradientButton(
                "Apply to my collection",
                { confirm = true },
                Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                enabled = !ui.busy && (ui.give.isNotEmpty() || ui.get.isNotEmpty()),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (picking != null) {
        ModalBottomSheet(onDismissRequest = { picking = null }, containerColor = Palette.Surface) {
            var q by remember { mutableStateOf("") }
            Column(Modifier.padding(horizontal = 16.dp)) {
                SearchField(q, { q = it; if (picking == "get") vm.suggest(it) }, placeholder = if (picking == "give") "Search binder" else "Search any card")
            }
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                if (picking == "give") {
                    val matches = collection.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }.take(60)
                    if (matches.isEmpty()) item { Text(if (collection.isEmpty()) "Binder is empty" else "No match", color = Palette.Muted, modifier = Modifier.padding(20.dp)) }
                    items(matches, key = { it.id }) { c ->
                        PickRow(c.name, "${c.setCode} · ×${c.quantity}", c.smallImageUrl) { vm.give(c); picking = null }
                    }
                } else {
                    if (ui.suggestions.isEmpty()) item { Text("Type a card name", color = Palette.Muted, modifier = Modifier.padding(20.dp)) }
                    items(ui.suggestions) { name -> PickRow(name, null, null) { vm.get(name); picking = null } }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
    if (wants) WantListSheet(collection, onAddAll = { vm.giveAll(it); wants = false }, onDismiss = { wants = false })
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.Surface,
            title = { Text("Apply this trade?") },
            text = { Text("Updates your collection both ways.") },
            confirmButton = { TextButton(onClick = { vm.apply(); confirm = false }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
    if (ui.busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
}

@Composable
private fun SideHeader(title: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add card")
        }
    }
}

@Composable
private fun TradeRow(item: TradeItem, vm: TradeViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CardImage(item.imageUrl, Modifier.width(40.dp), corner = 4.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val prices = if (AppSettings.showPrices) " · ${formatUsd(item.price)}" + item.buyPrice?.let { " · CK buys ${formatUsd(it)}" }.orEmpty() else ""
            Text(item.set + prices, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        QuantityStepper(item.qty, onChange = { vm.setQty(item, it.coerceAtMost(item.owned?.quantity ?: 99)) })
        IconButton(onClick = { vm.setQty(item, 0) }) { Icon(Icons.Filled.Close, "Remove", tint = Palette.Muted) }
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
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Palette.SurfaceHigh)) {
            Row(Modifier.fillMaxSize()) {
                if (give > 0) Box(Modifier.weight((give / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Palette.Info))
                if (get > 0) Box(Modifier.weight((get / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(color))
            }
            // The middle mark: an even trade splits the bar here.
            Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(Palette.Text))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Give", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("Get", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

/** Paste a friend's want list and see which binder cards they want. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WantListSheet(binder: List<CollectionCard>, onAddAll: (List<Pair<CollectionCard, Int>>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val matches = remember(text, binder) {
        matchWants(DecklistParser.parse(text), binder, { it.name }, { it.setCode }, { it.quantity })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Their want list", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Paste list") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
            when {
                text.isBlank() -> item { Text("One card per line", color = Palette.Muted, modifier = Modifier.padding(20.dp)) }
                matches.isEmpty() -> item { Text("No matches in this binder", color = Palette.Muted, modifier = Modifier.padding(20.dp)) }
                else -> items(matches, key = { it.first.id }) { m ->
                    val c = m.first
                    val sub = "${c.setCode} · ×${m.second}" + if (AppSettings.showPrices) " · ${formatUsd(c.unitPrice())}" else ""
                    PickRow(c.name, sub, c.smallImageUrl) { onAddAll(listOf(m)) }
                }
            }
        }
        GradientButton(
            if (matches.isEmpty()) "Add all to give" else "Add all ${matches.sumOf { it.second }} to give",
            { onAddAll(matches) },
            Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            enabled = matches.isNotEmpty(),
        )
    }
}

@Composable
private fun PickRow(title: String, sub: String?, image: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (image != null) { CardImage(image, Modifier.width(36.dp), corner = 3.dp); Spacer(Modifier.width(10.dp)) }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
        }
    }
}

/** Plain-text list to share, e.g. a trade binder or wishlist. */
fun shareText(title: String, lines: List<Triple<Int, String, Double?>>): String = buildString {
    appendLine(title)
    lines.forEach { (n, name, price) ->
        append("$n× $name")
        if (AppSettings.showPrices && price != null) append(" — ${formatUsd(price)}")
        appendLine()
    }
    appendLine()
    append("Shared from Grimoire")
}

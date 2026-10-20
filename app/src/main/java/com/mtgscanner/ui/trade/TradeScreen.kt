package com.mtgscanner.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
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
data class TradeItem(val key: String, val name: String, val set: String, val imageUrl: String?, val price: Double?, val qty: Int, val owned: CollectionCard? = null, val card: ScryfallCard? = null)

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

    fun setBinder(id: Long) { binderId.value = id }
    private var suggestJob: Job? = null

    fun give(c: CollectionCard) = _ui.update { u ->
        if (u.give.any { it.owned?.id == c.id }) u
        else u.copy(give = u.give + TradeItem("o${c.id}", c.name, c.setCode, c.smallImageUrl, c.unitPrice(), 1, owned = c))
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
            _ui.update { u ->
                if (card == null) u.copy(busy = false, message = "Couldn't find $name")
                else u.copy(
                    busy = false,
                    get = u.get + TradeItem("s${card.id}-${nextKey++}", card.name, card.set.uppercase(), card.smallImageUrl, card.prices.usd?.toDoubleOrNull(), 1, card = card),
                )
            }
        }
    }

    fun setQty(item: TradeItem, qty: Int) = _ui.update { u ->
        fun List<TradeItem>.upd() = if (qty <= 0) filterNot { it.key == item.key } else map { if (it.key == item.key) it.copy(qty = qty) else it }
        u.copy(give = u.give.upd(), get = u.get.upd())
    }

    /** Takes given cards out of the collection and adds the received ones (auto-sorted into boxes). */
    fun apply() {
        val u = _ui.value
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            u.give.forEach { it.owned?.let { o -> repo.remove(o.id, minOf(it.qty, o.quantity)) } }
            var unsorted = 0
            u.get.forEach { item ->
                val c = item.card ?: return@forEach
                val box = repo.pickBox(c, false)
                if (box != null) repo.add(c, false, item.qty, box) else unsorted++
            }
            _ui.value = TradeUi(message = if (unsorted > 0) "Trade saved. $unsorted cards fit no box and weren't added." else "Trade saved to your collection")
        }
    }

    fun clear() = _ui.update { TradeUi() }
    fun messageShown() = _ui.update { it.copy(message = null) }
}

/** Trade from a trade binder: give cards from the binder, get any card, then apply it to your collection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(binderId: Long, onBack: () -> Unit) {
    val vm: TradeViewModel = viewModel(key = "trade$binderId", factory = AppViewModels.Factory)
    LaunchedEffect(binderId) { vm.setBinder(binderId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val collection by vm.collection.collectAsStateWithLifecycle()
    val binderName by vm.binderName.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<String?>(null) } // "give" or "get"
    var confirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    if (binderName.isEmpty()) "Trade" else "Trade · $binderName",
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (ui.give.isNotEmpty() || ui.get.isNotEmpty()) TextButton(onClick = vm::clear) { Text("Clear") }
            }
            if (AppSettings.showPrices && (ui.give.isNotEmpty() || ui.get.isNotEmpty())) {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp), color = Palette.Surface) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("You give ${formatUsd(ui.giveValue)}", style = MaterialTheme.typography.bodyMedium)
                            Text("You get ${formatUsd(ui.getValue)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        ChangeText(ui.getValue - ui.giveValue, ui.giveValue, MaterialTheme.typography.titleMedium)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                item { SideHeader("You give", onAdd = { picking = "give" }) }
                items(ui.give, key = { it.key }) { TradeRow(it, vm) }
                item { SideHeader("You get", onAdd = { picking = "get" }) }
                items(ui.get, key = { it.key }) { TradeRow(it, vm) }
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
                    items(matches, key = { it.id }) { c ->
                        PickRow(c.name, "${c.setCode} · ×${c.quantity}", c.smallImageUrl) { vm.give(c); picking = null }
                    }
                } else {
                    items(ui.suggestions) { name -> PickRow(name, null, null) { vm.get(name); picking = null } }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
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
            Text(item.set + if (AppSettings.showPrices) " · ${formatUsd(item.price)}" else "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        QuantityStepper(item.qty, onChange = { vm.setQty(item, it.coerceAtMost(item.owned?.quantity ?: 99)) })
        IconButton(onClick = { vm.setQty(item, 0) }) { Icon(Icons.Filled.Close, "Remove", tint = Palette.Muted) }
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
    append("Shared from MTG Scanner")
}

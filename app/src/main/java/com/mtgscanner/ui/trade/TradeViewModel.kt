package com.mtgscanner.ui.trade

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

/** A card on one side of a trade. [owned] is set for cards you give away. */
@kotlinx.serialization.Serializable
data class TradeItem(
    val key: String, val name: String, val set: String, val imageUrl: String?, val price: Double?, val qty: Int,
    val owned: CollectionCard? = null, val card: ScryfallCard? = null,
    /** For cards you get: the finish, condition and language they're added with. */
    val foil: Boolean = false, val condition: String = "NM", val lang: String = "en",
) {
    /** What Card Kingdom pays for one copy, when known. */
    val buyPrice: Double? get() = owned?.let { CardKingdom.buyPrice(it.scryfallId, it.foil) } ?: card?.let { CardKingdom.buyPrice(it.id, foil) }
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
    private val _printings = MutableStateFlow<List<ScryfallCard>?>(null)
    /** Printings of the card being edited on the get side; null while loading. */
    val printings: StateFlow<List<ScryfallCard>?> = _printings

    /** The trade binder's cards: what you can give. */
    val collection: StateFlow<List<CollectionCard>> = combine(repo.observeAll(), binderId) { all, id -> all.filter { it.boxId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val binderName: StateFlow<String> = combine(repo.observeBoxes(), binderId) { boxes, id -> boxes.firstOrNull { it.id == id }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The binder whose saved draft is loaded; changes are saved back only after that. */
    private var draftFor: Long? = null

    init {
        CardKingdom.init(repo.appContext)
        TradeDrafts.init(repo.appContext)
        // Keep the trade as it's built, so Back or leaving the app doesn't lose it.
        viewModelScope.launch {
            _ui.map { TradeDraft(it.give, it.get) }.distinctUntilChanged().collect { d ->
                draftFor?.let { TradeDrafts.put(it, d) }
            }
        }
    }

    fun setBinder(id: Long) {
        binderId.value = id
        if (draftFor == id) return
        val d = TradeDrafts.get(id)
        nextKey = System.currentTimeMillis()
        _ui.update { it.copy(give = d.give, get = d.get) }
        draftFor = id
    }
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
                    get = u.get + getItem("s${card.id}-${nextKey++}", card),
                )
            }
        }
    }

    private fun updateGet(key: String, change: (TradeItem) -> TradeItem) =
        _ui.update { u -> u.copy(get = u.get.map { if (it.key == key) change(it) else it }) }

    /** Sets foil, condition and language of a card you get. */
    fun setDetails(key: String, foil: Boolean, condition: String, lang: String) = updateGet(key) { it.withDetails(foil, condition, lang) }

    private var printingsJob: Job? = null

    fun loadPrintings(item: TradeItem) {
        val card = item.card ?: return
        _printings.value = null
        printingsJob?.cancel()
        printingsJob = viewModelScope.launch {
            val list = try {
                repo.api.printings(card)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "Can't load printings") }
                listOf(card)
            }
            _printings.value = list
        }
    }

    fun setPrinting(key: String, p: ScryfallCard) = updateGet(key) { it.withPrinting(p) }

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
                    val box = repo.pickBox(c, item.foil) ?: run {
                        unsorted += item.qty
                        leftover ?: repo.anythingBox().also { leftover = it }
                    }
                    Triple(c, item, box)
                }
                // A saved draft holds the give cards as they were: use what's in this binder now, and
                // skip any card that has since left it (moved, merged or deleted).
                val inBinder = repo.observeAll().first().filter { it.boxId == binderId.value }.associateBy { it.id }
                val gives = u.give.mapNotNull { g -> g.owned?.let { o -> inBinder[o.id]?.let { now -> o.id to minOf(g.qty, now.quantity) } } }
                    .filter { it.second > 0 }
                val gone = u.give.count { g -> g.owned != null && gives.none { it.first == g.owned.id } }
                if (gives.isEmpty() && adds.isEmpty()) return@runCatching "Those cards aren't in the binder now"
                val before = repo.snapshot(gives.map { it.first })
                val added = mutableListOf<Pair<Long, Int>>()
                try {
                    gives.forEach { (id, n) -> repo.remove(id, n) }
                    adds.forEach { (c, item, box) -> added += repo.add(c, item.foil, item.qty, box, item.lang, item.condition) to item.qty }
                } catch (e: Exception) {
                    // Half a trade is worse than none: put back what already changed.
                    runCatching {
                        added.forEach { (id, n) -> repo.remove(id, n) }
                        repo.restore(before)
                    }
                    throw e
                }
                val where = leftover?.let { repo.box(it)?.name } ?: "a box"
                val notes = listOfNotNull(
                    "$unsorted fit no box, put in $where".takeIf { unsorted > 0 },
                    "$gone no longer in the binder".takeIf { gone > 0 },
                )
                Undo.offer((listOf("Trade saved") + notes).joinToString(". ")) {
                    added.forEach { (id, n) -> repo.remove(id, n) }
                    repo.restore(before)
                }
                null
            }
            Haptics.tick()
            val problem = result.getOrNull()
            _ui.value = when {
                result.isFailure -> u.copy(busy = false, message = "Couldn't save the trade")
                problem != null -> u.copy(busy = false, message = problem)
                else -> TradeUi()
            }
        }
    }

    /** Empties both sides, with Undo. */
    fun clear() {
        val before = _ui.value
        if (before.give.isEmpty() && before.get.isEmpty()) return
        _ui.update { TradeUi() }
        Undo.offer("Trade cleared") {
            _ui.update { it.copy(give = before.give, get = before.get) }
            draftFor?.let { TradeDrafts.put(it, TradeDraft(before.give, before.get)) }
        }
    }
    fun messageShown() = _ui.update { it.copy(message = null) }
}

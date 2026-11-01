package com.mtgscanner.ui.detail

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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CardDetailViewModel(private val repo: CollectionRepository, id: Long) : ViewModel() {
    private val currentId = MutableStateFlow(id)
    /** (loaded from the database yet, the card or null when it's gone). */
    private val state: StateFlow<Pair<Boolean, CollectionCard?>> = currentId
        .flatMapLatest { rowId -> repo.observe(rowId).map { Pair<Boolean, CollectionCard?>(true, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Pair<Boolean, CollectionCard?>(false, null))
    val card: StateFlow<CollectionCard?> = state
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val loaded: StateFlow<Boolean> = state
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val boxes: StateFlow<List<CardBox>> = repo.observeBoxes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Boxes and decks' card rows, to name where the card is. */
    val places: StateFlow<List<CardBox>> = repo.observePlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Decks the card can be moved into. */
    val decks: StateFlow<List<Deck>> = repo.observeDecks()
        .map { ds -> ds.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moves [qty] copies (all of them by default) to a box, into a deck, or to the box Auto-sort picks. */
    fun moveToDest(d: MoveDest, qty: Int = Int.MAX_VALUE) = viewModelScope.launch {
        val c = card.value ?: return@launch
        if (qty < 1) return@launch
        val from = currentId.value
        val before = repo.snapshot(listOf(from))
        val target = d.boxId ?: d.deckId?.let { repo.deckSlotId(it) } ?: repo.autoBoxFor(c)
        val name = if (d.boxId == null && d.deckId == null) repo.box(target)?.name ?: "box" else d.name
        val all = qty >= c.quantity
        if (all) currentId.value = repo.moveToBox(from, target) else repo.moveCopiesMany(listOf(from to qty), target)
        Undo.offer(if (all) "Moved to $name" else "Moved $qty to $name") { repo.restore(before); currentId.value = from }
    }

    /** Scryfall's copy of the card, and whether fetching it failed (offline). */
    private val liveState: StateFlow<Pair<com.mtgscanner.data.ScryfallCard?, Boolean>> = card
        .map { it?.scryfallId }
        .distinctUntilChanged()
        // Clear at once on a new card so the old card's text never shows under it while loading.
        .transformLatest { id ->
            emit(Pair<com.mtgscanner.data.ScryfallCard?, Boolean>(null, false))
            if (id != null) {
                val got = runCatching { repo.api.byId(id) }.getOrNull()
                emit(Pair(got, got == null))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Pair<com.mtgscanner.data.ScryfallCard?, Boolean>(null, false))

    /** The card's full text from Scryfall (flavor, faces, every format); null while loading or offline. */
    val live: StateFlow<com.mtgscanner.data.ScryfallCard?> = liveState
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Scryfall couldn't be reached for this card. */
    val liveFailed: StateFlow<Boolean> = liveState
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val quantityLock = kotlinx.coroutines.sync.Mutex()

    /** Adds [delta] copies (never below 1), reading the stored count so fast taps all count. */
    fun changeQuantity(delta: Int) = viewModelScope.launch {
        if (delta == 0) return@launch
        quantityLock.withLock {
            val id = currentId.value
            val cur = repo.cardOnce(id)?.quantity ?: return@withLock
            val next = (cur + delta).coerceAtLeast(1)
            if (next != cur) repo.setQuantity(id, next)
        }
    }
    /**
     * Saves an edit that can fold this row into a matching one (same finish, condition, language...).
     * When it does, offers Undo so the rows can be pulled apart again.
     */
    private fun edit(change: suspend (CollectionCard) -> Long) = viewModelScope.launch {
        val c = card.value ?: return@launch
        val from = currentId.value
        val before = repo.snapshot(listOf(from))
        val id = change(c)
        currentId.value = id
        if (id != from) Undo.offer("Merged with matching copies") { repo.restore(before); currentId.value = from }
    }
    fun setFoil(foil: Boolean) = edit { repo.setFoil(currentId.value, foil) }
    fun setCondition(c: String) = edit { repo.update(it.copy(condition = c)) }
    fun setNotes(n: String) = edit { repo.update(it.copy(notes = n)) }
    fun setLang(l: String) = edit { repo.update(it.copy(lang = l)) }
    fun setSigned(on: Boolean) = edit { repo.update(it.copy(signed = on)) }
    fun setAltered(on: Boolean) = edit { repo.update(it.copy(altered = on)) }
    fun setGraded(g: String) = edit { repo.update(it.copy(graded = g.trim())) }
    fun delete(done: () -> Unit) = viewModelScope.launch {
        val before = repo.snapshot(listOf(currentId.value))
        val name = card.value?.name ?: "card"
        repo.delete(currentId.value)
        Undo.offer("Removed $name") { repo.restore(before) }
        done()
    }

    /** Daily prices recorded for this printing, oldest first. */
    val priceHistory: StateFlow<List<PricePoint>> = card
        .map { it?.scryfallId }
        .distinctUntilChanged()
        .flatMapLatest { sid -> if (sid == null) flowOf(emptyList()) else repo.observePriceHistory(sid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The whole collection, read once for the flows below that need it. */
    private val allRows = repo.observeAll().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Every tag used anywhere in the collection, for suggestions; only collected while the tag dialog is open. */
    val allTags: StateFlow<List<String>> = allRows
        .map { all -> all.flatMap { it.userTagList() }.distinct().sorted() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(tag: String) = viewModelScope.launch { repo.addTag(listOf(currentId.value), tag) }
    fun removeTag(tag: String) = viewModelScope.launch { repo.removeTag(currentId.value, tag) }

    fun setPaid(p: Double?) = viewModelScope.launch { card.value?.let { currentId.value = repo.update(it.copy(purchasePrice = p)) } }
    fun setFrom(f: String) = viewModelScope.launch { card.value?.let { currentId.value = repo.update(it.copy(acquiredFrom = f.trim().take(30))) } }
    fun setProxy(on: Boolean) = edit {
        val id = repo.update(it.copy(proxy = on))
        repo.logEvent(it.scryfallId, it.name, "edited", if (on) "Proxy" else "Not proxy")
        id
    }
    fun setForSale(on: Boolean) = viewModelScope.launch {
        card.value?.let {
            currentId.value = repo.update(it.copy(forSale = on))
            repo.logEvent(it.scryfallId, it.name, "edited", if (on) "For sale" else "Not for sale")
        }
    }
    fun setAsk(p: Double?) = viewModelScope.launch { card.value?.let { currentId.value = repo.update(it.copy(askPrice = p, forSale = it.forSale || p != null)) } }

    /** Saves the picture at [uri] as this copy's photo; [temp] is a camera target to delete afterwards. */
    fun attachPhoto(uri: android.net.Uri, temp: Boolean) = viewModelScope.launch {
        val ctx = repo.appContext
        val c = card.value
        val path = if (c != null) CardPhotos.save(ctx, uri, c.id) else null
        if (temp) CardPhotos.discard(ctx, uri)
        if (c == null || path == null) return@launch
        val fresh = repo.cardOnce(currentId.value) ?: c
        currentId.value = repo.update(fresh.copy(photo = path))
        val old = c.photo
        if (old.isNotBlank() && old != path) {
            // Moving some copies to another box copies the photo path, so another row can still use the file.
            val inUse = runCatching { repo.dao.byScryfallIds(listOf(c.scryfallId)).any { it.photo == old } }.getOrDefault(true)
            if (!inUse) CardPhotos.delete(ctx, old)
        }
    }
    fun discardTemp(uri: android.net.Uri) = viewModelScope.launch { CardPhotos.discard(repo.appContext, uri) }
    /** Clears the photo with Undo; the file itself is deleted only once Undo has had its time. */
    fun removePhoto() = viewModelScope.launch {
        val c = card.value ?: return@launch
        val path = c.photo
        if (path.isBlank()) return@launch
        repo.update(c.copy(photo = ""))
        Undo.offer("Photo removed") {
            // From Recent actions this can run after the file was deleted.
            if (!java.io.File(path).exists()) { Undo.offer("Photo is gone"); return@offer }
            val now = repo.cardOnce(currentId.value) ?: repo.cardOnce(c.id)
            if (now != null && now.photo.isBlank()) repo.update(now.copy(photo = path))
        }
        // Outlives this screen, so the file never lingers once nobody points at it.
        repo.appScope.launch {
            kotlinx.coroutines.delay(PHOTO_UNDO_MS)
            val inUse = runCatching { repo.dao.byScryfallIds(listOf(c.scryfallId)).any { it.photo == path } }.getOrDefault(true)
            if (!inUse) CardPhotos.delete(repo.appContext, path)
        }
    }

    /** Splits some copies off this row with another condition, finish or language, with Undo. */
    fun split(change: SplitChange) = viewModelScope.launch {
        val c = card.value ?: return@launch
        if (!SplitMath.valid(c, change)) return@launch
        val before = repo.snapshot(listOf(c.id))
        repo.splitCopies(c.id, change) ?: return@launch
        Undo.offer(SplitMath.message(c, change)) { repo.restore(before); currentId.value = c.id }
    }
    fun newCameraTarget(): android.net.Uri? = CardPhotos.cameraTarget(repo.appContext)

    /** Copies counted from a photo of the stack's edge, waiting to be confirmed; null when none. */
    val stackCount = kotlinx.coroutines.flow.MutableStateFlow<com.mtgscanner.scan.StackCountResult?>(null)
    /** Counting a stack photo, or it found no cards. */
    val stackCounting = kotlinx.coroutines.flow.MutableStateFlow(false)
    val stackFailed = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Counts the cards in a photo of a stack's edge ([com.mtgscanner.scan.StackCount]). [temp]: the camera's file, deleted after. */
    fun countStack(uri: android.net.Uri, temp: Boolean) = viewModelScope.launch {
        val ctx = repo.appContext
        stackCounting.value = true
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val bmp = CardPhotos.downscaled(ctx, uri, 2400) ?: return@runCatching null
                try {
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    com.mtgscanner.scan.StackCount.count(IntArray(px.size) { com.mtgscanner.scan.ImageHash.luma(px[it]) }, bmp.width, bmp.height)
                } finally {
                    bmp.recycle()
                }
            }.getOrNull()
        }
        if (temp) CardPhotos.discard(ctx, uri)
        stackCounting.value = false
        if (result == null) stackFailed.value = true else stackCount.value = result
    }

    /** Sets the copies to the stack count. */
    fun useStackCount() {
        val n = stackCount.value?.count ?: return
        stackCount.value = null
        val cur = card.value?.quantity ?: return
        changeQuantity(n - cur)
    }

    /** What happened to this printing: added, moved, edited, removed (newest first). */
    val events: StateFlow<List<CardEvent>> = card
        .map { it?.scryfallId }
        .distinctUntilChanged()
        .flatMapLatest { sid -> if (sid == null) flowOf(emptyList()) else repo.observeHistory(sid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Row ids in this card's box, in swipe order; only re-sorted when the collection or the box changes, not on edits to this card. */
    private val boxOrder: kotlinx.coroutines.flow.Flow<List<Long>> = combine(card.map { it?.boxId }.distinctUntilChanged(), allRows) { boxId, all ->
        if (boxId == null) emptyList() else boxOrderOf(boxId, all)
    }.flowOn(Dispatchers.Default).distinctUntilChanged()

    /** The other cards in the same box, in name order, for swiping. */
    val neighbors: StateFlow<Neighbors> = combine(card.map { it?.id }.distinctUntilChanged(), boxOrder) { id, order ->
        if (id == null) Neighbors() else neighborsIn(id, order)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Neighbors())

    fun go(id: Long) { currentId.value = id }

    /** Every printing of this card, fetched when the Versions tab opens. */
    private val _printings = MutableStateFlow(Fetched<List<com.mtgscanner.data.ScryfallCard>>())
    val printings: StateFlow<Fetched<List<com.mtgscanner.data.ScryfallCard>>> = _printings
    /** Set code (lowercase) to its symbol, for the Versions list. */
    private val _setIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    val setIcons: StateFlow<Map<String, String>> = _setIcons
    /** Official rulings, fetched when the Rules tab opens. */
    private val _rulings = MutableStateFlow(Fetched<List<com.mtgscanner.data.ScryfallRuling>>())
    val rulings: StateFlow<Fetched<List<com.mtgscanner.data.ScryfallRuling>>> = _rulings

    /** Every row of this card, any printing, found in one pass over the collection. */
    private val sameCardRows: StateFlow<List<CollectionCard>> = combine(
        card.map { c -> c?.let { it.oracleId to it.name } }.distinctUntilChanged(),
        allRows,
    ) { key, all ->
        if (key == null) emptyList<CollectionCard>()
        else all.filter { if (key.first != null) it.oracleId == key.first else it.name == key.second }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Copies owned of each printing of this card (by Scryfall id), across all boxes. */
    val ownedPrintings: StateFlow<Map<String, Int>> = sameCardRows
        .map { rows -> rows.groupBy { it.scryfallId }.mapValues { (_, r) -> r.sumOf { it.quantity } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** (box id, copies) for every row of this card, any printing: the "where are my copies" line. */
    val copyRows: StateFlow<List<Pair<Long, Int>>> = sameCardRows
        .map { rows -> rows.map { it.boxId to it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadPrintings(force: Boolean = false) {
        val c = card.value ?: return
        val key = c.oracleId ?: c.name
        val now = _printings.value
        if (!force && now.key == key && (now.loading || now.data != null)) return
        _printings.value = Fetched(key, loading = true)
        viewModelScope.launch {
            val r = runCatching { repo.api.printingsOf(c.oracleId, c.name) }
            if (_printings.value.key == key) _printings.value = Fetched(key, r.getOrNull(), failed = r.isFailure)
            if (_setIcons.value.isEmpty()) {
                runCatching { repo.sets() }.getOrNull()?.let { sets ->
                    _setIcons.value = sets.mapNotNull { s -> s.iconSvgUri?.let { s.code.lowercase() to it } }.toMap()
                }
            }
        }
    }

    fun loadRulings(force: Boolean = false) {
        val key = card.value?.scryfallId ?: return
        val now = _rulings.value
        if (!force && now.key == key && (now.loading || now.data != null)) return
        _rulings.value = Fetched(key, loading = true)
        viewModelScope.launch {
            val r = runCatching { repo.api.rulings(key) }
            if (_rulings.value.key == key) _rulings.value = Fetched(key, r.getOrNull(), failed = r.isFailure)
        }
    }

    /** Switches this copy to another printing of the same card, with undo. */
    fun changePrinting(p: com.mtgscanner.data.ScryfallCard) = viewModelScope.launch {
        val from = currentId.value
        val change = try {
            repo.changePrinting(from, p)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Undo.offer("Couldn't change printing")
            return@launch
        } ?: return@launch
        currentId.value = change.newId
        Undo.offer("Now ${p.set.uppercase()} #${p.collectorNumber}") {
            repo.restore(change.before)
            currentId.value = from
        }
    }
}

/** How long a removed photo's file is kept for Undo (the snackbar is short, about 4s). */
private const val PHOTO_UNDO_MS = 15_000L

data class Neighbors(val prev: Long? = null, val next: Long? = null, val index: Int = 0, val total: Int = 0)

/** Previous and next card in [c]'s box, by name (then set, number, foil). Pure, for tests. */
internal fun neighborsOf(c: CollectionCard, all: List<CollectionCard>): Neighbors = neighborsIn(c.id, boxOrderOf(c.boxId, all))

/** Row ids in box [boxId], by name (then set, number, foil). */
internal fun boxOrderOf(boxId: Long, all: List<CollectionCard>): List<Long> = all.filter { it.boxId == boxId }
    .sortedWith(compareBy<CollectionCard>({ it.name.lowercase() }, { it.setCode }, { it.collectorNumber }, { it.foil }, { it.id }))
    .map { it.id }

/** Previous and next of [id] in [order]. */
internal fun neighborsIn(id: Long, order: List<Long>): Neighbors {
    val i = order.indexOf(id)
    if (i < 0) return Neighbors()
    return Neighbors(order.getOrNull(i - 1), order.getOrNull(i + 1), i + 1, order.size)
}

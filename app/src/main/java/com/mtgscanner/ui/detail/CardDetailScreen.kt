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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.foilShimmer
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums

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
    fun setFoil(foil: Boolean) = viewModelScope.launch { currentId.value = repo.setFoil(currentId.value, foil) }
    fun setCondition(c: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(condition = c)) } }
    fun setNotes(n: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(notes = n)) } }
    fun setLang(l: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(lang = l)) } }
    fun setSigned(on: Boolean) = viewModelScope.launch { card.value?.let { repo.update(it.copy(signed = on)) } }
    fun setAltered(on: Boolean) = viewModelScope.launch { card.value?.let { repo.update(it.copy(altered = on)) } }
    fun setGraded(g: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(graded = g.trim())) } }
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

    /** Every tag used anywhere in the collection, for suggestions. */
    val allTags: StateFlow<List<String>> = repo.observeAll()
        .map { all -> all.flatMap { it.userTagList() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(tag: String) = viewModelScope.launch { repo.addTag(listOf(currentId.value), tag) }
    fun removeTag(tag: String) = viewModelScope.launch { repo.removeTag(currentId.value, tag) }

    fun setPaid(p: Double?) = viewModelScope.launch { card.value?.let { repo.update(it.copy(purchasePrice = p)) } }
    fun setFrom(f: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(acquiredFrom = f.trim().take(30))) } }
    fun setProxy(on: Boolean) = viewModelScope.launch {
        card.value?.let {
            repo.update(it.copy(proxy = on))
            repo.logEvent(it.scryfallId, it.name, "edited", if (on) "Proxy" else "Not proxy")
        }
    }
    fun setForSale(on: Boolean) = viewModelScope.launch {
        card.value?.let {
            repo.update(it.copy(forSale = on))
            repo.logEvent(it.scryfallId, it.name, "edited", if (on) "For sale" else "Not for sale")
        }
    }
    fun setAsk(p: Double?) = viewModelScope.launch { card.value?.let { repo.update(it.copy(askPrice = p, forSale = it.forSale || p != null)) } }

    /** Saves the picture at [uri] as this copy's photo; [temp] is a camera target to delete afterwards. */
    fun attachPhoto(uri: android.net.Uri, temp: Boolean) = viewModelScope.launch {
        val ctx = repo.appContext
        val c = card.value
        val path = if (c != null) CardPhotos.save(ctx, uri, c.id) else null
        if (temp) CardPhotos.discard(ctx, uri)
        if (c == null || path == null) return@launch
        val fresh = repo.cardOnce(currentId.value) ?: c
        repo.update(fresh.copy(photo = path))
        if (c.photo.isNotBlank() && c.photo != path) CardPhotos.delete(ctx, c.photo)
    }
    fun discardTemp(uri: android.net.Uri) = viewModelScope.launch { CardPhotos.discard(repo.appContext, uri) }
    /** Clears the photo with Undo; the file itself is deleted only once Undo has had its time. */
    fun removePhoto() = viewModelScope.launch {
        val c = card.value ?: return@launch
        val path = c.photo
        if (path.isBlank()) return@launch
        repo.update(c.copy(photo = ""))
        Undo.offer("Photo removed") {
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

    /** What happened to this printing: added, moved, edited, removed (newest first). */
    val events: StateFlow<List<CardEvent>> = card
        .map { it?.scryfallId }
        .distinctUntilChanged()
        .flatMapLatest { sid -> if (sid == null) flowOf(emptyList()) else repo.observeHistory(sid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Row ids in this card's box, in swipe order; only re-sorted when the collection or the box changes, not on edits to this card. */
    private val boxOrder: kotlinx.coroutines.flow.Flow<List<Long>> = combine(card.map { it?.boxId }.distinctUntilChanged(), repo.observeAll()) { boxId, all ->
        if (boxId == null) emptyList() else boxOrderOf(boxId, all)
    }.distinctUntilChanged()

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

    /** Copies owned of each printing of this card (by Scryfall id), across all boxes. */
    val ownedPrintings: StateFlow<Map<String, Int>> = combine(card, repo.observeAll()) { c, all ->
        if (c == null) emptyMap<String, Int>()
        else all.filter { if (c.oracleId != null) it.oracleId == c.oracleId else it.name == c.name }
            .groupBy { it.scryfallId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** (box id, copies) for every row of this card, any printing: the "where are my copies" line. */
    val copyRows: StateFlow<List<Pair<Long, Int>>> = combine(card, repo.observeAll()) { c, all ->
        if (c == null) emptyList<Pair<Long, Int>>()
        else all.filter { if (c.oracleId != null) it.oracleId == c.oracleId else it.name == c.name }.map { it.boxId to it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

private val LANGUAGES = listOf(
    "en" to "English", "ja" to "Japanese", "de" to "German", "fr" to "French", "it" to "Italian", "es" to "Spanish",
    "pt" to "Portuguese", "ru" to "Russian", "ko" to "Korean", "zhs" to "Chinese (S)", "zht" to "Chinese (T)",
)

private val CONDITIONS = listOf("NM" to "Near mint", "LP" to "Lightly played", "MP" to "Moderately played", "HP" to "Heavily played", "DMG" to "Damaged")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(id: Long, onBack: () -> Unit) {
    val vm: CardDetailViewModel = viewModel(key = "card-$id", factory = AppViewModels.detail(id))
    val card by vm.card.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    val history by vm.priceHistory.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    var moveTarget by remember { mutableStateOf<MoveDest?>(null) }
    var editingNotes by remember { mutableStateOf(false) }
    var editingGraded by remember { mutableStateOf(false) }
    var editingPaid by remember { mutableStateOf(false) }
    var editingAsk by remember { mutableStateOf(false) }
    var editingFrom by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    var lending by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf(false) }
    var showAllEvents by remember { mutableStateOf(false) }
    val events by vm.events.collectAsStateWithLifecycle()
    val nb by vm.neighbors.collectAsStateWithLifecycle()
    val copyRows by vm.copyRows.collectAsStateWithLifecycle()
    val c = card
    // For the "Recently opened" strip on Browse.
    LaunchedEffect(c?.id) { c?.id?.let { com.mtgscanner.ui.collection.RecentCards.opened(ctx, it) } }

    // Photo of this copy: camera (Android 10+) or the photo picker; both are copied into app storage.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        cameraUri = null
        if (u != null) { if (ok) vm.attachPhoto(u, temp = true) else vm.discardTemp(u) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attachPhoto(uri, temp = false)
    }
    val startCamera: () -> Unit = {
        val u = vm.newCameraTarget()
        if (u != null) {
            cameraUri = u
            if (runCatching { takePhoto.launch(u) }.isFailure) { cameraUri = null; vm.discardTemp(u) }
        }
    }
    val startPicker: () -> Unit = {
        runCatching { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    // Swipe left/right for the next/previous card in the same box (name order).
    val nbNow by rememberUpdatedState(nb)
    var dragX by remember { mutableFloatStateOf(0f) }
    val swipe = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val limit = 72.dp.toPx()
                val target = when {
                    dragX < -limit -> nbNow.next
                    dragX > limit -> nbNow.prev
                    else -> null
                }
                dragX = 0f
                if (target != null) vm.go(target)
            },
            onDragCancel = { dragX = 0f },
        ) { change, amount ->
            change.consume()
            dragX += amount
        }
    }
    // A row briefly disappears while it merges into another box; only call it missing if it stays gone.
    var showMissing by remember { mutableStateOf(false) }
    LaunchedEffect(c == null, loaded) {
        showMissing = false
        if (c == null && loaded) {
            kotlinx.coroutines.delay(600)
            showMissing = true
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (c == null) {
            if (showMissing) {
                EmptyState("Card not found", Modifier.align(Alignment.Center), action = "Back", onAction = onBack)
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Accent)
            }
        }
        if (c != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(swipe)
                    .graphicsLayer { translationX = dragX * 0.4f }
                    .verticalScroll(rememberScrollState()),
            ) {
                // Only the card opened grows back into the picture it came from, not one swiped to.
                Hero(c, sharedId = if (c.id == id) id else 0L, onPhoto = { viewingPhoto = true })
                Column(Modifier.padding(horizontal = 16.dp).offset(y = (-12).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Hold the name to copy it.
                        Text(
                            c.name, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).pointerInput(c.name) {
                                detectTapGestures(onLongPress = {
                                    com.mtgscanner.util.Haptics.tick()
                                    com.mtgscanner.util.CopyText.copy(ctx, c.name)
                                })
                            },
                        )
                        ManaCost(c.manaCost, size = 20.dp)
                    }
                    // Where every copy is, when there's more than this row: "3 copies: Rares ×2, Atraxa deck ×1".
                    val copiesLine = remember(copyRows, places, decks) {
                        val deckNames = decks.associate { it.id to it.name }
                        val spots = places.associate { b ->
                            val spot = if (b.isDeckSlot) com.mtgscanner.data.CopiesLine.Spot(deckNames[b.deckId] ?: b.name, deck = true)
                            else com.mtgscanner.data.CopiesLine.Spot(b.name)
                            b.id to spot
                        }
                        com.mtgscanner.data.CopiesLine.text(copyRows, spots)
                    }
                    copiesLine?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Special-copy badges first, so they're seen next to the name.
                        if (c.lang.isNotBlank() && !c.lang.equals("en", ignoreCase = true)) Badge(c.lang.uppercase())
                        if (c.signed) Badge("Signed")
                        if (c.altered) Badge("Altered")
                        if (c.graded.isNotBlank()) Badge(c.graded)
                        if (c.proxy) InfoChip { Text("Proxy", style = MaterialTheme.typography.labelMedium, color = Palette.Danger) }
                        com.mtgscanner.ui.loans.loanLabel(c)?.let { Badge(it) }
                        if (c.forSale) Badge(if (c.askPrice != null && AppSettings.showPrices) "For sale ${formatUsd(c.askPrice)}" else "For sale")
                        InfoChip { RarityDot(c.rarity, 8.dp); Spacer(Modifier.width(6.dp)); Text(c.rarity.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium) }
                        InfoChip { Text("${c.setName} · #${c.collectorNumber}", style = MaterialTheme.typography.labelMedium) }
                        c.releasedAt?.let { InfoChip { Text(it.take(4), style = MaterialTheme.typography.labelMedium) } }
                        if (AppSettings.showPrices) InfoChip { Text(cardPrice(c), style = MaterialTheme.typography.labelMedium, color = Palette.AccentText) }
                        InfoChip {
                            Text(
                                listOf("Added ${shortDate(c.addedAt)}", c.acquiredFrom).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                            )
                        }
                    }

                    // Compact editing block: quantity, foil, condition, box, notes and tags.
                    Spacer(Modifier.height(10.dp))
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuantityStepper(c.quantity, onChange = { vm.changeQuantity(it - c.quantity) })
                                Spacer(Modifier.weight(1f))
                                SelectPill("Foil", c.foil, onClick = { vm.setFoil(!c.foil) })
                                ConditionPicker(c.condition) { vm.setCondition(it) }
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val inDeck = places.any { it.id == c.boxId && it.isDeckSlot }
                                if (boxes.size > 1 || decks.isNotEmpty() || inDeck) BoxPicker(boxes, decks, places, c.boxId) { d ->
                                    if (c.quantity > 1) moveTarget = d else vm.moveToDest(d)
                                }
                                LangPicker(c.lang) { vm.setLang(it) }
                                SpecialPicker(
                                    c,
                                    onSigned = { vm.setSigned(it) },
                                    onAltered = { vm.setAltered(it) },
                                    onGraded = { editingGraded = true },
                                    onProxy = { vm.setProxy(it) },
                                    onForSale = { vm.setForSale(it) },
                                    onAsk = { editingAsk = true },
                                )
                                MorePicker(
                                    c,
                                    onPaid = { editingPaid = true },
                                    onFrom = { editingFrom = true },
                                    onCamera = if (CardPhotos.canUseCamera) startCamera else null,
                                    onPick = startPicker,
                                    onRemovePhoto = { vm.removePhoto() },
                                    onSplit = if (c.quantity > 1) ({ splitting = true }) else null,
                                    onLend = { lending = true },
                                )
                                DropChip(
                                    if (c.notes.isBlank()) "Notes" else c.notes.lineSequence().first().take(18),
                                    onClick = { editingNotes = true },
                                    leading = { Icon(Icons.Filled.Edit, null, Modifier.size(16.dp), tint = Palette.Icon) },
                                    trailing = false,
                                )
                                DropChip(
                                    "Tag",
                                    onClick = { addingTag = true },
                                    leading = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = Palette.Icon) },
                                    trailing = false,
                                )
                            }
                            val userTags = c.userTagList()
                            if (userTags.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    userTags.forEach { t ->
                                        Row(
                                            Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh).padding(start = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(t, style = MaterialTheme.typography.labelMedium, color = Palette.AccentText)
                                            IconButton(onClick = { vm.removeTag(t) }) {
                                                Icon(Icons.Filled.Close, "Remove tag $t", Modifier.size(16.dp), tint = Palette.Icon)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Where its box sits on the shelf, when the shelf is laid out.
                    com.mtgscanner.ui.collection.ShelfSpot(boxes, setOf(c.boxId), Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(10.dp))
                    // Legality lives in the Rules tab below, with every format.
                    CardTextBlock((live?.textInfo() ?: c.textInfo()).copy(legalities = emptyMap()))

                    Spacer(Modifier.height(12.dp))
                    if (AppSettings.showPrices) Panel {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Price each", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text(cardPrice(c), style = MaterialTheme.typography.headlineMedium, color = Palette.AccentText)
                                if (c.conditionPrice != null && c.condition != "NM" && c.nearMintPrice() != null) {
                                    Text("${c.condition} · near mint ${formatUsd(c.nearMintPrice())}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                }
                                val change = c.changeSinceAdded()
                                val was = c.priceWhenAdded()
                                if (change != null && was != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ChangeText(change, was, MaterialTheme.typography.labelMedium)
                                        Text(
                                            if (c.purchasePrice != null) " vs paid ${formatUsd(c.purchasePrice)}" else " since added",
                                            style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("You own", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text("${c.quantity} · ${cardTotal(c)}", style = MaterialTheme.typography.titleMedium.merge(TabularNums))
                            }
                        }
                        PriceAlertRow(c)
                    }

                    val points = history.mapNotNull { p -> (if (c.foil) p.usdFoil ?: p.usd else p.usd)?.let { p.day to it } }
                    if (AppSettings.showPrices && points.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Price history", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                                ChangeText(points.last().second - points.first().second, points.first().second, MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(10.dp))
                            LineChart(points.map { it.second }, Modifier.fillMaxWidth().height(80.dp))
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Text(shortDay(points.first().first), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                                Text(
                                    "low ${formatUsd(points.minOf { it.second })} · high ${formatUsd(points.maxOf { it.second })}",
                                    style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(2f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Text(shortDay(points.last().first), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    } else if (AppSettings.showPrices) {
                        Text(
                            "Price chart appears after a few days.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    CardInfoTabs(c, live, vm)

                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip {
                            Text("Identity ", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            ManaCost(identityCost(c.colorIdentity), size = 18.dp)
                        }
                    }

                    val tags = c.tagList()
                    if (tags.any { !it.startsWith("legal:") }) {
                        Spacer(Modifier.height(12.dp))
                        Text("Tags", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.filterNot { it.startsWith("legal:") }.forEach { InfoChip { Text(it, style = MaterialTheme.typography.labelMedium) } }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("History", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Spacer(Modifier.height(6.dp))
                    if (events.isEmpty()) {
                        Text("Nothing yet.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    } else {
                        val shown = if (showAllEvents) events else events.take(4)
                        shown.forEach { e -> EventRow(e) }
                        if (events.size > 4) {
                            TextButton(onClick = { showAllEvents = !showAllEvents }) {
                                Text(if (showAllEvents) "Less" else "All ${events.size}")
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // Scryfall, then EDHREC and the two stores (Scryfall's own links once it has answered).
                    val links = remember(c.name, c.scryfallUri, live) {
                        listOf(com.mtgscanner.data.CardLinks.Link("Scryfall", c.scryfallUri)).filter { it.url.isNotBlank() } +
                            com.mtgscanner.data.CardLinks.of(c.name, live?.relatedUris.orEmpty(), live?.purchaseUris.orEmpty())
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        links.forEach { link ->
                            TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) } }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(link.label)
                            }
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(16.dp))
                }
            }
        }

        // Floating top buttons over the artwork.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            val btn = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White)
            IconButton(onClick = onBack, colors = btn) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (c != null && nb.total > 1) {
                Text(
                    "${nb.index} / ${nb.total}",
                    style = MaterialTheme.typography.labelMedium, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .clip(RoundedCornerShape(Corners.Small))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
            }
            if (c != null) IconButton(onClick = { confirmDelete = true }, colors = btn) { Icon(Icons.Filled.DeleteOutline, "Remove from collection") }
        }
    }

    moveTarget?.let { target ->
        val have = c?.quantity ?: 1
        var count by remember(target, have) { mutableIntStateOf(have) }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            containerColor = Palette.Surface,
            title = { Text("Move how many?") },
            text = {
                Column {
                    Text("To ${target.name}", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuantityStepper(count, onChange = { count = it.coerceIn(1, have) })
                        Spacer(Modifier.width(12.dp))
                        Text("of $have", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.moveToDest(target, count); moveTarget = null }) { Text("Move") } },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } },
        )
    }

    if (editingNotes && c != null) NotesDialog(c.notes, onSave = { vm.setNotes(it); editingNotes = false }, onDismiss = { editingNotes = false })

    if (editingGraded && c != null) GradedDialog(c.graded, onSave = { vm.setGraded(it); editingGraded = false }, onDismiss = { editingGraded = false })

    if (editingPaid && c != null) AmountDialog("Paid each", c.purchasePrice, onSave = { vm.setPaid(it); editingPaid = false }, onDismiss = { editingPaid = false })

    if (editingAsk && c != null) AmountDialog("Asking price", c.askPrice, onSave = { vm.setAsk(it); editingAsk = false }, onDismiss = { editingAsk = false })

    if (editingFrom && c != null) FromDialog(c.acquiredFrom, onSave = { vm.setFrom(it); editingFrom = false }, onDismiss = { editingFrom = false })

    if (viewingPhoto && c != null && c.photo.isNotBlank()) {
        PhotoViewer(
            c.photo,
            onRemove = { viewingPhoto = false; vm.removePhoto() },
            onReplace = { viewingPhoto = false; if (CardPhotos.canUseCamera) startCamera() else startPicker() },
            onDismiss = { viewingPhoto = false },
        )
    }

    if (splitting && c != null && c.quantity > 1) {
        SplitDialog(c, onSplit = { vm.split(it); splitting = false }, onDismiss = { splitting = false })
    }
    if (lending && c != null) {
        com.mtgscanner.ui.loans.LendDialog(c, onDismiss = { lending = false })
    }

    if (addingTag) TagDialog(allTags, onDone = { vm.addTag(it); addingTag = false }, onDismiss = { addingTag = false })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.Surface,
            title = { Text("Remove ${card?.name ?: "card"}?") },
            text = { Text("This removes all ${card?.quantity ?: 0} copies from your collection.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Remove", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Blurred artwork backdrop with the card itself floating on top. */
@Composable
private fun Hero(c: CollectionCard, sharedId: Long, onPhoto: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = artCropUrl(c.imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp) else Modifier),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Palette.Background.copy(alpha = 0.6f), Palette.Background))))
        CardImage(
            c.imageUrl,
            Modifier.align(Alignment.Center).statusBarsPadding().padding(top = 8.dp, bottom = 20.dp).height(170.dp)
                .sharedCard(sharedId)
                .foilShimmer(c.foil),
            zoom = true,
        )
        // Your own copy's photo, small in the corner; tap to view.
        if (c.photo.isNotBlank()) {
            AsyncImage(
                model = java.io.File(c.photo),
                contentDescription = "Your photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(Corners.Small))
                    .background(Palette.SurfaceHigh)
                    .clickable(onClick = onPhoto),
            )
        }
    }
}

@Composable
private fun PhotoViewer(path: String, onRemove: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
            Column(Modifier.padding(8.dp)) {
                AsyncImage(
                    model = java.io.File(path),
                    contentDescription = "Your photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(Corners.Small)).clickable(onClick = onDismiss),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRemove) { Text("Remove", color = Palette.Danger) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onReplace) { Text("Replace") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

/** Purchase price, where it came from and a photo, behind one small menu. */
@Composable
private fun MorePicker(
    c: CollectionCard,
    onPaid: () -> Unit,
    onFrom: () -> Unit,
    onCamera: (() -> Unit)?,
    onPick: () -> Unit,
    onRemovePhoto: () -> Unit,
    /** Change condition, finish or language for some copies; null with only one copy. */
    onSplit: (() -> Unit)? = null,
    onLend: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        DropChip("More", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onSplit != null) DropdownMenuItem(
                text = { Text("Split copies…") },
                onClick = { open = false; onSplit() },
            )
            if (onLend != null) DropdownMenuItem(
                text = { Text("Lend…") },
                onClick = { open = false; onLend() },
            )
            DropdownMenuItem(
                text = { Text(if (c.purchasePrice == null) "Paid…" else "Paid: ${formatUsd(c.purchasePrice)}", color = if (c.purchasePrice != null) Palette.AccentText else Palette.Text) },
                onClick = { open = false; onPaid() },
            )
            DropdownMenuItem(
                text = { Text(if (c.acquiredFrom.isBlank()) "From…" else "From: ${c.acquiredFrom}", color = if (c.acquiredFrom.isNotBlank()) Palette.AccentText else Palette.Text) },
                onClick = { open = false; onFrom() },
            )
            if (onCamera != null) DropdownMenuItem(
                text = { Text(if (c.photo.isBlank()) "Take photo" else "Retake photo") },
                onClick = { open = false; onCamera() },
            )
            DropdownMenuItem(
                text = { Text(if (c.photo.isBlank()) "Pick photo" else "Pick new photo") },
                onClick = { open = false; onPick() },
            )
            if (c.photo.isNotBlank()) DropdownMenuItem(
                text = { Text("Remove photo", color = Palette.Danger) },
                onClick = { open = false; onRemovePhoto() },
            )
        }
    }
}

/** Pick how many copies and what's different about them; they become their own row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitDialog(c: CollectionCard, onSplit: (SplitChange) -> Unit, onDismiss: () -> Unit) {
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
                    Spacer(Modifier.width(12.dp))
                    Text("of ${c.quantity}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Muted)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CONDITIONS.forEach { (code, _) -> SelectPill(code, condition == code, onClick = { condition = code }) }
                    SelectPill("Foil", foil, onClick = { foil = !foil })
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    val t = text.trim().removePrefix("$").trim().replace(',', '.')
    if (t.isEmpty()) return true to null
    val v = t.toDoubleOrNull() ?: return false to null
    return if (v < 0) (false to null) else (true to v)
}

@Composable
private fun AmountDialog(title: String, initial: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
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
private fun FromDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("From") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SOURCES.forEach { s -> SelectPill(s, text.equals(s, ignoreCase = true), onClick = { text = if (text.equals(s, ignoreCase = true)) "" else s }) }
                }
                Spacer(Modifier.height(10.dp))
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
private fun EventRow(e: CardEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            e.kind.replaceFirstChar(Char::uppercase),
            style = MaterialTheme.typography.labelMedium, color = Palette.AccentText,
            modifier = Modifier.width(64.dp),
        )
        Text(e.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
        Text(shortDate(e.at), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/** A timestamp as "Mar 3, 2025". */
internal fun shortDate(millis: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))
}.getOrDefault("")

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoChip(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun NotesDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
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
        title = { Text("Discard changes?") },
        confirmButton = { TextButton(onClick = { leaving = false; onDismiss() }) { Text("Discard", color = Palette.Danger) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep editing") } },
    )
}

/** A compact pill that opens a menu or dialog. */
@Composable
private fun DropChip(label: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, trailing: Boolean = true) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(Corners.Small))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (trailing) Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = Palette.Icon)
    }
}

@Composable
private fun Badge(text: String) {
    InfoChip { Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.AccentText) }
}

@Composable
private fun LangPicker(lang: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = lang.ifBlank { "en" }.lowercase()
    Box {
        DropChip(current.uppercase(), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LANGUAGES.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text("${code.uppercase()} · $label", color = if (code == current) Palette.AccentText else Palette.Text) },
                    onClick = { open = false; if (code != current) onPick(code) },
                )
            }
        }
    }
}

/** Signed, altered and graded, in one small menu. */
@Composable
private fun SpecialPicker(
    c: CollectionCard,
    onSigned: (Boolean) -> Unit,
    onAltered: (Boolean) -> Unit,
    onGraded: () -> Unit,
    onProxy: (Boolean) -> Unit,
    onForSale: (Boolean) -> Unit,
    onAsk: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val set = listOfNotNull(
        "Proxy".takeIf { c.proxy }, "Signed".takeIf { c.signed }, "Altered".takeIf { c.altered },
        c.graded.takeIf { it.isNotBlank() }, "Sale".takeIf { c.forSale },
    )
    Box {
        DropChip(if (set.isEmpty()) "Special" else set.joinToString(" · ").take(18), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Signed", color = if (c.signed) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.signed) Icon(Icons.Filled.Check, null, tint = Palette.AccentText) },
                onClick = { open = false; onSigned(!c.signed) },
            )
            DropdownMenuItem(
                text = { Text("Altered", color = if (c.altered) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.altered) Icon(Icons.Filled.Check, null, tint = Palette.AccentText) },
                onClick = { open = false; onAltered(!c.altered) },
            )
            DropdownMenuItem(
                text = { Text(if (c.graded.isBlank()) "Graded…" else "Graded: ${c.graded}", color = if (c.graded.isNotBlank()) Palette.AccentText else Palette.Text) },
                onClick = { open = false; onGraded() },
            )
            DropdownMenuItem(
                text = { Text("Proxy", color = if (c.proxy) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.proxy) Icon(Icons.Filled.Check, null, tint = Palette.AccentText) },
                onClick = { open = false; onProxy(!c.proxy) },
            )
            DropdownMenuItem(
                text = { Text("For sale", color = if (c.forSale) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.forSale) Icon(Icons.Filled.Check, null, tint = Palette.AccentText) },
                onClick = { open = false; onForSale(!c.forSale) },
            )
            DropdownMenuItem(
                text = { Text(if (c.askPrice == null) "Ask price…" else "Ask: ${formatUsd(c.askPrice)}", color = if (c.askPrice != null) Palette.AccentText else Palette.Text) },
                onClick = { open = false; onAsk() },
            )
        }
    }
}

@Composable
private fun GradedDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
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

@Composable
private fun ConditionPicker(condition: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        DropChip(condition, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CONDITIONS.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text("$code · $label", color = if (code == condition) Palette.AccentText else Palette.Text) },
                    onClick = { open = false; onPick(code) },
                )
            }
        }
    }
}

/** Where to move a card: a box ([boxId]), a deck ([deckId]), or neither for the box Auto-sort picks. */
data class MoveDest(val name: String, val boxId: Long? = null, val deckId: Long? = null)

/** Where the card is, and where it can go: boxes, then decks in their own group. Out of a deck, Auto-sort comes first. */
@Composable
private fun BoxPicker(boxes: List<CardBox>, decks: List<Deck>, places: List<CardBox>, current: Long, onPick: (MoveDest) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val here = places.firstOrNull { it.id == current }
    val inDeck = here?.isDeckSlot == true
    Box {
        DropChip(
            here?.placeName() ?: "Box",
            onClick = { open = true },
            leading = { Icon(boxIcon(here?.kind ?: "box"), null, Modifier.size(16.dp), tint = Palette.Icon) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (inDeck) {
                DropdownMenuItem(
                    text = { Text("Auto-sort") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Palette.Icon) },
                    onClick = { open = false; onPick(MoveDest("Auto-sort")) },
                )
            }
            boxes.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name, color = if (b.id == current) Palette.AccentText else Palette.Text) },
                    leadingIcon = { Icon(boxIcon(b.kind), null, tint = if (b.id == current) Palette.AccentText else Palette.Icon) },
                    onClick = { open = false; if (b.id != current) onPick(MoveDest(b.name, boxId = b.id)) },
                )
            }
            if (decks.isNotEmpty()) {
                HorizontalDivider(color = Palette.Outline)
                Text(
                    "Decks", style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                decks.forEach { d ->
                    val mine = inDeck && here?.deckId == d.id
                    DropdownMenuItem(
                        text = { Text(d.name, color = if (mine) Palette.AccentText else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(boxIcon("deck"), null, tint = if (mine) Palette.AccentText else Palette.Icon) },
                        onClick = { open = false; if (!mine) onPick(MoveDest(d.name, deckId = d.id)) },
                    )
                }
            }
        }
    }
}

/** Color identity as mana symbols, e.g. "WU" becomes "{W}{U}"; colorless is "{C}". */
internal fun identityCost(identity: String): String {
    val letters = identity.filter { it.uppercaseChar() in "WUBRG" }
    return if (letters.isEmpty()) "{C}" else letters.map { "{${it.uppercaseChar()}}" }.joinToString("")
}

private fun shortDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}.getOrDefault(day)

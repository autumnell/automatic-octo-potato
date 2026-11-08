package com.mtgscanner.ui.scan

import com.mtgscanner.data.matchPicture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.CardFacts
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.OfflineRead
import com.mtgscanner.data.ScanBarPrefs
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallException
import com.mtgscanner.data.SortDecision
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.data.SortReason
import com.mtgscanner.data.addToDeckList
import com.mtgscanner.data.alsoFits
import com.mtgscanner.data.facts
import com.mtgscanner.data.identify
import com.mtgscanner.data.cardById
import com.mtgscanner.data.offlineRead
import com.mtgscanner.data.ownedCopies
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.ImageHash
import com.mtgscanner.scan.FrameLight
import com.mtgscanner.scan.LightCheck
import com.mtgscanner.scan.PendingScanWork
import com.mtgscanner.scan.ScanPrefs
import com.mtgscanner.scan.ScanSettings
import com.mtgscanner.scan.ScanSound
import com.mtgscanner.scan.UnsureAction
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.data.copiesOf
import com.mtgscanner.data.keptIn
import com.mtgscanner.data.rowToRemove
import com.mtgscanner.scan.FramePool
import com.mtgscanner.scan.LeaveTracker
import com.mtgscanner.scan.sameCardInView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Binder page mode and photo scans, for ScanViewModel. The jobs they run are kept in the view model.

fun ScanViewModel.setPageMode(on: Boolean) {
    pageJob?.cancel()
    _state.update {
        it.copy(pageMode = on, wantPage = false, pageLoading = false, page = null, current = null, awaiting = false, wish = null,
            status = if (on) "Binder page in the frame, or pick a photo" else "Point your camera at a card")
    }
    resetTracking()
}

/** Reads the next camera frame as a whole page. */
fun ScanViewModel.capturePage() = _state.update { it.copy(wantPage = true, paused = false, status = "Hold still…") }

/** Stops waiting for a page read, if it's taking too long. */
fun ScanViewModel.cancelCapture() = _state.update {
    pageJob?.cancel()
    it.copy(wantPage = false, wantSpread = false, pageLoading = false, status = "Binder page in the frame, or pick a photo")
}

/** Called by the analyzer with one guess per pocket, left to right, top to bottom. */
fun ScanViewModel.onPage(guesses: List<CardTextGuess>) {
    _state.update { it.copy(wantPage = false, pageLoading = true, status = "Looking up the page…") }
    pageJob?.cancel()
    pageJob = viewModelScope.launch {
        val slots = guesses.mapIndexed { i, g ->
            async {
                val card = if (g.key == null) null else runCatching { repo.identify(g)?.card }.getOrNull()
                PageSlot(i, readLabel(g), card, card != null, lang = g.lang)
            }
        }.awaitAll()
        val found = slots.count { it.card != null }
        _state.update {
            it.copy(
                pageLoading = false,
                page = slots,
                status = if (found == 0) "Couldn't read that page · try more light, or get closer" else "Found $found of ${slots.size}",
            )
        }
    }
}

fun ScanViewModel.togglePageSlot(index: Int) = _state.update {
    it.copy(page = it.page?.map { s -> if (s.index == index && s.card != null) s.copy(selected = !s.selected) else s })
}

fun ScanViewModel.closePage() = _state.update { it.copy(page = null, status = "Binder page in the frame, or pick a photo") }

/** Adds the ticked pockets, with the scan bar's copies, one after another so box capacity stays right. */
fun ScanViewModel.addPage() {
    val st = _state.value
    val slots = st.page.orEmpty().filter { it.selected && it.card != null }
    _state.update { it.copy(page = null) }
    // Price check: the page was only looked at.
    if (st.target == ScanTarget.CHECK) {
        _state.update { it.copy(status = "Turn the page and capture again") }
        return
    }
    val qty = ScanBarPrefs.quantity
    viewModelScope.launch {
        val added = mutableListOf<Pair<Long, Int>>()
        slots.forEach { slot ->
            val card = slot.card ?: return@forEach
            val lang = (slot.lang?.takeIf { card.lang == "en" }) ?: ScanBarPrefs.langOrNull
            val now = _state.value
            addNow(card, qty, now, now.isFoil(card), lang, fromReview = false, auto = true, fromPage = true)
                ?.let { added += it.key to qty }
        }
        if (st.target != ScanTarget.COLLECTION) {
            // Deck, box check or removing: each card says what happened on its own.
            _state.update { it.copy(status = "Turn the page and capture again") }
            return@launch
        }
        val n = added.sumOf { it.second }
        val missed = slots.size - added.size
        _state.update {
            it.copy(
                status = (if (n == 1) "Added 1 card" else "Added $n cards") + (if (missed > 0) " · $missed not added" else "") +
                    " · turn the page",
            )
        }
        // One Undo for the whole page.
        if (added.isNotEmpty()) Undo.offer(if (n == 1) "Added 1 card" else "Added $n cards") {
            added.asReversed().forEach { (key, q) -> takeBack(key, q) }
        }
    }
}

/** Reads every card in the picture at [uri], then looks each one up. */
fun ScanViewModel.readPhoto(uri: android.net.Uri) = readCards("Photo") { com.mtgscanner.scan.PhotoCards.decode(repo.appContext, uri) }

/** Reads every card in the video at [uri], each once, then looks each one up for the review sheet. */
fun ScanViewModel.readVideo(uri: android.net.Uri) {
    photoJob?.cancel()
    _state.update { it.copy(photo = PhotoRun(title = "Video")) }
    photoJob = viewModelScope.launch {
        val reads = withContext(Dispatchers.Default) {
            runCatching {
                com.mtgscanner.scan.VideoCards.read(repo.appContext, uri, { b -> repo.artEmbedder.embed(b) }, repo.artIndex.hasAny) { p ->
                    _state.update { s -> s.photo?.takeIf { it.loading }?.let { s.copy(photo = it.copy(progress = p)) } ?: s }
                }
            }.getOrNull()
        }
        if (reads == null) {
            _state.update { it.copy(photo = PhotoRun(loading = false, failed = true, title = "Video")) }
            return@launch
        }
        val items = reads.mapIndexed { i, r -> async { photoItem(i, r) } }.awaitAll()
        _state.update { s -> s.photo?.let { s.copy(photo = PhotoRun(loading = false, items = items, title = "Video")) } ?: s }
    }
}

/** Loose cards on the table: reads every card in the next steady camera frame. */
fun ScanViewModel.captureSpread() = _state.update { it.copy(wantSpread = true, paused = false, status = "Hold still…") }

/** Called by the analyzer with the frame, turned upright; this takes it over. */
fun ScanViewModel.onSpread(frame: android.graphics.Bitmap) {
    _state.update { it.copy(wantSpread = false, status = "Binder page in the frame, or pick a photo") }
    // Cards left where they lay in the last capture aren't read again.
    readCards("Cards in view", spreadTracks) { frame }
}

/** Reads every card in the picture [load] gives (recycled after), then looks each one up, for the review sheet. */
private fun ScanViewModel.readCards(
    title: String, tracks: com.mtgscanner.scan.CardTracks<Pair<Long, com.mtgscanner.scan.CardTextGuess>>? = null,
    load: () -> android.graphics.Bitmap?,
) {
    photoJob?.cancel()
    _state.update { it.copy(photo = PhotoRun(title = title)) }
    photoJob = viewModelScope.launch {
        val reads = withContext(Dispatchers.Default) {
            runCatching {
                val bmp = load()
                if (bmp == null) null
                else try {
                    com.mtgscanner.scan.PhotoCards.read(bmp, { b -> repo.artEmbedder.embed(b) }, repo.artIndex.hasAny, tracks)
                } finally {
                    bmp.recycle()
                }
            }.getOrNull()
        }
        if (reads == null) {
            _state.update { it.copy(photo = PhotoRun(loading = false, failed = true)) }
            return@launch
        }
        val items = reads.mapIndexed { i, r -> async { photoItem(i, r) } }.awaitAll()
        _state.update { s -> s.photo?.let { s.copy(photo = PhotoRun(loading = false, items = items, title = title)) } ?: s }
    }
}

/** Looks up one card read from a photo: by its text, else by its art. */
private suspend fun ScanViewModel.photoItem(i: Int, r: com.mtgscanner.scan.PhotoRead): PhotoItem {
    val g = r.guess
    val card = when {
        g.key != null -> runCatching { repo.identify(g)?.card }.getOrNull()
        g.pictureOnly -> {
            val hit = runCatching { repo.matchPicture(g, SortPrefs.setLock) }.getOrNull()
            hit?.let { h -> runCatching { repo.cardById(h.id) }.getOrNull() }
        }
        else -> null
    }
    return PhotoItem(i, readLabel(g), card, g.lang?.takeIf { card == null || card.lang == "en" }, r.thumb)
}

/** Swaps a photo card for the printing picked. */
fun ScanViewModel.photoPrinting(key: Int, card: ScryfallCard) = _state.update {
    it.copy(photo = it.photo?.let { p -> p.copy(items = p.items.map { x -> if (x.key == key) x.copy(card = card, unsure = false) else x }) })
}

/** Takes a card off the photo's list (nothing was added yet). */
fun ScanViewModel.photoRemove(key: Int) = _state.update {
    it.copy(photo = it.photo?.let { p -> p.copy(items = p.items.filterNot { x -> x.key == key }) })
}

fun ScanViewModel.closePhoto() {
    photoJob?.cancel()
    // A batch being reviewed: what's left goes back on the list.
    _state.update { s -> s.copy(photo = null, batch = s.photo?.takeIf { it.fromBatch }?.items ?: s.batch) }
}

/** Adds every found card from the photo, with the scan bar's copies, one after another; one Undo for all. */
fun ScanViewModel.addPhoto() {
    val st = _state.value
    val items = st.photo?.items.orEmpty().filter { it.card != null }
    // Just checking prices adds nothing, so a reviewed batch goes back on its list rather than being dropped.
    if (st.target == ScanTarget.CHECK) { closePhoto(); return }
    // A batch reviewed and added starts a fresh list, still in batch mode.
    _state.update { it.copy(photo = null, batch = if (st.photo?.fromBatch == true) emptyList() else it.batch) }
    if (st.target == ScanTarget.CHECK || items.isEmpty()) return
    val qty = ScanBarPrefs.quantity
    viewModelScope.launch {
        val added = mutableListOf<Pair<Long, Int>>()
        items.forEach { item ->
            val card = item.card ?: return@forEach
            val lang = (item.lang?.takeIf { card.lang == "en" }) ?: ScanBarPrefs.langOrNull
            val now = _state.value
            // A batch card keeps the foil setting it was scanned with.
            val foil = item.foil?.let { f -> f && card.hasFoil || !card.hasNonFoil } ?: now.isFoil(card)
            // A batch card keeps the copy count it was scanned with too.
            val copies = item.count ?: qty
            addNow(card, copies, now, foil, lang, fromReview = false, auto = true, fromPage = true)
                ?.let { added += it.key to copies }
        }
        if (st.target != ScanTarget.COLLECTION) return@launch
        val n = added.sumOf { it.second }
        val missed = items.size - added.size
        _state.update {
            it.copy(status = (if (n == 1) "Added 1 card" else "Added $n cards") + (if (missed > 0) " · $missed not added" else ""))
        }
        if (added.isNotEmpty()) Undo.offer(if (n == 1) "Added 1 card" else "Added $n cards") {
            added.asReversed().forEach { (key, q) -> takeBack(key, q) }
        }
    }
}

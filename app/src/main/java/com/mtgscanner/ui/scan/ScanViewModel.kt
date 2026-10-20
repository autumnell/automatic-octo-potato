package com.mtgscanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.OfflineRead
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallException
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.ScanPrefs
import com.mtgscanner.scan.ScanSettings
import com.mtgscanner.scan.UnsureAction
import com.mtgscanner.ui.theme.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One add during this scanning session, newest first in the scan list. */
data class ScanEntry(
    val key: Long,
    val rowId: Long,
    val card: ScryfallCard,
    val foil: Boolean,
    val count: Int,
    val boxName: String = "",
) {
    val value: Double get() = (card.price(foil)?.toDoubleOrNull() ?: 0.0) * count
}

data class ScanUiState(
    val status: String = "Point your camera at a card",
    val reading: String? = null,
    val current: ScryfallCard? = null,
    val confident: Boolean = false,
    val lookingUp: Boolean = false,
    val paused: Boolean = false,
    /** Auto-add level, unsure action, skip rules, feedback. Saved, and switchable with presets. */
    val settings: ScanSettings = ScanSettings(),
    /** Auto-add is waiting for you to confirm a card it wasn't sure about. */
    val awaiting: Boolean = false,
    val torch: Boolean = false,
    /** The box to add to, or [Box.AUTO_SORT] to follow each box's rules. */
    val boxId: Long = Box.AUTO_SORT,
    /** The current card is on the wishlist. */
    val wish: WishlistItem? = null,
    /** A card read while offline, waiting to be saved for later. */
    val offline: OfflineRead? = null,
    val pendingCount: Int = 0,
    val showNewBox: Boolean = false,
    val session: List<ScanEntry> = emptyList(),
    /** Bumps on every add so the screen can buzz and flash. */
    val addedPulse: Int = 0,
    val showPrintings: Boolean = false,
    val printings: List<ScryfallCard>? = null,
    val showSettings: Boolean = false,
    val showList: Boolean = false,
    val error: String? = null,
    /** Unsure cards set aside while auto-adding. */
    val review: List<ScryfallCard> = emptyList(),
    val showReview: Boolean = false,
    /** The box the last card went into, shown big so you know where to file it. */
    val lastBoxName: String? = null,
    val lastBoxColor: Int = 0,
    /** Group the scan list by box, to file a pile of cards. */
    val listByBox: Boolean = false,
    /** Cards skipped by the skip rules this session. They're counted, not added. */
    val skipped: Int = 0,
    /** Bumps when a card is set aside, for its own sound. */
    val asidePulse: Int = 0,
) {
    val autoAdd: Boolean get() = settings.autoAdd
    val foil: Boolean get() = settings.foil
    val sessionCount: Int get() = session.sumOf { it.count }
    val sessionValue: Double get() = session.sumOf { it.value }
}

class ScanViewModel(private val repo: CollectionRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        ScanPrefs.settings.value.let { ScanUiState(settings = it, boxId = it.boxId ?: repo.lastBoxId) },
    )
    val state: StateFlow<ScanUiState> = _state

    /** null until loaded, so the screen doesn't flash "create a box" on open. */
    val boxes: StateFlow<List<Box>?> = repo.observeBoxes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            repo.observePending().collect { p -> _state.update { it.copy(pendingCount = p.sumOf { x -> x.quantity }) } }
        }
        viewModelScope.launch {
            ScanPrefs.settings.collect { s ->
                // A preset can choose the box too.
                if (s.boxId != null && s.boxId != _state.value.boxId) repo.lastBoxId = s.boxId
                _state.update { it.copy(settings = s, boxId = s.boxId ?: it.boxId, awaiting = it.awaiting && s.autoAdd) }
            }
        }
        // Try saved offline scans again whenever the scanner opens.
        viewModelScope.launch { runCatching { repo.resolvePending() } }
    }

    // Frame-to-frame bookkeeping for the camera analyzer.
    private var lastKey: String? = null
    private var sameKeyFrames = 0
    private var emptyFrames = 0
    private var lookedUpKey: String? = null
    private var lastAutoAddedId: String? = null
    private var entryKeys = 0L

    /** The analyzer skips frames while this is true. */
    val isPaused: Boolean
        get() = _state.value.let {
            it.paused || ((!it.autoAdd || it.awaiting) && (it.current != null || it.offline != null)) || it.lookingUp ||
                it.showPrintings || it.showSettings || it.showList || it.showNewBox || it.showReview || boxes.value.isNullOrEmpty()
        }

    fun onGuess(guess: CardTextGuess) {
        val key = guess.key
        if (key == null) {
            emptyFrames++
            // The card has left the frame: allow the same card to be auto-added again.
            if (emptyFrames >= 3) {
                lastAutoAddedId = null
                lastKey = null
                lookedUpKey = null
                _state.update {
                    it.copy(reading = null, status = if (it.autoAdd && it.current != null) it.status else "Point your camera at a card")
                }
            }
            return
        }
        emptyFrames = 0
        sameKeyFrames = if (key == lastKey) sameKeyFrames + 1 else 1
        lastKey = key
        _state.update { it.copy(reading = guess.nameCandidates.firstOrNull() ?: "${guess.setCode} #${guess.collectorNumber}") }

        // Wait for two frames that agree, so a blurry half-read doesn't trigger a lookup.
        if (sameKeyFrames < 2 || key == lookedUpKey) return
        lookedUpKey = key
        lookup(guess)
    }

    private fun lookup(guess: CardTextGuess) {
        _state.update { it.copy(lookingUp = true, status = "Looking it up…", error = null, offline = null) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching { repo.identify(guess) }
            logLookup(guess, result, System.currentTimeMillis() - started)
            result.onFailure { e ->
                lookedUpKey = null
                val offline = if (e is java.io.IOException && e !is ScryfallException) repo.offlineRead(guess) else null
                if (offline != null) {
                    _state.update { it.copy(lookingUp = false, offline = offline, status = "Offline · ${offline.name}") }
                    if (_state.value.autoAdd && offline.name != lastAutoAddedId) {
                        lastAutoAddedId = offline.name
                        saveOffline()
                    }
                } else {
                    _state.update { it.copy(lookingUp = false, status = "Couldn't reach Scryfall", error = e.message) }
                }
            }
            val id = result.getOrNull()
            if (result.isSuccess && id == null) {
                _state.update { it.copy(lookingUp = false, status = "No match yet · hold the card flat and well lit") }
            }
            if (id != null) onIdentified(id)
        }
    }

    /** Decides what happens to a match: add it, skip it, set it aside, or stop and ask. */
    private suspend fun onIdentified(id: com.mtgscanner.data.Identification) {
        val s = _state.value.settings
        val card = id.card
        if (s.autoAdd) {
            val foil = s.foil && card.hasFoil || !card.hasNonFoil
            val price = card.price(foil)?.toDoubleOrNull()
            val reason = s.skipReason(card.effectiveTypeLine, card.layout, price, AppSettings.showPrices)
            if (reason != null) {
                val counted = card.id != lastAutoAddedId
                lastAutoAddedId = card.id
                _state.update {
                    it.copy(lookingUp = false, current = null, skipped = it.skipped + if (counted) 1 else 0, status = "Skipped ${card.name} ($reason)")
                }
                return
            }
            if (!s.accepts(id.exact, id.confident)) {
                when (s.unsure) {
                    UnsureAction.ASIDE -> {
                        val fresh = card.id != lastAutoAddedId && _state.value.review.none { it.id == card.id }
                        lastAutoAddedId = card.id
                        _state.update {
                            it.copy(
                                lookingUp = false,
                                current = null,
                                review = if (fresh) it.review + card else it.review,
                                asidePulse = it.asidePulse + if (fresh) 1 else 0,
                                status = "Set aside ${card.name} to check later",
                            )
                        }
                        return
                    }
                    UnsureAction.SKIP -> {
                        _state.update { it.copy(lookingUp = false, current = null, status = "Not sure it's ${card.name}, skipped") }
                        return
                    }
                    UnsureAction.PAUSE -> Unit
                }
            }
        }
        val accepted = s.autoAdd && s.accepts(id.exact, id.confident)
        val wish = runCatching { repo.wishlistItem(card.oracleId) }.getOrNull()
        _state.update {
            it.copy(
                lookingUp = false,
                current = card,
                confident = id.confident || accepted,
                awaiting = s.autoAdd && !accepted,
                wish = wish,
                status = if (id.confident || accepted) card.name else "Is this right?",
            )
        }
        if (accepted && card.id != lastAutoAddedId) {
            lastAutoAddedId = card.id
            addCard(card, 1, auto = true)
        }
    }

    private fun logLookup(guess: CardTextGuess, result: Result<com.mtgscanner.data.Identification?>, ms: Long) {
        val id = result.getOrNull()
        val err = result.exceptionOrNull()
        val outcome = when {
            err is java.io.IOException && err !is ScryfallException -> "offline"
            err != null -> "error"
            id == null -> "no match"
            id.confident -> "matched"
            else -> "unsure"
        }
        viewModelScope.launch {
            runCatching {
                repo.scanLog.add(
                    com.mtgscanner.scan.ScanLogEntry(
                        read = guess.nameCandidates.take(4), setCode = guess.setCode, collectorNumber = guess.collectorNumber,
                        art = guess.artHash != null, outcome = outcome, matchName = id?.card?.name, matchSet = id?.card?.set,
                        matchNumber = id?.card?.collectorNumber, ms = ms, note = err?.message,
                    ),
                )
            }
        }
    }

    fun add(qty: Int = 1) {
        val card = _state.value.current ?: return
        // In auto-add mode, don't add this card again while it's still in view.
        if (_state.value.autoAdd) lastAutoAddedId = card.id
        addCard(card, qty)
    }

    /** Adds a card that was set aside. */
    fun addFromReview(card: ScryfallCard) {
        _state.update { it.copy(review = it.review.filterNot { c -> c.id == card.id }, showReview = it.review.size > 1) }
        addCard(card, 1, fromReview = true)
    }

    fun dropFromReview(card: ScryfallCard) =
        _state.update { it.copy(review = it.review.filterNot { c -> c.id == card.id }, showReview = it.review.size > 1) }

    fun showReview(show: Boolean) = _state.update { it.copy(showReview = show) }

    fun setListByBox(on: Boolean) = _state.update { it.copy(listByBox = on) }

    private fun addCard(card: ScryfallCard, qty: Int, fromReview: Boolean = false, auto: Boolean = false) {
        val s = _state.value
        val foil = s.foil && card.hasFoil || !card.hasNonFoil
        viewModelScope.launch {
            val wish = if (fromReview) runCatching { repo.wishlistItem(card.oracleId) }.getOrNull() else s.wish
            // A box that was deleted (say, saved in a preset) falls back to auto-sort.
            val known = boxes.value?.any { it.id == s.boxId } == true
            val boxId = if (s.boxId == Box.AUTO_SORT || !known) repo.pickBox(card, foil) else s.boxId
            if (boxId == null) {
                _state.update { it.copy(status = "No box fits ${card.name}", error = "Pick a box above, or add a box that takes any card.") }
                return@launch
            }
            val rowId = repo.add(card, foil, qty, boxId)
            if (wish != null) repo.fulfilWish(card.oracleId, qty)
            val box = boxes.value?.firstOrNull { it.id == boxId }
            val boxName = box?.name.orEmpty()
            val entry = ScanEntry(++entryKeys, rowId, card, foil, qty, boxName)
            _state.update {
                it.copy(
                    session = listOf(entry) + it.session,
                    addedPulse = it.addedPulse + 1,
                    status = if (boxName.isNotEmpty()) "Added ${card.name} → $boxName" else "Added ${card.name}",
                    error = null,
                    wish = null,
                    lastBoxName = boxName.ifEmpty { null },
                    lastBoxColor = box?.color ?: 0,
                    current = if (fromReview || auto) it.current else null,
                    awaiting = if (fromReview) it.awaiting else false,
                )
            }
            if (!_state.value.autoAdd && !fromReview) resetTracking()
        }
    }

    /** Keeps an offline read to look up and add once Scryfall can be reached. */
    fun saveOffline(qty: Int = 1) {
        val s = _state.value
        val read = s.offline ?: return
        viewModelScope.launch {
            repo.savePending(read, s.foil, qty, s.boxId)
            _state.update { it.copy(offline = null, addedPulse = it.addedPulse + 1, status = "Saved ${read.name} for later") }
            resetTracking()
        }
    }

    fun createBox(d: com.mtgscanner.ui.components.BoxDraft) {
        viewModelScope.launch {
            val id = repo.createBox(d.name, d.kind, d.rules, d.color)
            // The first box becomes the target; later ones keep the current choice.
            if (boxes.value.isNullOrEmpty() || _state.value.boxId != Box.AUTO_SORT) setBox(id)
            _state.update { it.copy(showNewBox = false) }
        }
    }

    fun showNewBox(show: Boolean) = _state.update { it.copy(showNewBox = show) }

    /** Takes a scan back out of the collection and the list. */
    fun remove(entry: ScanEntry) {
        viewModelScope.launch {
            repo.remove(entry.rowId, entry.count)
            _state.update { it.copy(session = it.session.filterNot { e -> e.key == entry.key }, status = "Removed ${entry.card.name}") }
        }
    }

    /** Clears the list on screen. Cards stay in the collection. */
    fun clearList() = _state.update { it.copy(session = emptyList(), showList = false) }

    fun skip() {
        val auto = _state.value.autoAdd
        _state.value.current?.let { if (auto) lastAutoAddedId = it.id }
        _state.update { it.copy(current = null, offline = null, wish = null, error = null, awaiting = false, status = "Point your camera at a card") }
        // While auto-adding, keep ignoring this card until it leaves the frame.
        if (!auto) resetTracking()
    }

    private fun resetTracking() {
        lastKey = null
        lookedUpKey = null
        sameKeyFrames = 0
    }

    fun togglePause() = _state.update {
        val p = !it.paused
        it.copy(paused = p, status = if (p) "Paused" else "Point your camera at a card", reading = null)
    }

    fun updateSettings(change: (ScanSettings) -> ScanSettings) = ScanPrefs.update(change)
    fun setAutoAdd(on: Boolean) = updateSettings { it.copy(autoAdd = on) }
    fun setFoil(on: Boolean) = updateSettings { it.copy(foil = on) }
    fun setTorch(on: Boolean) = _state.update { it.copy(torch = on) }
    fun setBox(id: Long) {
        repo.lastBoxId = id
        ScanPrefs.setBox(id)
        _state.update { it.copy(boxId = id, error = null) }
    }
    fun showSettings(show: Boolean) = _state.update { it.copy(showSettings = show) }
    fun showList(show: Boolean) = _state.update { it.copy(showList = show) }

    fun openPrintings() {
        val card = _state.value.current ?: return
        _state.update { it.copy(showPrintings = true, printings = null) }
        viewModelScope.launch {
            val list = runCatching { repo.api.printings(card) }.getOrDefault(listOf(card))
            _state.update { it.copy(printings = list) }
        }
    }

    fun pickPrinting(card: ScryfallCard) {
        val before = _state.value.current
        if (before != null && before.id != card.id) {
            viewModelScope.launch {
                runCatching {
                    repo.scanLog.add(
                        com.mtgscanner.scan.ScanLogEntry(
                            outcome = "corrected", matchName = card.name, matchSet = card.set, matchNumber = card.collectorNumber,
                            note = "was ${before.name} (${before.set.uppercase()} #${before.collectorNumber})",
                        ),
                    )
                }
            }
        }
        pickPrintingInternal(card)
    }

    private fun pickPrintingInternal(card: ScryfallCard) =
        _state.update { it.copy(current = card, confident = true, showPrintings = false, printings = null) }

    fun closePrintings() = _state.update { it.copy(showPrintings = false, printings = null) }
}

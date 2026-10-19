package com.mtgscanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Box
import com.mtgscanner.scan.CardTextGuess
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
    val autoAdd: Boolean = false,
    val foil: Boolean = false,
    val torch: Boolean = false,
    val boxId: Long = Box.BULK_ID,
    val session: List<ScanEntry> = emptyList(),
    /** Bumps on every add so the screen can buzz and flash. */
    val addedPulse: Int = 0,
    val showPrintings: Boolean = false,
    val printings: List<ScryfallCard>? = null,
    val showSettings: Boolean = false,
    val showList: Boolean = false,
    val error: String? = null,
) {
    val sessionCount: Int get() = session.sumOf { it.count }
    val sessionValue: Double get() = session.sumOf { it.value }
}

class ScanViewModel(private val repo: CollectionRepository) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    val boxes: StateFlow<List<Box>> = repo.observeBoxes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            it.paused || (!it.autoAdd && it.current != null) || it.lookingUp ||
                it.showPrintings || it.showSettings || it.showList
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
        _state.update { it.copy(lookingUp = true, status = "Looking it up…", error = null) }
        viewModelScope.launch {
            val result = runCatching { repo.identify(guess) }
            result.onFailure { e ->
                lookedUpKey = null
                _state.update { it.copy(lookingUp = false, status = "Couldn't reach Scryfall", error = e.message) }
            }
            val id = result.getOrNull()
            if (result.isSuccess && id == null) {
                _state.update { it.copy(lookingUp = false, status = "No match yet · hold the card flat and well lit") }
            }
            if (id != null) {
                _state.update {
                    it.copy(
                        lookingUp = false,
                        current = id.card,
                        confident = id.confident,
                        status = if (id.confident) id.card.name else "Is this right?",
                    )
                }
                if (_state.value.autoAdd && id.confident && id.card.id != lastAutoAddedId) {
                    lastAutoAddedId = id.card.id
                    add()
                }
            }
        }
    }

    fun add(qty: Int = 1) {
        val s = _state.value
        val card = s.current ?: return
        val foil = s.foil && card.hasFoil || !card.hasNonFoil
        viewModelScope.launch {
            val rowId = repo.add(card, foil, qty, s.boxId)
            val entry = ScanEntry(++entryKeys, rowId, card, foil, qty)
            _state.update {
                it.copy(
                    session = listOf(entry) + it.session,
                    addedPulse = it.addedPulse + 1,
                    status = "Added ${card.name}",
                    current = if (it.autoAdd) it.current else null,
                )
            }
            if (!_state.value.autoAdd) resetTracking()
        }
    }

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
        _state.update { it.copy(current = null, status = "Point your camera at a card") }
        resetTracking()
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

    fun setAutoAdd(on: Boolean) = _state.update { it.copy(autoAdd = on) }
    fun setFoil(on: Boolean) = _state.update { it.copy(foil = on) }
    fun setTorch(on: Boolean) = _state.update { it.copy(torch = on) }
    fun setBox(id: Long) = _state.update { it.copy(boxId = id) }
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

    fun pickPrinting(card: ScryfallCard) =
        _state.update { it.copy(current = card, confident = true, showPrintings = false, printings = null) }

    fun closePrintings() = _state.update { it.copy(showPrintings = false, printings = null) }
}

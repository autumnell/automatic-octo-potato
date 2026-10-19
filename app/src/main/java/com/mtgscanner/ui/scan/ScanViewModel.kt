package com.mtgscanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.scan.CardTextGuess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddedEntry(val rowId: Long, val name: String, val foil: Boolean, val count: Int)

data class ScanUiState(
    val status: String = "Point the camera at a card",
    val reading: String? = null,
    val current: ScryfallCard? = null,
    val confident: Boolean = false,
    val lookingUp: Boolean = false,
    val autoAdd: Boolean = false,
    val foil: Boolean = false,
    val torch: Boolean = false,
    val lastAdded: AddedEntry? = null,
    val sessionCount: Int = 0,
    val showPrintings: Boolean = false,
    val printings: List<ScryfallCard>? = null,
    val error: String? = null,
)

class ScanViewModel(private val repo: CollectionRepository) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    // Frame-to-frame bookkeeping. Only touched from onGuess, which runs one frame at a time.
    private var lastKey: String? = null
    private var sameKeyFrames = 0
    private var emptyFrames = 0
    private var lookedUpKey: String? = null
    private var lastAutoAddedId: String? = null

    /** Manual mode freezes on a found card until the user adds or skips it. */
    val isPaused: Boolean
        get() = _state.value.let { (!it.autoAdd && it.current != null) || it.lookingUp || it.showPrintings }

    fun onGuess(guess: CardTextGuess) {
        val key = guess.key
        if (key == null) {
            emptyFrames++
            // The card has left the frame: allow the same card to be auto-added again.
            if (emptyFrames >= 3) {
                lastAutoAddedId = null
                lastKey = null
                lookedUpKey = null
                if (_state.value.autoAdd) _state.update { it.copy(reading = null, status = "Point the camera at a card") }
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
        _state.update { it.copy(lookingUp = true, status = "Looking up on Scryfall…", error = null) }
        viewModelScope.launch {
            val result = runCatching { repo.identify(guess) }
            result.onFailure { e ->
                lookedUpKey = null
                _state.update { it.copy(lookingUp = false, status = "Couldn't reach Scryfall", error = e.message) }
            }
            val id = result.getOrNull()
            if (result.isSuccess && id == null) {
                _state.update { it.copy(lookingUp = false, status = "No match yet. Hold the card flat and well lit") }
            }
            if (id != null) {
                _state.update {
                    it.copy(
                        lookingUp = false,
                        current = id.card,
                        confident = id.confident,
                        status = if (id.confident) "Found" else "Is this right?",
                        foil = it.foil && id.card.hasFoil,
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
            val rowId = repo.add(card, foil, qty)
            _state.update {
                it.copy(
                    lastAdded = AddedEntry(rowId, card.name, foil, qty),
                    sessionCount = it.sessionCount + qty,
                    status = "Added ${card.name}",
                    current = if (it.autoAdd) it.current else null,
                )
            }
            if (!_state.value.autoAdd) resetTracking()
        }
    }

    fun undoLast() {
        val last = _state.value.lastAdded ?: return
        viewModelScope.launch {
            repo.remove(last.rowId, last.count)
            _state.update { it.copy(lastAdded = null, sessionCount = (it.sessionCount - last.count).coerceAtLeast(0), status = "Removed ${last.name}") }
        }
    }

    fun skip() {
        _state.update { it.copy(current = null, status = "Point the camera at a card") }
        resetTracking()
    }

    private fun resetTracking() {
        lastKey = null
        lookedUpKey = null
        sameKeyFrames = 0
    }

    fun setAutoAdd(on: Boolean) = _state.update { it.copy(autoAdd = on) }
    fun setFoil(on: Boolean) = _state.update { it.copy(foil = on) }
    fun setTorch(on: Boolean) = _state.update { it.copy(torch = on) }

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

    fun clearError() = _state.update { it.copy(error = null) }
}

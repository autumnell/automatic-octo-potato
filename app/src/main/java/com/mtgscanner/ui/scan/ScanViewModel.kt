package com.mtgscanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.BoxRules
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
import com.mtgscanner.data.ownedCopies
import com.mtgscanner.data.setScanLang
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.FrameLight
import com.mtgscanner.scan.LightCheck
import com.mtgscanner.scan.ScanPrefs
import com.mtgscanner.scan.ScanSettings
import com.mtgscanner.scan.ScanSound
import com.mtgscanner.scan.UnsureAction
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.AppSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
    val boxId: Long = 0,
    /** Divider inside the box ("Blue", "R"), when the box has dividers. */
    val spot: String? = null,
    /** Short note when the box wasn't picked by plain rules ("Extra copy"). */
    val note: String? = null,
    val full: Boolean = false,
    /** Set when the card went to a deck list instead of the collection ([rowId] is then 0). */
    val deckId: Long = 0,
    /** Language recorded for it, when not English. */
    val lang: String? = null,
    /** When copies were last added to this line, for quick quantity. */
    val at: Long = System.currentTimeMillis(),
) {
    /** "Rares → Blue" */
    val where: String get() = if (spot != null && boxName.isNotEmpty()) "$boxName → $spot" else boxName
    val value: Double get() = (card.price(foil)?.toDoubleOrNull() ?: 0.0) * count
}

/** A pack being opened, with the cards scanned from it. Kept apart from the scan list so clearing the list keeps them. */
data class PackDraft(val name: String, val cost: Double?, val entries: List<ScanEntry> = emptyList())

/** Sorting session: cards scanned since it started, to file by box afterwards. [done] holds the box ids filed. */
data class SortSession(val entries: List<ScanEntry> = emptyList(), val done: Set<Long> = emptySet())

/** Short label for why a card went where it did, or null for plain rules. */
fun sortNote(d: SortDecision?): String? = when (d?.reason) {
    SortReason.BULK -> SortPrefs.bulkFloor?.let { "Bulk (under $" + "%.2f".format(java.util.Locale.US, it) + ")" } ?: "Bulk"
    SortReason.EXTRA -> "Extra copy"
    else -> null
}

/** Where scanned cards go: the collection, a deck list, or nowhere (price check). */
enum class ScanTarget { COLLECTION, DECK, CHECK }

/** One pocket of a binder page. [card] is null when it couldn't be read or found. */
data class PageSlot(val index: Int, val read: String?, val card: ScryfallCard?, val selected: Boolean)

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
    /** Divider spot, reason note and full flag for the last card's box. */
    val lastSpot: String? = null,
    val lastNote: String? = null,
    val lastFull: Boolean = false,
    /** Group the scan list by box, to file a pile of cards. */
    val listByBox: Boolean = false,
    /** Cards skipped by the skip rules this session. They're counted, not added. */
    val skipped: Int = 0,
    /** Bumps when a card is set aside, for its own sound. */
    val asidePulse: Int = 0,
    /** Pack mode: the pack being opened, if any. */
    val pack: PackDraft? = null,
    val showPackStart: Boolean = false,
    /** Sorting session: the pile being scanned, if any. */
    val sort: SortSession? = null,
    val showSort: Boolean = false,
    val showSetLock: Boolean = false,
    /** Binder page mode: read a 3x3 page of sleeves at once. */
    val pageMode: Boolean = false,
    val wantPage: Boolean = false,
    val pageLoading: Boolean = false,
    val page: List<PageSlot>? = null,
    /** Foil picked on the result card for the card on screen; null follows the Foil setting. */
    val cardFoil: Boolean? = null,
    /** Stack mode: hands-free auto-add with a beep per card and a double beep when unsure. */
    val stack: Boolean = false,
    val target: ScanTarget = ScanTarget.COLLECTION,
    /** The deck list cards go to in [ScanTarget.DECK]. */
    val deck: Deck? = null,
    val showDeckPick: Boolean = false,
    /** Copies owned of the card on screen (price check and deck modes). */
    val owned: Int? = null,
    /** Language read off the card on screen, when not English. */
    val currentLang: String? = null,
    /** The frames suggest the card on screen is foil. Only a hint: tap to accept. */
    val foilHint: Boolean = false,
    /** Text read that matched no card for a while, offered to save for later. */
    val unknownRead: String? = null,
    val unknownCount: Int = 0,
    /** Other boxes the last card would also have fit, after the one it went to. */
    val lastAlso: List<String> = emptyList(),
) {
    val autoAdd: Boolean get() = settings.autoAdd
    val foil: Boolean get() = settings.foil

    /** Whether [card] gets added as foil. The result card's choice counts only for the card on screen. */
    fun isFoil(card: ScryfallCard): Boolean {
        val want = (if (card.id == current?.id) cardFoil else null) ?: settings.foil
        return want && card.hasFoil || !card.hasNonFoil
    }
    val sessionCount: Int get() = session.sumOf { it.count }
    val sessionValue: Double get() = session.sumOf { it.value }
    val packEntries: List<ScanEntry> get() = pack?.entries.orEmpty()
    val packValue: Double get() = packEntries.sumOf { it.value }
}

class ScanViewModel(private val repo: CollectionRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        ScanPrefs.settings.value.let { ScanUiState(settings = it, boxId = it.boxId ?: repo.lastBoxId) },
    )
    val state: StateFlow<ScanUiState> = _state

    /** null until loaded, so the screen doesn't flash "create a box" on open. */
    val boxes: StateFlow<List<Box>?> = repo.observeBoxes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Deck lists, for scan-to-deck mode. */
    val decks: StateFlow<List<Deck>> = repo.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The saved settings with the active modes on top: stack mode auto-adds and beeps, price check never adds. */
    private fun effective(s: ScanSettings, st: ScanUiState): ScanSettings = when {
        st.target == ScanTarget.CHECK -> s.copy(autoAdd = false)
        st.stack -> s.copy(autoAdd = true, unsure = UnsureAction.ASIDE, sound = ScanSound.BEEP, asideSound = ScanSound.DOUBLE)
        else -> s
    }

    /** Applies a mode change and recomputes the settings it affects. */
    private fun changeMode(change: (ScanUiState) -> ScanUiState) {
        _state.update { old ->
            val n = change(old)
            val s = effective(ScanPrefs.settings.value, n)
            n.copy(settings = s, awaiting = n.awaiting && s.autoAdd)
        }
        resetTracking()
    }

    init {
        _state.update { it.copy(settings = effective(it.settings, it)) }
        viewModelScope.launch {
            repo.observePending().collect { p ->
                _state.update {
                    it.copy(pendingCount = p.filterNot { x -> x.unknown }.sumOf { x -> x.quantity }, unknownCount = p.count { x -> x.unknown })
                }
            }
        }
        viewModelScope.launch {
            ScanPrefs.settings.collect { raw ->
                // A preset can choose the box too.
                if (raw.boxId != null && raw.boxId != _state.value.boxId) repo.lastBoxId = raw.boxId
                _state.update {
                    val s = effective(raw, it)
                    it.copy(settings = s, boxId = raw.boxId ?: it.boxId, awaiting = it.awaiting && s.autoAdd)
                }
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
    // The same card can come back as a different printing when the footer is only read on some frames,
    // so "already added while in view" also matches by card name (oracle id).
    private var lastAutoAddedOracle: String? = null
    private fun alreadyHandled(card: ScryfallCard) =
        card.id == lastAutoAddedId || (card.oracleId != null && card.oracleId == lastAutoAddedOracle)
    private fun markHandled(card: ScryfallCard) { lastAutoAddedId = card.id; lastAutoAddedOracle = card.oracleId }
    private val removedKeys = HashSet<Long>()
    private var entryKeys = 0L
    /** Every scan list line by key, as it is now (copies can be added or taken back). */
    private val latest = HashMap<Long, ScanEntry>()

    // Unknown card queue: when text keeps being read but matches nothing.
    private var noMatchSince = 0L
    private var noMatchGuess: CardTextGuess? = null

    /** The analyzer skips frames while this is true. */
    val isPaused: Boolean
        get() = _state.value.let {
            if (it.showSort || it.showSetLock || it.showDeckPick) true
            else if (it.pageMode) it.paused || !it.wantPage || it.pageLoading || it.page != null || it.showSettings || it.showList ||
                it.showNewBox || boxes.value.isNullOrEmpty()
            else it.paused || ((!it.autoAdd || it.awaiting) && (it.current != null || it.offline != null)) || it.lookingUp ||
                it.showPrintings || it.showSettings || it.showList || it.showNewBox || it.showReview || boxes.value.isNullOrEmpty()
        }

    /** Called on the camera thread; the work happens on the main thread so it never races the buttons. */
    fun onGuess(guess: CardTextGuess) {
        viewModelScope.launch { handleGuess(guess) }
    }

    private fun handleGuess(guess: CardTextGuess) {
        val key = guess.key
        if (key == null) {
            emptyFrames++
            // The card has left the frame: allow the same card to be auto-added again.
            if (emptyFrames >= 3) {
                lastAutoAddedId = null
                lastAutoAddedOracle = null
                lastKey = null
                lookedUpKey = null
                noMatchSince = 0L
                lights.clear()
                _state.update {
                    it.copy(
                        reading = null, status = if (it.autoAdd && it.current != null) it.status else "Point your camera at a card",
                        foilHint = it.foilHint && it.current != null,
                    )
                }
            }
            return
        }
        emptyFrames = 0
        sameKeyFrames = if (key == lastKey) sameKeyFrames + 1 else 1
        lastKey = key
        _state.update { it.copy(reading = guess.nameCandidates.firstOrNull() ?: "${guess.setCode} #${guess.collectorNumber}") }
        // Text keeps coming but nothing matched for a few seconds: offer to keep it for later.
        if (noMatchSince > 0 && System.currentTimeMillis() - noMatchSince >= UNKNOWN_AFTER_MS) offerUnknown()

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
                if (e is ScryfallException) {
                    // Scryfall is busy or rate limiting: wait a few seconds before trying this card again.
                    val key = guess.key
                    launch { delay(3_000); if (lookedUpKey == key) lookedUpKey = null }
                } else lookedUpKey = null
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
                if (noMatchSince == 0L) noMatchSince = System.currentTimeMillis()
                noMatchGuess = guess
                if (System.currentTimeMillis() - noMatchSince >= UNKNOWN_AFTER_MS) offerUnknown()
            }
            if (id != null) {
                noMatchSince = 0L
                noMatchGuess = null
                onIdentified(id, guess.lang)
            }
        }
    }

    /** Decides what happens to a match: add it, skip it, set it aside, or stop and ask. */
    private suspend fun onIdentified(id: com.mtgscanner.data.Identification, lang: String? = null) {
        val s = _state.value.settings
        val card = id.card
        // A real card was found: drop any "save for later" offer.
        _state.update { it.copy(unknownRead = null) }
        if (s.autoAdd) {
            val foil = s.foil && card.hasFoil || !card.hasNonFoil
            val price = card.price(foil)?.toDoubleOrNull()
            val reason = s.skipReason(card.effectiveTypeLine, card.layout, price, AppSettings.showPrices)
            if (reason != null) {
                val counted = !alreadyHandled(card)
                markHandled(card)
                _state.update {
                    it.copy(lookingUp = false, current = null, skipped = it.skipped + if (counted) 1 else 0, status = "Skipped ${card.name} ($reason)")
                }
                return
            }
            if (!s.accepts(id.exact, id.confident)) {
                when (s.unsure) {
                    UnsureAction.ASIDE -> {
                        val fresh = !alreadyHandled(card) && _state.value.review.none { it.id == card.id }
                        markHandled(card)
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
        val owned = if (_state.value.target != ScanTarget.COLLECTION) runCatching { repo.ownedCopies(card.oracleId) }.getOrNull() else null
        // Language only matters when Scryfall gave the English printing.
        val readLang = lang?.takeIf { card.lang == "en" }
        _state.update {
            val same = it.current?.id == card.id
            it.copy(
                lookingUp = false,
                current = card,
                cardFoil = if (same) it.cardFoil else null,
                foilHint = same && it.foilHint,
                confident = id.confident || accepted,
                awaiting = s.autoAdd && !accepted,
                wish = wish,
                owned = owned,
                currentLang = if (same) readLang ?: it.currentLang else readLang,
                status = if (id.confident || accepted) card.name else "Is this right?",
            )
        }
        if (accepted && !alreadyHandled(card)) {
            markHandled(card)
            addCard(card, ScanBarPrefs.quantity, auto = true, readLang = readLang)
        }
    }

    /** Offers to keep what was read for a card the scanner can't find. Not in price check. */
    private fun offerUnknown() {
        val g = noMatchGuess ?: return
        val st = _state.value
        if (st.unknownRead != null || st.target == ScanTarget.CHECK || (st.current != null && !st.autoAdd)) return
        val name = g.nameCandidates.firstOrNull() ?: g.text.lineSequence().map { it.trim() }.firstOrNull { it.length >= 3 } ?: return
        val footer = g.setCode?.let { set -> g.collectorNumber?.let { "$set $it" } ?: set }
        val text = if (footer != null) "$name ($footer)" else name
        _state.update { it.copy(unknownRead = text, asidePulse = if (it.stack) it.asidePulse + 1 else it.asidePulse) }
    }

    /** Keeps the unreadable card's text in the unknown queue, to search by hand later. */
    fun saveUnknown() {
        val s = _state.value
        val text = s.unknownRead ?: return
        noMatchSince = 0L
        noMatchGuess = null
        _state.update { it.copy(unknownRead = null, status = "Saved for later") }
        viewModelScope.launch { runCatching { repo.addUnknown(text, s.boxId, s.foil) } }
    }

    fun dismissUnknown() {
        noMatchSince = 0L
        noMatchGuess = null
        _state.update { it.copy(unknownRead = null) }
    }

    // Light: low-light flashlight and the foil hint

    /** Recent frames while a card is in view, for the foil hint. */
    private val lights = ArrayDeque<FrameLight>()
    private var darkRun = 0
    private var brightRun = 0
    /** The flashlight was turned on by low light, not by hand. */
    private var autoTorchOn = false
    /** Turned by hand this session: leave it alone from then on. */
    private var torchByHand = false
    private var torchCooldownUntil = 0L
    private var autoTorchCycles = 0

    /** Called on the camera thread a few times a second. */
    fun onLight(light: FrameLight) {
        viewModelScope.launch { handleLight(light) }
    }

    private fun handleLight(light: FrameLight) {
        val st = _state.value
        val card = st.current
        if (lastKey != null || card != null) {
            lights.addLast(light)
            while (lights.size > 8) lights.removeFirst()
        }
        if (card != null && !st.foilHint && st.target != ScanTarget.CHECK && card.hasFoil && card.hasNonFoil && !st.isFoil(card) &&
            LightCheck.looksFoil(lights.toList())
        ) {
            _state.update { it.copy(foilHint = true) }
        }

        darkRun = if (light.luma < LightCheck.DARK) darkRun + 1 else 0
        brightRun = if (light.luma > LightCheck.BRIGHT) brightRun + 1 else 0
        if (!st.settings.autoTorch || torchByHand || st.paused) return
        val now = System.currentTimeMillis()
        if (!st.torch && darkRun >= 10 && now >= torchCooldownUntil && autoTorchCycles < 3) {
            autoTorchOn = true
            darkRun = 0
            _state.update { it.copy(torch = true) }
        } else if (st.torch && autoTorchOn && brightRun >= 15) {
            // Bright again. Wait a bit before turning it back on, so it doesn't flicker.
            autoTorchOn = false
            autoTorchCycles++
            torchCooldownUntil = now + 10_000
            brightRun = 0
            _state.update { it.copy(torch = false) }
        }
    }

    /** Marks the card on screen foil after the "Looks foil" hint. After an auto-add, the copy just added becomes foil too. */
    fun acceptFoilHint() {
        val st = _state.value
        val card = st.current ?: return
        _state.update { it.copy(foilHint = false, cardFoil = true) }
        val head = st.session.firstOrNull()
        if (!st.autoAdd || head == null || head.card.id != card.id || head.foil) return
        viewModelScope.launch {
            val cur = latest[head.key] ?: head
            if (cur.key in removedKeys) return@launch
            val updated = if (cur.deckId != 0L) cur.copy(foil = true) else {
                repo.remove(cur.rowId, cur.count)
                val row = repo.add(cur.card, true, cur.count, cur.boxId)
                cur.copy(foil = true, rowId = row)
            }
            latest[cur.key] = updated
            replaceEverywhere(updated)
        }
    }

    fun dismissFoilHint() = _state.update { it.copy(foilHint = false) }

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
        if (_state.value.autoAdd) markHandled(card)
        addCard(card, qty, readLang = _state.value.currentLang)
    }

    /** Adds a card that was set aside. */
    fun addFromReview(card: ScryfallCard) {
        _state.update { it.copy(review = it.review.filterNot { c -> c.id == card.id }, showReview = it.review.size > 1) }
        addCard(card, ScanBarPrefs.quantity, fromReview = true)
    }

    fun dropFromReview(card: ScryfallCard) =
        _state.update { it.copy(review = it.review.filterNot { c -> c.id == card.id }, showReview = it.review.size > 1) }

    fun showReview(show: Boolean) = _state.update { it.copy(showReview = show) }

    fun setListByBox(on: Boolean) = _state.update { it.copy(listByBox = on) }

    /**
     * Quick quantity: the same card read again within a few seconds of the newest line adds to that
     * line (×4) instead of making a new one. Only when the copies went to the same place.
     */
    private fun mergeTarget(s: ScanUiState, card: ScryfallCard, foil: Boolean, boxId: Long, deckId: Long, rowId: Long): ScanEntry? {
        val head = s.session.firstOrNull() ?: return null
        val cur = latest[head.key] ?: head
        val now = System.currentTimeMillis()
        if (cur.key in removedKeys || now - cur.at > QUICK_MS) return null
        if (cur.card.id != card.id || cur.foil != foil || cur.deckId != deckId) return null
        if (deckId == 0L && (cur.boxId != boxId || cur.rowId != rowId)) return null
        // A pack or sorting session started since: keep its lines separate.
        if (s.pack != null && s.pack.entries.none { it.key == cur.key }) return null
        if (s.sort != null && s.sort.entries.none { it.key == cur.key }) return null
        return cur
    }

    private fun replaceIn(list: List<ScanEntry>, e: ScanEntry) = list.map { if (it.key == e.key) e else it }

    /** Puts the new version of a line into every list that has it. */
    private fun replaceEverywhere(e: ScanEntry) = _state.update {
        it.copy(
            session = replaceIn(it.session, e),
            pack = it.pack?.let { p -> p.copy(entries = replaceIn(p.entries, e)) },
            sort = it.sort?.let { o -> o.copy(entries = replaceIn(o.entries, e)) },
        )
    }

    /** Scan to a deck: the card goes on the deck list, not into the collection. */
    private suspend fun addToDeck(card: ScryfallCard, qty: Int, foil: Boolean, deck: Deck, fromReview: Boolean, auto: Boolean) {
        val listed = runCatching { repo.addToDeckList(deck.id, card.name, qty) }.getOrNull()
        if (listed == null) {
            changeMode { it.copy(target = ScanTarget.COLLECTION, deck = null, status = "Deck not found") }
            return
        }
        val owned = runCatching { repo.ownedCopies(card.oracleId) }.getOrDefault(0)
        val s = _state.value
        val merge = mergeTarget(s, card, foil, 0, deck.id, 0)
        val entry = merge?.copy(count = merge.count + qty, at = System.currentTimeMillis())
            ?: ScanEntry(++entryKeys, 0, card, foil, qty, deck.name, 0, note = "own $owned", deckId = deck.id)
        latest[entry.key] = entry
        _state.update {
            it.copy(
                session = if (merge != null) replaceIn(it.session, entry) else listOf(entry) + it.session,
                addedPulse = it.addedPulse + 1,
                status = "${card.name} → ${deck.name} ×$listed · own $owned",
                error = null,
                wish = null,
                lastBoxName = null,
                current = if (fromReview || auto) it.current else null,
                cardFoil = if (fromReview || auto) it.cardFoil else null,
                foilHint = if (fromReview || auto) it.foilHint else false,
                awaiting = if (fromReview) it.awaiting else false,
            )
        }
        if (!_state.value.autoAdd && !fromReview) resetTracking()
        if (!auto) Undo.offer("Added ${card.name} to ${deck.name}") { takeBack(entry.key, qty) }
    }

    private fun addCard(
        card: ScryfallCard, qty: Int, fromReview: Boolean = false, auto: Boolean = false, fromPage: Boolean = false, readLang: String? = null,
    ) {
        // Language read off the card wins; otherwise the scan bar's choice.
        val lang = readLang ?: ScanBarPrefs.langOrNull
        val s = _state.value
        val foil = s.isFoil(card)
        viewModelScope.launch {
            if (s.target == ScanTarget.CHECK) return@launch
            val deck = s.deck
            if (s.target == ScanTarget.DECK && deck != null) {
                addToDeck(card, qty, foil, deck, fromReview, auto)
                return@launch
            }
            val wish = if (fromReview || fromPage) runCatching { repo.wishlistItem(card.oracleId) }.getOrNull() else s.wish
            // A box that was deleted (say, saved in a preset) falls back to auto-sort.
            val known = boxes.value?.any { it.id == s.boxId } == true
            val facts = card.facts(foil)
            val decision = if (s.boxId == Box.AUTO_SORT || !known) runCatching { repo.pickDecision(facts, card.oracleId) }.getOrNull() else null
            val boxId = if (s.boxId == Box.AUTO_SORT || !known) decision?.boxId else s.boxId
            if (boxId == null) {
                _state.update { it.copy(status = "No box fits ${card.name}", error = "Pick a box above, or add a box that takes any card.") }
                return@launch
            }
            val rowId = repo.add(card, foil, qty, boxId)
            // Language read off the card. Only on a row of its own: copies merged into an existing row keep its language.
            val langSet = lang != null && card.lang == "en" && runCatching { repo.setScanLang(rowId, qty, lang) }.getOrDefault(false)
            if (wish != null) repo.fulfilWish(card.oracleId, qty)
            val box = boxes.value?.firstOrNull { it.id == boxId }
            val boxName = box?.name.orEmpty()
            val spot = runCatching { repo.spotFor(boxId, card.name, facts) }.getOrNull()
            val full = decision?.boxFull ?: (box != null && box.capacity > 0 &&
                (runCatching { repo.boxInfos() }.getOrNull()?.firstOrNull { b -> b.id == boxId }?.isFull == true))
            // Rule preview: the next boxes it would also have fit.
            val also = if (decision != null) {
                runCatching { repo.alsoFits(facts, boxId) }.getOrDefault(emptyList())
                    .mapNotNull { id -> boxes.value?.firstOrNull { b -> b.id == id }?.name }
            } else emptyList()
            val now = _state.value
            val merge = mergeTarget(now, card, foil, boxId, 0, rowId)
            val entry = merge?.copy(count = merge.count + qty, at = System.currentTimeMillis(), lang = merge.lang ?: lang.takeIf { langSet })
                ?: ScanEntry(++entryKeys, rowId, card, foil, qty, boxName, boxId, spot, sortNote(decision), full, lang = lang.takeIf { langSet })
            latest[entry.key] = entry
            _state.update {
                it.copy(
                    session = if (merge != null) replaceIn(it.session, entry) else listOf(entry) + it.session,
                    pack = it.pack?.let { p -> p.copy(entries = if (merge != null) replaceIn(p.entries, entry) else listOf(entry) + p.entries) },
                    sort = it.sort?.let { o ->
                        o.copy(entries = if (merge != null) replaceIn(o.entries, entry) else listOf(entry) + o.entries, done = o.done - boxId)
                    },
                    addedPulse = it.addedPulse + 1,
                    status = "Added ${card.name}" + (if (entry.count > 1) " ×${entry.count}" else "") +
                        (if (boxName.isNotEmpty()) " → ${entry.where}" else ""),
                    error = null,
                    wish = null,
                    lastBoxName = boxName.ifEmpty { null },
                    lastBoxColor = box?.color ?: 0,
                    lastSpot = spot,
                    lastNote = entry.note,
                    lastFull = full,
                    lastAlso = also,
                    current = if (fromReview || auto) it.current else null,
                    cardFoil = if (fromReview || auto) it.cardFoil else null,
                    foilHint = if (fromReview || auto) it.foilHint else false,
                    awaiting = if (fromReview) it.awaiting else false,
                )
            }
            if (!_state.value.autoAdd && !fromReview) resetTracking()
            if (!auto) Undo.offer("Added ${card.name}") { takeBack(entry.key, qty) }
        }
    }

    /** Keeps an offline read to look up and add once Scryfall can be reached. */
    fun saveOffline(qty: Int = ScanBarPrefs.quantity) {
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
            val e = latest[entry.key] ?: entry
            val before = if (e.deckId == 0L) repo.snapshot(listOf(e.rowId)) else emptyList()
            val inSort = _state.value.sort?.entries?.any { it.key == e.key } == true
            removeEntry(e)
            Undo.offer("Removed ${e.card.name}") {
                if (e.deckId != 0L) repo.addToDeckList(e.deckId, e.card.name, e.count) else repo.restore(before)
                removedKeys.remove(e.key)
                latest[e.key] = e
                _state.update {
                    it.copy(
                        session = upsert(it.session, listOf(e)),
                        sort = it.sort?.let { o -> if (inSort) o.copy(entries = upsert(o.entries, listOf(e))) else o },
                    )
                }
            }
        }
    }

    private fun upsert(list: List<ScanEntry>, olds: List<ScanEntry>): List<ScanEntry> {
        val keys = olds.map { it.key }.toSet()
        return (list.filterNot { it.key in keys } + olds).sortedByDescending { it.key }
    }

    /** Takes copies back out of wherever they went: the collection or the deck list. */
    private suspend fun unstore(e: ScanEntry, qty: Int) {
        if (e.deckId != 0L) runCatching { repo.addToDeckList(e.deckId, e.card.name, -qty) } else repo.remove(e.rowId, qty)
    }

    /** Takes [qty] copies off a line, removing the line when none are left. */
    private suspend fun takeBack(key: Long, qty: Int) {
        val cur = latest[key] ?: return
        if (key in removedKeys) return
        if (qty >= cur.count) {
            removeEntry(cur)
            return
        }
        unstore(cur, qty)
        val n = cur.copy(count = cur.count - qty)
        latest[key] = n
        replaceEverywhere(n)
        _state.update { it.copy(status = "Removed ${cur.card.name}") }
    }

    /** Undo last N: takes back the last [n] cards scanned, newest first. */
    fun removeLast(n: Int) {
        viewModelScope.launch {
            val st = _state.value
            val picks = mutableListOf<Pair<ScanEntry, Int>>()
            var left = n
            for (e in st.session) {
                if (left <= 0) break
                val cur = latest[e.key] ?: e
                val q = minOf(left, cur.count)
                picks += cur to q
                left -= q
            }
            if (picks.isEmpty()) return@launch
            val before = repo.snapshot(picks.filter { it.first.deckId == 0L }.map { it.first.rowId })
            val packKeys = st.pack?.entries?.map { it.key }?.toSet().orEmpty()
            val sortKeys = st.sort?.entries?.map { it.key }?.toSet().orEmpty()
            picks.forEach { (e, q) -> takeBack(e.key, q) }
            val total = picks.sumOf { it.second }
            _state.update { it.copy(status = "Removed last $total") }
            Undo.offer("Removed last $total") {
                if (before.isNotEmpty()) repo.restore(before)
                val olds = picks.map { it.first }
                olds.filter { it.deckId != 0L }.forEach { e ->
                    runCatching { repo.addToDeckList(e.deckId, e.card.name, picks.first { p -> p.first.key == e.key }.second) }
                }
                olds.forEach { removedKeys.remove(it.key); latest[it.key] = it }
                _state.update {
                    it.copy(
                        session = upsert(it.session, olds),
                        pack = it.pack?.let { p -> p.copy(entries = upsert(p.entries, olds.filter { e -> e.key in packKeys })) },
                        sort = it.sort?.let { o -> o.copy(entries = upsert(o.entries, olds.filter { e -> e.key in sortKeys })) },
                    )
                }
            }
        }
    }

    private suspend fun removeEntry(entry: ScanEntry) {
        // Already taken back (say, from the list before tapping Undo): don't remove another copy.
        // Clearing the list doesn't count, so Undo still works after it.
        if (!removedKeys.add(entry.key)) return
        unstore(latest[entry.key] ?: entry, (latest[entry.key] ?: entry).count)
        _state.update {
            it.copy(
                session = it.session.filterNot { e -> e.key == entry.key },
                pack = it.pack?.let { p -> p.copy(entries = p.entries.filterNot { e -> e.key == entry.key }) },
                sort = it.sort?.let { o -> o.copy(entries = o.entries.filterNot { e -> e.key == entry.key }) },
                status = "Removed ${entry.card.name}",
            )
        }
    }

    // Pack mode

    fun showPackStart(show: Boolean) = _state.update { it.copy(showPackStart = show) }

    fun startPack(name: String, cost: Double?) = _state.update {
        it.copy(
            pack = PackDraft(name.ifBlank { "Pack" }, cost), showPackStart = false, pageMode = false, page = null,
            status = "Opening ${name.ifBlank { "a pack" }} · scan each card",
        )
    }

    fun cancelPack() = _state.update { it.copy(pack = null, status = "Point your camera at a card") }

    /** Ends the pack. Cards stay in the collection; nothing is saved to history. */
    fun finishPack() {
        val s = _state.value
        val p = s.pack ?: return
        val n = s.packEntries.sumOf { it.count }
        val total = if (AppSettings.showPrices) " · " + formatPrice(s.packValue) else ""
        _state.update { it.copy(pack = null, status = if (n == 0) "Point your camera at a card" else "${p.name}: $n cards$total") }
    }

    private fun formatPrice(v: Double) = "$" + "%.2f".format(java.util.Locale.US, v)

    // Sorting session

    fun startSort() = _state.update {
        it.copy(sort = SortSession(), status = "Sorting · scan the pile")
    }

    fun showSort(show: Boolean) = _state.update { it.copy(showSort = show) }

    /** Checks a box's stack off as filed, or back on. */
    fun toggleSortDone(boxId: Long) = _state.update {
        it.copy(sort = it.sort?.let { o -> o.copy(done = if (boxId in o.done) o.done - boxId else o.done + boxId) })
    }

    /** Ends the session. Cards stay in their boxes. */
    fun endSort() = _state.update { it.copy(sort = null, showSort = false, status = "Point your camera at a card") }

    // Set lock

    fun showSetLock(show: Boolean) {
        _state.update { it.copy(showSetLock = show) }
        if (show) loadSets()
    }

    /** Paper sets for the set picker, newest first. Empty until loaded or when offline. */
    private val _sets = MutableStateFlow<List<ScryfallSet>>(emptyList())
    val sets: StateFlow<List<ScryfallSet>> = _sets

    fun loadSets() {
        if (_sets.value.isNotEmpty()) return
        viewModelScope.launch {
            val list = runCatching { repo.sets() }.getOrDefault(emptyList())
            _sets.value = list.filterNot { it.digital }.sortedByDescending { it.releasedAt.orEmpty() }
        }
    }

    fun setLock(code: String?) {
        SortPrefs.updateSetLock(code?.trim())
        _state.update { it.copy(showSetLock = false) }
        resetTracking()
    }

    // Binder page mode

    fun setPageMode(on: Boolean) {
        pageJob?.cancel()
        _state.update {
            it.copy(pageMode = on, wantPage = false, pageLoading = false, page = null, current = null, awaiting = false, wish = null,
                status = if (on) "Fill the frame with a binder page, then tap Capture page" else "Point your camera at a card")
        }
        resetTracking()
    }

    /** Reads the next camera frame as a whole page. */
    fun capturePage() = _state.update { it.copy(wantPage = true, paused = false, status = "Hold still…") }

    /** Stops waiting for a page read, if it's taking too long. */
    fun cancelCapture() = _state.update {
        pageJob?.cancel()
        it.copy(wantPage = false, pageLoading = false, status = "Fill the frame with a binder page, then tap Capture page")
    }

    /** Called by the analyzer with one guess per pocket, left to right, top to bottom. */
    private var pageJob: kotlinx.coroutines.Job? = null

    fun onPage(guesses: List<CardTextGuess>) {
        _state.update { it.copy(wantPage = false, pageLoading = true, status = "Looking up the page…") }
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            val slots = guesses.mapIndexed { i, g ->
                async {
                    val read = g.nameCandidates.firstOrNull() ?: g.setCode?.let { "$it #${g.collectorNumber}" }
                    val card = if (g.key == null) null else runCatching { repo.identify(g)?.card }.getOrNull()
                    PageSlot(i, read, card, card != null)
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

    fun togglePageSlot(index: Int) = _state.update {
        it.copy(page = it.page?.map { s -> if (s.index == index && s.card != null) s.copy(selected = !s.selected) else s })
    }

    fun closePage() = _state.update { it.copy(page = null, status = "Fill the frame with a binder page, then tap Capture page") }

    /** Adds the ticked pockets, one copy each. */
    fun addPage() {
        val cards = _state.value.page.orEmpty().filter { it.selected }.mapNotNull { it.card }
        _state.update { it.copy(page = null) }
        cards.forEach { addCard(it, 1, auto = true, fromPage = true) }
        _state.update { it.copy(status = "Added ${cards.size} cards · turn the page and capture again") }
    }

    /** Clears the list on screen. Cards stay in the collection. */
    fun clearList() = _state.update { it.copy(session = emptyList(), showList = false) }

    fun skip() {
        val auto = _state.value.autoAdd
        _state.value.current?.let { if (auto) markHandled(it) }
        _state.update {
            it.copy(
                current = null, cardFoil = null, offline = null, wish = null, error = null, awaiting = false, foilHint = false, owned = null,
                currentLang = null, status = "Point your camera at a card",
            )
        }
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
    fun setFoil(on: Boolean) {
        _state.update { it.copy(cardFoil = null) }
        updateSettings { it.copy(foil = on) }
    }

    /** Flips foil for the card on screen only, so Add uses it. */
    fun toggleCardFoil() = _state.update { s ->
        val card = s.current
        if (card == null || !card.hasFoil || !card.hasNonFoil) s else s.copy(cardFoil = !s.isFoil(card))
    }
    /** Flashlight by hand: low light leaves it alone from now on. */
    fun setTorch(on: Boolean) {
        torchByHand = true
        autoTorchOn = false
        _state.update { it.copy(torch = on) }
    }
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

    // Stack, deck and price check modes

    fun setStack(on: Boolean) = changeMode {
        it.copy(
            stack = on,
            target = if (on && it.target == ScanTarget.CHECK) ScanTarget.COLLECTION else it.target,
            current = null, awaiting = false, foilHint = false,
            status = if (on) "Stack mode · flip cards one by one" else "Point your camera at a card",
        )
    }

    fun showDeckPick(show: Boolean) = _state.update { it.copy(showDeckPick = show) }

    fun scanToDeck(deck: Deck) = changeMode {
        it.copy(
            target = ScanTarget.DECK, deck = deck, showDeckPick = false, current = null, awaiting = false, owned = null,
            status = "Adding to ${deck.name}",
        )
    }

    /** Makes an empty deck list and scans into it. */
    fun newDeck(name: String) {
        val clean = name.trim().ifEmpty { return }
        viewModelScope.launch {
            val id = runCatching { repo.saveDeck(0, clean, "") }.getOrNull() ?: return@launch
            scanToDeck(Deck(id = id, name = clean, text = ""))
        }
    }

    fun startCheck() = changeMode {
        it.copy(
            target = ScanTarget.CHECK, deck = null, stack = false, current = null, awaiting = false, owned = null,
            status = "Price check · nothing is added",
        )
    }

    /** Back to adding to the collection. */
    fun stopTarget() = changeMode {
        it.copy(target = ScanTarget.COLLECTION, deck = null, current = null, awaiting = false, owned = null, status = "Point your camera at a card")
    }

    private companion object {
        /** Same card again within this long adds to the newest line (quick quantity). */
        const val QUICK_MS = 6_000L
        /** Text read but no match for this long: offer to save it for later. */
        const val UNKNOWN_AFTER_MS = 3_000L
    }
}

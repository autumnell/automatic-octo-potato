package com.mtgscanner.ui.scan

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
import com.mtgscanner.data.ownedCopies
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.CardTextParser
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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
data class PageSlot(
    val index: Int, val read: String?, val card: ScryfallCard?, val selected: Boolean,
    /** Language read off the pocket's card, when not English. */
    val lang: String? = null,
)

/** What the camera read, for the status line: the name, else the footer ("MOM #123"). */
fun readLabel(g: CardTextGuess): String? =
    g.nameCandidates.firstOrNull() ?: listOfNotNull(g.setCode, g.collectorNumber?.let { "#$it" }).joinToString(" ").ifEmpty { null }

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
    /** Glare over the card's name or footer in recent frames. */
    val glare: Boolean = false,
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

    init {
        // Copies and language for the scan bar; read once, before anything uses them.
        ScanBarPrefs.init(repo.appContext)
    }

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
                // Saved offline scans are looked up as soon as the phone is back online, even with the scanner closed.
                if (p.any { !it.unknown }) runCatching { PendingScanWork.schedule(repo.appContext) }
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
        // Try saved offline scans again whenever the scanner opens, and when the network comes back.
        viewModelScope.launch { runCatching { repo.resolvePending() } }
        // Set lock on: make sure its art index is here (a failed download is tried again each time).
        SortPrefs.setLock?.let { repo.artIndex.enqueue(listOf(it)) }
    }

    // Frame-to-frame bookkeeping for the camera analyzer.
    private var lastKey: String? = null
    /** Name and footer of the card in view. Frames agree when either matches, as the footer is only read on some. */
    private var lastName: String? = null
    private var lastFooter: String? = null
    private var sameKeyFrames = 0
    private var emptyFrames = 0
    /** Keys already looked up for the card in view. */
    private val lookedUp = HashSet<String>()
    /** Bumps when tracking resets (pause, skip, mode change), so a lookup that was under way is dropped. */
    private var trackGen = 0
    private var lastAutoAddedId: String? = null
    /** Art seen when the last card was auto-added, and frames in a row since with clearly different art. */
    private var lastAddedArt: Long? = null
    private var artChangeFrames = 0
    /** The scan list line auto-added for the card on screen, so "Printing" and "Looks foil" can fix it. */
    private var autoEntryKey: Long? = null
    // The same card can come back as a different printing when the footer is only read on some frames,
    // so "already added while in view" also matches by card name (oracle id).
    private var lastAutoAddedOracle: String? = null
    private fun alreadyHandled(card: ScryfallCard) =
        card.id == lastAutoAddedId || (card.oracleId != null && card.oracleId == lastAutoAddedOracle)
    private fun markHandled(card: ScryfallCard, art: Long? = null) {
        lastAutoAddedId = card.id
        lastAutoAddedOracle = card.oracleId
        lastAddedArt = art
        artChangeFrames = 0
    }
    private val removedKeys = HashSet<Long>()
    private var entryKeys = 0L
    /** Every scan list line by key, as it is now (copies can be added or taken back). */
    private val latest = HashMap<Long, ScanEntry>()

    // Unknown card queue: when text keeps being read but matches nothing.
    private var noMatchSince = 0L
    private var noMatchGuess: CardTextGuess? = null

    // Pulses already played, so sounds and flashes don't replay when the screen comes back.
    private var seenAdded = 0
    private var seenAside = 0

    /** True once for each new add pulse. */
    fun freshAdded(pulse: Int): Boolean = (pulse > seenAdded).also { if (it) seenAdded = pulse }

    /** True once for each new set-aside pulse. */
    fun freshAside(pulse: Int): Boolean = (pulse > seenAside).also { if (it) seenAside = pulse }

    /** The analyzer skips frames while this is true. */
    val isPaused: Boolean
        get() = _state.value.let {
            // The first-run hints cover the screen: don't add cards behind them.
            if (it.showSort || it.showSetLock || it.showDeckPick || !ScanBarPrefs.hintsSeen) true
            else if (it.pageMode) it.paused || !it.wantPage || it.pageLoading || it.page != null || it.showSettings || it.showList ||
                it.showNewBox || boxes.value.isNullOrEmpty()
            else it.paused || ((!it.autoAdd || it.awaiting) && (it.current != null || it.offline != null)) || it.lookingUp ||
                it.showPrintings || it.showSettings || it.showList || it.showNewBox || it.showReview || boxes.value.isNullOrEmpty()
        }

    /** Called on the camera thread; the work happens on the main thread so it never races the buttons. */
    fun onGuess(guess: CardTextGuess) {
        viewModelScope.launch { handleGuess(guess) }
    }

    /** Frames in a row with glare, and without. */
    private var glareRun = 0
    private var clearRun = 0

    /** Shows "Tilt to reduce glare" after two frames with glare over the name or footer; hides it after two without. */
    private fun trackGlare(guess: CardTextGuess) {
        if (guess.glare) { glareRun++; clearRun = 0 } else { clearRun++; glareRun = 0 }
        val show = when {
            glareRun >= 2 -> true
            clearRun >= 2 -> false
            else -> return
        }
        if (_state.value.glare != show) _state.update { it.copy(glare = show) }
    }

    private fun handleGuess(guess: CardTextGuess) {
        trackGlare(guess)
        val key = guess.key
        if (key == null && guess.artOnly.isNotEmpty()) {
            // A card is there, just no readable text: not an empty frame.
            emptyFrames = 0
            handleArt(guess)
            return
        }
        artVote.reset()
        if (key == null) {
            emptyFrames++
            // The card has left the frame: allow the same card to be auto-added again.
            if (emptyFrames >= 3) {
                lastAutoAddedId = null
                lastAutoAddedOracle = null
                lastAddedArt = null
                lastKey = null
                lastName = null
                lastFooter = null
                lookedUp.clear()
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
        val name = guess.nameCandidates.firstOrNull()?.let { CardTextParser.normalize(it) }
        val footer = if (guess.setCode != null && guess.collectorNumber != null) key.takeIf { '#' in it } else null
        val agrees = (name != null && name == lastName) || (footer != null && footer == lastFooter)
        if (agrees) {
            sameKeyFrames++
            if (name != null) lastName = name
            if (footer != null) lastFooter = footer
        } else {
            sameKeyFrames = 1
            lastName = name
            lastFooter = footer
            lookedUp.clear()
        }
        lastKey = key
        _state.update { it.copy(reading = readLabel(guess)) }
        // Text keeps coming but nothing matched for a few seconds: offer to keep it for later.
        if (noMatchSince > 0 && System.currentTimeMillis() - noMatchSince >= UNKNOWN_AFTER_MS) offerUnknown()
        // A stack of the same name: clearly different artwork is a different printing, so a new card.
        val art = guess.artHash
        val added = lastAddedArt
        if (_state.value.autoAdd && art != null && added != null && lastAutoAddedOracle != null) {
            artChangeFrames = if (ImageHash.distance(art, added) > ART_CHANGED) artChangeFrames + 1 else 0
            if (artChangeFrames >= 2) {
                lastAutoAddedId = null
                lastAutoAddedOracle = null
                lastAddedArt = null
                lookedUp.clear()
            }
        }

        // Wait for two frames that agree, so a blurry half-read doesn't trigger a lookup.
        if (sameKeyFrames < 2 || key in lookedUp) return
        // Already looked up by the exact footer: a name-only read adds nothing.
        if (footer == null && lookedUp.any { '#' in it }) return
        lookedUp += key
        lookup(guess)
    }

    private fun lookup(guess: CardTextGuess) {
        _state.update { it.copy(lookingUp = true, status = "Looking it up…", error = null, offline = null) }
        val gen = trackGen
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching { repo.identify(guess) }
            logLookup(guess, result, System.currentTimeMillis() - started)
            // Paused, skipped or switched mode meanwhile: the answer is no longer wanted.
            if (gen != trackGen || _state.value.paused) {
                _state.update { it.copy(lookingUp = false) }
                return@launch
            }
            result.onFailure { e ->
                // Offline: keep the set lock with the saved read.
                val offline = if (isOffline(e)) {
                    repo.offlineRead(guess)?.let { r -> SortPrefs.setLock?.let { lock -> r.copy(setCode = lock) } ?: r }
                } else null
                onLookupFailed(e, offline, guess.key, gen)
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
                onIdentified(id, guess.lang, guess.artHash)
            }
        }
    }

    private fun isOffline(e: Throwable) = e is java.io.IOException && e !is ScryfallException

    /** A lookup that failed: offer to save the read for later when offline, else say so. [key] is retried after a pause. */
    private fun onLookupFailed(e: Throwable, offline: OfflineRead?, key: String?, gen: Int) {
        val autoSaved = offline != null && _state.value.autoAdd
        // Busy, rate limited or offline: wait a few seconds before trying this card again.
        // Saved for later while auto-adding: it's handled until it leaves the frame.
        if (!autoSaved) viewModelScope.launch { delay(3_000); if (gen == trackGen && key != null) lookedUp.remove(key) }
        if (offline != null) {
            _state.update { it.copy(lookingUp = false, offline = offline, status = "Offline · ${offline.name}") }
            if (autoSaved && offline.name != lastAutoAddedId) {
                lastAutoAddedId = offline.name
                saveOffline()
            }
        } else {
            _state.update { it.copy(lookingUp = false, status = "Couldn't reach Scryfall", error = e.message) }
        }
    }

    // Art match: cards with no readable text, found by their artwork in downloaded art indexes

    private val artVote = com.mtgscanner.scan.ArtVote(needed = 2)

    /** The analyzer hashes cards with no readable text only when there's an index to match them against. */
    fun wantsArt(): Boolean = repo.artIndex.hasAny

    /** The art model's embedding of a picture of artwork; null until the model is downloaded. Camera thread. */
    fun embedArt(art: android.graphics.Bitmap): ByteArray? = repo.artEmbedder.embed(art)

    private fun handleArt(guess: CardTextGuess) {
        val hit = repo.artIndex.match(guess.artOnly.toLongArray(), SortPrefs.setLock, guess.artOnlyEmbeddings)
        // The same card on two frames in a row, so one lucky frame doesn't add the wrong card.
        val agreed = artVote.offer(hit?.id) ?: return
        if (hit == null || hit.id != agreed) return
        val key = "art:$agreed"
        if (key in lookedUp) return
        if (lookedUp.none { it.startsWith("art:") }) lookedUp.clear()
        lookedUp += key
        lastKey = key
        lastName = null
        lastFooter = null
        _state.update { it.copy(reading = hit.name) }
        lookupArt(hit, guess.artOnly.first())
    }

    /** Fetches the printing the art matched. It's the exact printing, so it counts as sure. */
    private fun lookupArt(hit: com.mtgscanner.scan.ArtHit, art: Long) {
        _state.update { it.copy(lookingUp = true, status = "Looking it up…", error = null, offline = null) }
        val gen = trackGen
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching { repo.api.byId(hit.id) }
            if (AppSettings.scanLogUnlocked) {
                val outcome = when {
                    result.exceptionOrNull()?.let { isOffline(it) } == true -> "offline"
                    result.isFailure -> "error"
                    result.getOrNull() == null -> "no match"
                    else -> "art match"
                }
                runCatching {
                    repo.scanLog.add(
                        com.mtgscanner.scan.ScanLogEntry(
                            art = true, outcome = outcome, matchName = hit.name, matchSet = hit.set, matchNumber = hit.number,
                            ms = System.currentTimeMillis() - started, note = "art ${hit.distance} bits, next ${hit.runnerUp}",
                        ),
                    )
                }
            }
            if (gen != trackGen || _state.value.paused) {
                _state.update { it.copy(lookingUp = false) }
                return@launch
            }
            val card = result.getOrElse { e ->
                // Offline: the art said exactly which printing, so save that.
                val offline = if (isOffline(e)) OfflineRead(hit.name, hit.set, hit.number, scryfallId = hit.id) else null
                onLookupFailed(e, offline, "art:${hit.id}", gen)
                return@launch
            }
            if (card == null) {
                _state.update { it.copy(lookingUp = false, status = "No match yet · hold the card flat and well lit") }
                return@launch
            }
            noMatchSince = 0L
            noMatchGuess = null
            onIdentified(com.mtgscanner.data.Identification(card, confident = true, exact = true), null, art)
        }
    }

    /** Decides what happens to a match: add it, skip it, set it aside, or stop and ask. */
    private suspend fun onIdentified(id: com.mtgscanner.data.Identification, lang: String? = null, art: Long? = null) {
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
                markHandled(card, art)
                _state.update {
                    it.copy(lookingUp = false, current = null, skipped = it.skipped + if (counted) 1 else 0, status = "Skipped ${card.name} ($reason)")
                }
                return
            }
            if (!s.accepts(id.exact, id.confident)) {
                when (s.unsure) {
                    UnsureAction.ASIDE -> {
                        val fresh = !alreadyHandled(card) && _state.value.review.none { it.id == card.id }
                        markHandled(card, art)
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
        if (_state.value.current?.id != card.id) autoEntryKey = null
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
            markHandled(card, art)
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
        viewModelScope.launch { swapLine(head.key, card, foil = true) }
    }

    /**
     * Swaps the copies on a scan list line for another printing or finish: takes them out and adds
     * them again with the same language, letting auto-sort pick the box anew (a foil can go elsewhere).
     */
    private suspend fun swapLine(key: Long, card: ScryfallCard, foil: Boolean) {
        val cur = latest[key] ?: _state.value.session.firstOrNull { it.key == key } ?: return
        if (cur.key in removedKeys) return
        val updated = if (cur.deckId != 0L) cur.copy(card = card, foil = foil) else {
            repo.remove(cur.rowId, cur.count)
            val facts = card.facts(foil)
            val chosen = chooseBox(card, facts, _state.value.boxId)
            val boxId = chosen?.first ?: cur.boxId
            val row = repo.add(card, foil, cur.count, boxId, lang = rowLang(card, cur.lang))
            val box = boxes.value?.firstOrNull { it.id == boxId }
            val spot = runCatching { repo.spotFor(boxId, card.name, facts) }.getOrNull()
            cur.copy(
                card = card, foil = foil, rowId = row, boxId = boxId, boxName = box?.name ?: cur.boxName, spot = spot,
                note = if (chosen != null) sortNote(chosen.second) else cur.note,
            )
        }
        latest[cur.key] = updated
        replaceEverywhere(updated)
        if (updated.boxId != cur.boxId) {
            val box = boxes.value?.firstOrNull { it.id == updated.boxId }
            _state.update {
                it.copy(
                    addedPulse = it.addedPulse + 1, lastBoxName = updated.boxName.ifEmpty { null }, lastBoxColor = box?.color ?: 0,
                    lastSpot = updated.spot, lastNote = updated.note, lastAlso = emptyList(),
                )
            }
        }
    }

    /** The box for a card: the one picked in the header, or by the rules when auto-sorting (or the picked box is gone). */
    private suspend fun chooseBox(card: ScryfallCard, facts: CardFacts, picked: Long): Pair<Long, SortDecision?>? {
        val known = boxes.value?.any { it.id == picked } == true
        if (picked != Box.AUTO_SORT && known) return picked to null
        val d = runCatching { repo.pickDecision(facts, card.oracleId) }.getOrNull() ?: return null
        return d.boxId to d
    }

    /** The language a new row records: the printing's own, else what was read or picked in the scan bar. */
    private fun rowLang(card: ScryfallCard, lang: String?): String = if (card.lang != "en") card.lang else lang ?: "en"

    fun dismissFoilHint() = _state.update { it.copy(foilHint = false) }

    private fun logLookup(guess: CardTextGuess, result: Result<com.mtgscanner.data.Identification?>, ms: Long) {
        // The log is hidden until unlocked in Settings; don't write it for nothing.
        if (!AppSettings.scanLogUnlocked) return
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

    /** The Add button's add, still running: a second tap doesn't add the card twice. */
    private var addJob: Job? = null

    fun add(qty: Int = 1) {
        val card = _state.value.current ?: return
        if (addJob?.isActive == true) return
        // In auto-add mode, don't add this card again while it's still in view.
        if (_state.value.autoAdd) markHandled(card)
        addJob = addCard(card, qty, readLang = _state.value.currentLang)
    }

    /** Stack mode: another copy of the last card, when copies come one after another and look the same. */
    fun sameAgain() {
        val head = _state.value.session.firstOrNull() ?: return
        val cur = latest[head.key] ?: head
        if (cur.key in removedKeys || addJob?.isActive == true) return
        addJob = addCard(cur.card, 1, auto = true, readLang = cur.lang, foil = cur.foil)
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
        foil: Boolean? = null,
    ): Job {
        // Language read off the card wins; otherwise the scan bar's choice.
        val lang = readLang ?: ScanBarPrefs.langOrNull
        val s = _state.value
        val isFoil = foil ?: s.isFoil(card)
        return viewModelScope.launch {
            if (s.target == ScanTarget.CHECK) return@launch
            val deck = s.deck
            if (s.target == ScanTarget.DECK && deck != null) {
                addToDeck(card, qty, isFoil, deck, fromReview, auto)
                return@launch
            }
            val wish = if (fromReview || fromPage) runCatching { repo.wishlistItem(card.oracleId) }.getOrNull() else s.wish
            val facts = card.facts(isFoil)
            // A box that was deleted (say, saved in a preset) falls back to auto-sort.
            val chosen = chooseBox(card, facts, s.boxId)
            if (chosen == null) {
                _state.update { it.copy(status = "No box fits ${card.name}", error = "Pick a box above, or add a box that takes any card.") }
                return@launch
            }
            val (boxId, decision) = chosen
            val rowId = repo.add(card, isFoil, qty, boxId, lang = rowLang(card, lang))
            if (wish != null) repo.fulfilWish(card.oracleId, qty)
            val box = boxes.value?.firstOrNull { it.id == boxId }
            val boxName = box?.name.orEmpty()
            val spot = runCatching { repo.spotFor(boxId, card.name, facts) }.getOrNull()
            // One look at the boxes for both "full" and the rule preview.
            val needInfos = decision != null || (box != null && box.capacity > 0)
            val infos = if (needInfos) runCatching { repo.boxInfos() }.getOrNull() else null
            val full = decision?.boxFull ?: (infos?.firstOrNull { b -> b.id == boxId }?.isFull == true)
            // Rule preview: the next boxes it would also have fit.
            val also = if (decision != null) {
                infos.orEmpty().filter { it.id != boxId && !it.rules.isCatchAll && it.rules.matches(facts) }.take(3)
                    .mapNotNull { b -> boxes.value?.firstOrNull { it.id == b.id }?.name }
            } else emptyList()
            val shownLang = lang.takeIf { card.lang == "en" }
            val now = _state.value
            val merge = mergeTarget(now, card, isFoil, boxId, 0, rowId)?.takeIf { it.lang == shownLang }
            val entry = merge?.copy(count = merge.count + qty, at = System.currentTimeMillis())
                ?: ScanEntry(++entryKeys, rowId, card, isFoil, qty, boxName, boxId, spot, sortNote(decision), full, lang = shownLang)
            latest[entry.key] = entry
            if (auto && !fromPage && !fromReview && card.id == _state.value.current?.id) autoEntryKey = entry.key
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
        // Cleared now, so a second tap can't save it twice.
        _state.update { it.copy(offline = null) }
        // Keep what the live scanner would have used: the language read (or picked in the scan bar), and
        // auto-add's skip rules, so the card is skipped the same way once it's looked up.
        val withLang = read.copy(lang = read.lang ?: ScanBarPrefs.langOrNull)
        val skip = if (s.autoAdd) s.settings.skipRules(AppSettings.showPrices) else null
        viewModelScope.launch {
            repo.savePending(withLang, s.foil, qty, s.boxId, skip)
            _state.update { it.copy(addedPulse = it.addedPulse + 1, status = "Saved ${read.name} for later") }
            // While auto-adding, the card is still in view: don't look it up again.
            if (!s.autoAdd) resetTracking()
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
            val before = if (e.deckId == 0L) rowsBefore(listOf(e.rowId)) else emptyMap()
            val st = _state.value
            val inSort = st.sort?.entries?.any { it.key == e.key } == true
            val inPack = st.pack?.entries?.any { it.key == e.key } == true
            removeEntry(e)
            Undo.offer("Removed ${e.card.name}") {
                if (e.deckId != 0L) repo.addToDeckList(e.deckId, e.card.name, e.count) else putBack(before, mapOf(e.rowId to e.count))
                removedKeys.remove(e.key)
                latest[e.key] = e
                _state.update {
                    it.copy(
                        session = upsert(it.session, listOf(e)),
                        pack = it.pack?.let { p -> if (inPack) p.copy(entries = upsert(p.entries, listOf(e))) else p },
                        sort = it.sort?.let { o -> if (inSort) o.copy(entries = upsert(o.entries, listOf(e))) else o },
                    )
                }
            }
        }
    }

    /** The rows as they are now, to put back on undo. */
    private suspend fun rowsBefore(rowIds: List<Long>): Map<Long, CollectionCard> =
        rowIds.distinct().mapNotNull { id -> runCatching { repo.cardOnce(id) }.getOrNull()?.let { id to it } }.toMap()

    /**
     * Undoes taking [taken] copies off each row. A row still there gets its copies back; a row that was
     * deleted comes back as it was. Other rows of the same printing (copies added since) are left alone.
     */
    private suspend fun putBack(before: Map<Long, CollectionCard>, taken: Map<Long, Int>) {
        taken.forEach { (rowId, qty) ->
            val now = repo.cardOnce(rowId)
            when {
                now != null -> repo.setQuantity(rowId, now.quantity + qty)
                else -> before[rowId]?.let { repo.dao.putCards(listOf(it)) }
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
            val inCollection = picks.filter { it.first.deckId == 0L }
            val before = rowsBefore(inCollection.map { it.first.rowId })
            // Copies taken per row (two lines can share a row).
            val taken = inCollection.groupBy { it.first.rowId }.mapValues { (_, l) -> l.sumOf { it.second } }
            val packKeys = st.pack?.entries?.map { it.key }?.toSet().orEmpty()
            val sortKeys = st.sort?.entries?.map { it.key }?.toSet().orEmpty()
            picks.forEach { (e, q) -> takeBack(e.key, q) }
            val total = picks.sumOf { it.second }
            _state.update { it.copy(status = "Removed last $total") }
            Undo.offer("Removed last $total") {
                putBack(before, taken)
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
        // A locked set gets its art index, so cards with no readable text can still be found.
        SortPrefs.setLock?.let { repo.artIndex.enqueue(listOf(it)) }
    }

    // Art indexes

    val artSets: StateFlow<List<com.mtgscanner.scan.ArtSetInfo>> get() = repo.artIndex.sets
    val artDownload: StateFlow<com.mtgscanner.scan.ArtDownload?> get() = repo.artIndex.download
    val artFailed: StateFlow<String?> get() = repo.artIndex.failed

    fun downloadArt(codes: Collection<String>) = repo.artIndex.enqueue(codes)
    fun deleteArt(code: String) = repo.artIndex.delete(code)
    fun cancelArtDownloads() = repo.artIndex.cancelQueue()

    /** Set codes of the cards in the collection, most cards first. */
    suspend fun ownedSets(): List<String> =
        repo.observeAll().first().groupBy { it.setCode.lowercase() }.entries.sortedByDescending { it.value.size }.map { it.key }

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

    fun togglePageSlot(index: Int) = _state.update {
        it.copy(page = it.page?.map { s -> if (s.index == index && s.card != null) s.copy(selected = !s.selected) else s })
    }

    fun closePage() = _state.update { it.copy(page = null, status = "Fill the frame with a binder page, then tap Capture page") }

    /** Adds the ticked pockets, with the scan bar's copies, one after another so box capacity stays right. */
    fun addPage() {
        val slots = _state.value.page.orEmpty().filter { it.selected && it.card != null }
        _state.update { it.copy(page = null) }
        val qty = ScanBarPrefs.quantity
        viewModelScope.launch {
            slots.forEach { slot ->
                val card = slot.card ?: return@forEach
                addCard(card, qty, auto = true, fromPage = true, readLang = slot.lang?.takeIf { card.lang == "en" }).join()
            }
            _state.update { it.copy(status = "Added ${slots.size * qty} cards · turn the page and capture again") }
        }
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
        if (!auto) resetTracking() else trackGen++
    }

    private fun resetTracking() {
        lastKey = null
        lastName = null
        lastFooter = null
        lookedUp.clear()
        sameKeyFrames = 0
        trackGen++
    }

    fun togglePause() {
        trackGen++
        _state.update {
            val p = !it.paused
            it.copy(paused = p, status = if (p) "Paused" else "Point your camera at a card", reading = null, lookingUp = false)
        }
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
        val st = _state.value
        val before = st.current
        // Auto-add already added the card on screen: swap that copy for the right printing.
        val head = st.session.firstOrNull()
        if (before != null && before.id != card.id && st.autoAdd && head != null && head.key == autoEntryKey && head.card.id == before.id) {
            markHandled(card)
            val foil = if (head.foil) card.hasFoil || !card.hasNonFoil else !card.hasNonFoil
            viewModelScope.launch { swapLine(head.key, card, foil) }
        }
        if (before != null && before.id != card.id && AppSettings.scanLogUnlocked) {
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
        /** Art hashes further apart than this (of 64 bits) are different artwork. */
        const val ART_CHANGED = 22
    }
}

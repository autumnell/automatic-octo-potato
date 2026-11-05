package com.mtgscanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.matchPicture
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
import com.mtgscanner.data.printingsFor
import com.mtgscanner.data.rememberPick
import com.mtgscanner.data.cardById
import com.mtgscanner.data.freshCard
import com.mtgscanner.data.offlineRead
import com.mtgscanner.data.prefetch
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

class ScanViewModel(internal val repo: CollectionRepository) : ViewModel() {

    init {
        // Copies and language for the scan bar; read once, before anything uses them.
        ScanBarPrefs.init(repo.appContext)
    }

    internal val _state = MutableStateFlow(
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
        // Box check counts every card, basics too, hands-free.
        st.target == ScanTarget.BOX -> s.copy(
            autoAdd = true, unsure = UnsureAction.ASIDE, skipBasics = false, skipTokens = false, skipUnder = null,
            sound = if (st.stack) ScanSound.BEEP else s.sound, asideSound = ScanSound.DOUBLE,
        )
        // Removing ignores the skip rules: a sold basic land still comes out.
        st.target == ScanTarget.REMOVE -> s.copy(skipBasics = false, skipTokens = false, skipUnder = null)
        // Batch keeps scanning by itself; every card waits on the list to be checked.
        st.batch != null -> s.copy(autoAdd = true, unsure = UnsureAction.ASIDE)
        st.stack -> s.copy(autoAdd = true, unsure = UnsureAction.ASIDE, sound = ScanSound.BEEP, asideSound = ScanSound.DOUBLE)
        else -> s
    }

    /** Applies a mode change and recomputes the settings it affects. */
    internal fun changeMode(change: (ScanUiState) -> ScanUiState) {
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
                // The saved box (picked in the header) comes back with the settings.
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

    // The scan session kept on disk, so closing the app doesn't lose a pile half scanned.

    private val sessionStore = ScanSessionStore(repo.appContext.filesDir)

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { sessionStore.load() }
            if (saved != null && !saved.isEmpty) restoreSession(saved)
            // From now on, every change is written a moment after it settles. Compared by reference, so reading,
            // glare and torch changes don't deep-compare the whole pile.
            _state.map { SavedScanSession.of(it) }.distinctUntilChanged { a, b -> a.sameAs(b) }.collectLatest { s ->
                delay(500)
                withContext(Dispatchers.IO) { sessionStore.save(s) }
            }
        }
    }

    /** Puts a saved session back, after anything scanned since the screen opened. Keys are moved past those in use. */
    private fun restoreSession(saved: SavedScanSession) {
        val offset = entryKeys
        fun moved(e: ScanEntry) = e.copy(key = e.key + offset)
        val session = saved.session.map(::moved)
        val sort = saved.sort?.let { it.copy(entries = it.entries.map(::moved)) }
        val pack = saved.pack?.let { it.copy(entries = it.entries.map(::moved)) }
        val all = session + sort?.entries.orEmpty() + pack?.entries.orEmpty()
        all.forEach { latest[it.key] = it }
        entryKeys = maxOf(entryKeys, all.maxOfOrNull { it.key } ?: 0L)
        _state.update {
            it.copy(
                session = it.session + session,
                sort = it.sort ?: sort,
                pack = it.pack ?: pack,
                review = it.review + saved.review.filter { r -> it.review.none { x -> x.card.id == r.card.id } },
                boxCheck = it.boxCheck ?: saved.boxCheck,
            )
        }
        // A batch pile not reviewed yet comes back, in batch mode.
        val pile = saved.batchCards
        if (pile.isNotEmpty()) {
            val items = pile.map { b -> PhotoItem(++batchKeys, b.card.name, b.card, b.lang, foil = b.foil, unsure = b.unsure) }
            if (_state.value.batch == null) setBatch(true)
            _state.update { it.copy(batch = it.batch.orEmpty() + items, status = "Batch · ${it.batch.orEmpty().size + items.size} to review") }
        }
        // A box check under way carries on in its mode.
        if (saved.boxCheck != null && _state.value.target == ScanTarget.COLLECTION) changeMode { it.copy(target = ScanTarget.BOX) }
    }

    override fun onCleared() {
        // Written now: the delayed write may not get its turn.
        val s = SavedScanSession.of(_state.value)
        runCatching { sessionStore.save(s) }
        super.onCleared()
    }

    // Frame-to-frame bookkeeping for the camera analyzer.
    private var lastKey: String? = null
    /** Name and footer of the card in view. Frames agree when either matches, as the footer is only read on some. */
    private var lastName: String? = null
    private var lastFooter: String? = null
    private var sameKeyFrames = 0
    /** Tells when the card has left: an empty guide counts, a tilt that blanks the text barely does. */
    private val leave = LeaveTracker()
    /** Year, symbol and art of the card in view, over its frames. */
    private val pool = FramePool()
    /** Border widths and looks of the card in view over its frames, for the centering and proxy checks. */
    private val centerings = ArrayDeque<com.mtgscanner.scan.Centering>()
    private val looks = ArrayDeque<com.mtgscanner.scan.FrameLook>()

    private fun clearPool() {
        pool.clear()
        centerings.clear()
        looks.clear()
    }

    /** Keeps this frame's border widths and look; shows the centering once two frames have measured it. */
    private fun noteFrame(g: CardTextGuess) {
        g.centering?.let { centerings.addLast(it); while (centerings.size > 5) centerings.removeFirst() }
        g.frameLook?.let { looks.addLast(it); while (looks.size > 3) looks.removeFirst() }
        val st = _state.value
        if (st.settings.centering && st.current != null && centerings.size >= 2) {
            val c = com.mtgscanner.scan.Centering.median(centerings.toList())
            if (c != st.centering) _state.update { it.copy(centering = c) }
        }
    }

    /** Keys already looked up for the card in view. */
    private val lookedUp = HashSet<String>()
    /** Keys whose lookup was started early, on their first frame (see [handleGuess]). */
    private val prefetched = HashSet<String>()
    /** When the card in view first gave a read; 0 when none is in view. For the scan log's timings. */
    private var cardSeenAt = 0L
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
    /** The card handled was known exactly (footer or art): another exact printing of it is another card. */
    private var lastAutoAddedExact = false
    private fun alreadyHandled(card: ScryfallCard, exact: Boolean = false) =
        sameCardInView(lastAutoAddedId, lastAutoAddedOracle, lastAutoAddedExact, card.id, card.oracleId, exact)
    private fun markHandled(card: ScryfallCard, art: Long? = null, exact: Boolean = false) {
        lastAutoAddedId = card.id
        lastAutoAddedOracle = card.oracleId
        lastAutoAddedExact = exact
        lastAddedArt = art
        artChangeFrames = 0
    }
    /** The card in view was already added or set aside: the camera can read it less often until it moves. */
    fun holdingCard(): Boolean = lastAutoAddedId != null

    private fun forgetHandled() {
        lastAutoAddedId = null
        lastAutoAddedOracle = null
        lastAutoAddedExact = false
        lastAddedArt = null
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

    private var seenSkip = 0
    private var seenBatch = 0

    /** True once for each new batch pulse. */
    fun freshBatch(pulse: Int): Boolean = (pulse > seenBatch).also { if (it) seenBatch = pulse }

    /** True once for each new skip pulse. */
    fun freshSkip(pulse: Int): Boolean = (pulse > seenSkip).also { if (it) seenSkip = pulse }

    /** The analyzer skips frames while this is true. */
    val isPaused: Boolean
        get() = _state.value.let {
            // The first-run hints cover the screen: don't add cards behind them.
            if (it.anySheetOpen || !ScanBarPrefs.hintsSeen || boxes.value.isNullOrEmpty()) true
            else if (it.pageMode) it.paused || !(it.wantPage || it.wantSpread) || it.pageLoading
            else it.paused || ((!it.autoAdd || it.awaiting) && (it.current != null || it.offline != null)) || it.lookingUp
        }

    /** Called on the camera thread; the work happens on the main thread so it never races the buttons. */
    fun onGuess(guess: CardTextGuess) {
        viewModelScope.launch { handleGuess(guess) }
    }

    private val glareHint = GlareHint()

    /** Shows or hides "Tilt to reduce glare" ([GlareHint]). */
    private fun trackGlare(guess: CardTextGuess) {
        val show = glareHint.onFrame(guess.glare) ?: return
        if (_state.value.glare != show) _state.update { it.copy(glare = show) }
    }

    private fun handleGuess(guess: CardTextGuess) {
        // Something's there but no card edges show: say why nothing is being read.
        if (guess.noEdges) {
            val st = _state.value
            if (st.current == null && !st.lookingUp && st.photo == null) {
                _state.update { it.copy(status = "Can't see the card's edges · try a plain, darker surface") }
            }
            return
        }
        trackGlare(guess)
        val key = guess.key
        lastFrameArtOnly = guess.pictureOnly
        if (guess.pictureOnly) {
            // A card is there, just no readable text: not an empty frame.
            leave.seen()
            handleArt(guess)
            return
        }
        artVote.reset()
        printPool.clear()
        if (key == null) {
            // The card has left the frame: allow the same card to be added again. Tilting to beat glare blanks the
            // text for a moment, so a frame with a card but nothing read barely counts; an empty guide does.
            if (leave.nothing(guess.guideEmpty)) {
                leave.reset()
                forgetHandled()
                clearPool()
                prefetched.clear()
                cardSeenAt = 0L
                lastKey = null
                lastName = null
                lastFooter = null
                lookedUp.clear()
                noMatchSince = 0L
                lights.clear()
                _state.update {
                    it.copy(
                        reading = null, looksLike = null, status = if (it.autoAdd && it.current != null) it.status else "Point your camera at a card",
                        foilHint = it.foilHint && it.current != null,
                    )
                }
            }
            return
        }
        leave.seen()
        if (cardSeenAt == 0L) cardSeenAt = System.currentTimeMillis()
        val name = guess.nameKey
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
            clearPool()
        }
        pool.add(guess)
        noteFrame(guess)
        lastKey = key
        _state.update { it.copy(reading = readLabel(guess)) }
        showLooksLike(guess.cardPrint)
        // Text keeps coming but nothing matched for a few seconds: offer to keep it for later.
        if (noMatchSince > 0 && System.currentTimeMillis() - noMatchSince >= UNKNOWN_AFTER_MS) offerUnknown()
        // A stack of the same name: clearly different artwork is a different printing, so a new card.
        val art = guess.artHash
        val added = lastAddedArt
        if (_state.value.autoAdd && art != null && added != null && lastAutoAddedOracle != null) {
            val d = ImageHash.distance(art, added)
            artChangeFrames = if (d > ART_CHANGED) artChangeFrames + 1 else 0
            // Plainly different art is a new card at once; a smaller change waits a frame more, in case of glare.
            if (artChangeFrames >= 2 || d > ART_CHANGED_CLEARLY) {
                forgetHandled()
                lookedUp.clear()
                clearPool()
                pool.add(guess)
            }
        }

        val nameGood = guess.nameCandidates.firstOrNull()?.let { com.mtgscanner.scan.CardTextParser.readableScore(it) >= 4 } == true
        // The footer and a clear name on one frame: the lookup checks they agree, so one frame is enough when
        // it finds that exact printing. Anything less waits for the next frame as usual.
        val oneFrame = sameKeyFrames == 1 && footer != null && nameGood && key !in lookedUp
        // A first good read starts the lookup in the background, so it's ready when the next frame agrees.
        if (!oneFrame && sameKeyFrames == 1 && (footer != null || nameGood) && key !in lookedUp && prefetched.add(key)) {
            if (prefetched.size > 40) prefetched.clear()
            repo.prefetch(guess, earlySkip())
        }
        // A name read that the card's picture agrees with is as good as two frames: look it up now. This also
        // catches a clear card whose read wobbles a letter from frame to frame, so no two frames ever agree.
        if (!oneFrame && sameKeyFrames == 1 && guess.nameCandidates.isNotEmpty() && guess.cardPrint != null && key !in lookedUp) {
            confirmByPicture(guess, key)
        }
        // Wait for two frames that agree, so a blurry half-read doesn't trigger a lookup.
        if (!oneFrame && (sameKeyFrames < 2 || key in lookedUp)) return
        // Already looked up by the exact footer: a name-only read adds nothing.
        if (footer == null && lookedUp.any { '#' in it }) return
        // No full footer: the printing comes from the year, symbol and art, so give them a frame more unless
        // they're all here.
        if (footer == null && sameKeyFrames < POOL_FRAMES && !pool.complete) return
        lookedUp += key
        lookup(if (footer == null) pool.pooled(guess) else guess, single = oneFrame, key = key)
    }

    /** Reads whose picture check has started, so each is checked once. */
    private val pictureChecked = HashSet<String>()

    /**
     * Looks up [guess] at once when the card's picture shows the card its name read names ([CardPrints.agreesWith]).
     * The picture's card name goes first, so a read a letter off still finds it.
     */
    private fun confirmByPicture(guess: CardTextGuess, key: String) {
        if (!pictureChecked.add(key)) return
        if (pictureChecked.size > 40) pictureChecked.retainAll(setOf(key))
        val gen = trackGen
        viewModelScope.launch {
            // Scans every printing's fingerprint: off the main thread.
            val hit = withContext(Dispatchers.Default) {
                runCatching { com.mtgscanner.scan.CardPrints.agreesWith(guess.cardPrint, guess.nameCandidates) }.getOrNull()
            } ?: return@launch
            // Moved on, paused, or already looked up meanwhile.
            if (gen != trackGen || isPaused || lastKey != key || key in lookedUp) return@launch
            lookedUp += key
            val name = hit.name.substringBefore(" // ")
            val g = pool.pooled(guess)
            lookup(g.copy(nameCandidates = (listOf(name) + g.nameCandidates).distinct()), key = key)
        }
    }

    /** [single]: looked up on one frame; only an exact, sure answer is used, else the next frame tries again. */
    private fun lookup(guess: CardTextGuess, single: Boolean = false, key: String? = guess.key) {
        _state.update { it.copy(lookingUp = true, status = "Looking it up…", error = null, offline = null) }
        val gen = trackGen
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching { repo.identify(guess, earlySkip()) }
            if (single && result.getOrNull()?.let { it.exact && it.confident } != true) {
                // Not sure from one frame: let the next agreeing frame look it up (the answer is cached).
                if (gen == trackGen && key != null) lookedUp.remove(key)
                _state.update { it.copy(lookingUp = false) }
                return@launch
            }
            logLookup(guess, result, System.currentTimeMillis() - started)
            // Paused, skipped or switched mode meanwhile: the answer is no longer wanted.
            if (gen != trackGen || _state.value.paused) {
                _state.update { it.copy(lookingUp = false) }
                return@launch
            }
            result.onFailure { e ->
                // Offline: the set lock stands in for a set code that wasn't read.
                val offline = if (isOffline(e)) {
                    val lock = SortPrefs.setLock
                    repo.offlineRead(if (guess.setCode == null && lock != null) guess.copy(setCode = lock) else guess)
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
                lastMatch = Triple(guess, earlySkip() != null, id.card.id)
                onIdentified(id, guess.lang, guess.artHash, guess.footerFoil)
                if (id.exact) checkLook(id.card)
            }
        }
    }

    /** The last read looked up, whether skip rules were on, and the card it matched: a printing picked for it is remembered. */
    private var lastMatch: Triple<CardTextGuess, Boolean, String>? = null

    private fun isOffline(e: Throwable) = e is java.io.IOException && e !is ScryfallException

    /**
     * Auto-add's type skip rules (basic lands, tokens), checked on the name read before any printing is searched.
     * The price rule waits for the printing. Null when none are on.
     */
    private fun earlySkip(): ((ScryfallCard) -> Boolean)? {
        val s = _state.value.settings
        if (!s.autoAdd || (!s.skipBasics && !s.skipTokens)) return null
        val rules = com.mtgscanner.scan.SkipRules(basics = s.skipBasics, tokens = s.skipTokens)
        return { c -> rules.reason(c.effectiveTypeLine, c.layout, null) != null }
    }

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
    /** Picture fingerprints of the card in view over its frames, matched together as well as one by one. */
    private val printPool = com.mtgscanner.scan.PrintPool()

    /** The analyzer hashes cards with no readable text only when there's an index to match them against. */
    fun wantsArt(): Boolean = repo.artIndex.hasAny

    /** The art model's embedding of a picture of artwork; null until the model is downloaded. Camera thread. */
    fun embedArt(art: android.graphics.Bitmap): com.mtgscanner.scan.ArtVector? = repo.artEmbedder.embed(art)

    /** The art match under way; frames that come meanwhile are let go. */
    private var artJob: Job? = null
    /** The newest frame had a card but no text, so an art match that finishes still applies. */
    private var lastFrameArtOnly = false

    /** Picture checks for [showLooksLike], one at a time. */
    private var looksJob: Job? = null

    /** Shows the card the picture most looks like (a quick search), so the answer shows before the scan settles. */
    private fun showLooksLike(print: FloatArray?) {
        if (print == null || looksJob?.isActive == true || _state.value.current != null) return
        val gen = trackGen
        looksJob = viewModelScope.launch {
            val name = withContext(Dispatchers.Default) {
                runCatching { com.mtgscanner.scan.CardPrints.likely(print) }.getOrNull()
            }?.name?.substringBefore(" // ") ?: return@launch
            if (gen != trackGen || isPaused || _state.value.current != null) return@launch
            if (_state.value.looksLike != name) _state.update { it.copy(looksLike = name) }
        }
    }

    private fun handleArt(guess: CardTextGuess) {
        showLooksLike(guess.artOnlyPrints.firstOrNull())
        printPool.add(guess.artOnlyPrints)
        if (artJob?.isActive == true) return
        val gen = trackGen
        val lock = SortPrefs.setLock
        // Scans every printing or downloaded entry: off the main thread.
        // Several frames' fingerprints pooled go first; the frame's own still count.
        val pooled = printPool.pooled()
        val g = if (pooled.isEmpty()) guess else guess.copy(artOnlyPrints = pooled + guess.artOnlyPrints)
        artJob = viewModelScope.launch {
            val hit = withContext(Dispatchers.Default) { repo.matchPicture(g, lock) }
            // Paused, reset, or text came into view meanwhile: this answer is stale.
            if (gen != trackGen || !lastFrameArtOnly || isPaused) return@launch
            onArtHit(hit, guess)
        }
    }

    private fun onArtHit(hit: com.mtgscanner.scan.ArtHit?, guess: CardTextGuess) {
        // The same card on two frames in a row, so one lucky frame doesn't add the wrong card; a picture match
        // clear enough (and the only printing with that picture) is taken at once.
        val sure = hit != null && hit.onlyPrinting && com.mtgscanner.scan.CardGallery.isSure(hit.standOut, hit.lead)
        val agreed = artVote.offer(hit?.id).let { if (sure) hit!!.id else it } ?: return
        if (hit == null || hit.id != agreed) return
        val key = "art:$agreed"
        if (key in lookedUp) return
        if (lookedUp.none { it.startsWith("art:") }) lookedUp.clear()
        lookedUp += key
        lastKey = key
        lastName = null
        lastFooter = null
        _state.update { it.copy(reading = hit.name) }
        lookupArt(hit, guess.artOnly.firstOrNull())
    }

    /** Fetches the printing the art matched. It's the exact printing, so it counts as sure. */
    private fun lookupArt(hit: com.mtgscanner.scan.ArtHit, art: Long?) {
        _state.update { it.copy(lookingUp = true, status = "Looking it up…", error = null, offline = null) }
        val gen = trackGen
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching { repo.cardById(hit.id) }
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
                            ms = System.currentTimeMillis() - started,
                            note = if (hit.standOut > 0f) "picture %.2f, %.1f sd".format(hit.similarity, hit.standOut)
                            else "art ${hit.distance} bits, next ${hit.runnerUp}",
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
    private suspend fun onIdentified(id: com.mtgscanner.data.Identification, lang: String? = null, art: Long? = null, footerFoil: Boolean? = null) {
        val s = _state.value.settings
        val card = id.card
        // Language only matters when Scryfall gave the English printing.
        val readLang = lang?.takeIf { card.lang == "en" }
        // A real card was found: drop any "save for later" offer.
        _state.update { it.copy(unknownRead = null) }
        // Keeping test photos: the straightened card just read, named by what it was found to be.
        com.mtgscanner.scan.TestPhotos.saveFor(repo.appContext, card.id)
        // Added by hand a moment ago and still under the camera: don't offer it again until it leaves.
        if (!s.autoAdd && _state.value.current == null && alreadyHandled(card, id.exact)) {
            _state.update { it.copy(lookingUp = false) }
            return
        }
        if (s.autoAdd) {
            val foil = s.foil && card.hasFoil || !card.hasNonFoil
            val price = card.price(foil)?.toDoubleOrNull()
            val reason = s.skipReason(card.effectiveTypeLine, card.layout, price, AppSettings.showPrices)
            if (reason != null) {
                val counted = !alreadyHandled(card, id.exact)
                markHandled(card, art, id.exact)
                _state.update {
                    it.copy(
                        lookingUp = false, current = null, skipped = it.skipped + if (counted) 1 else 0,
                        skipPulse = it.skipPulse + if (counted) 1 else 0,
                        skippedCards = if (counted) (listOf(SkippedItem(card, reason, readLang, ++skipKeys)) + it.skippedCards).take(200) else it.skippedCards,
                        status = "Skipped ${card.name} ($reason)",
                    )
                }
                return
            }
            // Batch: every card goes on the list to review, nothing is added yet.
            if (_state.value.batch != null) {
                toBatch(card, readLang, art, id.exact, unsure = !s.accepts(id.exact, id.confident))
                return
            }
            if (!s.accepts(id.exact, id.confident)) {
                when (s.unsure) {
                    UnsureAction.ASIDE -> {
                        val fresh = !alreadyHandled(card, id.exact) && _state.value.review.none { it.card.id == card.id }
                        markHandled(card, art, id.exact)
                        val photo = if (fresh) runCatching {
                            com.mtgscanner.scan.ScanPhotos.save(repo.appContext, "review-${card.id}-${System.currentTimeMillis()}")
                        }.getOrNull() else null
                        _state.update {
                            it.copy(
                                lookingUp = false,
                                current = null,
                                review = if (fresh) it.review + ReviewItem(card, readLang, photo) else it.review,
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
        val sure = s.autoAdd && s.accepts(id.exact, id.confident)
        // The same printing added on its own a few seconds ago, back in view: ask before adding another.
        // Not in stack mode, where a run of the same card is the point.
        val again = sure && !_state.value.stack && lastAutoAdd?.let { (cardId, at) ->
            cardId == card.id && android.os.SystemClock.elapsedRealtime() - at < REPEAT_MS
        } == true && !alreadyHandled(card, id.exact)
        val accepted = sure && !again
        val wish = runCatching { repo.wishlistItem(card.oracleId) }.getOrNull()
        val target = _state.value.target
        val owned = if (target != ScanTarget.COLLECTION) runCatching { repo.ownedCopies(card.oracleId) }.getOrNull() else null
        // Price check and scan to remove: which boxes hold it.
        val kept = if ((target == ScanTarget.CHECK || target == ScanTarget.REMOVE) && (owned ?: 0) > 0) {
            val names = boxes.value.orEmpty().associate { it.id to it.name }
            runCatching { repo.keptIn(card.oracleId) }.getOrDefault(emptyList()).map { (boxId, n) -> (names[boxId] ?: "?") to n }
        } else emptyList()
        if (_state.value.current?.id != card.id) autoEntryKey = null
        _state.update {
            val same = it.current?.id == card.id
            it.copy(
                lookingUp = false,
                current = card,
                // The footer's star or dot says foil or not, when the card comes both ways.
                cardFoil = (if (same) it.cardFoil else null) ?: footerFoil?.takeIf { card.hasFoil && card.hasNonFoil },
                foilHint = same && it.foilHint && footerFoil == null,
                centering = if (same) it.centering else null,
                lookOff = same && it.lookOff,
                priceFresh = same && it.priceFresh,
                confident = id.confident || accepted,
                awaiting = s.autoAdd && !accepted,
                wish = wish,
                owned = owned,
                keptIn = kept,
                currentLang = if (same) readLang ?: it.currentLang else readLang,
                status = when {
                    again -> "Added ${card.name} a moment ago · add another?"
                    id.confident || accepted -> card.name
                    else -> "Is this right?"
                },
            )
        }
        if (accepted && !alreadyHandled(card, id.exact)) {
            markHandled(card, art, id.exact)
            lastAutoAdd = card.id to android.os.SystemClock.elapsedRealtime()
            addCard(card, ScanBarPrefs.quantity, auto = true, readLang = readLang)
        }
        // Price check: today's price, not the one saved with the card list.
        if (target == ScanTarget.CHECK) {
            viewModelScope.launch {
                val fresh = repo.freshCard(card.id) ?: return@launch
                _state.update { if (it.current?.id == fresh.id) it.copy(current = fresh, priceFresh = true) else it }
            }
        }
    }

    internal var batchKeys = 0

    /**
     * Spot proxies: compares the look of the card in view (its last frames) with [card]'s own picture, and says so
     * when they're far apart. Only a hint, and only for exact matches, where the printing is known.
     */
    private fun checkLook(card: ScryfallCard) {
        val st = _state.value
        if (!st.settings.proxyCheck || looks.isEmpty() || st.current?.id != card.id) return
        val seen = looks.toList()
        viewModelScope.launch {
            val d = runCatching { repo.art.lookDistance(card, seen) }.getOrNull() ?: return@launch
            if (d > PROXY_DISTANCE) _state.update { if (it.current?.id == card.id) it.copy(lookOff = true) else it }
        }
    }

    /** The last card added on its own, and when, for the "add another?" check. */
    private var lastAutoAdd: Pair<String, Long>? = null

    /** Puts a match on the batch list, once while it stays in view. */
    private fun toBatch(card: ScryfallCard, lang: String?, art: Long?, exact: Boolean, unsure: Boolean = false) {
        if (alreadyHandled(card, exact)) {
            _state.update { it.copy(lookingUp = false) }
            return
        }
        markHandled(card, art, exact)
        _state.update {
            // The foil setting as it is now, while this card is in view; an unsure match is marked to check.
            val list = it.batch.orEmpty() + PhotoItem(++batchKeys, card.name, card, lang, foil = it.isFoil(card), unsure = unsure)
            it.copy(lookingUp = false, current = null, batch = list, batchPulse = it.batchPulse + 1, status = "${card.name} · ${list.size} to review")
        }
    }

    /** Offers to keep what was read for a card the scanner can't find. Not in price check. */
    private fun offerUnknown() {
        val g = noMatchGuess ?: return
        val st = _state.value
        if (st.unknownRead != null || st.target != ScanTarget.COLLECTION && st.target != ScanTarget.DECK || (st.current != null && !st.autoAdd)) return
        val name = g.nameCandidates.firstOrNull() ?: g.printedName ?: g.text.lineSequence().map { it.trim() }.firstOrNull { it.length >= 3 } ?: return
        val footer = g.setCode?.let { set -> g.collectorNumber?.let { "$set $it" } ?: set }
        val text = if (footer != null) "$name ($footer)" else name
        _state.update { it.copy(unknownRead = text, asidePulse = if (it.stack) it.asidePulse + 1 else it.asidePulse) }
    }

    /** Keeps the unreadable card's text in the unknown queue, to search by hand later. */
    fun saveUnknown() {
        val s = _state.value
        val text = s.unknownRead ?: return
        // Keep the copies and language the card would have been added with.
        val lang = noMatchGuess?.lang ?: ScanBarPrefs.langOrNull
        val qty = ScanBarPrefs.quantity
        noMatchSince = 0L
        noMatchGuess = null
        _state.update { it.copy(unknownRead = null, status = "Saved for later") }
        // The photo is taken now, while it's fresh, and named once the scan has its id.
        val tmp = runCatching { com.mtgscanner.scan.ScanPhotos.save(repo.appContext, "unsaved") }.getOrNull()
        viewModelScope.launch {
            runCatching {
                val id = repo.addUnknown(text, s.boxId, s.foil, qty, lang)
                if (tmp != null && id != null) com.mtgscanner.scan.ScanPhotos.rename(repo.appContext, tmp, com.mtgscanner.scan.ScanPhotos.pendingName(id))
            }
        }
    }

    fun dismissUnknown() {
        noMatchSince = 0L
        noMatchGuess = null
        _state.update { it.copy(unknownRead = null) }
    }

    // Light: low-light flashlight and the foil hint

    /** Recent frames while a card is in view, for the foil hint. */
    private val lights = ArrayDeque<FrameLight>()
    /** Low-light flashlight. */
    private val autoTorch = AutoTorch()

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

        autoTorch.onFrame(light.luma, st.torch, st.settings.autoTorch, st.paused, System.currentTimeMillis())?.let { on ->
            _state.update { it.copy(torch = on) }
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
        val updated = if (!cur.inCollection) cur.copy(card = card, foil = foil) else {
            repo.remove(cur.rowId, cur.count)
            val facts = card.facts(foil)
            val chosen = chooseBox(card, facts, _state.value.boxId)
            val boxId = chosen?.first ?: cur.boxId
            val row = repo.add(card, foil, cur.count, boxId, lang = rowLang(card, cur.lang), condition = cur.condition)
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
                    // Its new box's stack needs filing again, or the card would be hidden under "Done".
                    sort = it.sort?.let { o -> if (o.entries.any { e -> e.key == updated.key }) o.copy(done = o.done - updated.boxId) else o },
                    addedPulse = it.addedPulse + 1, lastBoxName = updated.boxName.ifEmpty { null }, lastBoxColor = box?.color ?: 0,
                    lastSpot = updated.spot, lastNear = null, lastNote = updated.note, lastAlso = emptyList(),
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
    internal fun rowLang(card: ScryfallCard, lang: String?): String = if (card.lang != "en") card.lang else lang ?: "en"

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
                        read = (guess.nameCandidates + listOfNotNull(guess.printedName)).take(4), setCode = guess.setCode, collectorNumber = guess.collectorNumber,
                        art = guess.artHash != null, outcome = outcome, matchName = id?.card?.name, matchSet = id?.card?.set,
                        matchNumber = id?.card?.collectorNumber, ms = ms, note = err?.message,
                        seenMs = cardSeenAt.takeIf { it > 0 }?.let { System.currentTimeMillis() - it } ?: 0, frames = sameKeyFrames,
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
        // Don't add (or, by hand, offer) this card again while it's still in view.
        markHandled(card)
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
    fun addFromReview(item: ReviewItem) {
        _state.update { it.copy(review = it.review.filterNot { c -> c.card.id == item.card.id }, showReview = it.review.size > 1) }
        // The language read off it when it was set aside.
        addCard(item.card, ScanBarPrefs.quantity, fromReview = true, readLang = item.lang)
    }

    fun dropFromReview(item: ReviewItem) =
        _state.update { it.copy(review = it.review.filterNot { c -> c.card.id == item.card.id }, showReview = it.review.size > 1) }

    /** Swaps a set-aside card for the printing picked in the review sheet. */
    fun reviewPrinting(item: ReviewItem, card: ScryfallCard) = _state.update {
        it.copy(review = it.review.map { r -> if (r.card.id == item.card.id) r.copy(card = card) else r }.distinctBy { r -> r.card.id })
    }

    /** Every printing of a card, for the printing pickers. */
    suspend fun printingsOf(card: ScryfallCard): List<ScryfallCard> = runCatching { repo.printingsFor(card) }.getOrDefault(listOf(card))

    // Skipped cards and unknown scans

    private var skipKeys = 0L

    fun showSkipped(show: Boolean) = _state.update { it.copy(showSkipped = show) }

    /** Adds a card the skip rules skipped. */
    fun addSkipped(item: SkippedItem) {
        _state.update {
            val left = it.skippedCards.filterNot { s -> s.key == item.key }
            it.copy(skippedCards = left, skipped = (it.skipped - 1).coerceAtLeast(0), showSkipped = left.isNotEmpty())
        }
        addCard(item.card, ScanBarPrefs.quantity, fromReview = true, readLang = item.lang)
    }

    fun showUnknown(show: Boolean) = _state.update { it.copy(showUnknown = show) }

    /** Cards saved because they couldn't be identified, to search by hand. */
    val unknownScans: StateFlow<List<com.mtgscanner.data.db.PendingScan>> = repo.observePending()
        .map { list -> list.filter { it.unknown } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun searchCards(text: String): List<ScryfallCard> {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        val found = runCatching { repo.api.search(q) }.getOrDefault(emptyList()).take(20)
        if (found.isNotEmpty()) return found
        return listOfNotNull(runCatching { repo.api.named(q) }.getOrNull())
    }

    /** Adds the card picked for an unknown scan, with its copies and language, and clears the scan. */
    fun resolveUnknown(scan: com.mtgscanner.data.db.PendingScan, card: ScryfallCard) = viewModelScope.launch {
        val foil = scan.foil && card.hasFoil || !card.hasNonFoil
        val box = if (scan.boxId != Box.AUTO_SORT && repo.box(scan.boxId) != null) scan.boxId
        else repo.pickBox(card, foil) ?: repo.anythingBox()
        val qty = scan.quantity.coerceAtLeast(1)
        val row = repo.add(card, foil, qty, box, lang = rowLang(card, scan.lang))
        repo.deletePending(scan.id)
        Undo.offer("Added ${card.name}") {
            repo.remove(row, qty)
            repo.dao.addPending(scan)
        }
    }

    fun deleteUnknown(scan: com.mtgscanner.data.db.PendingScan) = viewModelScope.launch {
        repo.deletePending(scan.id)
        Undo.offer("Scan deleted") { repo.dao.addPending(scan) }
    }

    fun showReview(show: Boolean) = _state.update { it.copy(showReview = show) }

    fun setListByBox(on: Boolean) = _state.update { it.copy(listByBox = on) }

    /**
     * Quick quantity: the same card read again within a few seconds of the newest line adds to that
     * line (×4) instead of making a new one. Only when the copies went to the same place.
     */
    private fun mergeTarget(s: ScanUiState, card: ScryfallCard, foil: Boolean, boxId: Long, deckId: Long, rowId: Long): ScanEntry? {
        if (s.target == ScanTarget.CHECK || s.target == ScanTarget.REMOVE || s.target == ScanTarget.BOX) return null
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

    /** Scan to wishlist: the card goes on the wishlist, not into the collection. */
    private suspend fun addToWish(card: ScryfallCard, qty: Int, foil: Boolean, fromReview: Boolean, auto: Boolean) {
        if (runCatching { repo.addToWishlist(card, qty) }.isFailure) return
        val wanted = runCatching { repo.wishlistItem(card.oracleId)?.wanted }.getOrNull() ?: qty
        val s = _state.value
        val merge = mergeTarget(s, card, foil, 0, 0, 0)?.takeIf { it.toWishlist }
        val entry = merge?.copy(count = merge.count + qty, at = System.currentTimeMillis())
            ?: ScanEntry(++entryKeys, 0, card, foil, qty, "Wishlist", 0, note = "want $wanted", toWishlist = true)
        latest[entry.key] = entry
        _state.update {
            it.copy(
                session = if (merge != null) replaceIn(it.session, entry) else listOf(entry) + it.session,
                addedPulse = it.addedPulse + 1,
                status = "${card.name} → wishlist · want $wanted",
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
        if (!auto) Undo.offer("Added ${card.name} to wishlist") { takeBack(entry.key, qty) }
    }

    private fun addCard(
        card: ScryfallCard, qty: Int, fromReview: Boolean = false, auto: Boolean = false, fromPage: Boolean = false, readLang: String? = null,
        foil: Boolean? = null,
    ): Job {
        // Language read off the card wins; otherwise the scan bar's choice.
        val lang = readLang ?: ScanBarPrefs.langOrNull
        val s = _state.value
        val isFoil = foil ?: s.isFoil(card)
        return viewModelScope.launch { addNow(card, qty, s, isFoil, lang, fromReview, auto, fromPage) }
    }

    /** The add itself. Returns the scan list line it made or grew, or null when nothing was added. */
    internal suspend fun addNow(
        card: ScryfallCard, qty: Int, s: ScanUiState, isFoil: Boolean, lang: String?,
        fromReview: Boolean, auto: Boolean, fromPage: Boolean,
    ): ScanEntry? {
        run {
            when (s.target) {
                ScanTarget.CHECK -> return null
                ScanTarget.REMOVE -> { removeScanned(card, isFoil, auto); return null }
                ScanTarget.BOX -> { recordBoxScan(card, isFoil, qty, fromReview, auto); return null }
                ScanTarget.WISHLIST -> { addToWish(card, qty, isFoil, fromReview, auto); return null }
                ScanTarget.DECK -> {
                    val deck = s.deck
                    if (deck != null) {
                        addToDeck(card, qty, isFoil, deck, fromReview, auto)
                        return null
                    }
                }
                ScanTarget.COLLECTION -> Unit
            }
        }
        run {
            val condition = ScanBarPrefs.condition
            val wish = if (fromReview || fromPage) runCatching { repo.wishlistItem(card.oracleId) }.getOrNull() else s.wish
            val facts = card.facts(isFoil)
            // A box that was deleted falls back to auto-sort.
            val chosen = chooseBox(card, facts, s.boxId)
            if (chosen == null) {
                _state.update { it.copy(status = "No box fits ${card.name}", error = "Pick a box above, or add a box that takes any card") }
                return null
            }
            val (boxId, decision) = chosen
            val rowId = repo.add(card, isFoil, qty, boxId, lang = rowLang(card, lang), condition = condition)
            if (wish != null) repo.fulfilWish(card.oracleId, qty)
            val wished = if (wish != null) minOf(qty, wish.wanted) else 0
            val box = boxes.value?.firstOrNull { it.id == boxId }
            val boxName = box?.name.orEmpty()
            val spot = runCatching { repo.spotFor(boxId, card.name, facts) }.getOrNull()
            // Where it goes among the box's cards: "between Opt and Ponder".
            val near = runCatching { repo.nearFor(boxId, card.name, facts, rowId, qty) }.getOrNull()
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
            val merge = mergeTarget(now, card, isFoil, boxId, 0, rowId)?.takeIf { it.lang == shownLang && it.condition == condition }
            val entry = merge?.copy(count = merge.count + qty, at = System.currentTimeMillis(), wish = merge.wish ?: wish, wished = merge.wished + wished)
                ?: ScanEntry(
                    ++entryKeys, rowId, card, isFoil, qty, boxName, boxId, spot, sortNote(decision), full, lang = shownLang, condition = condition,
                    wish = wish, wished = wished,
                )
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
                    lastNear = near,
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
            return entry
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
            val before = if (e.inCollection) rowsBefore(listOf(e.rowId)) else emptyMap()
            val st = _state.value
            val inSort = st.sort?.entries?.any { it.key == e.key } == true
            val inPack = st.pack?.entries?.any { it.key == e.key } == true
            removeEntry(e)
            Undo.offer("Removed ${e.card.name}") {
                when {
                    e.deckId != 0L -> repo.addToDeckList(e.deckId, e.card.name, e.count)
                    e.toWishlist -> repo.addToWishlist(e.card, e.count)
                    else -> putBack(before, mapOf(e.rowId to e.count))
                }
                if (e.wished > 0) runCatching { repo.fulfilWish(e.card.oracleId, e.wished) }
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
    internal suspend fun rowsBefore(rowIds: List<Long>): Map<Long, CollectionCard> =
        rowIds.distinct().mapNotNull { id -> runCatching { repo.cardOnce(id) }.getOrNull()?.let { id to it } }.toMap()

    /**
     * Undoes taking [taken] copies off each row. A row still there gets its copies back; a row that was
     * deleted comes back as it was. Other rows of the same printing (copies added since) are left alone.
     */
    internal suspend fun putBack(before: Map<Long, CollectionCard>, taken: Map<Long, Int>) {
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
        when {
            e.deckId != 0L -> runCatching { repo.addToDeckList(e.deckId, e.card.name, -qty) }
            e.toWishlist -> runCatching { repo.fulfilWish(e.card.oracleId, qty) }
            else -> repo.remove(e.rowId, qty)
        }
        giveBackWish(e, qty)
    }

    /** Puts back on the wishlist the copies this line took off it, [qty] at most. */
    private suspend fun giveBackWish(e: ScanEntry, qty: Int) {
        val w = e.wish ?: return
        val n = minOf(qty, e.wished)
        if (n <= 0) return
        runCatching {
            val now = repo.wishlistItem(e.card.oracleId)
            if (now != null) repo.setWishWanted(now, now.wanted + n) else repo.restoreWish(w.copy(wanted = n))
        }
    }

    /** Takes [qty] copies off a line, removing the line when none are left. */
    internal suspend fun takeBack(key: Long, qty: Int) {
        val cur = latest[key] ?: return
        if (key in removedKeys) return
        if (qty >= cur.count) {
            removeEntry(cur)
            return
        }
        unstore(cur, qty)
        val n = cur.copy(count = cur.count - qty, wished = cur.wished - minOf(qty, cur.wished))
        latest[key] = n
        replaceEverywhere(n)
        _state.update { it.copy(status = "Removed ${cur.card.name}") }
    }

    /** Does what was said ([com.mtgscanner.scan.VoiceCommands]); false when it didn't apply to anything. */
    fun onVoice(c: com.mtgscanner.scan.VoiceCommand): Boolean {
        val st = _state.value
        val card = st.current
        return when (c) {
            is com.mtgscanner.scan.VoiceCommand.Copies -> { ScanBarPrefs.updateQuantity(c.n); _state.update { it.copy(status = "×${c.n}") }; true }
            is com.mtgscanner.scan.VoiceCommand.Foil -> voiceFoil(c.on)
            com.mtgscanner.scan.VoiceCommand.Undo -> if (st.session.isEmpty()) false else { removeLast(1); true }
            com.mtgscanner.scan.VoiceCommand.Skip -> if (card == null) false else { skip(); true }
            com.mtgscanner.scan.VoiceCommand.SetAside -> setAsideCurrent()
            com.mtgscanner.scan.VoiceCommand.Add -> {
                // Already added on its own: "add" adds one more, like the volume key.
                if (card == null || st.target == ScanTarget.CHECK) false
                else { if (st.autoAdd && st.session.firstOrNull()?.card?.id == card.id) sameAgain() else add(ScanBarPrefs.quantity); true }
            }
        }
    }

    /** "foil" / "not foil": for the card on screen (and the copy just added), else the scan bar's foil setting. */
    private fun voiceFoil(on: Boolean): Boolean {
        val st = _state.value
        val card = st.current ?: run { setFoil(on); return true }
        if (on && !card.hasFoil || !on && !card.hasNonFoil) return false
        if (st.isFoil(card) == on) return true
        if (on) { acceptFoilHint(); return true }
        _state.update { it.copy(cardFoil = false, foilHint = false) }
        val head = st.session.firstOrNull()
        if (st.autoAdd && head != null && head.card.id == card.id && head.foil) viewModelScope.launch { swapLine(head.key, card, foil = false) }
        return true
    }

    /** Sets the card on screen aside to check later, as an unsure match would be. */
    private fun setAsideCurrent(): Boolean {
        val st = _state.value
        val card = st.current ?: return false
        if (st.review.any { it.card.id == card.id }) return false
        markHandled(card)
        val photo = runCatching { com.mtgscanner.scan.ScanPhotos.save(repo.appContext, "review-${card.id}-${System.currentTimeMillis()}") }.getOrNull()
        _state.update {
            it.copy(
                current = null, awaiting = false, review = it.review + ReviewItem(card, st.currentLang, photo),
                asidePulse = it.asidePulse + 1, status = "Set aside ${card.name} to check later",
            )
        }
        return true
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
            val inCollection = picks.filter { it.first.inCollection }
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
                olds.filter { it.toWishlist }.forEach { e ->
                    runCatching { repo.addToWishlist(e.card, picks.first { p -> p.first.key == e.key }.second) }
                }
                olds.filter { it.deckId != 0L }.forEach { e ->
                    runCatching { repo.addToDeckList(e.deckId, e.card.name, picks.first { p -> p.first.key == e.key }.second) }
                }
                picks.forEach { (e, q) -> if (minOf(q, e.wished) > 0) runCatching { repo.fulfilWish(e.card.oracleId, minOf(q, e.wished)) } }
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

    /** Leaves pack mode without a summary. Cards stay in the collection. */
    fun cancelPack() = _state.update { it.copy(pack = null, status = "Point your camera at a card") }

    /** Ends the pack and shows what it held, with what it cost. Cards stay in the collection. */
    fun finishPack() = _state.update {
        val p = it.pack ?: return@update it
        val summary = summaryOf(p.entries)?.copy(title = p.name, paid = p.cost?.takeIf { c -> c > 0 })
        it.copy(pack = null, status = "Point your camera at a card", summary = summary)
    }

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
    fun endSort() = _state.update {
        it.copy(sort = null, showSort = false, status = "Point your camera at a card", summary = summaryOf(it.sort?.entries.orEmpty()))
    }

    fun dismissSummary() = _state.update { it.copy(summary = null) }

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
    /** Sets whose art index was made without the chosen art model (still matched by hash). */
    fun staleArt(): List<String> = repo.artIndex.stale()
    fun rebuildArt(codes: Collection<String>) = repo.artIndex.rebuild(codes)
    fun deleteArt(code: String) = repo.artIndex.delete(code)
    fun cancelArtDownloads() = repo.artIndex.cancelQueue()

    /** The art model chosen in scan settings, its download, and whether that failed. */
    val artModel: StateFlow<com.mtgscanner.scan.ArtModel> get() = repo.artEmbedder.chosen
    val artModelDownload: StateFlow<com.mtgscanner.scan.ModelDownload?> get() = repo.artEmbedder.download
    val artModelFailed: StateFlow<Boolean> get() = repo.artEmbedder.failed
    fun artModelHere(m: com.mtgscanner.scan.ArtModel): Boolean = repo.artEmbedder.has(m)
    fun selectArtModel(m: com.mtgscanner.scan.ArtModel) = repo.artEmbedder.select(m)

    /** Set codes of the cards in the collection, most cards first. */
    suspend fun ownedSets(): List<String> = repo.dao.ownedSetCodes()

    // Binder page mode and photo scans: the functions are in ScanPagePhoto.kt.

    internal var pageJob: kotlinx.coroutines.Job? = null
    internal var photoJob: Job? = null
    /** The cards of the last "Cards in view" capture, by where they lay, so the next capture doesn't read them again. */
    internal val spreadTracks = com.mtgscanner.scan.CardTracks<Pair<Long, com.mtgscanner.scan.CardTextGuess>>(minOverlap = 0.7f, maxMissed = 1)

    /** Clears the list on screen. Cards stay in the collection. */
    fun clearList() = _state.update { it.copy(session = emptyList(), showList = false, summary = summaryOf(it.session)) }

    fun skip() {
        val auto = _state.value.autoAdd
        _state.value.current?.let { if (auto) markHandled(it) }
        _state.update {
            it.copy(
                current = null, cardFoil = null, offline = null, wish = null, error = null, awaiting = false, foilHint = false, owned = null,
                keptIn = emptyList(), currentLang = null, status = "Point your camera at a card",
            )
        }
        // While auto-adding, keep ignoring this card until it leaves the frame.
        if (!auto) resetTracking() else trackGen++
    }

    internal fun resetTracking() {
        lastKey = null
        lastName = null
        lastFooter = null
        lookedUp.clear()
        sameKeyFrames = 0
        trackGen++
    }

    fun togglePause() {
        // Drops a lookup under way and forgets what was looked up, so the card in view is read again on resume.
        // Cards already added stay handled until they leave the frame.
        resetTracking()
        _state.update {
            val p = !it.paused
            it.copy(paused = p, status = if (p) "Paused" else "Point your camera at a card", reading = null, looksLike = null, lookingUp = false)
        }
    }

    fun updateSettings(change: (ScanSettings) -> ScanSettings) = ScanPrefs.update(change)
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
        autoTorch.turnedByHand()
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
            val list = runCatching { repo.printingsFor(card) }.getOrDefault(listOf(card))
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
        // The same read gives this printing from now on.
        val match = lastMatch
        if (before != null && before.id != card.id && match != null && match.third == before.id) {
            viewModelScope.launch { runCatching { repo.rememberPick(match.first, match.second, card) } }
            lastMatch = match.copy(third = card.id)
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

    /** Back to adding to the collection. A box check is kept to look at until ended. */
    fun startWishlist() = changeMode {
        it.copy(
            target = ScanTarget.WISHLIST, deck = null, stack = false, current = null, awaiting = false, owned = null,
            status = "Adding to wishlist",
        )
    }

    fun stopTarget() = changeMode {
        it.copy(target = ScanTarget.COLLECTION, deck = null, current = null, awaiting = false, owned = null, keptIn = emptyList(), status = "Point your camera at a card")
    }

    // Scan to remove

    fun startRemove() = changeMode {
        it.copy(
            target = ScanTarget.REMOVE, deck = null, current = null, awaiting = false, owned = null, removedCount = 0,
            status = "Removing · scan each card going out",
        )
    }

    /** Takes one copy of a scanned card out of the collection, with Undo. */
    private suspend fun removeScanned(card: ScryfallCard, foil: Boolean, auto: Boolean) {
        val row = runCatching { repo.rowToRemove(card, foil) }.getOrNull()
        if (row == null) {
            _state.update {
                it.copy(current = if (auto) it.current else null, awaiting = false, status = "${card.name} · not in your collection")
            }
            if (!_state.value.autoAdd) resetTracking()
            return
        }
        repo.remove(row.id, 1)
        val box = boxes.value?.firstOrNull { it.id == row.boxId }
        _state.update {
            it.copy(
                removedCount = it.removedCount + 1, addedPulse = it.addedPulse + 1,
                lastBoxName = box?.name, lastBoxColor = box?.color ?: 0, lastSpot = null, lastNear = null, lastNote = null, lastFull = false, lastAlso = emptyList(),
                status = "Removed ${card.name}" + (box?.name?.let { b -> " from $b" } ?: ""),
                current = if (auto) it.current else null, cardFoil = null, foilHint = false, awaiting = false,
                owned = it.owned?.let { n -> (n - 1).coerceAtLeast(0) },
            )
        }
        if (!_state.value.autoAdd) resetTracking()
        Undo.offer("Removed ${card.name}") {
            putBack(mapOf(row.id to row), mapOf(row.id to 1))
            _state.update { it.copy(removedCount = (it.removedCount - 1).coerceAtLeast(0)) }
        }
    }

    // Box check: the functions are in ScanBoxCheck.kt.

    internal val _boxResult = MutableStateFlow<com.mtgscanner.data.BoxCheckResult?>(null)
    /** Found, missing and not recorded for the box check; null while it's worked out. */
    val boxResult: StateFlow<com.mtgscanner.data.BoxCheckResult?> = _boxResult

    private companion object {
        /** A card back in view this soon after it was added on its own is asked about, not added again. */
        private const val REPEAT_MS = 6_000L

        /** Same card again within this long adds to the newest line (quick quantity). */
        const val QUICK_MS = 6_000L
        /** Text read but no match for this long: offer to save it for later. */
        const val UNKNOWN_AFTER_MS = 3_000L
        /** Art hashes further apart than this (of 64 bits) are different artwork. */
        const val ART_CHANGED = 22
        /** Art this different is another card on one frame. */
        const val ART_CHANGED_CLEARLY = 34
        /** A card this unlike its printing's own picture ([com.mtgscanner.scan.FrameLook.distance]) gets the proxy hint. */
        const val PROXY_DISTANCE = 0.8f
        /** Frames agreeing on a name (no footer) before its printing is picked from their pooled year, symbol and art. */
        const val POOL_FRAMES = 3
    }
}

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
import com.mtgscanner.data.identify
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

data class ScanUiState(
    val status: String = "Point your camera at a card",
    val reading: String? = null,
    /** The card the picture most looks like while it isn't sure yet, shown faintly until the scan settles. */
    val looksLike: String? = null,
    val current: ScryfallCard? = null,
    val confident: Boolean = false,
    val lookingUp: Boolean = false,
    val paused: Boolean = false,
    /** Auto-add level, unsure action, skip rules, feedback. Saved. */
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
    val review: List<ReviewItem> = emptyList(),
    val showReview: Boolean = false,
    /** The box the last card went into, shown big so you know where to file it. */
    val lastBoxName: String? = null,
    val lastBoxColor: Int = 0,
    /** Divider spot, reason note and full flag for the last card's box. */
    val lastSpot: String? = null,
    /** Where the last card goes among the box's cards: "between Opt and Ponder". */
    val lastNear: String? = null,
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
    /** Cards read from a photo picked from the gallery, while the review sheet is open. */
    val photo: PhotoRun? = null,
    /** Batch mode: cards scanned so far, to review and add at once; null when batch mode is off. */
    val batch: List<PhotoItem>? = null,
    /** Bumps when a card goes on the batch list, for its sound. */
    val batchPulse: Int = 0,
    /** Loose cards on the table: the next steady frame is read for every card in it. */
    val wantSpread: Boolean = false,
    /** Price check: the card on screen has today's price from Scryfall. */
    val priceFresh: Boolean = false,
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
    /** How evenly the card on screen's border is printed (centering check on). */
    val centering: com.mtgscanner.scan.Centering? = null,
    /** The card on screen doesn't look like its printing's own picture: maybe a proxy or a misprint. */
    val lookOff: Boolean = false,
    /** Glare over the card's name or footer in recent frames. */
    val glare: Boolean = false,
    /** Text read that matched no card for a while, offered to save for later. */
    val unknownRead: String? = null,
    val unknownCount: Int = 0,
    /** Other boxes the last card would also have fit, after the one it went to. */
    val lastAlso: List<String> = emptyList(),
    /** The cards behind [skipped], newest first, to add anyway. */
    val skippedCards: List<SkippedItem> = emptyList(),
    /** Bumps when a card is skipped, for a soft tick in stack mode. */
    val skipPulse: Int = 0,
    val showSkipped: Boolean = false,
    val showUnknown: Boolean = false,
    /** Box check under way, if any. */
    val boxCheck: BoxCheckRun? = null,
    val showBoxPick: Boolean = false,
    val showBoxCheck: Boolean = false,
    /** Price check: where the copies of the card on screen are kept ("Rares" to 3). */
    val keptIn: List<Pair<String, Int>> = emptyList(),
    /** Scan to remove: copies taken out this time. */
    val removedCount: Int = 0,
    /** What a scan list or sorting session added, shown once when it ends. */
    val summary: ScanSummary? = null,
) {
    val autoAdd: Boolean get() = settings.autoAdd
    val foil: Boolean get() = settings.foil

    /** A sheet or dialog covers the camera: no adding, no volume keys, no hints. */
    val anySheetOpen: Boolean
        get() = showPrintings || showSettings || showList || showReview || showPackStart || showSort || showSetLock ||
            showDeckPick || showNewBox || showSkipped || showUnknown || showBoxPick || showBoxCheck ||
            page != null || photo != null || summary != null

    /** Whether [card] gets added as foil. The result card's choice counts only for the card on screen. */
    fun isFoil(card: ScryfallCard): Boolean {
        val want = (if (card.id == current?.id) cardFoil else null) ?: settings.foil
        return want && card.hasFoil || !card.hasNonFoil
    }
    val sessionCount: Int get() = session.sumOf { it.count }
    val sessionValue: Double get() = session.sumOf { it.value }
    val sessionEur: Double get() = session.sumOf { it.eurValue }
    val packEntries: List<ScanEntry> get() = pack?.entries.orEmpty()
    val packValue: Double get() = packEntries.sumOf { it.value }
    val packEur: Double get() = packEntries.sumOf { it.eurValue }
}

/** The end-of-session summary for [entries], or null when they added nothing. */
fun summaryOf(entries: List<ScanEntry>): ScanSummary? = ScanSummary.of(
    entries.map { SummaryLine(it.card.name, it.card.imageUrl, it.card.usdPrice(it.foil), it.count, it.boxName, it.card.eurPrice(it.foil)) },
)

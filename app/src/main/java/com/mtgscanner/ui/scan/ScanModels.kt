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

/** One add during this scanning session, newest first in the scan list. */
@Serializable
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
    /** Condition recorded for the copies ("NM", "LP" ...). */
    val condition: String = "NM",
    /** The wishlist entry as it was before this line counted copies off it, to put back on undo. */
    val wish: WishlistItem? = null,
    /** Copies of this line that came off the wishlist. */
    val wished: Int = 0,
    /** Set when the card went on the wishlist instead of into the collection ([rowId] is then 0). */
    val toWishlist: Boolean = false,
) {
    /** The copies went into the collection (not a deck list or the wishlist). */
    val inCollection: Boolean get() = deckId == 0L && !toWishlist

    /** "Rares → Blue" */
    val where: String get() = if (spot != null && boxName.isNotEmpty()) "$boxName → $spot" else boxName
    val value: Double get() = (card.usdPrice(foil) ?: 0.0) * count
    val eurValue: Double get() = (card.eurPrice(foil) ?: 0.0) * count
}

/** A pack being opened, with the cards scanned from it. Kept apart from the scan list so clearing the list keeps them. */
@Serializable
data class PackDraft(val name: String, val cost: Double?, val entries: List<ScanEntry> = emptyList())

/** Sorting session: cards scanned since it started, to file by box afterwards. [done] holds the box ids filed. */
@Serializable
data class SortSession(val entries: List<ScanEntry> = emptyList(), val done: Set<Long> = emptySet())

/** A card set aside while auto-adding, with the language read off it. */
@Serializable
data class ReviewItem(val card: ScryfallCard, val lang: String? = null)

/** A card the skip rules skipped this session, and why. */
data class SkippedItem(val card: ScryfallCard, val reason: String, val lang: String? = null, val key: Long = 0)

/** A card scanned while checking a box. */
@Serializable
data class CheckScan(val card: ScryfallCard, val foil: Boolean, val count: Int = 1)

/** Box check: the box being checked and the cards scanned from it. [ticked] holds rows marked found by hand. */
@Serializable
data class BoxCheckRun(
    val boxId: Long,
    val boxName: String,
    val scans: List<CheckScan> = emptyList(),
    val ticked: Map<Long, Int> = emptyMap(),
)

/** Short label for why a card went where it did, or null for plain rules. */
fun sortNote(d: SortDecision?): String? = when (d?.reason) {
    SortReason.BULK -> SortPrefs.bulkFloor?.let { "Bulk (under $" + "%.2f".format(java.util.Locale.US, it) + ")" } ?: "Bulk"
    SortReason.EXTRA -> "Extra copy"
    else -> null
}

/**
 * Where scanned cards go: the collection, a deck list, nowhere (price check), out of the collection (scan to
 * remove), or into a box check's list.
 */
enum class ScanTarget { COLLECTION, DECK, CHECK, REMOVE, BOX, WISHLIST }

/** One pocket of a binder page. [card] is null when it couldn't be read or found. */
data class PageSlot(
    val index: Int, val read: String?, val card: ScryfallCard?, val selected: Boolean,
    /** Language read off the pocket's card, when not English. */
    val lang: String? = null,
)

/** One card found in a photo. [card] is null when it couldn't be read or found. */
data class PhotoItem(
    val key: Int, val read: String?, val card: ScryfallCard?,
    /** Language read off the card, when not English. */
    val lang: String? = null,
    /** The card as cut out of the photo. */
    val thumb: android.graphics.Bitmap? = null,
    /** Batch: whether it was scanned as foil (null: use the scan bar's setting when added). */
    val foil: Boolean? = null,
    /** Batch: the match wasn't sure, so it's shown first, marked, to check. */
    val unsure: Boolean = false,
)

/** Cards read from a photo, to check before adding. */
data class PhotoRun(
    val loading: Boolean = true, val items: List<PhotoItem> = emptyList(), val failed: Boolean = false,
    val title: String = "Photo",
    /** The batch list being reviewed: closing the sheet puts what's left back on it. */
    val fromBatch: Boolean = false,
)

/** What the camera read, for the status line: the name, else the footer ("MOM #123"). */
fun readLabel(g: CardTextGuess): String? =
    g.nameCandidates.firstOrNull() ?: g.printedName ?: listOfNotNull(g.setCode, g.collectorNumber?.let { "#$it" }).joinToString(" ").ifEmpty { null }

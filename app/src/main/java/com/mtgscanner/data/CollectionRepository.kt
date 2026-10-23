package com.mtgscanner.data

import android.content.Context
import android.util.LruCache
import androidx.room.withTransaction
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.CardEvent
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Goal
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.mergedWith
import com.mtgscanner.data.db.placeName
import com.mtgscanner.data.db.sameCopyAs
import com.mtgscanner.data.db.nearMint
import com.mtgscanner.data.db.userTagList
import com.mtgscanner.scan.ArtMatcher
import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.CardTextParser
import com.mtgscanner.scan.NameIndex
import com.mtgscanner.util.DecklistParser
import com.mtgscanner.util.ImportRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.LocalDate

/** A Scryfall match for what the camera read, and whether it's safe to add without asking. */
data class Identification(
    val card: ScryfallCard,
    val confident: Boolean,
    /** The exact printing is known: the footer was read, or the artwork matched. */
    val exact: Boolean = false,
)

/** A card name read with no connection, good enough to look up later. */
data class OfflineRead(
    val name: String, val setCode: String?, val collectorNumber: String?,
    /** Language read off the card, when not English. */
    val lang: String? = null,
    /** The exact printing, when the artwork matched a downloaded art index. */
    val scryfallId: String? = null,
)

/** One card in a checked decklist. */
data class DeckCheckLine(
    val name: String,
    val section: String,
    val needed: Int,
    val owned: Int,
    /** Where the owned copies are: box name to count. */
    val boxes: List<Pair<String, Int>>,
    val price: Double?,
    val imageUrl: String?,
    val oracleId: String?,
    /** The printing the list names ("fdc", "123"), if any. */
    val setCode: String? = null,
    val collectorNumber: String? = null,
    /** The list named a printing Scryfall doesn't have, so the picture and price are another printing's. */
    val otherPrinting: Boolean = false,
    /** Of [owned], copies kept in the deck itself; the rest are in boxes or other decks. */
    val inDeck: Int = 0,
    /** How many other decks hold copies of the card (they're left out of [boxes]). */
    val otherDecks: Int = 0,
    /** The collector number shown: the listed one, or the printing found or owned for a set-only line. */
    val shownNumber: String? = null,
    /** "Borderless", "Showcase"… for the printing shown; null for a normal one. */
    val variant: String? = null,
    /** Copies are owned, but none of the printing the list names. */
    val ownedOtherPrinting: Boolean = false,
) {
    /** "LCI #287 · Borderless"; null when the list names no set. */
    val printing: String? get() = setCode?.let { s ->
        listOfNotNull(s.uppercase() + (shownNumber?.let { " #$it" } ?: ""), variant).joinToString(" · ")
    }

    /** "In deck ×1 · Binder ×2 · 2 other decks". */
    val whereText: String get() = DeckPlaces.text(boxes, otherDecks)

    val missing: Int get() = (needed - owned).coerceAtLeast(0)
    /** In a section that's played (main deck, commander, companion); sideboard, maybeboard and tokens aren't. */
    val played: Boolean get() = section in DeckCheck.PLAYED

    companion object {
        /** [boxes] name for the copies kept in the deck being checked. */
        const val IN_DECK = "In deck"
    }
}

/** Totals count only played lines; the other lines are kept in [lines] for showing. */
data class DeckCheck(val lines: List<DeckCheckLine>) {
    val needed: Int get() = lines.filter { it.played }.sumOf { it.needed }
    val owned: Int get() = lines.filter { it.played }.sumOf { minOf(it.owned, it.needed) }
    val missingCost: Double get() = lines.filter { it.played }.sumOf { (it.price ?: 0.0) * it.missing }
    /** Some missing played card has no price, so [missingCost] is short. */
    val missingCostPartial: Boolean get() = missingLines.any { it.price == null }
    /** Played lines with copies still missing. */
    val missingLines: List<DeckCheckLine> get() = lines.filter { it.played && it.missing > 0 }

    companion object {
        val PLAYED = setOf("Main", "Commander", "Companion", "Signature")
    }
}

/**
 * What an import added: copies, names not found, and for Undo ([undoImport]) the copies added per row id and the box
 * made for it (null when none was).
 */
data class ImportResult(
    val added: Int,
    val notFound: List<String>,
    val rows: List<Pair<Long, Int>> = emptyList(),
    val newBox: Long? = null,
)

// Backup (the file format) lives in BackupIO.kt.

/** A price change worth telling the user about. */
data class PriceMove(val name: String, val then: Double, val now: Double, val wanted: Boolean)

class CollectionRepository(
    context: Context,
    internal val db: AppDatabase,
    val api: ScryfallApi,
) {
    /** Internal so feature files can add repository extensions. */
    internal val dao = db.cardDao()
    val appContext: Context = context.applicationContext
    private val prefs = context.getSharedPreferences("collection", Context.MODE_PRIVATE)
    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val nameIndex = NameIndex(context.filesDir, api)
    /** The on-device art model, downloaded the first time art needs comparing. */
    val artEmbedder = com.mtgscanner.scan.ArtEmbedder(context.applicationContext)
    /** Printings already in a downloaded art index are compared from there, without fetching their pictures. */
    private val art by lazy { ArtMatcher(embedder = artEmbedder, known = { id -> artIndex.artOf(id) }) }
    /** Downloaded per-set art hashes, to find cards from their artwork when no text can be read. */
    val artIndex = com.mtgscanner.scan.ArtIndexStore(context.filesDir, api, artEmbedder)
    /** Set symbol shapes, drawn from Scryfall's icons as sets are needed. */
    private val setSymbols = com.mtgscanner.scan.SetSymbols(context.applicationContext, api)
    val scanLog = com.mtgscanner.scan.ScanLog(context.filesDir)

    /** One shared read of the cards table for every screen; kept 5 s after the last screen stops watching. */
    private val allCards: Flow<List<CollectionCard>> =
        dao.observeAll().shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun observeAll(): Flow<List<CollectionCard>> = allCards
    fun observe(id: Long): Flow<CollectionCard?> = dao.observe(id)
    fun observeBoxes(): Flow<List<Box>> = dao.observeBoxes()
    fun observeSnapshots(): Flow<List<ValueSnapshot>> = dao.observeSnapshots()
    fun observeDecks(): Flow<List<Deck>> = dao.observeDecks()
    fun observeDeck(id: Long): Flow<Deck?> = dao.observeDeck(id)
    fun observeWishlist(): Flow<List<WishlistItem>> = dao.observeWishlist()
    fun observePending(): Flow<List<PendingScan>> = dao.observePending()
    fun observePriceHistory(scryfallId: String): Flow<List<PricePoint>> = dao.observePriceHistory(scryfallId)

    /**
     * Starts background work: the name list, daily prices and offline scans, and tidies away
     * photos of deleted cards. Called once when a screen first opens, not when a background job or the
     * widget starts the app.
     */
    fun startBackgroundWork() {
        appScope.launch { runCatching { nameIndex.load() } }
        appScope.launch { runCatching { CardPhotos.sweepOrphans(appContext, dao.allPhotos()) } }
        appScope.launch {
            runCatching { refreshPricesIfStale() }
            runCatching { resolvePending() }
            runCatching { dropOldConditionPrices() }
        }
    }

    // Boxes

    /** Every box with how many cards it holds and what they're worth. */
    fun observeBoxSummaries(): Flow<List<BoxSummary>> = boxSummaries

    // Totals come from the database (one small row per box), not from reading every card.
    private val boxSummaries: Flow<List<BoxSummary>> = combine(dao.observeBoxes(), dao.observeBoxTotals()) { boxes, totals ->
        val byBox = totals.associateBy { it.boxId }
        boxes.map { b ->
            val t = byBox[b.id]
            BoxSummary(b.id, b.name, b.kind, t?.n ?: 0, t?.value ?: 0.0, b.rules, b.color, b.fallback, b.capacity, b.divider, valueEur = t?.valueEur ?: 0.0)
        }
    }.shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** New boxes go to the end of the Auto-sort order. */
    suspend fun createBox(name: String, kind: String, rules: BoxRules = BoxRules(), color: Int = 0): Long {
        val next = (dao.allBoxes().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return dao.insertBox(Box(name = name.trim(), kind = kind, rules = rules.toJson(), color = color, sortOrder = next))
            .also { id -> boxChange?.created?.add(id) }
    }

    suspend fun updateBox(
        id: Long, name: String, kind: String, rules: BoxRules, color: Int,
        capacity: Int? = null, divider: String? = null,
    ) {
        val b = dao.box(id) ?: return
        dao.updateBox(
            b.copy(
                name = name.trim(), kind = kind, rules = rules.toJson(), color = color,
                capacity = capacity ?: b.capacity, divider = divider ?: b.divider,
            ),
        )
    }

    /** Sets how many cards a box holds (0 = no limit) and how it's split inside. */
    suspend fun setBoxLayout(id: Long, capacity: Int, divider: String) {
        val b = dao.box(id) ?: return
        dao.updateBox(b.copy(capacity = capacity.coerceAtLeast(0), divider = divider))
    }

    /** Saves the Auto-sort order: [ids] first to last. */
    suspend fun reorderBoxes(ids: List<Long>) = db.withTransaction { ids.forEachIndexed { i, id -> dao.setBoxOrder(id, i) } }

    /** Makes [id] the box for cards nothing else fits, or clears it with null. */
    suspend fun setFallbackBox(id: Long?) = dao.setFallback(id ?: Long.MIN_VALUE)

    suspend fun box(id: Long): Box? = dao.box(id)

    /**
     * Deletes a box. Its cards move to [moveTo], or are deleted when [moveTo] is null.
     * With [moveTo] = [Box.AUTO_SORT] each card goes to the box whose rules fit it.
     */
    suspend fun deleteBox(id: Long, moveTo: Long?) {
        try { deleteBoxNow(id, moveTo) } finally { boxChange = null }
    }

    private suspend fun deleteBoxNow(id: Long, moveTo: Long?) {
        db.withTransaction {
            val cards = dao.inBox(id)
            if (moveTo == null) {
                cards.forEach { logEvent(it.scryfallId, it.name, "removed", "×${it.quantity}") }
                cards.map { it.id }.chunked(500).forEach { dao.deleteLoansOf(it) }
                dao.deleteCardsInBox(id)
            } else {
                // The box is deleted after its cards move, so their history names it.
                val names = placeNames()
                val policy = SortPrefs.policy
                // Counts go up as cards move in, so boxes with a card limit fill and the rest go on to the next box.
                val boxes = boxInfos().filter { it.id != id }.toMutableList()
                for (c in cards) {
                    val f = c.facts()
                    // The deleted box's own copies don't count toward the playset limit: they're the ones moving.
                    // Cards no box's rules fit go to an Anything box rather than being lost.
                    val target = (if (moveTo == Box.AUTO_SORT) decideIn(boxes, f, c.oracleId, policy, exclude = id)?.boxId else moveTo)
                        ?: anythingBox(except = id, facts = f)
                    moveToBox(c.id, target, c, names)
                    val i = boxes.indexOfFirst { it.id == target }
                    if (i >= 0) boxes[i] = boxes[i].copy(count = boxes[i].count + c.quantity)
                }
            }
            dao.box(id)?.let { dao.deleteBox(it) }
        }
        SortPrefs.forgetBox(id)
        afterChange()
    }

    /** What Undo needs to bring back a box after it's deleted or split: the box as it was, its cards' rows and the box order. */
    class BoxUndo internal constructor(
        val box: Box,
        internal val cards: List<CollectionCard>,
        internal val boxIds: Set<Long>,
        internal val order: List<Long>,
        /** Playset limit when this was the extras box, else null. */
        internal val extrasLimit: Int?,
        /** Bulk price floor when this was the bulk box, else null. */
        internal val bulkFloor: Double?,
    ) {
        /** Boxes the delete or split itself made (a new Anything box, a split's second half). */
        internal val created: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet())
    }

    /** The delete or split under way, between [boxUndo] and its end, so the boxes it makes are known. */
    @Volatile private var boxChange: BoxUndo? = null

    /** Takes a [BoxUndo] for [id] before it's deleted or split; null when there's no such box. */
    suspend fun boxUndo(id: Long): BoxUndo? {
        val b = dao.box(id) ?: return null
        val boxes = dao.allBoxes()
        return BoxUndo(
            b, snapshot(dao.inBox(id).map { it.id }), boxes.map { it.id }.toSet(), boxes.map { it.id },
            SortPrefs.playsetLimit.takeIf { SortPrefs.extrasBoxId == id },
            SortPrefs.bulkFloor.takeIf { SortPrefs.bulkBoxId == id },
        ).also { boxChange = it }
    }

    /**
     * Undoes a delete or split: the box comes back with the same id, name, rules and place in the Auto-sort order,
     * its cards go back exactly as they were, and boxes the change made (a split's second half, a new Anything box) go
     * when they're empty again.
     */
    suspend fun undoBox(u: BoxUndo) {
        db.withTransaction {
            dao.putBox(u.box)
            if (u.box.fallback) dao.setFallback(u.box.id)
        }
        restore(u.cards)
        db.withTransaction {
            // Only boxes the change made; an empty box made later stays.
            for (b in dao.allBoxes()) if (b.id in u.created && b.id !in u.boxIds && dao.inBox(b.id).isEmpty()) {
                dao.deleteBox(b)
                SortPrefs.forgetBox(b.id)
            }
            val now = dao.allBoxes().map { it.id }
            // Boxes in their old order; any added since keep their place at the end.
            (u.order.filter { it in now } + now.filter { it !in u.order }).forEachIndexed { i, id -> dao.setBoxOrder(id, i) }
        }
        u.extrasLimit?.let { SortPrefs.updatePlayset(it, u.box.id) }
        u.bulkFloor?.let { SortPrefs.updateBulk(it, u.box.id) }
        afterChange()
    }

    /**
     * Sorts a box (usually a catch-all "Anything" box) after the fact: each card that fits another
     * box's rules moves there, in Auto-sort order. Cards that fit nothing stay put, and so do cards this
     * box's own rules take first and cards set to stay. Returns copies moved.
     */
    suspend fun sortBox(sourceId: Long): Int {
        var moved = 0
        db.withTransaction {
            // The same choice as scanning (rules, room, bulk floor), counting cards as they move in.
            val policy = SortPrefs.policy.copy(playsetLimit = 0)
            val boxes = boxInfos().toMutableList()
            val names = placeNames()
            val src = boxes.indexOfFirst { it.id == sourceId }
            for (c in dao.inBox(sourceId)) {
                if (c.pinned) continue
                // This box is counted without the card, so a full box doesn't push out a card already in it.
                if (src >= 0) boxes[src] = boxes[src].copy(count = boxes[src].count - c.quantity)
                val d = Sorting.sortOutTarget(boxes, sourceId, c.facts(), policy)
                if (d == null) {
                    if (src >= 0) boxes[src] = boxes[src].copy(count = boxes[src].count + c.quantity)
                    continue
                }
                moveToBox(c.id, d.boxId, c, names)
                moved += c.quantity
                val i = boxes.indexOfFirst { it.id == d.boxId }
                if (i >= 0) boxes[i] = boxes[i].copy(count = boxes[i].count + c.quantity)
            }
        }
        return moved
    }

    /** Moves every card in [sourceId] that fits [rules] into [targetId]. Returns copies moved. */
    suspend fun sortInto(sourceId: Long, targetId: Long, rules: BoxRules): Int {
        var moved = 0
        db.withTransaction {
            val names = placeNames()
            for (c in dao.inBox(sourceId)) {
                if (!rules.matches(c.facts())) continue
                moveToBox(c.id, targetId, c, names)
                moved += c.quantity
            }
        }
        return moved
    }

    /**
     * A box that takes any card (never a trade binder or deck), made if there isn't one. With [facts], half of
     * a split "Anything" box whose name range fits the card counts too.
     */
    suspend fun anythingBox(except: Long? = null, facts: CardFacts? = null): Long {
        val boxes = dao.allBoxes().filter { it.id != except && it.kind != "trade" && it.kind != "deck" }
        return boxes.firstOrNull { it.boxRules().isCatchAll }?.id
            ?: facts?.let { f -> boxes.firstOrNull { b -> b.boxRules().let { it.isOpen && it.matches(f) } }?.id }
            ?: createBox("Anything", "box")
    }

    suspend fun cardsInBox(id: Long): List<CollectionCard> = dao.inBox(id)

    /** Every box in Auto-sort order with its card count, for the sorting rules. */
    suspend fun boxInfos(): List<BoxInfo> {
        val counts = dao.boxCounts().associate { it.boxId to it.n }
        return dao.allBoxes().map { BoxInfo(it.id, it.boxRules(), it.kind, it.capacity, counts[it.id] ?: 0, it.fallback) }
    }

    /** Copies of a card already in main boxes, for the keep-a-playset rule. */
    private suspend fun ownedInMain(oracleId: String?, boxes: List<BoxInfo>, policy: SortPolicy, exclude: Long? = null): Int {
        if (oracleId == null || policy.playsetLimit <= 0) return 0
        val notMain = (boxes.filterNot { Sorting.isMainBox(it, policy) }.map { it.id } + listOfNotNull(exclude)).ifEmpty { listOf(Long.MIN_VALUE) }
        return dao.ownedInBoxesOutside(oracleId, notMain)
    }

    /** Where a card goes and why, using box rules, capacity, the bulk floor and the playset limit. */
    suspend fun pickDecision(facts: CardFacts, oracleId: String? = null): SortDecision? =
        decideIn(boxInfos(), facts, oracleId, SortPrefs.policy)

    /** [exclude] is a box whose copies don't count toward the playset limit (a box being emptied). */
    private suspend fun decideIn(boxes: List<BoxInfo>, facts: CardFacts, oracleId: String?, policy: SortPolicy, exclude: Long? = null): SortDecision? =
        Sorting.decide(boxes, facts, policy, ownedInMain(oracleId, boxes, policy, exclude))

    /** The box a card belongs in, or null when no box fits. */
    suspend fun pickBox(facts: CardFacts, oracleId: String? = null): Long? = pickDecision(facts, oracleId)?.boxId

    suspend fun pickBox(card: ScryfallCard, foil: Boolean): Long? = pickBox(card.facts(foil), card.oracleId)

    /** The divider inside [boxId] a card goes behind ("Blue", "R", "MH3"), or null when the box has none. */
    suspend fun spotFor(boxId: Long, name: String, facts: CardFacts): String? {
        val b = dao.box(boxId) ?: return null
        return Sorting.spot(BoxLayout.of(b.divider), name, facts)
    }

    /**
     * Cards that now belong in a different box, after rules changed. Pass [cards] when already loaded. [planned]
     * swaps in boxes as they would be, for a box plan (see BoxPlanRepo.kt).
     */
    suspend fun misplaced(cards: List<CollectionCard>? = null, planned: List<BoxInfo>? = null): List<Sorting.Move<CollectionCard>> {
        val all = cards ?: dao.all()
        val boxes = planned ?: boxInfos()
        val policy = SortPrefs.policy
        val slack = SortPrefs.priceBuffer / 100.0
        return withContext(Dispatchers.Default) {
            // Copies in main boxes per card, for cards leaving the bulk box under the keep-a-playset rule.
            val owned: Map<String, Int> = if (policy.playsetLimit <= 0) emptyMap() else {
                val main = boxes.filter { Sorting.isMainBox(it, policy) }.map { it.id }.toSet()
                all.filter { it.boxId in main && it.oracleId != null }.groupBy { it.oracleId!! }.mapValues { (_, v) -> v.sumOf { it.quantity } }
            }
            Sorting.misplaced(
                all, { it.boxId }, { it.facts() }, boxes, policy,
                copies = { it.quantity },
                ownedInMain = { c -> c.oracleId?.let { owned[it] } ?: 0 },
                priceSlack = slack,
                pinned = { it.pinned },
            )
        }
    }

    /** Applies moves; returns what's needed to undo them (entry id → old box). */
    suspend fun applyMoves(moves: List<Pair<Long, Long>>): List<Pair<Long, Long>> = db.withTransaction {
        val names = placeNames()
        moves.mapNotNull { (id, to) ->
            val c = dao.get(id) ?: return@mapNotNull null
            val newId = moveToBox(id, to, c, names)
            newId to c.boxId
        }
    }

    /**
     * Splits a full box in two by first letter: the original keeps A to the split letter, a new box
     * takes the rest with the same rules. [letter] is where the second half starts; null picks the letter that
     * splits the cards evenly. Returns the new box id, or null when it can't be split.
     */
    suspend fun splitBox(id: Long, letter: Char? = null): Long? = try { splitBoxNow(id, letter) } finally { boxChange = null }

    private suspend fun splitBoxNow(id: Long, letter: Char?): Long? = db.withTransaction {
        val b = dao.box(id) ?: return@withTransaction null
        val cards = dao.inBox(id)
        val rules = b.boxRules()
        // Only cards in the box's own name range decide the split.
        val inRange = cards.filter { rules.inNameRange(it.name) }
        val at = (letter?.uppercaseChar()?.takeIf { it in Sorting.splitLetters(rules.nameFrom, rules.nameTo) }
            ?: Sorting.splitLetter(inRange.map { it.name to it.quantity })) ?: return@withTransaction null
        val before = (at - 1)
        val first = rules.copy(nameFrom = rules.nameFrom ?: "A", nameTo = before.toString())
        val second = rules.copy(nameFrom = at.toString(), nameTo = rules.nameTo ?: "Z")
        val base = b.name.replace(Regex("""\s+\S–\S$"""), "")
        dao.updateBox(b.copy(name = "$base ${first.nameFrom}–${first.nameTo}", rules = first.toJson()))
        val newId = createBox("$base ${second.nameFrom}–${second.nameTo}", b.kind, second, b.color)
        dao.box(newId)?.let { dao.updateBox(it.copy(capacity = b.capacity, divider = b.divider)) }
        // Put the new box right after the old one in the Auto-sort order.
        val order = dao.allBoxes().map { it.id }.toMutableList()
        order.remove(newId); order.add(order.indexOf(id) + 1, newId)
        reorderBoxes(order)
        // Names starting with a digit or symbol fit both halves; they stay in the first, as sorting picks it first.
        // A fallback box also holds cards its rules don't fit (leftovers); those split by name too.
        val names = placeNames()
        for (c in inRange) {
            val letter = BoxRules.nameLetter(c.name)
            if (letter != '#' && letter >= at && (b.fallback || second.matches(c.facts()))) moveToBox(c.id, newId, c, names)
        }
        newId
    }


    /**
     * The row in [boxId] that copies like [c] merge into: same printing ([scryfallId]), [foil], language,
     * condition and signed/altered/graded/proxy flags. Copies that differ in any of these keep their own row.
     */
    internal suspend fun twinOf(
        c: CollectionCard, boxId: Long = c.boxId, scryfallId: String = c.scryfallId, foil: Boolean = c.foil,
    ): CollectionCard? = dao.find(scryfallId, foil, boxId, c.lang, c.condition, c.signed, c.altered, c.graded, c.proxy)

    /**
     * Moves an entry to another box, merging with identical copies already there. Returns the surviving id.
     * Bulk callers pass the [row] they already read and [names] of places (see [placeNames]), to save reads per card.
     */
    suspend fun moveToBox(id: Long, boxId: Long, row: CollectionCard? = null, names: Map<Long, String>? = null): Long {
        val c = row?.takeIf { it.id == id } ?: dao.get(id) ?: return id
        if (c.boxId == boxId) return id
        val other = twinOf(c, boxId = boxId)
        val now = System.currentTimeMillis()
        suspend fun name(box: Long) = names?.get(box) ?: boxName(box)
        logEvent(c.scryfallId, c.name, "moved", "${name(c.boxId)} → ${name(boxId)}")
        return if (other != null) {
            dao.update(other.mergedWith(c, now = now))
            dao.delete(c)
            dao.moveLoans(c.id, other.id)
            other.id
        } else {
            // Its old binder pocket means nothing in the new box.
            dao.update(c.copy(boxId = boxId, binderSlot = 0, updatedAt = now))
            id
        }
    }

    /** The box chosen last time on the scan screen. */
    var lastBoxId: Long
        get() = prefs.getLong("lastBox", Box.AUTO_SORT)
        set(v) { prefs.edit().putLong("lastBox", v).apply() }

    // Identifying scans

    /** Reads that found a card. Bounded, like the other caches, so a long scanning session doesn't keep growing it. */
    private val lookupCache = LruCache<String, Identification>(500)
    /** Reads that found nothing, and when: the same garbage read isn't looked up again for [MISS_TTL_MS]. */
    private val missCache = HashMap<String, Long>()

    /**
     * Finds the card for an OCR guess. Tries the exact printing (set + collector number) first,
     * then the card name (corrected against the offline name list), and uses the artwork to pick
     * the printing when the footer couldn't be read. Throws [IOException] when offline.
     */
    suspend fun identify(raw: CardTextGuess, skip: ((ScryfallCard) -> Boolean)? = null): Identification? {
        // With the set lock on, a read with no set code is looked up in that set first. A set code printed on the
        // card wins (Special Guests and The List carry their own).
        val lock = SortPrefs.setLock?.takeIf { raw.setCode == null }
        val guess = lock?.let { raw.copy(setCode = it) } ?: raw
        // [skip]: a card auto-add will skip anyway (say, a basic land) isn't narrowed to its printing.
        val key = (guess.key ?: return null) + (lock?.let { "@$it" } ?: "") + (if (skip != null) "|s" else "")
        val cached: Identification? = lookupCache.get(key)
        if (cached == null) {
            val missedAt = synchronized(missCache) { missCache[key] }
            if (missedAt != null && System.currentTimeMillis() - missedAt < MISS_TTL_MS) return null
        }
        val base = cached ?: doIdentify(guess, skip).also { r ->
            if (r != null) lookupCache.put(key, r)
            else synchronized(missCache) {
                if (missCache.size > 500) missCache.clear()
                missCache[key] = System.currentTimeMillis()
            }
        }
        base ?: return null
        if (guess.collectorNumber == null && skip?.invoke(base.card) != true) choosePrinting(base, guess)?.let { return it }
        return base
    }

    /**
     * The footer's number wasn't read, so the name alone gave Scryfall's newest printing. Narrows the printings
     * by the copyright year, then the set symbol, then the artwork; with only the year to go on, the newest
     * printing from that year. Null when nothing narrows it (keep [base]).
     */
    private suspend fun choosePrinting(base: Identification, guess: CardTextGuess): Identification? {
        val year = guess.year
        val symbol = guess.symbol
        val hash = guess.artHash
        val embedding = guess.artEmbedding
        if (year == null && symbol == null && hash == null && embedding == null) return null
        val all = runCatching { printingsCached(base.card) }.getOrNull() ?: return null
        // With the set code read (or the set lock standing in for it), only that set's printings are candidates.
        val set = guess.setCode
        var pool = set?.let { s -> all.filter { it.set.equals(s, ignoreCase = true) }.ifEmpty { all } } ?: all
        if (pool.size < 2) return null
        pool = PrintingPick.newestFirst(pool) { it.releasedAt }
        var narrowed = false
        if (year != null) {
            val inYear = PrintingPick.byYear(pool, year) { it.releasedAt }
            if (inYear.isNotEmpty()) { pool = inYear; narrowed = true }
        }
        var bySymbol = false
        val sets = pool.map { it.set.lowercase() }.distinct()
        if (symbol != null && sets.size > 1) {
            val winners = runCatching { setSymbols.pick(symbol, sets) }.getOrNull()
            if (winners != null) {
                pool = pool.filter { it.set.lowercase() in winners }
                narrowed = true
                bySymbol = true
            }
        }
        if (pool.size > 1 && (hash != null || embedding != null)) {
            runCatching { art.bestPrinting(pool, hash, embedding) }.getOrNull()?.let { return base.copy(card = it, exact = true) }
        }
        if (!narrowed) return null
        val pick = pool.first()
        return base.copy(card = pick, exact = base.exact || (bySymbol && pool.size == 1))
    }

    /**
     * Printings sharing the collector number read, narrowed by the copyright year, the set symbol and the art.
     * Returns the pick and whether it's sure (one printing left, or the art matched).
     */
    private suspend fun narrowSameNumber(same: List<ScryfallCard>, guess: CardTextGuess): Pair<ScryfallCard, Boolean> {
        if (same.size == 1) return same[0] to true
        var pool = PrintingPick.newestFirst(same) { it.releasedAt }
        guess.year?.let { y -> PrintingPick.byYear(pool, y) { it.releasedAt }.takeIf { it.isNotEmpty() }?.let { pool = it } }
        val symbol = guess.symbol
        val sets = pool.map { it.set.lowercase() }.distinct()
        if (pool.size > 1 && symbol != null && sets.size > 1) {
            runCatching { setSymbols.pick(symbol, sets) }.getOrNull()?.let { winners ->
                pool.filter { it.set.lowercase() in winners }.takeIf { it.isNotEmpty() }?.let { pool = it }
            }
        }
        if (pool.size > 1 && (guess.artHash != null || guess.artEmbedding != null)) {
            runCatching { art.bestPrinting(pool, guess.artHash, guess.artEmbedding) }.getOrNull()?.let { return it to true }
        }
        return pool.first() to (pool.size == 1)
    }

    private val printingsCache = LruCache<String, List<ScryfallCard>>(300)
    private suspend fun printingsCached(card: ScryfallCard): List<ScryfallCard> {
        val k = card.oracleId ?: card.name
        printingsCache.get(k)?.let { return it }
        val list = api.printings(card)
        printingsCache.put(k, list)
        return list
    }

    /** What to save for later when the camera read a name but Scryfall can't be reached. */
    suspend fun offlineRead(guess: CardTextGuess): OfflineRead? {
        val best = withContext(Dispatchers.Default) { nameIndex.correct(guess.nameCandidates) }.firstOrNull()?.takeIf { it.score >= 0.85 }
        if (best != null) return OfflineRead(best.name, guess.setCode, guess.collectorNumber, lang = guess.lang)
        // No English name (only the footer, or a name in Japanese, Chinese or Korean): set and number find it later.
        val set = guess.setCode ?: return null
        val number = guess.collectorNumber ?: return null
        return OfflineRead(guess.printedName ?: "${set.uppercase()} #$number", set, number, lang = guess.lang)
    }

    private suspend fun doIdentify(guess: CardTextGuess, skip: ((ScryfallCard) -> Boolean)? = null): Identification? {
        // Real card names close to what was read come first, then the raw text.
        // Comparing against every card name is heavy: keep it off the main thread.
        val corrected = withContext(Dispatchers.Default) { nameIndex.correct(guess.nameCandidates) }
        val names = (corrected.map { it.name } + guess.nameCandidates).distinct()
        fun bestSimilarity(card: ScryfallCard): Double {
            val cardNames = listOf(card.name) + card.name.split(" // ")
            return names.maxOfOrNull { n -> cardNames.maxOf { CardTextParser.similarity(n, it) } } ?: 0.0
        }

        if (guess.setCode != null && guess.collectorNumber != null) {
            val card = runCatching { api.bySetAndNumber(guess.setCode, guess.collectorNumber) }
                .onFailure { if (it is IOException && it !is ScryfallException) throw it }
                .getOrNull()
            if (card != null) {
                val sim = bestSimilarity(card)
                // If we couldn't read a name, trust the footer; otherwise the name has to agree.
                if (names.isEmpty() || sim >= 0.6) return Identification(card, confident = true, exact = true)
            }
            // A token prints its card set's code: the number may be in the token set ("tmom") instead.
            if (names.isNotEmpty()) {
                val token = runCatching { api.bySetAndNumber("t" + guess.setCode, guess.collectorNumber) }
                    .onFailure { if (it is IOException && it !is ScryfallException) throw it }
                    .getOrNull()
                if (token != null && bestSimilarity(token) >= 0.6) return Identification(token, confident = true, exact = true)
            }
        }

        // The name read is a token's or emblem's: look in the extras, which the name lookup is not made for.
        corrected.firstOrNull()?.takeIf { it.extra && it.score >= 0.85 }?.let { t ->
            runCatching { findExtra(t.name, guess) }
                .onFailure { if (it is IOException && it !is ScryfallException) throw it }
                .getOrNull()?.let { return it }
        }

        val knownGood = corrected.firstOrNull()?.takeIf { it.score >= 0.85 }?.name
        for (name in names) {
            val inSet = guess.setCode?.let { set -> runCatching { api.named(name, set) }.getOrNull() }
            val card = inSet ?: api.named(name) ?: continue
            val sim = bestSimilarity(card)
            if (sim < 0.5) continue
            val confident = sim >= 0.85 || card.name == knownGood
            // Skipped anyway (a basic land with "Skip basics" on): don't search its many printings.
            if (skip?.invoke(card) == true) return Identification(card, confident = confident)
            // Fuzzy search returns the newest printing; with the collector number but no set code (2003-2019 cards
            // print "241/350"), several sets can have that number: narrow them, and it's exact only if one is left.
            val number = guess.collectorNumber
            if (inSet == null && number != null) {
                val same = runCatching { printingsCached(card) }.getOrNull().orEmpty()
                    .filter { it.collectorNumber.equals(number, ignoreCase = true) }
                if (same.isNotEmpty()) {
                    val (pick, sure) = narrowSameNumber(same, guess)
                    return Identification(pick, confident = confident, exact = sure)
                }
            }
            return Identification(card, confident = confident, exact = inSet != null && number != null)
        }
        guess.printedName?.let { printed -> return identifyPrinted(printed, guess) }
        return null
    }

    /**
     * A name read in Japanese, Chinese or Korean: searched among Scryfall's printed names in that language, the
     * whole name first, then each half (one misread letter spoils a search), and the closest printed name taken
     * when it's close enough. The English printing with the same set and number is used when there is one, so
     * prices, art indexes and printing choice work as for any card; the language read is recorded with it.
     */
    private suspend fun identifyPrinted(printed: String, guess: CardTextGuess): Identification? {
        val lang = guess.lang?.takeIf { it in com.mtgscanner.scan.CjkNames.LANGS }
        for (term in com.mtgscanner.scan.CjkNames.searchTerms(printed)) {
            val found = runCatching { api.searchPrinted(term, lang) }
                .onFailure { if (it is IOException && it !is ScryfallException) throw it }
                .getOrNull().orEmpty()
            if (found.isEmpty()) continue
            val names = found.map { c -> c to (c.printedName ?: c.cardFaces.orEmpty().firstNotNullOfOrNull { it.printedName }) }
            val (card, score) = com.mtgscanner.scan.CjkNames.best(printed, names) ?: continue
            val english = if (card.lang != "en") {
                runCatching { api.bySetAndNumber(card.set, card.collectorNumber) }
                    .onFailure { if (it is IOException && it !is ScryfallException) throw it }
                    .getOrNull()?.takeIf { it.oracleId == null || it.oracleId == card.oracleId }
            } else null
            return Identification(english ?: card, confident = score >= 0.85)
        }
        return null
    }

    // Adding and editing

    private val addLock = Mutex()

    /**
     * Adds [qty] copies to a box, merging with an existing row of identical copies (same printing, finish,
     * [lang] and [condition], unsigned, unaltered, ungraded, not a proxy). Returns the row id.
     */
    suspend fun add(
        card: ScryfallCard, foil: Boolean, qty: Int = 1, boxId: Long,
        lang: String = card.lang, condition: String = "NM",
    ): Long {
        val now = System.currentTimeMillis()
        val entity = card.toEntity(foil, qty, now).copy(boxId = boxId, lang = lang, condition = condition)
        // One add at a time, so two copies added together (like a binder page) merge instead of clashing.
        val id = addLock.withLock {
            val existing = twinOf(entity)
            if (existing != null) {
                dao.update(existing.mergedWith(entity, now = now, details = false))
                existing.id
            } else {
                dao.insert(entity)
            }
        }
        logEvent(card.id, card.name, "added", "×$qty → ${boxName(boxId)}")
        appScope.launch { runCatching { dao.putPricePoints(listOf(card.pricePoint())) } }
        afterChange()
        return id
    }

    /** Collection changes waiting for the value snapshot. */
    private val changes = Channel<Unit>(Channel.UNLIMITED)

    /**
     * Notes a change to the collection. The value snapshot (and widget) runs once
     * things go quiet for [CHANGE_IDLE_MS], or after [CHANGE_BATCH] changes, not once per card.
     */
    private fun afterChange() { changes.trySend(Unit) }

    init {
        appScope.launch {
            while (true) {
                changes.receive()
                var n = 1
                while (n < CHANGE_BATCH && withTimeoutOrNull(CHANGE_IDLE_MS) { changes.receive() } != null) n++
                runCatching { recordSnapshot() }
            }
        }
    }

    /** Removes [qty] copies (used for undo). Deletes the row when it reaches zero. */
    suspend fun remove(id: Long, qty: Int = 1) {
        val c = dao.get(id) ?: return
        logEvent(c.scryfallId, c.name, "removed", "×${minOf(qty, c.quantity)}")
        if (c.quantity <= qty) deleteRow(c)
        else dao.update(c.copy(quantity = c.quantity - qty, updatedAt = System.currentTimeMillis()))
        afterChange()
    }

    suspend fun setQuantity(id: Long, qty: Int) {
        val c = dao.get(id) ?: return
        if (qty <= 0) deleteRow(c) else dao.update(c.copy(quantity = qty, updatedAt = System.currentTimeMillis()))
        afterChange()
    }

    /** Deletes a row whose copies left the collection, and any loan of them, so a loan never moves to another copy. */
    private suspend fun deleteRow(c: CollectionCard) {
        dao.delete(c)
        dao.deleteLoansOf(listOf(c.id))
    }

    /**
     * Saves an edited row. When the edit makes it identical to another row (say, its condition now matches),
     * the two merge. Returns the id of the row now holding the copies.
     */
    suspend fun update(card: CollectionCard): Long {
        val old = dao.get(card.id)
        // A new condition or finish needs a new condition price.
        val changed = old != null && (old.condition != card.condition || old.foil != card.foil)
        if (old != null && old.condition != card.condition) logEvent(card.scryfallId, card.name, "edited", "${old.condition} → ${card.condition}")
        val now = System.currentTimeMillis()
        val twin = if (old != null && !old.sameCopyAs(card)) twinOf(card)?.takeIf { it.id != card.id } else null
        if (twin != null) {
            db.withTransaction {
                dao.update(twin.mergedWith(card, now = now))
                dao.delete(card)
                dao.moveLoans(card.id, twin.id)
            }
            afterChange()
            return twin.id
        }
        dao.update(card.copy(updatedAt = now).let { if (changed) it.copy(conditionPrice = null, conditionPriceAt = 0) else it })
        afterChange()
        return card.id
    }

    /** Switches foil on/off, merging into the other entry if one already exists. Returns the surviving id. */
    suspend fun setFoil(id: Long, foil: Boolean): Long {
        val c = dao.get(id) ?: return id
        if (c.foil == foil) return id
        val other = twinOf(c, foil = foil)
        val kept = if (other != null) {
            db.withTransaction {
                dao.update(other.mergedWith(c))
                dao.delete(c)
                dao.moveLoans(c.id, other.id)
            }
            other.id
        } else {
            dao.update(c.copy(foil = foil, conditionPrice = null, conditionPriceAt = 0, updatedAt = System.currentTimeMillis()))
            id
        }
        afterChange()
        return kept
    }

    suspend fun delete(id: Long) {
        dao.get(id)?.let { logEvent(it.scryfallId, it.name, "removed", "×${it.quantity}"); deleteRow(it) }
        afterChange()
    }

    // History

    private suspend fun boxName(id: Long): String = dao.box(id)?.placeName() ?: "?"

    /** Every box's and deck's name by id, read once for a bulk move's history notes. */
    internal suspend fun placeNames(): Map<Long, String> = dao.allPlaces().associate { it.id to it.placeName() }

    /** Records something that happened to a card, for its history. Never fails the change itself. */
    suspend fun logEvent(scryfallId: String, name: String, kind: String, detail: String = "") {
        runCatching { dao.addEvent(CardEvent(scryfallId = scryfallId, name = name, kind = kind, detail = detail)) }
    }

    fun observeHistory(scryfallId: String): Flow<List<CardEvent>> = dao.observeEvents(scryfallId)

    // Goals

    fun observeGoals(): Flow<List<Goal>> = dao.observeGoals()
    suspend fun addGoal(goal: Goal): Long = dao.addGoal(goal)
    suspend fun deleteGoal(id: Long) = dao.deleteGoal(id)

    // Unknown scans

    /** Saves a card the scanner couldn't identify, so it can be searched by hand later. */
    suspend fun addUnknown(text: String, boxId: Long, foil: Boolean = false, qty: Int = 1, lang: String? = null) {
        if (text.isBlank()) return
        dao.addPending(
            PendingScan(
                name = text.trim().take(80), setCode = null, collectorNumber = null, foil = foil, quantity = qty.coerceAtLeast(1),
                boxId = boxId, unknown = true, lang = lang?.takeIf { it != "en" },
            ),
        )
    }

    // Older Androids allow at most 999 values in one query, so long id lists go in chunks.
    private suspend fun rowsByIds(ids: List<Long>) = ids.distinct().chunked(500).flatMap { dao.byIds(it) }
    private suspend fun rowsByScryfallIds(ids: List<String>) = ids.distinct().chunked(500).flatMap { dao.byScryfallIds(it) }

    // Undo, bulk edits and tags

    /**
     * Everything needed to undo a change to these rows: every row of the same printings. Rows only ever merge
     * with rows of the same printing, so this covers the rows a move or merge lands in too.
     */
    suspend fun snapshot(rowIds: List<Long>): List<CollectionCard> {
        val ids = rowsByIds(rowIds).map { it.scryfallId }.distinct()
        return if (ids.isEmpty()) emptyList() else rowsByScryfallIds(ids)
    }

    /**
     * Puts rows back as [snapshot] saw them, undoing merges, moves and deletes. Rows of those printings added
     * since (new scans, newer than anything in [before]) are kept; one identical to a restored row joins it.
     * Copies that were added into an existing row since can't be told apart, so they're undone too.
     */
    suspend fun restore(before: List<CollectionCard>) {
        if (before.isEmpty()) return
        db.withTransaction {
            val ids = before.map { it.scryfallId }.distinct()
            val keepIds = before.map { it.id }.toSet()
            val since = before.maxOf { it.updatedAt }
            val newer = rowsByScryfallIds(ids).filter { it.id !in keepIds && it.addedAt > since }
            ids.chunked(500).forEach { dao.deleteByScryfallIds(it) }
            val rows = before.toMutableList()
            for (n in newer) {
                val i = rows.indexOfFirst { it.sameCopyAs(n) }
                if (i >= 0) rows[i] = rows[i].mergedWith(n, details = false) else rows += n
            }
            dao.putCards(rows)
        }
        afterChange()
    }

    suspend fun moveMany(rowIds: List<Long>, boxId: Long) = db.withTransaction {
        val names = placeNames()
        rowIds.forEach { moveToBox(it, boxId, names = names) }
    }

    suspend fun deleteMany(rowIds: List<Long>) {
        db.withTransaction { rowsByIds(rowIds).forEach { logEvent(it.scryfallId, it.name, "removed", "×${it.quantity}"); deleteRow(it) } }
        afterChange()
    }

    /** Moves [qty] copies of a row to another box, splitting the row if some copies stay. All or nothing. */
    suspend fun moveCopies(rowId: Long, qty: Int, boxId: Long): Unit = db.withTransaction {
        val c = dao.get(rowId) ?: return@withTransaction
        if (c.boxId == boxId) return@withTransaction
        if (qty >= c.quantity) { moveToBox(rowId, boxId); return@withTransaction }
        val now = System.currentTimeMillis()
        dao.update(c.copy(quantity = c.quantity - qty, updatedAt = now))
        val other = twinOf(c, boxId = boxId)
        if (other != null) dao.update(other.mergedWith(c, copies = qty, now = now, details = false))
        else dao.insert(c.copy(id = 0, quantity = qty, boxId = boxId, binderSlot = 0, updatedAt = now))
    }

    /** Several [moveCopies] at once, all or nothing. Moves are (row id, copies). */
    suspend fun moveCopiesMany(moves: List<Pair<Long, Int>>, boxId: Long) =
        db.withTransaction { moves.forEach { (id, n) -> moveCopies(id, n, boxId) } }

    suspend fun addTag(rowIds: List<Long>, tag: String) {
        val t = tag.trim().replace(",", " ").ifEmpty { return }
        db.withTransaction {
            rowsByIds(rowIds).forEach { c ->
                val tags = c.userTagList()
                if (tags.none { it.equals(t, ignoreCase = true) }) dao.update(c.copy(userTags = (tags + t).joinToString(",")))
            }
        }
    }

    /** Takes a tag off, matched like [addTag]: trimmed, any case. */
    suspend fun removeTag(rowId: Long, tag: String) {
        val c = dao.get(rowId) ?: return
        val t = tag.trim()
        dao.update(c.copy(userTags = c.userTagList().filterNot { it.equals(t, ignoreCase = true) }.joinToString(",")))
    }

    // Sets

    private var setsCache: List<ScryfallSet>? = null
    /** The last 20 sets opened; a set can hold about 2,000 printings. */
    private val setCardsCache = LruCache<String, List<ScryfallCard>>(20)

    /** Paper sets from Scryfall, fetched once per app run. */
    suspend fun sets(): List<ScryfallSet> = setsCache ?: api.sets().also { setsCache = it }

    /** Every printing in a set, extras included, in collector number order. */
    suspend fun setCards(code: String): List<ScryfallCard> {
        val k = code.lowercase()
        setCardsCache.get(k)?.let { return it }
        val list = api.searchAll("e:$k include:extras", unique = "prints", maxPages = 12)
            .sortedWith(compareBy<ScryfallCard> { it.collectorNumber.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.collectorNumber })
        setCardsCache.put(k, list)
        return list
    }

    /**
     * Condition prices came from JustTCG, which the app no longer uses, and no price reads them any more. They're
     * cleared once on every phone (a restored backup's are dropped too), so none are left in the database.
     */
    private suspend fun dropOldConditionPrices() {
        if (prefs.getBoolean("conditionPricesCleared", false)) return
        clearConditionPrices()
        appContext.getSharedPreferences("justtcg", Context.MODE_PRIVATE).edit().clear().apply()
        prefs.edit().putBoolean("conditionPricesCleared", true).apply()
    }

    suspend fun cardOnce(id: Long): CollectionCard? = dao.get(id)

    /** Drops every condition price, back to near-mint prices. */
    suspend fun clearConditionPrices() {
        dao.clearConditionPrices()
        runCatching { recordSnapshot() }
    }

    suspend fun allCards(): List<CollectionCard> = dao.all()

    // Prices

    private val priceLock = Mutex()

    /**
     * Pulls today's prices from Scryfall for every card in the collection. Returns how many were updated.
     * One refresh runs at a time; skipped (0) when prices are newer than [maxAgeMs], or when another
     * refresh finished while this one waited.
     */
    suspend fun refreshPrices(maxAgeMs: Long = 0): Int {
        val asked = System.currentTimeMillis()
        return priceLock.withLock {
            val last = prefs.getLong("pricesAt", 0)
            if (last >= asked || (maxAgeMs > 0 && asked - last < maxAgeMs)) 0 else refreshPricesLocked()
        }
    }

    private suspend fun refreshPricesLocked(): Int {
        val ids = dao.allScryfallIds()
        if (ids.isEmpty()) return 0
        // Fetched and saved a batch at a time: a failed request keeps what came before, and network
        // errors and "too many requests" are tried again after a pause.
        var updated = 0
        for (chunk in ids.chunked(150)) {
            val fresh = withBackoff { api.collection(chunk) }
            // Only the price columns are written, so a quantity or box changed during the download is kept.
            db.withTransaction {
                fresh.forEach { f ->
                    // One UPDATE per printing. Bans and rarity changes reach box rules.
                    updated += dao.setFreshPrinting(
                        scryfallId = f.id,
                        usd = f.prices.usd?.toDoubleOrNull(),
                        foil = (f.prices.usdFoil ?: f.prices.usdEtched)?.toDoubleOrNull(),
                        treatments = f.treatments.joinToString(","),
                        eur = f.prices.eur?.toDoubleOrNull(),
                        eurFoil = f.prices.eurFoil?.toDoubleOrNull(),
                        power = f.power ?: f.cardFaces?.firstOrNull()?.power,
                        toughness = f.toughness ?: f.cardFaces?.firstOrNull()?.toughness,
                        reserved = f.reserved,
                        rarity = f.rarity,
                        tags = f.scryfallTags().joinToString(","),
                    )
                }
            }
            dao.putPricePoints(fresh.map { it.pricePoint() })
        }
        // Daily points older than 90 days thin out to weekly ones.
        runCatching { dao.thinPriceHistory(LocalDate.now().minusDays(90).toString()) }
        // Wishlist prices too, so its total and alerts stay current.
        val wishes = dao.allWishlist()
        if (wishes.isNotEmpty()) {
            val freshWish = runCatching { withBackoff { api.collection(wishes.map { it.scryfallId }) } }.getOrDefault(emptyList()).associateBy { it.id }
            wishes.forEach { w -> freshWish[w.scryfallId]?.let { f -> dao.updateWish(w.copy(priceUsd = f.prices.usd?.toDoubleOrNull())) } }
        }
        prefs.edit().putLong("pricesAt", System.currentTimeMillis()).apply()
        recordSnapshot()
        return updated
    }

    /**
     * Refreshes prices and returns the cards (owned or wanted) whose price moved by at least
     * [percent] and [dollars] since the last alert about them. New cards just start being watched.
     */
    private val alertSer = MapSerializer(String.serializer(), Double.serializer())

    suspend fun priceMoves(percent: Double, dollars: Double): List<PriceMove> {
        // Prices fetched in the last hour (the app just opened) are fresh enough.
        refreshPrices(maxAgeMs = 60L * 60 * 1000)
        val baseFile = java.io.File(appContext.filesDir, "alert-baseline.json")
        val base: MutableMap<String, Double> = runCatching {
            Json.decodeFromString(alertSer, baseFile.readText())
        }.getOrDefault(emptyMap()).toMutableMap()
        val current = buildMap {
            dao.allPriceRows().forEach { c -> c.nearMint()?.let { put("own:${c.scryfallId}:${c.foil}", Triple(c.name, it, false)) } }
            dao.allWishlist().forEach { w -> w.priceUsd?.let { put("want:${w.oracleId}", Triple(w.name, it, true)) } }
        }
        val moves = mutableListOf<PriceMove>()
        current.forEach { (key, v) ->
            val (name, now, wanted) = v
            val then = base[key]
            if (then == null) { base[key] = now; return@forEach }
            val diff = now - then
            val pct = if (then > 0) kotlin.math.abs(diff) / then * 100 else 100.0
            if (kotlin.math.abs(diff) >= dollars && pct >= percent) {
                moves += PriceMove(name, then, now, wanted)
                base[key] = now
            }
        }
        base.keys.retainAll(current.keys)
        runCatching {
            baseFile.writeText(Json.encodeToString(alertSer, base))
        }
        return moves.sortedByDescending { kotlin.math.abs(it.now - it.then) }
    }

    /** Refreshes prices once a day, when the app opens. */
    suspend fun refreshPricesIfStale() {
        val last = prefs.getLong("pricesAt", 0)
        if (System.currentTimeMillis() - last > 20L * 60 * 60 * 1000) refreshPrices()
        else recordSnapshot()
    }

    private val snapshotLock = Mutex()

    /** Saves today's collection value for the value chart, and the totals for the widget. Summed in SQL, not by reading every card. */
    suspend fun recordSnapshot() = snapshotLock.withLock {
        val t = dao.collectionTotal()
        if (t.n > 0) dao.putSnapshot(ValueSnapshot(LocalDate.now().toString(), t.value, t.n))
        com.mtgscanner.widget.CollectionWidget.publish(appContext, t.value, t.n, t.valueEur)
    }

    // Decks

    /** Saves a deck. A null [format] or [commanders] keeps what the deck has (none for a new deck). */
    suspend fun saveDeck(id: Long, name: String, text: String, format: String? = null, commanders: String? = null): Long {
        val now = System.currentTimeMillis()
        val existing = if (id != 0L) dao.deck(id) else null
        val deck = existing?.copy(
            name = name.trim(), text = text, updatedAt = now,
            format = format ?: existing.format, commanders = commanders ?: existing.commanders,
        ) ?: Deck(name = name.trim(), text = text, format = format.orEmpty(), commanders = commanders.orEmpty())
        val r = dao.putDeck(deck)
        // The cards kept in the deck go by its name.
        if (existing != null && existing.name != deck.name) dao.deckSlot(existing.id)?.let { dao.updateBox(it.copy(name = deck.name)) }
        return if (existing != null) existing.id else r
    }

    suspend fun deleteDeck(id: Long) = dao.deleteDeck(id)

    /**
     * Compares a decklist with the collection: what's owned, where it is, and what's missing. [places] are boxes
     * and decks' card rows; copies kept in [deckSlotId] (this deck's own cards) count as in the deck.
     */
    suspend fun checkDeck(text: String, cards: List<CollectionCard>, places: List<Box>, deckSlotId: Long? = null): DeckCheck {
        val lines = DecklistParser.parse(text)
        // This deck's own rows (normally one), and every place by id.
        val ownDeck = places.firstOrNull { it.id == deckSlotId }?.deckId?.takeIf { it != 0L }
        val ownSlots = places.filter { it.id == deckSlotId || (ownDeck != null && it.deckId == ownDeck) }.map { it.id }.toSet()
        val placeInfo = places.associate { it.id to DeckPlaces.Place(it.placeName(), it.deckId) }
        fun key(n: String) = com.mtgscanner.util.CardNames.key(n)
        val byName = cards.groupBy { key(it.name) }
        // Hand owned copies out line by line, so a card in both the main deck and sideboard isn't counted twice.
        // Played sections (main deck, commander, companion) get them first.
        val left = byName.mapValues { (_, cs) -> cs.sumOf { it.quantity } }.toMutableMap()
        val ownedFor = IntArray(lines.size)
        val order = lines.indices.sortedBy { if (lines[it].section in DeckCheck.PLAYED) 0 else 1 }
        for (i in order) {
            val l = lines[i]
            val k = key(l.name)
            val have = minOf(left[k] ?: 0, l.quantity)
            left[k] = (left[k] ?: 0) - have
            ownedFor[i] = have
        }
        // Of those, the copies already kept in this deck, handed out the same way.
        val inDeckLeft: MutableMap<String, Int> = if (ownSlots.isEmpty()) HashMap() else
            byName.mapValues { (_, cs) -> cs.filter { it.boxId in ownSlots }.sumOf { it.quantity } }.toMutableMap()
        val inDeckFor = IntArray(lines.size)
        for (i in order) {
            val k = key(lines[i].name)
            val n = minOf(inDeckLeft[k] ?: 0, ownedFor[i])
            inDeckLeft[k] = (inDeckLeft[k] ?: 0) - n
            inDeckFor[i] = n
        }
        // Copies of the exact printing a line names, for its picture and price.
        val samePrinting = lines.map { l ->
            if (l.setCode == null) emptyList()
            else byName[key(l.name)].orEmpty().filter { PrintingLookup.samePrinting(l.setCode, l.collectorNumber, it.setCode, it.collectorNumber) }
        }
        // Prices and pictures for missing cards, and for printings named but not owned, come from Scryfall:
        // that set's printing (and number) first. Ignore failures (offline).
        val toLook = lines.indices.filter { ownedFor[it] < lines[it].quantity || (lines[it].setCode != null && samePrinting[it].isEmpty()) }
        val looked = runCatching {
            val wants = toLook.map { PrintingLookup.Wanted(lines[it].name, lines[it].setCode, lines[it].collectorNumber) }
            val found = PrintingLookup.resolve(wants) { ids -> api.identify(ids.map(PrintingLookup::json)) }
            // A set with no number: show that set's normal printing, not a borderless or showcase one.
            api.preferRegular(wants, found)
        }.getOrNull()
        val lookedUp = HashMap<Int, ScryfallCard>()
        looked?.forEachIndexed { k, c -> if (c != null) lookedUp[toLook[k]] = c }
        return DeckCheck(lines.mapIndexed { i, l ->
            val owned = byName[key(l.name)].orEmpty()
            val exact = samePrinting[i]
            val sc = lookedUp[i]
            val named = l.setCode != null
            val where = DeckPlaces.where(owned.map { it.boxId to it.quantity }, placeInfo, ownSlots)
            // The printing shown: a copy of the listed one, else the one Scryfall found for the line.
            val scShown = sc?.takeIf { named && PrintingLookup.samePrinting(l.setCode, null, it.set, it.collectorNumber) }
            DeckCheckLine(
                name = owned.firstOrNull()?.name ?: sc?.name ?: l.name,
                section = l.section,
                needed = l.quantity,
                owned = ownedFor[i],
                boxes = where.boxes,
                otherDecks = where.otherDecks,
                inDeck = inDeckFor[i],
                shownNumber = l.collectorNumber ?: exact.map { it.collectorNumber }.distinct().singleOrNull() ?: scShown?.collectorNumber,
                variant = if (!named) null else if (exact.isNotEmpty()) SetPrintings.label(exact.first().treatments)
                    else scShown?.let { SetPrintings.label(it.treatments) },
                ownedOtherPrinting = named && ownedFor[i] > 0 && exact.isEmpty(),
                price = exact.mapNotNull { it.priceUsd }.minOrNull()
                    ?: (if (named) sc?.prices?.usd?.toDoubleOrNull() else null)
                    ?: owned.mapNotNull { it.priceUsd }.minOrNull() ?: sc?.prices?.usd?.toDoubleOrNull(),
                imageUrl = exact.firstOrNull()?.imageUrl ?: (if (named) sc?.imageUrl else null) ?: owned.firstOrNull()?.imageUrl ?: sc?.imageUrl,
                oracleId = owned.firstOrNull()?.oracleId ?: sc?.oracleId,
                setCode = l.setCode,
                collectorNumber = l.collectorNumber,
                otherPrinting = named && exact.isEmpty() && sc != null && !PrintingLookup.samePrinting(l.setCode, l.collectorNumber, sc.set, sc.collectorNumber),
            )
        })
    }

    // Wishlist

    suspend fun addToWishlist(card: ScryfallCard, wanted: Int = 1) {
        val oracle = card.oracleId ?: return
        val existing = dao.wishlistItem(oracle)
        if (existing != null) dao.updateWish(existing.copy(wanted = existing.wanted + wanted))
        else dao.addWish(
            WishlistItem(
                oracleId = oracle, scryfallId = card.id, name = card.name, imageUrl = card.imageUrl,
                priceUsd = card.prices.usd?.toDoubleOrNull(), wanted = wanted,
            ),
        )
    }

    /** Wants at least [wanted] copies of a card: raises the wishlist count to [wanted] if it's lower, never adds on top. */
    suspend fun wantAtLeast(card: ScryfallCard, wanted: Int) {
        if (wanted <= 0) return
        val oracle = card.oracleId ?: return
        val existing = dao.wishlistItem(oracle)
        if (existing != null) { if (existing.wanted < wanted) dao.updateWish(existing.copy(wanted = wanted)) }
        else addToWishlist(card, wanted)
    }

    /**
     * Adds the missing cards of a deck's played sections to the wishlist, wanting as many as are missing
     * (tapping again doesn't add more). Returns how many cards were added or raised.
     */
    suspend fun wishlistMissing(check: DeckCheck): Int {
        val missing = check.missingLines.groupBy { it.name }.map { (name, ls) -> name to ls.sumOf { it.missing } }
        if (missing.isEmpty()) return 0
        val cards = PrintingLookup.resolve(missing.map { PrintingLookup.Wanted(it.first) }) { ids -> api.identify(ids.map(PrintingLookup::json)) }
        var n = 0
        missing.zip(cards).forEach { (m, c) -> if (c != null) { wantAtLeast(c, m.second); n++ } }
        return n
    }

    suspend fun wishlistItem(oracleId: String?): WishlistItem? = oracleId?.let { dao.wishlistItem(it) }

    suspend fun removeWish(id: Long) = dao.deleteWish(id)

    /** Sets how many copies of a wishlist card you want. */
    suspend fun setWishWanted(item: WishlistItem, wanted: Int) = dao.updateWish(item.copy(wanted = wanted.coerceAtLeast(1)))

    /** Puts a removed wishlist row back, for Undo. */
    suspend fun restoreWish(item: WishlistItem) { dao.putWishlist(listOf(item)) }

    /** Counts one copy of a wishlist card as found. */
    suspend fun fulfilWish(oracleId: String?, qty: Int = 1) {
        val w = wishlistItem(oracleId) ?: return
        if (w.wanted <= qty) dao.deleteWish(w.id) else dao.updateWish(w.copy(wanted = w.wanted - qty))
    }

    // Offline scans

    /** Saves a card read offline. [skip] holds the skip rules auto-add had on, applied once it's looked up. */
    suspend fun savePending(read: OfflineRead, foil: Boolean, qty: Int, boxId: Long, skip: com.mtgscanner.scan.SkipRules? = null) {
        dao.addPending(
            PendingScan(
                name = read.name, setCode = read.setCode, collectorNumber = read.collectorNumber, foil = foil, quantity = qty, boxId = boxId,
                lang = read.lang?.takeIf { it != "en" }, scryfallId = read.scryfallId, skip = skip?.encode().orEmpty(),
            ),
        )
    }

    private val pendingLock = Mutex()

    /** Looks up cards saved while offline and adds them. Returns how many were added. */
    suspend fun resolvePending(): Int = pendingLock.withLock { resolvePendingLocked() }

    private suspend fun resolvePendingLocked(): Int {
        val pending = dao.allPending().filterNot { it.unknown }
        if (pending.isEmpty()) return 0
        val cards = api.identify(pending.map { identifierFor(it.name, it.setCode, it.collectorNumber, it.scryfallId) })
        var n = 0
        val retry = mutableListOf<PendingScan>()
        pending.zip(cards).forEach { (p, c) -> if (c != null) { if (addPendingCard(p, c)) n++ } else retry += p }
        // A misread footer shouldn't lose the card: try by name alone.
        if (retry.isNotEmpty()) {
            val byName = PrintingLookup.resolve(retry.map { PrintingLookup.Wanted(it.name) }) { ids -> api.identify(ids.map(PrintingLookup::json)) }
            retry.zip(byName).forEach { (p, c) -> if (c != null && addPendingCard(p, c)) n++ }
        }
        return n
    }

    /**
     * Adds a saved scan the way the scanner would have: foil only when the printing has it, the language it was
     * read in, and the skip rules auto-add had on. False when it was skipped, no box fits it (it stays saved),
     * or another run already added it.
     */
    private suspend fun addPendingCard(p: PendingScan, c: ScryfallCard): Boolean {
        val foil = p.foil && c.hasFoil || !c.hasNonFoil
        val skipped = com.mtgscanner.scan.SkipRules.decode(p.skip)?.reason(c.effectiveTypeLine, c.layout, c.price(foil)?.toDoubleOrNull())
        if (skipped != null) {
            withContext(NonCancellable) { dao.deletePending(p.id) }
            return false
        }
        val box = if (p.boxId == Box.AUTO_SORT || dao.box(p.boxId) == null) pickBox(c, foil) else p.boxId
        if (box == null) return false
        // The printing's own language wins; an English printing keeps the language the card was read in.
        val lang = if (c.lang != "en") c.lang else p.lang ?: "en"
        // Claim the saved scan first (only one run can), then add it even if the screen closes meanwhile.
        return withContext(NonCancellable) {
            if (dao.deletePending(p.id) > 0) { add(c, foil, p.quantity, box, lang = lang); true } else false
        }
    }

    suspend fun deletePending(id: Long) = dao.deletePending(id)

    // Import and backup

    /**
     * Adds cards from another app's CSV, in one transaction. Each card goes to [boxId], or to the box whose rules fit
     * when [boxId] is [Box.AUTO_SORT]. Cards that fit no box go to the box [fallbackBox] makes. See [BackupIO.importRows].
     */
    suspend fun importRows(rows: List<ImportRow>, boxId: Long, fallbackBox: (suspend () -> Long)? = null): ImportResult =
        BackupIO.importRows(this, rows, boxId, fallbackBox)

    private fun identifierFor(name: String, set: String?, number: String?, id: String?): JsonObject = buildJsonObject {
        when {
            id != null -> put("id", id)
            set != null && number != null -> { put("set", set.lowercase()); put("collector_number", number) }
            else -> { put("name", name); if (set != null) put("set", set.lowercase()) }
        }
    }

    companion object {
        /** How long a read that found nothing isn't looked up again. */
        private const val MISS_TTL_MS = 30_000L
        /** The value snapshot waits for this much quiet after a change… */
        private const val CHANGE_IDLE_MS = 3_000L
        /** …or this many changes. */
        private const val CHANGE_BATCH = 25
    }
}

/** Tags come from Scryfall data: keywords plus the words in the type line. */
fun ScryfallCard.scryfallTags(): List<String> {
    val typeWords = effectiveTypeLine
        .split("//", "—")
        .flatMap { it.trim().split(' ') }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.first().isUpperCase() }
    val formats = legalities.filterValues { it == "legal" }.keys
        .filter { it in setOf("standard", "pioneer", "modern", "commander", "pauper") }
        .map { "legal:$it" }
    return (keywords + typeWords + formats).distinct()
}

fun ScryfallCard.toEntity(foil: Boolean, qty: Int, now: Long) = CollectionCard(
    scryfallId = id,
    oracleId = oracleId,
    name = name,
    setCode = set.uppercase(),
    setName = setName,
    collectorNumber = collectorNumber,
    rarity = rarity,
    typeLine = effectiveTypeLine,
    manaCost = effectiveManaCost,
    cmc = cmc,
    colors = effectiveColors.joinToString(""),
    colorIdentity = colorIdentity.joinToString(""),
    oracleText = effectiveOracleText,
    tags = scryfallTags().joinToString(","),
    priceUsd = prices.usd?.toDoubleOrNull(),
    priceUsdFoil = (prices.usdFoil ?: prices.usdEtched)?.toDoubleOrNull(),
    imageUrl = imageUrl,
    smallImageUrl = smallImageUrl,
    scryfallUri = scryfallUri,
    lang = lang,
    releasedAt = releasedAt,
    artist = artist,
    foil = foil,
    quantity = qty,
    boxId = 0,
    priceAtAdd = (if (foil) (prices.usdFoil ?: prices.usdEtched ?: prices.usd) else prices.usd)?.toDoubleOrNull(),
    treatments = treatments.joinToString(","),
    power = power ?: cardFaces?.firstOrNull()?.power,
    toughness = toughness ?: cardFaces?.firstOrNull()?.toughness,
    reserved = reserved,
    addedAt = now,
    updatedAt = now,
    priceEur = prices.eur?.toDoubleOrNull(),
    priceEurFoil = prices.eurFoil?.toDoubleOrNull(),
)

fun ScryfallCard.pricePoint() = PricePoint(
    scryfallId = id,
    day = LocalDate.now().toString(),
    usd = prices.usd?.toDoubleOrNull(),
    usdFoil = (prices.usdFoil ?: prices.usdEtched)?.toDoubleOrNull(),
)

/** The facts box rules look at, for a card about to be added. */
fun ScryfallCard.facts(foil: Boolean) = CardFacts(
    colors = effectiveColors.joinToString(""),
    rarity = rarity,
    typeLine = effectiveTypeLine,
    treatments = treatments.toSet(),
    foil = foil,
    price = (if (foil) (prices.usdFoil ?: prices.usdEtched ?: prices.usd) else prices.usd)?.toDoubleOrNull(),
    setCode = set,
    name = name,
    colorIdentity = colorIdentity.joinToString(""),
    cmc = cmc,
    power = power ?: cardFaces?.firstOrNull()?.power,
    toughness = toughness ?: cardFaces?.firstOrNull()?.toughness,
    reserved = reserved,
    formats = legalities.filterValues { it == "legal" || it == "restricted" }.keys,
    number = collectorNumber,
)

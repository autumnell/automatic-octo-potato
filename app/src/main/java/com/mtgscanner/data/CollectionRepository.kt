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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

/** Rows of some printings and their loans as they were, for [CollectionRepository.restore]. */
class RowSnapshot(val cards: List<CollectionCard>, val loans: List<com.mtgscanner.data.db.Loan>)

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
    /** Real set codes, to correct a misread footer. */
    val setCodes = com.mtgscanner.scan.SetCodes(context.filesDir, api)
    /** Cards the scanner identified before, kept on the phone. */
    internal val identifyStore = IdentifyStore(context.filesDir)
    /** The on-device art model, downloaded the first time art needs comparing. */
    val artEmbedder = com.mtgscanner.scan.ArtEmbedder(context.applicationContext)
    /** Printings already in a downloaded art index are compared from there, without fetching their pictures. */
    internal val art by lazy { ArtMatcher(embedder = artEmbedder, known = { id -> artIndex.artOf(id) }) }
    /** Downloaded per-set art hashes, to find cards from their artwork when no text can be read. */
    val artIndex = com.mtgscanner.scan.ArtIndexStore(context.filesDir, api, artEmbedder)
    /** Set symbol shapes, drawn from Scryfall's icons as sets are needed. */
    internal val setSymbols = com.mtgscanner.scan.SetSymbols(context.applicationContext, api)
    val scanLog = com.mtgscanner.scan.ScanLog(context.filesDir)

    /** One shared read of the cards table for every screen; kept 5 s after the last screen stops watching. */
    private val allCards: Flow<List<CollectionCard>> =
        dao.observeAll().shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun observeAll(): Flow<List<CollectionCard>> = allCards

    /** Copies owned by card name ([com.mtgscanner.util.CardNames.key]), worked out once per change for every screen. */
    private val ownedNames: Flow<Map<String, Int>> = allCards
        .map { all -> OwnedNames.of(all) }
        .flowOn(Dispatchers.Default)
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun observeOwnedByName(): Flow<Map<String, Int>> = ownedNames
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
        appScope.launch { runCatching { setCodes.load() } }
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
        internal val cards: RowSnapshot,
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

    // Identifying scans: identify, offlineRead and the printing pickers are in Identify.kt.

    /** Reads that found a card. Bounded, like the other caches, so a long scanning session doesn't keep growing it. */
    internal val lookupCache = LruCache<String, Identification>(500)
    /** Reads that found nothing, and when: the same garbage read isn't looked up again for [MISS_TTL_MS]. */
    internal val missCache = HashMap<String, Long>()
    internal val printingsCache = LruCache<String, List<ScryfallCard>>(300)
    /** Lookups under way by key, so a lookup started early (see prefetch) is joined, not repeated. */
    internal val inFlight = HashMap<String, kotlinx.coroutines.Deferred<Identification?>>()

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
        dao.update(card.copy(updatedAt = now))
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
            dao.update(c.copy(foil = foil, updatedAt = System.currentTimeMillis()))
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
    suspend fun snapshot(rowIds: List<Long>): RowSnapshot {
        val ids = rowsByIds(rowIds).map { it.scryfallId }.distinct()
        if (ids.isEmpty()) return RowSnapshot(emptyList(), emptyList())
        val idSet = ids.toHashSet()
        return RowSnapshot(rowsByScryfallIds(ids), dao.allLoans().filter { it.scryfallId in idSet })
    }

    /**
     * Puts rows back as [snapshot] saw them, undoing merges, moves and deletes. Rows of those printings added
     * since (new scans, newer than anything in [before]) are kept; one identical to a restored row joins it.
     * Copies that were added into an existing row since can't be told apart, so they're undone too.
     */
    suspend fun restore(snap: RowSnapshot) {
        val before = snap.cards
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
            // Loans go back too: a deleted row's loans were deleted, and a merged row's moved to the row it joined.
            if (snap.loans.isNotEmpty()) dao.putLoans(snap.loans)
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

    // The deck check (checkDeck) is in DeckCheck.kt.

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
    suspend fun resolvePending(): Int = resolvePendingWithUndo().added

    /** What a lookup of saved scans did: how many copies it added, the copies per row id, and the saved scans it used up. */
    class PendingResult(val added: Int, val rows: List<Pair<Long, Int>>, val used: List<PendingScan>)

    /** [resolvePending], keeping exactly what it added so Undo takes off only that. */
    suspend fun resolvePendingWithUndo(): PendingResult = pendingLock.withLock { resolvePendingLocked() }

    /** Undoes [r]: takes its copies off and puts its saved scans back. */
    suspend fun undoPending(r: PendingResult) {
        db.withTransaction {
            r.rows.forEach { (id, q) -> remove(id, q) }
            dao.putPending(r.used)
        }
    }

    private suspend fun resolvePendingLocked(): PendingResult {
        val pending = dao.allPending().filterNot { it.unknown }
        if (pending.isEmpty()) return PendingResult(0, emptyList(), emptyList())
        val cards = api.identify(pending.map { identifierFor(it.name, it.setCode, it.collectorNumber, it.scryfallId) })
        val rows = mutableListOf<Pair<Long, Int>>()
        val used = mutableListOf<PendingScan>()
        val retry = mutableListOf<PendingScan>()
        for ((p, c) in pending.zip(cards)) {
            if (c == null) { retry += p; continue }
            addPendingCard(p, c)?.let { rows += it to p.quantity; used += p }
        }
        // A misread footer shouldn't lose the card: try by name alone.
        if (retry.isNotEmpty()) {
            val byName = PrintingLookup.resolve(retry.map { PrintingLookup.Wanted(it.name) }) { ids -> api.identify(ids.map(PrintingLookup::json)) }
            for ((p, c) in retry.zip(byName)) {
                if (c != null) addPendingCard(p, c)?.let { rows += it to p.quantity; used += p }
            }
        }
        return PendingResult(rows.sumOf { it.second }, rows, used)
    }

    /**
     * Adds a saved scan the way the scanner would have: foil only when the printing has it, the language it was
     * read in, and the skip rules auto-add had on. Returns the row it went to; null when it was skipped, no box
     * fits it (it stays saved), or another run already added it.
     */
    private suspend fun addPendingCard(p: PendingScan, c: ScryfallCard): Long? {
        val foil = p.foil && c.hasFoil || !c.hasNonFoil
        val skipped = com.mtgscanner.scan.SkipRules.decode(p.skip)?.reason(c.effectiveTypeLine, c.layout, c.price(foil)?.toDoubleOrNull())
        if (skipped != null) {
            withContext(NonCancellable) { dao.deletePending(p.id) }
            return null
        }
        val box = if (p.boxId == Box.AUTO_SORT || dao.box(p.boxId) == null) pickBox(c, foil) else p.boxId
        if (box == null) return null
        // The printing's own language wins; an English printing keeps the language the card was read in.
        val lang = if (c.lang != "en") c.lang else p.lang ?: "en"
        // Claim the saved scan first (only one run can), then add it even if the screen closes meanwhile.
        return withContext(NonCancellable) {
            if (dao.deletePending(p.id) > 0) add(c, foil, p.quantity, box, lang = lang) else null
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
        internal const val MISS_TTL_MS = 30_000L
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

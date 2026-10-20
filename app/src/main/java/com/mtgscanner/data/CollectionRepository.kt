package com.mtgscanner.data

import android.content.Context
import androidx.room.withTransaction
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.scan.ArtMatcher
import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.CardTextParser
import com.mtgscanner.scan.NameIndex
import com.mtgscanner.util.DecklistParser
import com.mtgscanner.util.ImportRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.LocalDate

/** A Scryfall match for what the camera read, and whether it's safe to add without asking. */
data class Identification(val card: ScryfallCard, val confident: Boolean)

/** A card name read with no connection, good enough to look up later. */
data class OfflineRead(val name: String, val setCode: String?, val collectorNumber: String?)

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
) {
    val missing: Int get() = (needed - owned).coerceAtLeast(0)
}

data class DeckCheck(val lines: List<DeckCheckLine>) {
    val needed: Int get() = lines.sumOf { it.needed }
    val owned: Int get() = lines.sumOf { minOf(it.owned, it.needed) }
    val missingCost: Double get() = lines.sumOf { (it.price ?: 0.0) * it.missing }
}

data class ImportResult(val added: Int, val notFound: List<String>)

@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val boxes: List<Box> = emptyList(),
    val cards: List<CollectionCard> = emptyList(),
    val snapshots: List<ValueSnapshot> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val wishlist: List<WishlistItem> = emptyList(),
    val pending: List<PendingScan> = emptyList(),
)

/** Collection value and its change since cards were added. */
data class ValueChange(val value: Double, val paid: Double) {
    val change: Double get() = value - paid
    val percent: Double get() = if (paid > 0) change / paid * 100 else 0.0
}

/** A price change worth telling the user about. */
data class PriceMove(val name: String, val then: Double, val now: Double, val wanted: Boolean)

class CollectionRepository(
    context: Context,
    private val db: AppDatabase,
    val api: ScryfallApi,
) {
    private val dao = db.cardDao()
    private val appContext: Context = context.applicationContext
    private val prefs = context.getSharedPreferences("collection", Context.MODE_PRIVATE)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val nameIndex = NameIndex(context.filesDir, api)
    private val art = ArtMatcher()
    val scanLog = com.mtgscanner.scan.ScanLog(context.filesDir)

    fun observeAll(): Flow<List<CollectionCard>> = dao.observeAll()
    fun observe(id: Long): Flow<CollectionCard?> = dao.observe(id)
    fun observeBoxes(): Flow<List<Box>> = dao.observeBoxes()
    fun observeSnapshots(): Flow<List<ValueSnapshot>> = dao.observeSnapshots()
    fun observeDecks(): Flow<List<Deck>> = dao.observeDecks()
    fun observeDeck(id: Long): Flow<Deck?> = dao.observeDeck(id)
    fun observeWishlist(): Flow<List<WishlistItem>> = dao.observeWishlist()
    fun observePending(): Flow<List<PendingScan>> = dao.observePending()

    /** Starts background work: the name list, daily prices, card roles and offline scans. */
    fun startBackgroundWork() {
        appScope.launch { runCatching { nameIndex.load() } }
        appScope.launch {
            runCatching { refreshPricesIfStale() }
            runCatching { resolvePending() }
            runCatching { fetchRoles() }
        }
    }

    // Boxes

    /** Every box with how many cards it holds and what they're worth. */
    fun observeBoxSummaries(): Flow<List<BoxSummary>> = combine(dao.observeBoxes(), dao.observeAll()) { boxes, cards ->
        val byBox = cards.groupBy { it.boxId }
        boxes.map { b ->
            val inBox = byBox[b.id].orEmpty()
            BoxSummary(b.id, b.name, b.kind, inBox.sumOf { it.quantity }, inBox.sumOf { it.totalPrice() }, b.rules)
        }
    }

    suspend fun createBox(name: String, kind: String, rules: BoxRules = BoxRules()): Long =
        dao.insertBox(Box(name = name.trim(), kind = kind, rules = rules.toJson()))

    suspend fun updateBox(id: Long, name: String, kind: String, rules: BoxRules) {
        val b = dao.box(id) ?: return
        dao.updateBox(b.copy(name = name.trim(), kind = kind, rules = rules.toJson()))
    }

    suspend fun box(id: Long): Box? = dao.box(id)

    /**
     * Deletes a box. Its cards move to [moveTo], or are deleted when [moveTo] is null.
     * With [moveTo] = [Box.AUTO_SORT] each card goes to the box whose rules fit it.
     */
    suspend fun deleteBox(id: Long, moveTo: Long?) {
        val cards = dao.inBox(id)
        if (moveTo == null) dao.deleteCardsInBox(id)
        else {
            dao.box(id)?.let { dao.deleteBox(it) }
            for (c in cards) {
                val target = if (moveTo == Box.AUTO_SORT) pickBox(c.facts()) else moveTo
                if (target == null) dao.delete(c) else moveToBox(c.id, target)
            }
        }
        dao.box(id)?.let { dao.deleteBox(it) }
    }

    /** The box a card belongs in by the boxes' rules, or null when no box fits. */
    suspend fun pickBox(facts: CardFacts): Long? =
        BoxRules.pick(dao.allBoxes().map { it.id to it.boxRules() }, facts)

    suspend fun pickBox(card: ScryfallCard, foil: Boolean): Long? = pickBox(card.facts(foil))

    /** Moves an entry to another box, merging with the same printing already there. Returns the surviving id. */
    suspend fun moveToBox(id: Long, boxId: Long): Long {
        val c = dao.get(id) ?: return id
        if (c.boxId == boxId) return id
        val other = dao.find(c.scryfallId, c.foil, boxId)
        val now = System.currentTimeMillis()
        return if (other != null) {
            dao.update(other.copy(quantity = other.quantity + c.quantity, updatedAt = now))
            dao.delete(c)
            other.id
        } else {
            dao.update(c.copy(boxId = boxId, updatedAt = now))
            id
        }
    }

    /** The box chosen last time on the scan screen. */
    var lastBoxId: Long
        get() = prefs.getLong("lastBox", Box.AUTO_SORT)
        set(v) { prefs.edit().putLong("lastBox", v).apply() }

    // Identifying scans

    private val lookupCache = HashMap<String, Identification?>()

    /**
     * Finds the card for an OCR guess. Tries the exact printing (set + collector number) first,
     * then the card name (corrected against the offline name list), and uses the artwork to pick
     * the printing when the footer couldn't be read. Throws [IOException] when offline.
     */
    suspend fun identify(guess: CardTextGuess): Identification? {
        val key = guess.key ?: return null
        val cached = synchronized(lookupCache) { if (lookupCache.containsKey(key)) lookupCache[key] else null }
        val base = cached ?: doIdentify(guess).also { r -> if (r != null) synchronized(lookupCache) { lookupCache[key] = r } }
        base ?: return null
        val hash = guess.artHash
        if (hash != null && guess.collectorNumber == null) {
            refineByArt(base.card, hash)?.let { return base.copy(card = it) }
        }
        return base
    }

    /** Picks the printing whose artwork matches what the camera sees. */
    suspend fun refineByArt(card: ScryfallCard, artHash: Long): ScryfallCard? {
        val printings = runCatching { printingsCached(card) }.getOrNull() ?: return null
        if (printings.size < 2) return null
        return art.bestPrinting(printings, artHash)
    }

    private val printingsCache = HashMap<String, List<ScryfallCard>>()
    private suspend fun printingsCached(card: ScryfallCard): List<ScryfallCard> {
        val k = card.oracleId ?: card.name
        synchronized(printingsCache) { printingsCache[k]?.let { return it } }
        val list = api.printings(card)
        synchronized(printingsCache) { printingsCache[k] = list }
        return list
    }

    /** What to save for later when the camera read a name but Scryfall can't be reached. */
    fun offlineRead(guess: CardTextGuess): OfflineRead? {
        val best = nameIndex.correct(guess.nameCandidates).firstOrNull() ?: return null
        if (best.score < 0.85) return null
        return OfflineRead(best.name, guess.setCode, guess.collectorNumber)
    }

    private suspend fun doIdentify(guess: CardTextGuess): Identification? {
        // Real card names close to what was read come first, then the raw text.
        val corrected = nameIndex.correct(guess.nameCandidates)
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
                if (names.isEmpty() || sim >= 0.6) return Identification(card, confident = true)
            }
        }

        val knownGood = corrected.firstOrNull()?.takeIf { it.score >= 0.85 }?.name
        for (name in names) {
            val inSet = guess.setCode?.let { set -> runCatching { api.named(name, set) }.getOrNull() }
            val card = inSet ?: api.named(name) ?: continue
            val sim = bestSimilarity(card)
            if (sim < 0.5) continue
            // Fuzzy search returns the newest printing; if we know the collector number, pick that one.
            val exact = if (inSet == null && guess.collectorNumber != null) {
                runCatching { printingsCached(card) }.getOrNull()
                    ?.firstOrNull { it.collectorNumber.equals(guess.collectorNumber, ignoreCase = true) }
            } else null
            val confident = sim >= 0.85 || card.name == knownGood
            return Identification(exact ?: card, confident = confident)
        }
        return null
    }

    // Adding and editing

    /** Adds [qty] copies to a box, merging with an existing entry for the same printing and finish. Returns the row id. */
    suspend fun add(card: ScryfallCard, foil: Boolean, qty: Int = 1, boxId: Long): Long {
        val now = System.currentTimeMillis()
        val existing = dao.find(card.id, foil, boxId)
        val id = if (existing != null) {
            dao.update(existing.copy(quantity = existing.quantity + qty, updatedAt = now))
            existing.id
        } else {
            dao.insert(card.toEntity(foil, qty, now).copy(boxId = boxId))
        }
        appScope.launch {
            runCatching { recordSnapshot() }
            runCatching { fetchRoles() }
        }
        return id
    }

    /** Removes [qty] copies (used for undo). Deletes the row when it reaches zero. */
    suspend fun remove(id: Long, qty: Int = 1) {
        val c = dao.get(id) ?: return
        if (c.quantity <= qty) dao.delete(c)
        else dao.update(c.copy(quantity = c.quantity - qty, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setQuantity(id: Long, qty: Int) {
        val c = dao.get(id) ?: return
        if (qty <= 0) dao.delete(c) else dao.update(c.copy(quantity = qty, updatedAt = System.currentTimeMillis()))
    }

    suspend fun update(card: CollectionCard) = dao.update(card.copy(updatedAt = System.currentTimeMillis()))

    /** Switches foil on/off, merging into the other entry if one already exists. Returns the surviving id. */
    suspend fun setFoil(id: Long, foil: Boolean): Long {
        val c = dao.get(id) ?: return id
        if (c.foil == foil) return id
        val other = dao.find(c.scryfallId, foil, c.boxId)
        return if (other != null) {
            dao.update(other.copy(quantity = other.quantity + c.quantity, updatedAt = System.currentTimeMillis()))
            dao.delete(c)
            other.id
        } else {
            dao.update(c.copy(foil = foil, updatedAt = System.currentTimeMillis()))
            id
        }
    }

    suspend fun delete(id: Long) { dao.get(id)?.let { dao.delete(it) } }

    suspend fun allCards(): List<CollectionCard> = dao.all()

    // Prices

    /** Pulls today's prices from Scryfall for every card in the collection. Returns how many were updated. */
    suspend fun refreshPrices(): Int {
        val all = dao.all()
        if (all.isEmpty()) return 0
        val fresh = api.collection(all.map { it.scryfallId }).associateBy { it.id }
        val updated = all.mapNotNull { c ->
            val f = fresh[c.scryfallId] ?: return@mapNotNull null
            c.copy(
                priceUsd = f.prices.usd?.toDoubleOrNull(),
                priceUsdFoil = (f.prices.usdFoil ?: f.prices.usdEtched)?.toDoubleOrNull(),
                treatments = f.treatments.joinToString(","),
                priceAtAdd = c.priceAtAdd ?: (if (c.foil) f.prices.usdFoil ?: f.prices.usd else f.prices.usd)?.toDoubleOrNull(),
            )
        }
        dao.updateAll(updated)
        // Wishlist prices too, so its total and alerts stay current.
        val wishes = dao.allWishlist()
        if (wishes.isNotEmpty()) {
            val freshWish = runCatching { api.collection(wishes.map { it.scryfallId }) }.getOrDefault(emptyList()).associateBy { it.id }
            wishes.forEach { w -> freshWish[w.scryfallId]?.let { f -> dao.updateWish(w.copy(priceUsd = f.prices.usd?.toDoubleOrNull())) } }
        }
        prefs.edit().putLong("pricesAt", System.currentTimeMillis()).apply()
        recordSnapshot()
        return updated.size
    }

    /**
     * Refreshes prices and returns the cards (owned or wanted) whose price moved by at least
     * [percent] and [dollars] since the last alert about them. New cards just start being watched.
     */
    private val alertSer = MapSerializer(String.serializer(), Double.serializer())

    suspend fun priceMoves(percent: Double, dollars: Double): List<PriceMove> {
        refreshPrices()
        val baseFile = java.io.File(appContext.filesDir, "alert-baseline.json")
        val base: MutableMap<String, Double> = runCatching {
            Json.decodeFromString(alertSer, baseFile.readText())
        }.getOrDefault(emptyMap()).toMutableMap()
        val current = buildMap {
            dao.all().forEach { c -> c.unitPrice()?.let { put("own:${c.scryfallId}:${c.foil}", Triple(c.name, it, false)) } }
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

    /** Saves today's collection value for the value chart. */
    suspend fun recordSnapshot() = snapshotLock.withLock {
        val all = dao.all()
        if (all.isEmpty()) return@withLock
        dao.putSnapshot(ValueSnapshot(LocalDate.now().toString(), all.sumOf { it.totalPrice() }, all.sumOf { it.quantity }))
    }

    // Roles (Scryfall community tags)

    private val rolesLock = Mutex()

    /** Looks up what each card does (removal, ramp, card draw…) for cards that haven't been checked yet. */
    suspend fun fetchRoles() = rolesLock.withLock {
        val pending = dao.needingRoles().mapNotNull { it.oracleId }.distinct()
        for (chunk in pending.chunked(25)) {
            val ids = chunk.joinToString(" or ") { "oracleid:$it" }
            val found = HashMap<String, MutableSet<String>>()
            for (role in ROLES) {
                val tags = role.tags.joinToString(" or ") { "otag:$it" }
                val cards = runCatching { api.searchAll("($tags) ($ids)", maxPages = 1) }
                    .onFailure { if (it is IOException && it !is ScryfallException) return@withLock }
                    .getOrNull().orEmpty()
                cards.forEach { c -> c.oracleId?.let { found.getOrPut(it) { linkedSetOf() } += role.label } }
            }
            val now = System.currentTimeMillis()
            chunk.forEach { dao.setRoles(it, found[it].orEmpty().joinToString(","), now) }
        }
    }

    /** Clears and re-fetches every card's roles. */
    suspend fun refreshAllRoles() {
        dao.resetRoles()
        fetchRoles()
    }

    // Decks

    suspend fun saveDeck(id: Long, name: String, text: String): Long {
        val now = System.currentTimeMillis()
        val existing = if (id != 0L) dao.allDecks().firstOrNull { it.id == id } else null
        val deck = existing?.copy(name = name.trim(), text = text, updatedAt = now) ?: Deck(name = name.trim(), text = text)
        val r = dao.putDeck(deck)
        return if (existing != null) existing.id else r
    }

    suspend fun deleteDeck(id: Long) = dao.deleteDeck(id)

    /** Compares a decklist with the collection: what's owned, where it is, and what's missing. */
    suspend fun checkDeck(text: String, cards: List<CollectionCard>, boxes: List<Box>): DeckCheck {
        val lines = DecklistParser.parse(text)
        val boxNames = boxes.associate { it.id to it.name }
        fun key(n: String) = CardTextParser.normalize(n.substringBefore(" // "))
        val byName = cards.groupBy { key(it.name) }
        val missingNames = lines.filter { l -> (byName[key(l.name)]?.sumOf { it.quantity } ?: 0) < l.quantity }.map { it.name }.distinct()
        // Prices and pictures for missing cards come from Scryfall; ignore failures (offline).
        val looked = runCatching {
            api.identify(missingNames.map { n -> buildJsonObject { put("name", n) } })
        }.getOrNull()
        val lookedUp = missingNames.zip(looked ?: List(missingNames.size) { null }).toMap()
        return DeckCheck(lines.map { l ->
            val owned = byName[key(l.name)].orEmpty()
            val sc = lookedUp[l.name]
            DeckCheckLine(
                name = owned.firstOrNull()?.name ?: sc?.name ?: l.name,
                section = l.section,
                needed = l.quantity,
                owned = owned.sumOf { it.quantity },
                boxes = owned.groupBy { it.boxId }.map { (b, cs) -> (boxNames[b] ?: "Unsorted") to cs.sumOf { it.quantity } },
                price = owned.mapNotNull { it.priceUsd }.minOrNull() ?: sc?.prices?.usd?.toDoubleOrNull(),
                imageUrl = owned.firstOrNull()?.imageUrl ?: sc?.imageUrl,
                oracleId = owned.firstOrNull()?.oracleId ?: sc?.oracleId,
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

    /** Adds the missing cards of a deck to the wishlist. Returns how many were added. */
    suspend fun wishlistMissing(check: DeckCheck): Int {
        val missing = check.lines.filter { it.missing > 0 }
        val cards = api.identify(missing.map { m -> buildJsonObject { put("name", m.name) } })
        var n = 0
        missing.zip(cards).forEach { (m, c) -> if (c != null) { addToWishlist(c, m.missing); n++ } }
        return n
    }

    suspend fun wishlistItem(oracleId: String?): WishlistItem? = oracleId?.let { dao.wishlistItem(it) }

    suspend fun removeWish(id: Long) = dao.deleteWish(id)

    /** Counts one copy of a wishlist card as found. */
    suspend fun fulfilWish(oracleId: String?, qty: Int = 1) {
        val w = wishlistItem(oracleId) ?: return
        if (w.wanted <= qty) dao.deleteWish(w.id) else dao.updateWish(w.copy(wanted = w.wanted - qty))
    }

    // Offline scans

    suspend fun savePending(read: OfflineRead, foil: Boolean, qty: Int, boxId: Long) {
        dao.addPending(PendingScan(name = read.name, setCode = read.setCode, collectorNumber = read.collectorNumber, foil = foil, quantity = qty, boxId = boxId))
    }

    /** Looks up cards saved while offline and adds them. Returns how many were added. */
    suspend fun resolvePending(): Int {
        val pending = dao.allPending()
        if (pending.isEmpty()) return 0
        val cards = api.identify(pending.map { identifierFor(it.name, it.setCode, it.collectorNumber, null) })
        var n = 0
        val retry = mutableListOf<PendingScan>()
        pending.zip(cards).forEach { (p, c) -> if (c != null) { addPendingCard(p, c); n++ } else retry += p }
        // A misread footer shouldn't lose the card: try by name alone.
        if (retry.isNotEmpty()) {
            val byName = api.identify(retry.map { buildJsonObject { put("name", it.name) } })
            retry.zip(byName).forEach { (p, c) -> if (c != null) { addPendingCard(p, c); n++ } }
        }
        return n
    }

    private suspend fun addPendingCard(p: PendingScan, c: ScryfallCard) {
        val box = if (p.boxId == Box.AUTO_SORT || dao.box(p.boxId) == null) pickBox(c, p.foil) else p.boxId
        if (box == null) return
        add(c, p.foil, p.quantity, box)
        dao.deletePending(p.id)
    }

    suspend fun deletePending(id: Long) = dao.deletePending(id)

    // Import and backup

    /**
     * Adds cards from another app's CSV. Each card goes to [boxId], or to the box whose rules fit
     * when [boxId] is [Box.AUTO_SORT]. Cards that fit no box go to the box [fallbackBox] makes.
     */
    suspend fun importRows(rows: List<ImportRow>, boxId: Long, fallbackBox: (suspend () -> Long)? = null): ImportResult {
        var fallbackBoxId: Long? = null
        suspend fun fallback(): Long? = fallbackBoxId ?: fallbackBox?.invoke()?.also { fallbackBoxId = it }
        val found = api.identify(rows.map { identifierFor(it.name, it.setCode, it.collectorNumber, it.scryfallId) }).toMutableList()
        val retry = rows.indices.filter { found[it] == null && (rows[it].setCode != null || rows[it].scryfallId != null) }
        if (retry.isNotEmpty()) {
            val byName = api.identify(retry.map { buildJsonObject { put("name", rows[it].name) } })
            retry.zip(byName).forEach { (i, c) -> found[i] = c }
        }
        var added = 0
        val notFound = mutableListOf<String>()
        rows.zip(found).forEach { (r, c) ->
            if (c == null) { notFound += r.name; return@forEach }
            val target = (if (boxId == Box.AUTO_SORT) pickBox(c, r.foil) else boxId) ?: fallback()
            if (target == null) { notFound += r.name; return@forEach }
            val id = add(c, r.foil, r.quantity, target)
            if (r.condition != null && r.condition != "NM") dao.get(id)?.let { dao.update(it.copy(condition = r.condition)) }
            added += r.quantity
        }
        return ImportResult(added, notFound)
    }

    private fun identifierFor(name: String, set: String?, number: String?, id: String?): JsonObject = buildJsonObject {
        when {
            id != null -> put("id", id)
            set != null && number != null -> { put("set", set.lowercase()); put("collector_number", number) }
            else -> { put("name", name); if (set != null) put("set", set.lowercase()) }
        }
    }

    private val backupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportBackup(): String = backupJson.encodeToString(
        Backup.serializer(),
        Backup(
            boxes = dao.allBoxes(), cards = dao.all(), snapshots = dao.allSnapshots(),
            decks = dao.allDecks(), wishlist = dao.allWishlist(), pending = dao.allPending(),
        ),
    )

    /** Replaces everything with a backup. Returns the number of cards restored. */
    suspend fun restoreBackup(text: String): Int {
        val b = backupJson.decodeFromString(Backup.serializer(), text)
        db.withTransaction {
            dao.deleteAllCards(); dao.deleteAllBoxes(); dao.deleteSnapshots()
            dao.deleteDecks(); dao.deleteWishlist(); dao.deleteAllPending()
            dao.putBoxes(b.boxes); dao.putCards(b.cards); dao.putSnapshots(b.snapshots)
            dao.putDecks(b.decks); dao.putWishlist(b.wishlist); dao.putPending(b.pending)
        }
        return b.cards.sumOf { it.quantity }
    }

    companion object {
        /** A role shown in the app, and the Scryfall tagger tags that mean it. */
        data class Role(val label: String, val tags: List<String>)

        val ROLES = listOf(
            Role("Removal", listOf("removal")),
            Role("Ramp", listOf("ramp")),
            Role("Card draw", listOf("draw", "card-advantage")),
            Role("Tutor", listOf("tutor")),
            Role("Counterspell", listOf("counterspell")),
            Role("Board wipe", listOf("sweeper", "board-wipe")),
            Role("Mana rock", listOf("mana-rock")),
            Role("Mana dork", listOf("mana-dork")),
            Role("Lifegain", listOf("lifegain")),
            Role("Burn", listOf("burn")),
            Role("Discard", listOf("discard")),
            Role("Mill", listOf("mill")),
            Role("Reanimation", listOf("reanimate")),
            Role("Recursion", listOf("recursion")),
            Role("Sac outlet", listOf("sacrifice-outlet")),
            Role("Graveyard hate", listOf("graveyard-hate")),
            Role("Protection", listOf("protects-creature", "gives-protection")),
            Role("Blink", listOf("blink", "flicker")),
            Role("Tokens", listOf("token-generator")),
        )
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
    addedAt = now,
    updatedAt = now,
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
)

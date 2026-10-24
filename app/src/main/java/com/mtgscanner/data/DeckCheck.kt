package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.placeName
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /** [price] in euros, picked the same way. */
    val priceEur: Double? = null,
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
 * Compares a decklist with the collection: what's owned, where it is, and what's missing. [places] are boxes
 * and decks' card rows; copies kept in [deckSlotId] (this deck's own cards) count as in the deck.
 */
suspend fun CollectionRepository.checkDeck(text: String, cards: List<CollectionCard>, places: List<Box>, deckSlotId: Long? = null): DeckCheck = withContext(Dispatchers.Default) {
    // Parsing and matching a long list against a big collection is too slow for the main thread.
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
    DeckCheck(lines.mapIndexed { i, l ->
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
            priceEur = exact.mapNotNull { it.priceEur }.minOrNull()
                ?: (if (named) sc?.prices?.eur?.toDoubleOrNull() else null)
                ?: owned.mapNotNull { it.priceEur }.minOrNull() ?: sc?.prices?.eur?.toDoubleOrNull(),
            imageUrl = exact.firstOrNull()?.imageUrl ?: (if (named) sc?.imageUrl else null) ?: owned.firstOrNull()?.imageUrl ?: sc?.imageUrl,
            oracleId = owned.firstOrNull()?.oracleId ?: sc?.oracleId,
            setCode = l.setCode,
            collectorNumber = l.collectorNumber,
            otherPrinting = named && exact.isEmpty() && sc != null && !PrintingLookup.samePrinting(l.setCode, l.collectorNumber, sc.set, sc.collectorNumber),
        )
    })
}

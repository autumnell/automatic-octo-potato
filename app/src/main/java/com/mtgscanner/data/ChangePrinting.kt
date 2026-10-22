package com.mtgscanner.data

import androidx.room.withTransaction
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.mergedWith

/** Result of [changePrinting]: the row now holding the copies, and what undo puts back. */
data class PrintingChange(val newId: Long, val before: List<CollectionCard>)

/**
 * Switches a collection row to another printing of the same card ([to]), keeping quantity,
 * box, condition, notes and your own details. Merges into an existing row of identical copies
 * of that printing in the same box. Null when the row is gone or already this printing.
 */
suspend fun CollectionRepository.changePrinting(id: Long, to: ScryfallCard): PrintingChange? {
    val c = dao.get(id) ?: return null
    if (c.scryfallId == to.id) return null
    // Every row of both printings, so undo restores merges exactly.
    val sameNew = dao.byScryfallIds(listOf(to.id)).map { it.id }
    val before = snapshot(listOf(id) + sameNew)
    // Keep the finish when the new printing has it; otherwise use the one it has.
    val foil = when {
        c.foil && !to.hasFoil -> false
        !c.foil && !to.hasNonFoil -> true
        else -> c.foil
    }
    val now = System.currentTimeMillis()
    val e = to.toEntity(foil, c.quantity, now)
    val newId = db.withTransaction {
        val other = twinOf(c, scryfallId = to.id, foil = foil)
        if (other != null && other.id != c.id) {
            dao.update(other.mergedWith(c, now = now))
            dao.delete(c)
            other.id
        } else {
            dao.update(
                c.copy(
                    scryfallId = e.scryfallId,
                    oracleId = e.oracleId ?: c.oracleId,
                    name = e.name,
                    setCode = e.setCode,
                    setName = e.setName,
                    collectorNumber = e.collectorNumber,
                    rarity = e.rarity,
                    typeLine = e.typeLine,
                    manaCost = e.manaCost,
                    cmc = e.cmc,
                    colors = e.colors,
                    colorIdentity = e.colorIdentity,
                    oracleText = e.oracleText,
                    tags = e.tags,
                    priceUsd = e.priceUsd,
                    priceUsdFoil = e.priceUsdFoil,
                    priceEur = e.priceEur,
                    priceEurFoil = e.priceEurFoil,
                    imageUrl = e.imageUrl,
                    smallImageUrl = e.smallImageUrl,
                    scryfallUri = e.scryfallUri,
                    releasedAt = e.releasedAt,
                    artist = e.artist,
                    foil = foil,
                    priceAtAdd = e.priceAtAdd,
                    treatments = e.treatments,
                    power = e.power,
                    toughness = e.toughness,
                    reserved = e.reserved,
                    conditionPrice = null,
                    conditionPriceAt = 0,
                    updatedAt = now,
                ),
            )
            c.id
        }
    }
    logEvent(to.id, to.name, "edited", "From ${c.setCode} #${c.collectorNumber}")
    runCatching { dao.putPricePoints(listOf(to.pricePoint())) }
    return PrintingChange(newId, before)
}

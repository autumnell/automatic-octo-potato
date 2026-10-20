package com.mtgscanner.data

import android.content.Context
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The cover card picked for each box (box id -> card row id), kept in SharedPreferences. */
class BoxCovers(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("box_covers", Context.MODE_PRIVATE)
    private val _covers = MutableStateFlow(load())
    val covers: StateFlow<Map<Long, Long>> = _covers

    private fun load(): Map<Long, Long> = prefs.all.mapNotNull { (k, v) ->
        val box = k.toLongOrNull() ?: return@mapNotNull null
        val card = (v as? Long) ?: return@mapNotNull null
        box to card
    }.toMap()

    /** [cardId] null goes back to the most valuable card. */
    fun set(boxId: Long, cardId: Long?) {
        val e = prefs.edit()
        if (cardId == null) e.remove(boxId.toString()) else e.putLong(boxId.toString(), cardId)
        e.apply()
        _covers.value = if (cardId == null) _covers.value - boxId else _covers.value + (boxId to cardId)
    }
}

/** Value of a box now and when its cards were added, plus the card shown behind it. */
data class BoxStats(
    val value: Double = 0.0,
    /** Total price when added, for cards that have one. */
    val valueThen: Double = 0.0,
    /** Today's total for the same cards as [valueThen]. */
    val valueNowTracked: Double = 0.0,
    val coverCardId: Long? = null,
    val artUrl: String? = null,
) {
    val change: Double get() = valueNowTracked - valueThen
    /** Percent change, or null when nothing has a price from when it was added. */
    val changePercent: Double? get() = if (valueThen > 0) change / valueThen * 100 else null
}

object BoxArt {
    /** The picked cover when it's still in the box, else the most valuable card with an image. */
    fun coverCard(inBox: List<CollectionCard>, coverId: Long?): CollectionCard? =
        inBox.firstOrNull { it.id == coverId && it.imageUrl != null }
            ?: inBox.filter { it.imageUrl != null }.maxByOrNull { it.unitPrice() ?: 0.0 }

    fun statsFor(inBox: List<CollectionCard>, coverId: Long?): BoxStats {
        val tracked = inBox.filter { !it.proxy && it.priceWhenAdded() != null }
        val cover = coverCard(inBox, coverId)
        return BoxStats(
            value = inBox.sumOf { it.totalPrice() },
            valueThen = tracked.sumOf { (it.priceWhenAdded() ?: 0.0) * it.quantity },
            valueNowTracked = tracked.sumOf { it.totalPrice() },
            coverCardId = cover?.id,
            artUrl = artCropUrl(cover?.imageUrl),
        )
    }

    /** Stats for every box that has cards, keyed by box id. */
    fun stats(cards: List<CollectionCard>, covers: Map<Long, Long>): Map<Long, BoxStats> =
        cards.groupBy { it.boxId }.mapValues { (id, inBox) -> statsFor(inBox, covers[id]) }
}

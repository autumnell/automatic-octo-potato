package com.mtgscanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A place cards live: a storage box, a binder, a deck or a trade binder. Rules say what belongs in it. */
@Entity(tableName = "boxes")
@Serializable
data class Box(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "box", "binder", "deck" or "trade". Changes the icon; trade binders are also listed for trading. */
    val kind: String = "box",
    val createdAt: Long = System.currentTimeMillis(),
    /** [com.mtgscanner.data.BoxRules] as JSON; empty means any card fits. */
    val rules: String = "",
    /** Label color as ARGB; 0 uses the accent color. */
    val color: Int = 0,
    /** Position in the Auto-sort order; lower is checked first. */
    val sortOrder: Int = 0,
    /** Cards that fit no box go here. At most one box has this set. */
    val fallback: Boolean = false,
    /** Most cards the box holds; 0 means no limit. */
    val capacity: Int = 0,
    /** How the box is split inside: a [com.mtgscanner.data.Divider] key; empty means no dividers. */
    val divider: String = "",
    /** A binder where each copy gets its own pocket, so 3 copies fill 3 pockets. */
    val pocketPerCopy: Boolean = false,
    /**
     * Not 0: this row isn't a box but holds the cards kept in that deck (DB v14). Box lists, pickers and sorting
     * never see it: [CardDao.observeBoxes] and [CardDao.allBoxes] leave it out.
     */
    val deckId: Long = 0,
) {
    companion object {
        /** The "Bulk" box made by older versions. New installs start with no boxes. */
        const val BULK_ID = 1L
        /** Scan setting meaning "pick the box from each box's rules". */
        const val AUTO_SORT = -2L
        val KINDS = listOf("box", "binder", "deck", "trade")
        /** Label colors to pick from. 0 is the accent color. */
        val COLORS = listOf(
            0, 0xFFE5534B.toInt(), 0xFFF0883E.toInt(), 0xFFE3B341.toInt(), 0xFF3FB950.toInt(),
            0xFF39C5CF.toInt(), 0xFF4C8DF6.toInt(), 0xFFA371F7.toInt(), 0xFFEC6CB9.toInt(), 0xFF8B949E.toInt(),
        )
    }
}

fun Box.boxRules(): com.mtgscanner.data.BoxRules = com.mtgscanner.data.BoxRules.fromJson(rules)

/** Holds the cards kept in a deck rather than being a box. */
val Box.isDeckSlot: Boolean get() = deckId != 0L

/** "Deck: Atraxa" for a deck's cards, else the box name. */
fun Box.placeName(): String = if (isDeckSlot) deckPlaceName(name) else name

/** How a deck reads as the place a card is in. */
fun deckPlaceName(deckName: String): String = "Deck: $deckName"

/** A box with its totals, for the collection overview. */
data class BoxSummary(
    val id: Long,
    val name: String,
    val kind: String,
    val cards: Int,
    val value: Double,
    val rules: String = "",
    val color: Int = 0,
    val fallback: Boolean = false,
    val capacity: Int = 0,
    val divider: String = "",
    /** [value] in euros (Cardmarket prices). */
    val valueEur: Double = 0.0,
)

/** Cards in a box and what they're worth (dollars and euros), added up by the database. */
data class BoxTotal(val boxId: Long, val n: Int, val value: Double, val valueEur: Double)

/** Copies in a box, for capacity checks. */
data class BoxCount(val boxId: Long, val n: Int)

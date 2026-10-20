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
)

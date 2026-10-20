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
) {
    companion object {
        /** The "Bulk" box made by older versions. New installs start with no boxes. */
        const val BULK_ID = 1L
        /** Scan setting meaning "pick the box from each box's rules". */
        const val AUTO_SORT = -2L
        val KINDS = listOf("box", "binder", "deck", "trade")
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
)

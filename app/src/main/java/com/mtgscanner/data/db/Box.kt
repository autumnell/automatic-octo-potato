package com.mtgscanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A place cards live: a storage box, a binder or a deck. Box 1 ("Bulk") always exists. */
@Entity(tableName = "boxes")
data class Box(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "box", "binder" or "deck". Only changes the icon. */
    val kind: String = "box",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val BULK_ID = 1L
        val KINDS = listOf("box", "binder", "deck")
    }
}

/** A box with its totals, for the collection overview. */
data class BoxSummary(
    val id: Long,
    val name: String,
    val kind: String,
    val cards: Int,
    val value: Double,
)

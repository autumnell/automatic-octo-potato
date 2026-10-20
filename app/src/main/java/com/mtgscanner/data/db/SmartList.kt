package com.mtgscanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A saved Browse search, like "blue rares over $5". It holds no cards itself; it updates as the collection changes. */
@Entity(tableName = "smart_lists")
@Serializable
data class SmartList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The saved filters as JSON (see BrowseViewModel). */
    val filter: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class BoxCount(val boxId: Long, val n: Int)

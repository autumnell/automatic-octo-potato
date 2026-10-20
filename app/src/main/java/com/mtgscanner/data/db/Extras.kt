package com.mtgscanner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Collection value on one day, for the value chart. */
@Entity(tableName = "value_snapshots")
@Serializable
data class ValueSnapshot(
    /** yyyy-MM-dd */
    @PrimaryKey val day: String,
    val value: Double,
    val cards: Int,
)

/** A saved decklist, checked against the collection. */
@Entity(tableName = "decks")
@Serializable
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The list as pasted: one card per line, like "4 Lightning Bolt". */
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A card you want. Matched by oracle id, so any printing counts. */
@Entity(tableName = "wishlist", indices = [Index(value = ["oracleId"], unique = true)])
@Serializable
data class WishlistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val oracleId: String,
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val priceUsd: Double?,
    val wanted: Int = 1,
    val note: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

/** A card read while offline. It's added to the collection once Scryfall can be reached. */
@Entity(tableName = "pending_scans")
@Serializable
data class PendingScan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val setCode: String?,
    val collectorNumber: String?,
    val foil: Boolean,
    val quantity: Int,
    val boxId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

/** One day's price for a printing, recorded when prices refresh. Builds each card's price chart. */
@Entity(tableName = "price_history", primaryKeys = ["scryfallId", "day"])
@Serializable
data class PricePoint(
    val scryfallId: String,
    /** yyyy-MM-dd */
    val day: String,
    val usd: Double?,
    val usdFoil: Double?,
)

/** A pack opened in pack mode: what it cost and what came out. */
@Entity(tableName = "packs")
@Serializable
data class PackOpening(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cost: Double?,
    val value: Double,
    val cards: Int,
    /** The best pull, like "Ragavan (MH2) $52.10". */
    val best: String = "",
    /** One line per card: "name|set|number|price". */
    val pulls: String = "",
    val openedAt: Long = System.currentTimeMillis(),
)

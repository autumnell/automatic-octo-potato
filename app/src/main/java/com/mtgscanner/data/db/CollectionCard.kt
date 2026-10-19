package com.mtgscanner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One line of the collection: a specific printing, foil or not, with a quantity. */
@Entity(
    tableName = "cards",
    indices = [Index(value = ["scryfallId", "foil", "boxId"], unique = true), Index("name"), Index("setCode"), Index("boxId")],
)
data class CollectionCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scryfallId: String,
    val oracleId: String?,
    val name: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val rarity: String,
    val typeLine: String,
    val manaCost: String,
    val cmc: Double,
    /** Color letters, e.g. "WU". Empty means colorless. */
    val colors: String,
    val colorIdentity: String,
    val oracleText: String,
    /** Comma separated tags taken from Scryfall: keywords, subtypes, supertypes. */
    val tags: String,
    val priceUsd: Double?,
    val priceUsdFoil: Double?,
    val imageUrl: String?,
    val smallImageUrl: String?,
    val scryfallUri: String,
    val lang: String,
    val releasedAt: String?,
    val artist: String?,
    val foil: Boolean,
    val quantity: Int,
    val condition: String = "NM",
    val notes: String = "",
    /** Which [Box] the copies are in. */
    val boxId: Long = Box.BULK_ID,
    val addedAt: Long,
    val updatedAt: Long,
)

fun CollectionCard.unitPrice(): Double? = if (foil) priceUsdFoil ?: priceUsd else priceUsd
fun CollectionCard.totalPrice(): Double = (unitPrice() ?: 0.0) * quantity
/** Scryfall serves the same image path in several sizes; this swaps in the art-only crop. */
fun artCropUrl(imageUrl: String?): String? = imageUrl
    ?.replace("/normal/", "/art_crop/")?.replace("/large/", "/art_crop/")?.replace("/small/", "/art_crop/")

fun CollectionCard.tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

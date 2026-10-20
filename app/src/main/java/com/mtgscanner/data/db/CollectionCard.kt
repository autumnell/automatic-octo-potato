package com.mtgscanner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One line of the collection: a specific printing, foil or not, with a quantity. */
@Entity(
    tableName = "cards",
    indices = [Index(value = ["scryfallId", "foil", "boxId"], unique = true), Index("name"), Index("setCode"), Index("boxId")],
)
@Serializable
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
    val boxId: Long,
    /** Price per copy when it was added, to show gains and losses. */
    val priceAtAdd: Double? = null,
    /** Comma separated Scryfall community tags describing what the card does (removal, ramp…). */
    val roles: String = "",
    /** When [roles] were last fetched (0 = never). */
    val rolesCheckedAt: Long = 0,
    /** Special printing treatments (borderless, showcase…), comma separated. */
    val treatments: String = "",
    /** Your own labels, comma separated, like "for Kenji's deck" or "lent out". */
    val userTags: String = "",
    /** Price per copy in this condition and finish from JustTCG, when a key is set (null = use near mint). */
    val conditionPrice: Double? = null,
    /** When [conditionPrice] was fetched (0 = never). */
    val conditionPriceAt: Long = 0,
    val addedAt: Long,
    val updatedAt: Long,
)

/** Scryfall's price for this printing, which is the near-mint market price. */
fun CollectionCard.nearMintPrice(): Double? = if (foil) priceUsdFoil ?: priceUsd else priceUsd

/** Price for one copy: JustTCG's price for its condition when there is one, else Scryfall's near-mint price. */
fun CollectionCard.unitPrice(): Double? = conditionPrice ?: nearMintPrice()
fun CollectionCard.totalPrice(): Double = (unitPrice() ?: 0.0) * quantity
/** Scryfall serves the same image path in several sizes; this swaps in the art-only crop. */
fun artCropUrl(imageUrl: String?): String? = imageUrl
    ?.replace("/normal/", "/art_crop/")?.replace("/large/", "/art_crop/")?.replace("/small/", "/art_crop/")

/** Price per copy when it was added, in its condition. */
fun CollectionCard.priceWhenAdded(): Double? {
    val then = priceAtAdd ?: return null
    // The price when added was near mint; scale it the same way today's condition price relates to near mint.
    val nm = nearMintPrice()
    val now = unitPrice()
    return if (conditionPrice != null && nm != null && nm > 0 && now != null) then * now / nm else then
}

/** Change in value per copy since it was added, or null when either price is unknown. */
fun CollectionCard.changeSinceAdded(): Double? {
    val now = unitPrice() ?: return null
    val then = priceWhenAdded() ?: return null
    return now - then
}

fun CollectionCard.facts() = com.mtgscanner.data.CardFacts(
    colors = colors, rarity = rarity, typeLine = typeLine,
    treatments = treatments.split(',').filter { it.isNotBlank() }.toSet(),
    foil = foil, price = unitPrice(), setCode = setCode,
)

fun CollectionCard.roleList(): List<String> = roles.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun CollectionCard.userTagList(): List<String> = userTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun CollectionCard.tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

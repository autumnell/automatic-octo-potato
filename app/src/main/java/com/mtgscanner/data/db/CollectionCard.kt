package com.mtgscanner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One line of the collection: copies of a specific printing that are alike (finish, box, language,
 * condition, signed, altered, graded, proxy), with a quantity. Copies that differ get their own rows.
 */
@Entity(
    tableName = "cards",
    indices = [Index(value = ["scryfallId", "foil", "boxId", "lang", "condition", "signed", "altered", "graded", "proxy"], unique = true), Index("name"), Index("setCode"), Index("boxId"), Index("oracleId"), Index("addedAt")],
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
    /** Left from JustTCG condition prices: cleared once on start, and no price uses it. */
    val conditionPrice: Double? = null,
    /** When [conditionPrice] was fetched (0 = never). */
    val conditionPriceAt: Long = 0,
    /** Signed by the artist or a player. */
    val signed: Boolean = false,
    /** Altered art. */
    val altered: Boolean = false,
    /** Grading, like "PSA 9"; empty when ungraded. */
    val graded: String = "",
    /** What you paid per copy; used for gains instead of [priceAtAdd] when set. */
    val purchasePrice: Double? = null,
    /** Where the card came from: "Pack", "Trade", a store name… */
    val acquiredFrom: String = "",
    /** Path of a photo of your own copy, in app storage; empty when none. */
    val photo: String = "",
    /** A proxy or suspected fake: listed but not counted in value. */
    val proxy: Boolean = false,
    /** Listed for sale. */
    val forSale: Boolean = false,
    /** Asking price per copy when for sale; null uses the market price. */
    val askPrice: Double? = null,
    /** Binder position, 1-based (0 = not placed). Page = (slot-1)/9+1 for 9-pocket pages. */
    val binderSlot: Int = 0,
    val power: String? = null,
    val toughness: String? = null,
    /** On the Reserved List. */
    val reserved: Boolean = false,
    /** "Stay in this box": re-sort and "Sort out" leave it where it is. */
    val pinned: Boolean = false,
    val addedAt: Long,
    val updatedAt: Long,
    /** Cardmarket price per copy in euros, from Scryfall (DB v12); null when Scryfall has none. */
    val priceEur: Double? = null,
    /** Cardmarket foil price per copy in euros (DB v12). */
    val priceEurFoil: Double? = null,
)

/** Columns that must all match for copies to share a row: the unique index on cards (DB v9) and [sameCopyAs]. */
const val COPY_KEY_SQL = "`scryfallId`, `foil`, `boxId`, `lang`, `condition`, `signed`, `altered`, `graded`, `proxy`"

/** Whether two rows hold copies that belong together: same printing, finish, box, language, condition and flags. */
fun CollectionCard.sameCopyAs(o: CollectionCard): Boolean =
    scryfallId == o.scryfallId && foil == o.foil && boxId == o.boxId && lang == o.lang && condition == o.condition &&
        signed == o.signed && altered == o.altered && graded == o.graded && proxy == o.proxy

/** Average of two per-copy prices weighted by copies; an unknown price is left out. */
fun blendPrice(a: Double?, aCopies: Int, b: Double?, bCopies: Int): Double? = when {
    a == null -> b
    b == null -> a
    aCopies + bCopies <= 0 -> a
    else -> (a * aCopies + b * bCopies) / (aCopies + bCopies)
}

/**
 * This row with [copies] copies of [from] added. Prices paid and at add are averaged by copies.
 * With [details], empty notes, source and photo are taken from [from] and tags are combined.
 */
fun CollectionCard.mergedWith(
    from: CollectionCard,
    copies: Int = from.quantity,
    now: Long = System.currentTimeMillis(),
    details: Boolean = true,
): CollectionCard {
    val paid = if (purchasePrice == null && from.purchasePrice == null) null
    else blendPrice(purchasePrice ?: priceAtAdd, quantity, from.purchasePrice ?: from.priceAtAdd, copies)
    val merged = copy(
        quantity = quantity + copies,
        priceAtAdd = blendPrice(priceAtAdd, quantity, from.priceAtAdd, copies),
        purchasePrice = paid,
        updatedAt = now,
    )
    if (!details) return merged
    return merged.copy(
        notes = notes.ifBlank { from.notes },
        acquiredFrom = acquiredFrom.ifBlank { from.acquiredFrom },
        photo = photo.ifBlank { from.photo },
        userTags = (userTagList() + from.userTagList()).distinctBy { it.lowercase() }.joinToString(","),
    )
}

/** Scryfall's price for this printing, which is the near-mint market price. */
fun CollectionCard.nearMintPrice(): Double? = if (foil) priceUsdFoil ?: priceUsd else priceUsd

/**
 * Price for one copy: Scryfall's near-mint price. (The old JustTCG [CollectionCard.conditionPrice] is cleared once on
 * start and no longer counts.)
 */
fun CollectionCard.unitPrice(): Double? = nearMintPrice()
fun CollectionCard.totalPrice(): Double = if (proxy) 0.0 else (unitPrice() ?: 0.0) * quantity

/** Scryfall's Cardmarket price in euros for this printing and finish. */
fun CollectionCard.nearMintEur(): Double? = if (foil) priceEurFoil ?: priceEur else priceEur

/** Price for one copy in euros. */
fun CollectionCard.unitEur(): Double? = nearMintEur()

/** What the copies are worth in euros; proxies count 0. Cards with no euro price count 0. */
fun CollectionCard.totalEur(): Double = if (proxy) 0.0 else (unitEur() ?: 0.0) * quantity
/** Scryfall serves the same image path in several sizes; this swaps in the art-only crop. */
fun artCropUrl(imageUrl: String?): String? = imageUrl
    ?.replace("/normal/", "/art_crop/")?.replace("/large/", "/art_crop/")?.replace("/small/", "/art_crop/")
    // Scryfall API image links ("...?format=image&version=normal").
    ?.replace("version=normal", "version=art_crop")?.replace("version=large", "version=art_crop")?.replace("version=small", "version=art_crop")

/** Price per copy when it was added: what you paid, else the market price then. */
fun CollectionCard.priceWhenAdded(): Double? = purchasePrice ?: priceAtAdd

/** Change in value per copy since it was added, or null when either price is unknown. */
fun CollectionCard.changeSinceAdded(): Double? {
    val now = unitPrice() ?: return null
    val then = priceWhenAdded() ?: return null
    return now - then
}

fun CollectionCard.facts(): com.mtgscanner.data.CardFacts = run { com.mtgscanner.data.SetDates.note(setCode, releasedAt); com.mtgscanner.data.CardFacts(
    colors = colors, rarity = rarity, typeLine = typeLine,
    treatments = treatments.split(',').filter { it.isNotBlank() }.toSet(),
    foil = foil, price = unitPrice(), setCode = setCode, name = name,
    colorIdentity = colorIdentity, cmc = cmc, power = power, toughness = toughness, reserved = reserved,
    formats = tagList().filter { it.startsWith("legal:") }.map { it.removePrefix("legal:") }.toSet(),
    number = collectorNumber,
) }

fun CollectionCard.userTagList(): List<String> = userTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun CollectionCard.tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

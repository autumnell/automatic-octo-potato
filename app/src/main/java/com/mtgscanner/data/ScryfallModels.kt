package com.mtgscanner.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScryfallCard(
    val id: String,
    val name: String,
    val lang: String = "en",
    @SerialName("oracle_id") val oracleId: String? = null,
    @SerialName("released_at") val releasedAt: String? = null,
    @SerialName("scryfall_uri") val scryfallUri: String = "",
    @SerialName("mana_cost") val manaCost: String? = null,
    val cmc: Double = 0.0,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val colors: List<String>? = null,
    @SerialName("color_identity") val colorIdentity: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val set: String,
    @SerialName("set_name") val setName: String,
    @SerialName("collector_number") val collectorNumber: String,
    val rarity: String,
    val artist: String? = null,
    @SerialName("flavor_text") val flavorText: String? = null,
    val watermark: String? = null,
    val prices: Prices = Prices(),
    @SerialName("image_uris") val imageUris: ImageUris? = null,
    @SerialName("card_faces") val cardFaces: List<CardFace>? = null,
    val finishes: List<String> = emptyList(),
    val legalities: Map<String, String> = emptyMap(),
    /** On the Reserved List. */
    val reserved: Boolean = false,
    @SerialName("border_color") val borderColor: String? = null,
    @SerialName("frame_effects") val frameEffects: List<String> = emptyList(),
    @SerialName("full_art") val fullArt: Boolean = false,
    val promo: Boolean = false,
    val layout: String? = null,
    val frame: String? = null,
) {
    /** Special printings, used by box rules: borderless, showcase, extended art, full art, etched, promo. */
    val treatments: List<String>
        get() = buildList {
            if (borderColor == "borderless") add("borderless")
            if ("showcase" in frameEffects) add("showcase")
            if ("extendedart" in frameEffects) add("extendedart")
            if (fullArt) add("fullart")
            if ("etched" in frameEffects || "etched" in finishes) add("etched")
            if (promo) add("promo")
            if (frame == "1993" || frame == "1997") add("retro")
        }

    /** Colors of the card, falling back to the faces for double-faced cards. */
    val effectiveColors: List<String>
        get() = colors ?: cardFaces.orEmpty().flatMap { it.colors.orEmpty() }.distinct()

    val effectiveTypeLine: String
        get() = typeLine ?: cardFaces.orEmpty().mapNotNull { it.typeLine }.joinToString(" // ")

    val effectiveManaCost: String
        get() = manaCost?.takeIf { it.isNotEmpty() }
            ?: cardFaces.orEmpty().mapNotNull { it.manaCost?.takeIf { c -> c.isNotEmpty() } }.joinToString(" // ")

    val effectiveOracleText: String
        get() = oracleText ?: cardFaces.orEmpty().joinToString("\n—\n") { "${it.name}: ${it.oracleText.orEmpty()}" }

    val imageUrl: String?
        get() = imageUris?.normal ?: cardFaces?.firstOrNull()?.imageUris?.normal

    val smallImageUrl: String?
        get() = imageUris?.small ?: cardFaces?.firstOrNull()?.imageUris?.small

    val hasFoil: Boolean get() = finishes.isEmpty() || "foil" in finishes || "etched" in finishes
    val hasNonFoil: Boolean get() = finishes.isEmpty() || "nonfoil" in finishes

    fun price(foil: Boolean): String? =
        if (foil) prices.usdFoil ?: prices.usdEtched ?: prices.usd else prices.usd
}

@Serializable
data class Prices(
    val usd: String? = null,
    @SerialName("usd_foil") val usdFoil: String? = null,
    @SerialName("usd_etched") val usdEtched: String? = null,
    val eur: String? = null,
)

@Serializable
data class ImageUris(
    val small: String? = null,
    val normal: String? = null,
    val large: String? = null,
    @SerialName("art_crop") val artCrop: String? = null,
)

@Serializable
data class CardFace(
    val name: String = "",
    @SerialName("mana_cost") val manaCost: String? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    @SerialName("flavor_text") val flavorText: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val colors: List<String>? = null,
    @SerialName("image_uris") val imageUris: ImageUris? = null,
)

@Serializable
data class CardList(
    val data: List<ScryfallCard> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_page") val nextPage: String? = null,
    @SerialName("not_found") val notFound: List<kotlinx.serialization.json.JsonElement> = emptyList(),
)

@Serializable
data class Catalog(val data: List<String> = emptyList())

@Serializable
data class ScryfallSet(
    val code: String,
    val name: String,
    @SerialName("set_type") val setType: String = "",
    @SerialName("released_at") val releasedAt: String? = null,
    @SerialName("card_count") val cardCount: Int = 0,
    /** Cards in the main set, the number printed after the collector number; null when it matches [cardCount]. */
    @SerialName("printed_size") val printedSize: Int? = null,
    @SerialName("icon_svg_uri") val iconSvgUri: String? = null,
    val digital: Boolean = false,
    @SerialName("parent_set_code") val parentSetCode: String? = null,
)

@Serializable
data class SetList(val data: List<ScryfallSet> = emptyList())

/** One official ruling on a card, from /cards/{id}/rulings. */
@Serializable
data class ScryfallRuling(
    val source: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val comment: String = "",
)

@Serializable
data class RulingList(val data: List<ScryfallRuling> = emptyList())

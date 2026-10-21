package com.mtgscanner.ui.trade

import com.mtgscanner.data.ScryfallCard

/** Card conditions, best first. */
internal val TRADE_CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")

/** Scryfall language codes offered for cards you get. */
internal val TRADE_LANGS = listOf("en", "ja", "de", "fr", "it", "es", "pt", "ru", "ko", "zhs", "zht")

/**
 * A card you get in a trade, priced for its finish. Foil is forced on or off when the
 * printing only comes one way.
 */
internal fun getItem(
    key: String,
    card: ScryfallCard,
    qty: Int = 1,
    foil: Boolean = !card.hasNonFoil,
    condition: String = "NM",
    lang: String = card.lang.ifBlank { "en" },
): TradeItem {
    val f = when {
        !card.hasFoil -> false
        !card.hasNonFoil -> true
        else -> foil
    }
    return TradeItem(
        key = key,
        name = card.name,
        set = card.set.uppercase(),
        imageUrl = card.smallImageUrl,
        price = card.price(f)?.toDoubleOrNull(),
        qty = qty,
        card = card,
        foil = f,
        condition = condition,
        lang = lang,
    )
}

/** The same row with a different printing; keeps quantity, foil (when possible), condition and language. */
internal fun TradeItem.withPrinting(p: ScryfallCard): TradeItem = getItem(key, p, qty, foil, condition, lang)

/** The same row with a new finish, condition or language. */
internal fun TradeItem.withDetails(foil: Boolean = this.foil, condition: String = this.condition, lang: String = this.lang): TradeItem =
    card?.let { getItem(key, it, qty, foil, condition, lang) } ?: this

/** Short line for a card you get, like "MH3 #123 · Foil · LP · JA". */
internal fun TradeItem.getLabel(): String = buildList {
    add(card?.let { "$set #${it.collectorNumber}" } ?: set)
    if (foil) add("Foil")
    if (condition != "NM") add(condition)
    if (lang.isNotBlank() && lang != "en") add(lang.uppercase())
}.joinToString(" · ")

package com.mtgscanner.data

import java.net.URLEncoder

/** Links from the card page to EDHREC, TCGplayer and Cardmarket. Pure, for tests. */
object CardLinks {
    data class Link(val label: String, val url: String)

    /**
     * Scryfall's own links when it gave them ([related] is related_uris, [purchase] is purchase_uris),
     * else a search on each site by the card's front-face name.
     */
    fun of(name: String, related: Map<String, String>, purchase: Map<String, String>): List<Link> {
        val front = name.substringBefore(" // ").trim()
        if (front.isEmpty()) return emptyList()
        val q = URLEncoder.encode(front, "UTF-8")
        fun ok(u: String?) = u?.takeIf { it.startsWith("https://") }
        return listOf(
            Link("EDHREC", ok(related["edhrec"]) ?: "https://edhrec.com/cards/${Edhrec.slug(front)}"),
            Link("TCGplayer", ok(purchase["tcgplayer"]) ?: "https://www.tcgplayer.com/search/magic/product?q=$q"),
            Link("Cardmarket", ok(purchase["cardmarket"]) ?: "https://www.cardmarket.com/en/Magic/Products/Search?searchString=$q"),
        )
    }
}

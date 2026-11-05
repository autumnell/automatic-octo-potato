package com.mtgscanner.util

/** One line of a list to share: a box, the collection, the wishlist or a deck's missing cards. */
data class ShareLine(
    val name: String,
    val quantity: Int,
    val setCode: String = "",
    val number: String = "",
    /** Price per copy, or null when unknown. */
    val price: Double? = null,
    val foil: Boolean = false,
    val condition: String = "NM",
    /** Color letters, like "WU"; empty = colorless. */
    val colors: String = "",
    val imageUrl: String? = null,
    val link: String = "",
    val proxy: Boolean = false,
)

/** How a list goes out: as text, as a web page, or as a store's cart list. */
sealed interface ShareAs {
    data object Text : ShareAs
    data object Page : ShareAs
    data class Cart(val store: BuyStore) : ShareAs
}

/** The text of a shared list. Pure, for tests. */
object ShareList {
    /**
     * "2× Card Name (SET #123) foil — $4.50 LP" per line, then the total.
     * [money] formats a price; prices are left out when [showPrices] is off.
     */
    fun text(title: String, lines: List<ShareLine>, showPrices: Boolean, money: (Double) -> String): String = buildString {
        appendLine(title)
        appendLine()
        lines.forEach { c ->
            append("${c.quantity}× ${c.name}")
            if (c.setCode.isNotBlank()) append(" (${c.setCode.uppercase()}" + (if (c.number.isNotBlank()) " #${c.number}" else "") + ")")
            if (c.foil) append(" foil")
            val extras = mutableListOf<String>()
            if (showPrices && c.price != null) extras += money(c.price)
            if (c.condition.isNotBlank() && c.condition != "NM") extras += c.condition
            if (extras.isNotEmpty()) append(" · ${extras.joinToString(" ")}")
            appendLine()
        }
        appendLine()
        val n = lines.sumOf { it.quantity }
        append("Total: ${if (n == 1) "1 card" else "$n cards"}")
        if (showPrices) append(" · ${money(lines.sumOf { (it.price ?: 0.0) * it.quantity })}")
        appendLine()
        append("Shared from Grimoire")
    }

    /** Lines for the web page. Proxies are left out: they're not for trade. */
    fun haveItems(lines: List<ShareLine>): List<HaveItem> = lines.filter { !it.proxy }.map { c ->
        HaveItem(
            name = c.name, setCode = c.setCode, number = c.number, quantity = c.quantity,
            price = c.price, colors = c.colors, imageUrl = c.imageUrl,
            foil = c.foil, condition = c.condition, link = c.link,
        )
    }

    /** A store's "paste a list" text. */
    fun cart(lines: List<ShareLine>, store: BuyStore): String = BuyList.text(lines.map { it.name to it.quantity }, store)
}

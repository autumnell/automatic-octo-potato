package com.mtgscanner.util

/** Stores whose carts take a pasted list. */
enum class BuyStore(val label: String) {
    TCGPLAYER("TCGplayer"),
    CARD_KINGDOM("Card Kingdom"),
    CARDMARKET("Cardmarket"),
}

/** Lists of cards to buy, in the text each store's "paste a list" box reads. Pure, for tests. */
object BuyList {
    /** One line per card, counts merged by name. TCGplayer Mass Entry and Card Kingdom take "4 Name"; Cardmarket "4x Name". */
    fun text(cards: List<Pair<String, Int>>, store: BuyStore): String {
        val merged = LinkedHashMap<String, Pair<String, Int>>()
        cards.filter { it.second > 0 && it.first.isNotBlank() }.forEach { (name, n) ->
            val k = name.trim().lowercase()
            merged[k] = (merged[k]?.first ?: name.trim()) to ((merged[k]?.second ?: 0) + n)
        }
        return merged.values.joinToString("\n") { (name, n) ->
            when (store) {
                BuyStore.CARDMARKET -> "${n}x $name"
                // Card Kingdom names double-faced cards by their front.
                BuyStore.CARD_KINGDOM -> "$n ${name.substringBefore(" // ")}"
                BuyStore.TCGPLAYER -> "$n $name"
            }
        }
    }
}

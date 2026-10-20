package com.mtgscanner.ui.collection

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.formatUsd

/**
 * A plain text list of a trade binder to send to a friend:
 * "2× Card Name (SET #123) foil — $4.50 LP", then a total.
 */
fun binderShareText(name: String, cards: List<CollectionCard>, showPrices: Boolean): String = buildString {
    appendLine("$name (for trade)")
    appendLine()
    cards.forEach { c ->
        append("${c.quantity}× ${c.name} (${c.setCode.uppercase()} #${c.collectorNumber})")
        if (c.foil) append(" foil")
        val extras = mutableListOf<String>()
        val p = c.unitPrice()
        if (showPrices && p != null) extras += formatUsd(p)
        if (c.condition.isNotBlank() && c.condition != "NM") extras += c.condition
        if (extras.isNotEmpty()) append(" — ${extras.joinToString(" ")}")
        appendLine()
    }
    appendLine()
    val n = cards.sumOf { it.quantity }
    append("Total: ${if (n == 1) "1 card" else "$n cards"}")
    if (showPrices) append(" · ${formatUsd(cards.sumOf { it.totalPrice() })}")
    appendLine()
    append("Shared from MTG Scanner")
}

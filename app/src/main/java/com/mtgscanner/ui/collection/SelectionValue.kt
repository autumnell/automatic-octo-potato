package com.mtgscanner.ui.collection

/** One picked row: its unit price (null when unknown), how many copies, and whether it's a proxy. */
data class PickedRow(val id: Long, val unitPrice: Double?, val quantity: Int, val proxy: Boolean = false)

object SelectionValue {
    /**
     * What the picked rows are worth: unit price × quantity, proxies left out.
     * Null when none of the picked rows has a price, so the bar shows no value.
     */
    fun total(rows: List<PickedRow>, selected: Set<Long>): Double? {
        val picked = rows.distinctBy { it.id }.filter { it.id in selected && !it.proxy && it.unitPrice != null }
        if (picked.isEmpty()) return null
        return picked.sumOf { it.unitPrice!! * it.quantity }
    }
}

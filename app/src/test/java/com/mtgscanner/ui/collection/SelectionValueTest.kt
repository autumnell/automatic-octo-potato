package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionValueTest {
    private val rows = listOf(
        PickedRow(1, 2.50, 4),
        PickedRow(2, null, 1),
        PickedRow(3, 10.0, 1, proxy = true),
        PickedRow(4, 0.20, 1),
        PickedRow(1, 2.50, 4), // same row shown twice (grouped by tag)
    )

    @Test fun sumsPriceTimesQuantity() = assertEquals(10.20, SelectionValue.total(rows, setOf(1, 2, 4))!!, 1e-9)
    @Test fun proxiesDontCount() = assertEquals(0.20, SelectionValue.total(rows, setOf(3, 4))!!, 1e-9)
    @Test fun nothingPricedIsNull() = assertNull(SelectionValue.total(rows, setOf(2, 3)))
    @Test fun emptySelectionIsNull() = assertNull(SelectionValue.total(rows, emptySet()))
}

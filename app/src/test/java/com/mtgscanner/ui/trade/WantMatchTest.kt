package com.mtgscanner.ui.trade

import com.mtgscanner.util.DecklistParser
import org.junit.Assert.assertEquals
import org.junit.Test

class WantMatchTest {
    private data class Row(val name: String, val set: String, val qty: Int)

    private fun match(text: String, rows: List<Row>) =
        matchWants(DecklistParser.parse(text), rows, { it.name }, { it.set }, { it.qty })

    @Test fun matchesByNameAndCapsQuantity() {
        val rows = listOf(Row("Lightning Bolt", "m10", 1), Row("Lightning Bolt", "2xm", 2), Row("Opt", "xln", 4))
        val out = match("4 lightning bolt (2XM)\n1 Counterspell", rows)
        assertEquals(listOf(rows[1] to 2, rows[0] to 1), out)
    }

    @Test fun ignoresBackFace() {
        val rows = listOf(Row("Delver of Secrets // Insectile Aberration", "isd", 3))
        assertEquals(listOf(rows[0] to 1), match("1 Delver of Secrets", rows))
    }
}

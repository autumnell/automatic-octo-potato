package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WishTargetMathTest {
    @Test fun tellsOnceUntilThePriceGoesBackOver() {
        val told = HashSet<String>()
        fun run(prices: Map<String, Double?>) =
            WishTargetMath.due(mapOf("a" to 40.0, "b" to 5.0), prices, { it in told }) { id, on -> if (on) told += id else told -= id }
        assertEquals(listOf("a" to 38.5), run(mapOf("a" to 38.5, "b" to 9.0)))
        assertEquals(emptyList<Pair<String, Double>>(), run(mapOf("a" to 37.0, "b" to null)))
        assertEquals(emptyList<Pair<String, Double>>(), run(mapOf("a" to 41.0)))
        assertEquals(listOf("a" to 40.0), run(mapOf("a" to 40.0)))
    }
}

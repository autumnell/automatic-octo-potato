package com.mtgscanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaritySparkTest {
    @Test fun rarityTiers() {
        assertNull(RarityTier.of(null))
        assertNull(RarityTier.of(""))
        assertEquals(RarityTier.COMMON, RarityTier.of("common"))
        assertEquals(RarityTier.UNCOMMON, RarityTier.of("Uncommon"))
        assertEquals(RarityTier.RARE, RarityTier.of("rare"))
        assertEquals(RarityTier.MYTHIC, RarityTier.of("mythic"))
        assertEquals(RarityTier.SPECIAL, RarityTier.of("special"))
        assertEquals(RarityTier.SPECIAL, RarityTier.of("bonus"))
    }

    @Test fun heightsSpanZeroToOne() {
        assertEquals(listOf(0f, 0.5f, 1f), Spark.heights(listOf(1.0, 2.0, 3.0)))
        assertEquals(listOf(0.5f, 0.5f), Spark.heights(listOf(4.0, 4.0)))
        assertEquals(emptyList<Float>(), Spark.heights(emptyList()))
    }

    @Test fun trendIgnoresTinyMoves() {
        assertEquals(1, Spark.trend(listOf(1.0, 1.2)))
        assertEquals(-1, Spark.trend(listOf(10.0, 9.0)))
        assertEquals(0, Spark.trend(listOf(10.0, 10.02)))
        assertEquals(0, Spark.trend(listOf(1.0)))
    }
}

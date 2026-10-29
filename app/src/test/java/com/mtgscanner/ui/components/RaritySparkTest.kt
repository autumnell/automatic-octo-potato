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
}

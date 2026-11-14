package com.mtgscanner.ui.scan

import com.mtgscanner.data.ScryfallCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFoilTest {
    private fun card(vararg finishes: String) = ScryfallCard(
        id = "c", name = "Bolt", set = "m10", setName = "Magic 2010", collectorNumber = "1", rarity = "common",
        finishes = finishes.toList(),
    )

    @Test fun aBatchCardKeepsTheFinishItWasScannedWith() {
        val state = ScanUiState() // the scan bar says non-foil
        val c = card("nonfoil", "foil")
        assertTrue(state.photoFoil(PhotoItem(1, null, c, foil = true), c))
        assertFalse(state.photoFoil(PhotoItem(1, null, c, foil = false), c))
        assertFalse(state.photoFoil(PhotoItem(1, null, c), c))
    }

    @Test fun aFoilOnlyCardIsAlwaysFoil() {
        val c = card("foil")
        assertTrue(ScanUiState().photoFoil(PhotoItem(1, null, c, foil = false), c))
    }
}

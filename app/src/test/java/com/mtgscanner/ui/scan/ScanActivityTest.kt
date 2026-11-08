package com.mtgscanner.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanActivityTest {
    private val names: (Long) -> String? = { if (it == 7L) "Binder" else null }

    @Test fun aRunWinsOverWhereCardsGo() {
        assertEquals(ScanActivity.ADD, ScanUiState().activity())
        assertEquals(ScanActivity.PACK, ScanUiState(pack = PackDraft("Foundations", null), target = ScanTarget.DECK).activity())
        assertEquals(ScanActivity.SORT, ScanUiState(sort = SortSession()).activity())
        assertEquals(ScanActivity.BATCH, ScanUiState(batch = emptyList()).activity())
        assertEquals(ScanActivity.CHECK, ScanUiState(target = ScanTarget.CHECK).activity())
    }

    @Test fun theLineSaysWhereTheNextCardGoes() {
        assertEquals("Next card goes where your box rules say", ScanUiState().nextCard(names, null))
        assertEquals("Next card goes into Binder", ScanUiState(boxId = 7L).nextCard(names, null))
        assertEquals("Next card goes into Binder · hands-free · FDN only", ScanUiState(boxId = 7L, stack = true).nextCard(names, "fdn"))
        assertEquals("Next card is only priced", ScanUiState(target = ScanTarget.CHECK, stack = true).nextCard(names, null))
        assertEquals("Next card counts toward Foundations", ScanUiState(pack = PackDraft("Foundations", null)).nextCard(names, null))
    }
}

package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPreviewTest {
    @Test fun countsEachHalf() {
        val names = listOf("Abrade" to 2, "Mox" to 1, "Negate" to 3, "Zap" to 1, "+2 Mace" to 4)
        // Digits and symbols stay with the first half.
        assertEquals(7 to 4, Sorting.splitCounts(names, 'N'))
        assertEquals(6 to 5, Sorting.splitCounts(names, 'M'))
        assertEquals(4 to 7, Sorting.splitCounts(names, 'A'))
    }

    @Test fun lettersStayInsideTheRange() {
        assertEquals(('B'..'Z').toList(), Sorting.splitLetters(null, null))
        assertEquals(listOf('B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M'), Sorting.splitLetters("A", "M"))
        assertEquals(listOf('O', 'P'), Sorting.splitLetters("n", "p"))
        assertTrue(Sorting.splitLetters("Q", "Q").isEmpty())
    }

    @Test fun suggestedLetterIsOneYouCanPick() {
        val names = listOf("Abrade" to 5, "Bolt" to 5, "Counter" to 5, "Duress" to 5)
        val at = Sorting.splitLetter(names)!!
        assertTrue(at in Sorting.splitLetters(null, null))
        assertEquals(10 to 10, Sorting.splitCounts(names, at))
    }
}

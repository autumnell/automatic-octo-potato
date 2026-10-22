package com.mtgscanner.ui.decks

import com.mtgscanner.data.CommanderRules
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckIssueTextTest {
    @Test fun rows() {
        val i = CommanderRules.Issues(offColor = listOf("Shock"), overLimit = listOf("Sol Ring"), banned = listOf("Mana Crypt"), notLegal = listOf("Brainstorm"))
        assertEquals(
            listOf("Mana Crypt" to "Banned", "Brainstorm" to "Not legal", "Sol Ring" to "Too many", "Shock" to "Off-color"),
            DeckIssueText.rows(i),
        )
        assertEquals("4 issues", DeckIssueText.count(i.count))
        assertEquals("1 issue", DeckIssueText.count(1))
    }
}

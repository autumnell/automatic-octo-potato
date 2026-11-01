package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {
    @Test fun commands() {
        assertEquals(VoiceCommand.Foil(true), VoiceCommands.parse("Foil"))
        assertEquals(VoiceCommand.Foil(false), VoiceCommands.parse("not foil"))
        assertEquals(VoiceCommand.Undo, VoiceCommands.parse("undo."))
        assertEquals(VoiceCommand.SetAside, VoiceCommands.parse("Set aside"))
        assertEquals(VoiceCommand.Skip, VoiceCommands.parse("next"))
        assertEquals(VoiceCommand.Add, VoiceCommands.parse("yes"))
    }

    @Test fun copies() {
        assertEquals(VoiceCommand.Copies(4), VoiceCommands.parse("four"))
        assertEquals(VoiceCommand.Copies(3), VoiceCommands.parse("times 3"))
        assertEquals(VoiceCommand.Copies(4), VoiceCommands.parse("playset"))
        assertEquals(VoiceCommand.Copies(2), VoiceCommands.parse("two copies"))
        assertEquals(VoiceCommand.Copies(4), VoiceCommands.parse("times for"))
    }

    @Test fun commonWordsAloneAreNothing() {
        assertNull(VoiceCommands.parse("for"))
        assertNull(VoiceCommands.parse("to"))
        assertNull(VoiceCommands.parse("lightning bolt"))
        assertNull(VoiceCommands.parse(""))
    }

    @Test fun firstGuessThatIsACommand() {
        assertEquals(VoiceCommand.Copies(2), VoiceCommands.first(listOf("to", "two")))
    }
}

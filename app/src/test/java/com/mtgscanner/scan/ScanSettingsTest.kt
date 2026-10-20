package com.mtgscanner.scan

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSettingsTest {
    @Test fun levels() {
        val exact = ScanSettings(level = AutoAddLevel.EXACT)
        assertTrue(exact.accepts(exact = true, confident = false))
        assertFalse(exact.accepts(exact = false, confident = true))
        val confident = ScanSettings(level = AutoAddLevel.CONFIDENT)
        assertTrue(confident.accepts(exact = false, confident = true))
        assertTrue(confident.accepts(exact = true, confident = false))
        assertFalse(confident.accepts(exact = false, confident = false))
        assertTrue(ScanSettings(level = AutoAddLevel.ANY).accepts(exact = false, confident = false))
    }

    @Test fun skipRules() {
        val s = ScanSettings(skipBasics = true, skipTokens = true, skipUnder = 0.25)
        assertEquals("basic land", s.skipReason("Basic Land — Forest", "normal", 0.1, true))
        assertNull(ScanSettings().skipReason("Basic Land — Forest", "normal", 0.1, true))
        assertEquals("token", s.skipReason("Token Creature — Goblin", "token", 0.0, true))
        assertEquals("token", s.skipReason("Emblem — Ajani", "emblem", null, true))
        assertEquals("under $0.25", s.skipReason("Instant", "normal", 0.1, true))
        assertEquals("under $0.25", s.skipReason("Instant", "normal", null, true))
        assertNull(s.skipReason("Instant", "normal", 3.0, true))
        // With prices hidden, the price rule is off.
        assertNull(s.skipReason("Instant", "normal", 0.1, false))
        assertNull(s.skipReason("Legendary Land", "normal", 0.5, true))
    }

    @Test fun savesAndLoads() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = ScanSettings(autoAdd = true, level = AutoAddLevel.EXACT, unsure = UnsureAction.ASIDE, boxId = -2L, sound = ScanSound.CHIME)
        assertEquals(s, json.decodeFromString(ScanSettings.serializer(), json.encodeToString(ScanSettings.serializer(), s)))
        assertEquals(ScanSettings(), json.decodeFromString(ScanSettings.serializer(), "{}"))
    }
}

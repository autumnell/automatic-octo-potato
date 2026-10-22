package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdhrecTest {
    @Test fun slugs() {
        assertEquals("atraxa-praetors-voice", Edhrec.slug("Atraxa, Praetors' Voice"))
        assertEquals("krrik-son-of-yawgmoth", Edhrec.slug("K'rrik, Son of Yawgmoth"))
        assertEquals("eowyn-lady-of-rohan", Edhrec.slug("Éowyn, Lady of Rohan"))
        assertEquals("lim-dul-the-necromancer", Edhrec.slug("Lim-Dûl the Necromancer"))
        assertEquals("esika-god-of-the-tree", Edhrec.slug("Esika, God of the Tree // The Prismatic Bridge"))
        assertEquals("the-ur-dragon", Edhrec.slug("The Ur-Dragon"))
        assertEquals("yshtola-nights-blessed", Edhrec.slug("Y'shtola, Night's Blessed"))
        assertEquals("yshtola-nights-blessed", Edhrec.slug("Y’shtola, Night’s Blessed"))
        assertEquals("aesi-tyrant-of-gyre-strait", Edhrec.slug("  Aesi,  Tyrant of Gyre Strait "))
    }

    @Test fun partnersJoinInAlphabeticalOrder() {
        val url = "https://edhrec.com/commanders/thrasios-triton-hero-tymna-the-weaver"
        assertEquals(url, Edhrec.commanderUrl(listOf("Tymna the Weaver", "Thrasios, Triton Hero")))
        assertEquals(url, Edhrec.commanderUrl(listOf("Thrasios, Triton Hero", "Tymna the Weaver")))
        assertEquals("https://edhrec.com/commanders/atraxa-praetors-voice", Edhrec.commanderUrl(listOf("Atraxa, Praetors' Voice")))
        assertNull(Edhrec.commanderUrl(emptyList()))
    }

    @Test fun scryfallLinkForOneCommanderOnly() {
        val sf = "https://edhrec.com/route/?cc=Atraxa%2C+Praetors%27+Voice"
        assertEquals(sf, Edhrec.linkFor(listOf("Atraxa, Praetors' Voice"), sf))
        assertEquals("https://edhrec.com/commanders/atraxa-praetors-voice", Edhrec.linkFor(listOf("Atraxa, Praetors' Voice"), null))
        assertEquals(
            "https://edhrec.com/commanders/thrasios-triton-hero-tymna-the-weaver",
            Edhrec.linkFor(listOf("Thrasios, Triton Hero", "Tymna the Weaver"), sf),
        )
        assertNull(Edhrec.linkFor(emptyList(), sf))
    }
}

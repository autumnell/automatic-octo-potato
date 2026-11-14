package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ColorGroupsTest {
    @Test fun namesOneToFiveColors() {
        assertEquals("White", ColorGroups.name("W"))
        assertEquals("Red", ColorGroups.name("RR"))
        assertEquals("Azorius", ColorGroups.name("uw"))
        assertEquals("Abzan", ColorGroups.name("GBW"))
        assertEquals("Temur", ColorGroups.name("R,G,U"))
        assertEquals(ColorGroups.FOUR, ColorGroups.name("WUBR"))
        assertEquals(ColorGroups.FIVE, ColorGroups.name("WUBRG"))
    }

    @Test fun noColorsHasNoName() {
        assertNull(ColorGroups.name(""))
        assertNull(ColorGroups.name("C"))
    }

    /** A pair or trio missing from the tables would leave those cards with no group. */
    @Test fun everyPairAndTrioHasItsOwnName() {
        val colors = "WUBRG"
        val names = HashSet<String>()
        for (i in colors.indices) for (j in i + 1 until colors.length) {
            val pair = "${colors[i]}${colors[j]}"
            names += assertNamed(pair)
            for (k in j + 1 until colors.length) names += assertNamed(pair + colors[k])
        }
        assertEquals(20, names.size)
        assertEquals(22, ColorGroups.MULTI.size)
        assertEquals(22, ColorGroups.MULTI.toSet().size)
    }

    private fun assertNamed(colors: String): String {
        val name = ColorGroups.name(colors)
        assertNotNull("No name for $colors", name)
        return name!!
    }
}

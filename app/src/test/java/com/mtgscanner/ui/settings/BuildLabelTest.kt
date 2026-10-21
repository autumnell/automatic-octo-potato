package com.mtgscanner.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildLabelTest {
    @Test fun ciBuildShowsNumberAndShortDate() =
        assertEquals("Grimoire build 412 · Oct 21", buildLabel(412, "2026-10-21"))

    @Test fun localBuildSaysDev() =
        assertEquals("Grimoire dev build · Jan 5", buildLabel(0, "2027-01-05"))

    @Test fun badDateIsShownAsIs() =
        assertEquals("Grimoire build 7 · ?", buildLabel(7, "?"))
}

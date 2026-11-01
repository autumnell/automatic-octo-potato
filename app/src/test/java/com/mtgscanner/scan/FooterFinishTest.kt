package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FooterFinishTest {
    @Test fun starIsFoilDotIsNot() {
        assertEquals(true, CardTextParser.findFinish("0123 R MOM ★ EN"))
        assertEquals(false, CardTextParser.findFinish("0123 R MOM • EN"))
        assertEquals(false, CardTextParser.findFinish("MKM · DE"))
    }

    @Test fun anAsteriskOrNoMarkSaysNothing() {
        assertNull(CardTextParser.findFinish("MOM * EN"))
        assertNull(CardTextParser.findFinish("MOM EN"))
        assertNull(CardTextParser.findFinish("Draw a card • then discard"))
    }
}

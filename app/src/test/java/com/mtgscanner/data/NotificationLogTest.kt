package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationLogTest {
    @Test fun savedFormRoundTrips() {
        val list = listOf(
            NotificationEntry(2L, "card/12", "Price alert: Bolt", "Bolt over $5.00 · now $5.20\nsecond line"),
            NotificationEntry(1L, "settings", "Weekly backup\tfailed", ""),
        )
        val back = NotificationLogMath.decode(NotificationLogMath.encode(list))
        assertEquals(2, back.size)
        assertEquals(list[0], back[0])
        assertEquals("Weekly backup failed", back[1].title)
        assertEquals("", back[1].text)
    }

    @Test fun newestFirstAndCapped() {
        var list = emptyList<NotificationEntry>()
        for (i in 1..NotificationLogMath.MAX + 5) list = NotificationLogMath.add(list, NotificationEntry(i.toLong(), "home", "n$i", ""))
        assertEquals(NotificationLogMath.MAX, list.size)
        assertEquals("n${NotificationLogMath.MAX + 5}", list.first().title)
    }

    @Test fun badLinesAreSkipped() {
        assertEquals(emptyList<NotificationEntry>(), NotificationLogMath.decode("junk\nx\ty\tz\tw"))
        assertEquals(emptyList<NotificationEntry>(), NotificationLogMath.decode(null))
    }
}

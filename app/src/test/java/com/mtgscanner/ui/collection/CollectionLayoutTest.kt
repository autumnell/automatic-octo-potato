package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionLayoutTest {
    @Test fun neverSavedShowsEverything() {
        val l = CollectionLayout.load(null, null, null, false)
        assertEquals(CollectionSection.entries, l.sections)
        assertTrue(CollectionPart.entries.all { l.shows(it) })
        assertFalse(l.compact)
        assertEquals(CollectionLayout.DEFAULT, l)
    }

    @Test fun savedOrderAndHiddenRoundTrip() {
        val l = CollectionLayout(
            listOf(CollectionSection.BOXES, CollectionSection.RING),
            setOf(CollectionPart.ART, CollectionPart.DECKS),
            compact = true,
        )
        val back = CollectionLayout.load(
            CollectionLayout.sectionsKey(l.sections), CollectionLayout.knownKey(), CollectionLayout.hiddenKey(l.hidden), l.compact,
        )
        assertEquals(l, back)
    }

    @Test fun unknownKeysAreDropped() {
        assertEquals(
            listOf(CollectionSection.LISTS, CollectionSection.RING),
            CollectionLayout.sections("lists,pie,ring,ring", CollectionLayout.knownKey()),
        )
        assertEquals(setOf(CollectionPart.VALUE), CollectionLayout.hidden("value, sparkle,,"))
        assertEquals(emptySet<CollectionPart>(), CollectionLayout.hidden(""))
    }

    @Test fun sectionsAddedLaterShowAtTheEnd() {
        // Saved by a version that only knew the ring and boxes; the ring was hidden.
        assertEquals(
            listOf(CollectionSection.BOXES, CollectionSection.BROWSE, CollectionSection.TO_SORT, CollectionSection.LISTS),
            CollectionLayout.sections("boxes", "ring,boxes"),
        )
    }

    @Test fun hiddenSectionsStayHidden() {
        assertEquals(listOf(CollectionSection.BOXES), CollectionLayout.sections("boxes", CollectionLayout.knownKey()))
        assertEquals(emptyList<CollectionSection>(), CollectionLayout.sections("", CollectionLayout.knownKey()))
    }

    @Test fun boxesTabAlwaysShows() {
        val l = CollectionLayout(hidden = setOf(CollectionPart.DECKS, CollectionPart.WISHLIST, CollectionPart.TOOLS))
        assertEquals(listOf(CollectionTab.BOXES), CollectionTab.shown(l))
        assertEquals(CollectionTab.entries, CollectionTab.shown(CollectionLayout.DEFAULT))
    }
}

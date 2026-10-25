package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BulkUriTest {
    private val list = """{"object":"list","has_more":false,"data":[
        {"object":"bulk_data","type":"oracle_cards","download_uri":"https://data.scryfall.io/oracle-cards/a.json"},
        {"object":"bulk_data","type":"default_cards","download_uri":"https://data.scryfall.io/default-cards/b.json"}
    ]}"""

    @Test fun findsTheFileByType() {
        assertEquals("https://data.scryfall.io/default-cards/b.json", ScryfallApi().bulkUriIn(list, "default_cards"))
    }

    @Test fun missingTypeIsNull() {
        assertNull(ScryfallApi().bulkUriIn(list, "all_cards"))
        assertNull(ScryfallApi().bulkUriIn("""{"object":"error"}""", "default_cards"))
    }
}

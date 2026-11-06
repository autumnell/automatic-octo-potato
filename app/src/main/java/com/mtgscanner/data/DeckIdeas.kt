package com.mtgscanner.data

import com.mtgscanner.util.CardNames
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A card EDHREC suggests for a commander: the share of its decks that play it (0..1) and its synergy (-1..1). */
data class EdhrecRec(val name: String, val share: Double?, val synergy: Double?)

/** One list on an EDHREC commander page ("Top Cards", "Creatures"…). */
data class EdhrecGroup(val header: String, val cards: List<EdhrecRec>)

/** A combo from Commander Spellbook: its cards, what it makes, and the cards it still needs (none when complete). */
data class Combo(val id: String, val cards: List<String>, val produces: List<String>, val missing: List<String>)

/** What Commander Spellbook finds for a deck: combos it has, and combos one card away. */
data class DeckCombos(val included: List<Combo>, val almost: List<Combo>)

/** Reading EDHREC and Commander Spellbook. Pure, for tests. */
object DeckIdeasMath {
    private val json = Json { ignoreUnknownKeys = true }
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull

    /**
     * The card lists of an EDHREC commander page (container.json_dict.cardlists), in page order, without "New
     * Cards". A card's share is num_decks over potential_decks.
     */
    fun edhrecGroups(text: String): List<EdhrecGroup> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val lists = ((root["container"] as? JsonObject)?.get("json_dict") as? JsonObject)?.get("cardlists") as? JsonArray ?: return emptyList()
        return lists.mapNotNull { l ->
            val o = l as? JsonObject ?: return@mapNotNull null
            if (o.str("tag") == "newcards") return@mapNotNull null
            val cards = (o["cardviews"] as? JsonArray).orEmpty().mapNotNull { v ->
                val c = v as? JsonObject ?: return@mapNotNull null
                val name = c.str("name") ?: return@mapNotNull null
                val n = c.num("num_decks") ?: c.num("inclusion")
                val pot = c.num("potential_decks")
                val share = if (n != null && pot != null && pot > 0) (n / pot).coerceIn(0.0, 1.0) else null
                EdhrecRec(name, share, c.num("synergy"))
            }
            if (cards.isEmpty()) null else EdhrecGroup(o.str("header") ?: "Cards", cards)
        }
    }

    /** The body for Spellbook's find-my-combos: the commanders and the main deck's cards with their counts. */
    fun spellbookBody(commanders: List<String>, main: List<Pair<String, Int>>): String = buildJsonObject {
        put("commanders", buildJsonArray { commanders.forEach { add(buildJsonObject { put("card", it); put("quantity", 1) }) } })
        put("main", buildJsonArray { main.forEach { (n, q) -> add(buildJsonObject { put("card", n); put("quantity", q) }) } })
    }.toString()

    /**
     * Spellbook's answer: results.included (in the deck) and results.almostIncluded (one card away). A combo's
     * missing cards are its cards that [has] says the deck doesn't have.
     */
    fun combos(text: String, has: (String) -> Boolean): DeckCombos? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val r = root["results"] as? JsonObject ?: return null
        fun list(k: String): List<Combo> = (r[k] as? JsonArray).orEmpty().mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val cards = (o["uses"] as? JsonArray).orEmpty().mapNotNull { u -> ((u as? JsonObject)?.get("card") as? JsonObject)?.str("name") }
            if (cards.isEmpty()) return@mapNotNull null
            val produces = (o["produces"] as? JsonArray).orEmpty().mapNotNull { p -> ((p as? JsonObject)?.get("feature") as? JsonObject)?.str("name") }
            Combo(o.str("id") ?: cards.joinToString("+"), cards, produces, cards.filterNot(has))
        }
        return DeckCombos(list("included"), list("almostIncluded"))
    }

    /** Spellbook's page for a combo. */
    fun comboUrl(id: String): String = "https://commanderspellbook.com/combo/$id/"

    /** Whether [name] is one of [names] (by [CardNames.key]). */
    fun contains(names: Set<String>, name: String): Boolean = CardNames.key(name) in names
}

private val ideasClient by lazy { sharedHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

/** EDHREC's card lists for [commanders]; null when EDHREC can't be reached or has no page. */
suspend fun edhrecGroups(commanders: List<String>): List<EdhrecGroup>? =
    edhrecPage(commanders)?.let(DeckIdeasMath::edhrecGroups)?.takeIf { it.isNotEmpty() }

/** Commander Spellbook's combos for a deck; null when it can't be reached. */
suspend fun spellbookCombos(commanders: List<String>, main: List<Pair<String, Int>>): DeckCombos? = withContext(Dispatchers.IO) {
    val names = (commanders + main.map { it.first }).map(CardNames::key).toSet()
    val req = Request.Builder().url("https://backend.commanderspellbook.com/find-my-combos")
        .header("User-Agent", "MTGScanner/0.1 (personal collection app)")
        .post(DeckIdeasMath.spellbookBody(commanders, main).toRequestBody("application/json".toMediaType()))
        .build()
    runCatching {
        ideasClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()?.let { body -> DeckIdeasMath.combos(body) { DeckIdeasMath.contains(names, it) } }
}

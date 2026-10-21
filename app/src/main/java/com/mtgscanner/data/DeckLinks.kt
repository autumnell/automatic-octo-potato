package com.mtgscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A deck read from a Moxfield or Archidekt link: its name and a decklist the app can read. */
data class LinkedDeck(val name: String, val text: String)

/** Public decks from Moxfield and Archidekt links, through their public JSON. Parsing is pure, for tests. */
object DeckLinks {
    private val MOXFIELD = Regex("""https?://(?:www\.)?moxfield\.com/decks/([A-Za-z0-9_-]+)""")
    private val ARCHIDEKT = Regex("""https?://(?:www\.)?archidekt\.com/(?:api/)?decks/(\d+)""")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The first Moxfield or Archidekt deck link in [text], or null. */
    fun find(text: String): String? = (MOXFIELD.find(text) ?: ARCHIDEKT.find(text))?.value

    /** Downloads the deck behind [url]. Throws IOException when offline or the deck is private or gone. */
    suspend fun fetch(url: String): LinkedDeck = withContext(Dispatchers.IO) {
        MOXFIELD.find(url)?.let { m ->
            val id = m.groupValues[1]
            val body = get("https://api2.moxfield.com/v3/decks/all/$id") ?: get("https://api2.moxfield.com/v2/decks/all/$id")
                ?: throw IOException("Deck not found. Is it public?")
            return@withContext parseMoxfield(json.parseToJsonElement(body)) ?: throw IOException("Couldn't read that deck")
        }
        ARCHIDEKT.find(url)?.let { m ->
            val body = get("https://archidekt.com/api/decks/${m.groupValues[1]}/") ?: throw IOException("Deck not found. Is it public?")
            return@withContext parseArchidekt(json.parseToJsonElement(body)) ?: throw IOException("Couldn't read that deck")
        }
        throw IOException("Not a Moxfield or Archidekt link")
    }

    private val client by lazy { sharedHttpClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build() }

    /** The body, or null on 404 and other refusals. Throws on network errors. */
    private fun get(url: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "MTGScanner/0.1 (personal collection app)")
            .header("Accept", "application/json")
            .build()
        return client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private data class Entry(val qty: Int, val name: String, val set: String?, val number: String?, val foil: Boolean)

    /** Moxfield's v3 ("boards": {"mainboard": {"cards": …}}) or v2 ("mainboard": {…}) deck JSON. */
    fun parseMoxfield(root: JsonElement): LinkedDeck? {
        val o = root as? JsonObject ?: return null
        val boards = o["boards"] as? JsonObject
        fun board(key: String): List<Entry> {
            val b = (boards?.get(key) as? JsonObject)?.get("cards") as? JsonObject ?: o[key] as? JsonObject ?: return emptyList()
            return b.values.mapNotNull { v ->
                val e = v as? JsonObject ?: return@mapNotNull null
                val card = e["card"] as? JsonObject ?: return@mapNotNull null
                val name = card.str("name") ?: return@mapNotNull null
                val finish = e.str("finish").orEmpty()
                Entry(
                    (e["quantity"] as? JsonPrimitive)?.intOrNull ?: 1, name, card.str("set"), card.str("cn"),
                    finish == "foil" || finish == "etched" || (e["isFoil"] as? JsonPrimitive)?.booleanOrNull == true,
                )
            }
        }
        val sections = listOf(
            "Commander" to board("commanders"),
            "Companion" to board("companions"),
            "Deck" to board("mainboard"),
            "Sideboard" to board("sideboard"),
        )
        if (sections.all { it.second.isEmpty() }) return null
        return LinkedDeck(o.str("name") ?: "Moxfield deck", text(sections))
    }

    /** Archidekt's deck JSON: cards with categories; categories left out of the deck (maybeboard) are skipped. */
    fun parseArchidekt(root: JsonElement): LinkedDeck? {
        val o = root as? JsonObject ?: return null
        val cards = o["cards"] as? JsonArray ?: return null
        val outOfDeck = (o["categories"] as? JsonArray).orEmpty().mapNotNull { c ->
            val co = c as? JsonObject ?: return@mapNotNull null
            co.str("name")?.takeIf { (co["includedInDeck"] as? JsonPrimitive)?.booleanOrNull == false }
        }.toSet()
        val bySection = linkedMapOf<String, MutableList<Entry>>("Commander" to mutableListOf(), "Deck" to mutableListOf(), "Sideboard" to mutableListOf())
        for (c in cards) {
            val co = c as? JsonObject ?: continue
            val card = co["card"] as? JsonObject ?: continue
            val name = (card["oracleCard"] as? JsonObject)?.str("name") ?: card.str("name") ?: continue
            val cats = (co["categories"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            val section = when {
                cats.any { it.equals("Commander", true) } -> "Commander"
                cats.any { it.equals("Sideboard", true) } -> "Sideboard"
                cats.any { it.equals("Maybeboard", true) } || (cats.isNotEmpty() && cats.first() in outOfDeck) -> continue
                else -> "Deck"
            }
            val modifier = co.str("modifier").orEmpty()
            bySection.getValue(section) += Entry(
                (co["quantity"] as? JsonPrimitive)?.intOrNull ?: 1, name,
                (card["edition"] as? JsonObject)?.str("editioncode"), card.str("collectorNumber"),
                modifier.equals("Foil", true) || modifier.equals("Etched", true),
            )
        }
        if (bySection.values.all { it.isEmpty() }) return null
        return LinkedDeck(o.str("name") ?: "Archidekt deck", text(bySection.toList()))
    }

    /** "Commander\n1 Atraxa (ONE) 196\n\nDeck\n…" with each section that has cards. */
    private fun text(sections: List<Pair<String, List<Entry>>>): String =
        sections.filter { it.second.isNotEmpty() }.joinToString("\n\n") { (title, entries) ->
            title + "\n" + entries.joinToString("\n") { e ->
                buildString {
                    append(e.qty).append(' ').append(e.name)
                    if (!e.set.isNullOrBlank()) {
                        append(" (").append(e.set.uppercase()).append(')')
                        if (!e.number.isNullOrBlank()) append(' ').append(e.number)
                    }
                    if (e.foil) append(" *F*")
                }
            }
        }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

package com.mtgscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ScryfallException(message: String) : IOException(message)

/**
 * Small client for https://scryfall.com/docs/api.
 * Scryfall asks for a User-Agent, an Accept header, and no more than ~10 requests a second.
 */
class ScryfallApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val base = "https://api.scryfall.com".toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val rateLock = Mutex()
    private var lastRequestAt = 0L

    /** Fuzzy name lookup. Returns null when Scryfall has no match. */
    suspend fun named(fuzzy: String, set: String? = null): ScryfallCard? {
        val url = base.newBuilder().addPathSegments("cards/named")
            .addQueryParameter("fuzzy", fuzzy)
            .apply { if (set != null) addQueryParameter("set", set.lowercase()) }
            .build()
        return getOrNull(url)
    }

    /** Exact printing by set code and collector number. */
    suspend fun bySetAndNumber(set: String, number: String): ScryfallCard? {
        val url = base.newBuilder()
            .addPathSegment("cards")
            .addPathSegment(set.lowercase())
            .addPathSegment(number.lowercase())
            .build()
        return getOrNull(url)
    }

    suspend fun byId(id: String): ScryfallCard? =
        getOrNull(base.newBuilder().addPathSegment("cards").addPathSegment(id).build())

    /** All printings of a card, newest first. */
    suspend fun printings(card: ScryfallCard): List<ScryfallCard> {
        val q = card.oracleId?.let { "oracleid:$it" } ?: "!\"${card.name}\""
        return search("$q include:extras", unique = "prints", order = "released")
    }

    /** All printings of the card with this oracle id (or exact [name] when there's none), newest first. */
    suspend fun printingsOf(oracleId: String?, name: String): List<ScryfallCard> {
        val q = oracleId?.let { "oracleid:$it" } ?: "!\"$name\""
        return search("$q include:extras", unique = "prints", order = "released")
    }

    /** Official rulings for a card, oldest first. Empty when there are none. */
    suspend fun rulings(id: String): List<ScryfallRuling> {
        val url = base.newBuilder().addPathSegment("cards").addPathSegment(id).addPathSegment("rulings").build()
        val body = request(Request.Builder().url(url).build()) ?: return emptyList()
        return json.decodeFromString(RulingList.serializer(), body).data
    }

    suspend fun search(query: String, unique: String = "cards", order: String = "name"): List<ScryfallCard> {
        val url = base.newBuilder().addPathSegments("cards/search")
            .addQueryParameter("q", query)
            .addQueryParameter("unique", unique)
            .addQueryParameter("order", order)
            .build()
        val body = request(Request.Builder().url(url).build()) ?: return emptyList()
        return json.decodeFromString(CardList.serializer(), body).data
    }

    /** One page (up to 175 cards) of a full Scryfall search. Empty when nothing matches. */
    suspend fun searchPage(query: String, order: String = "name", page: Int = 1, unique: String = "cards"): CardList {
        val url = base.newBuilder().addPathSegments("cards/search")
            .addQueryParameter("q", query)
            .addQueryParameter("unique", unique)
            .addQueryParameter("order", order)
            .addQueryParameter("page", page.toString())
            .build()
        val body = request(Request.Builder().url(url).build()) ?: return CardList()
        return json.decodeFromString(CardList.serializer(), body)
    }

    /** Paper sets, newest first. */
    suspend fun sets(): List<ScryfallSet> {
        val body = request(Request.Builder().url(base.newBuilder().addPathSegment("sets").build()).build())
            ?: return emptyList()
        return json.decodeFromString(SetList.serializer(), body).data.filter { !it.digital && it.cardCount > 0 }
    }

    suspend fun autocomplete(text: String): List<String> {
        if (text.length < 2) return emptyList()
        val url = base.newBuilder().addPathSegments("cards/autocomplete")
            .addQueryParameter("q", text).build()
        val body = request(Request.Builder().url(url).build()) ?: return emptyList()
        return json.decodeFromString(Catalog.serializer(), body).data
    }

    /** Fetches up to 75 cards per request by Scryfall id. Used to refresh prices. */
    suspend fun collection(ids: List<String>): List<ScryfallCard> =
        identify(ids.distinct().map { buildJsonObject { put("id", it) } }).filterNotNull()

    /**
     * Looks up many cards at once. Each identifier is a JSON object such as {"id": …},
     * {"set": …, "collector_number": …} or {"name": …}. Returns one entry per identifier, null
     * when Scryfall couldn't find it.
     */
    suspend fun identify(identifiers: List<JsonObject>): List<ScryfallCard?> {
        val out = ArrayList<ScryfallCard?>(identifiers.size)
        for (chunk in identifiers.chunked(75)) {
            val payload = buildJsonObject {
                put("identifiers", buildJsonArray { chunk.forEach { add(it) } })
            }.toString()
            val req = Request.Builder()
                .url(base.newBuilder().addPathSegments("cards/collection").build())
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val body = request(req)
            if (body == null) { repeat(chunk.size) { out += null }; continue }
            val found = json.decodeFromString(CardList.serializer(), body).data
            val pool = found.toMutableList()
            for (ident in chunk) {
                val i = pool.indexOfFirst { matchesIdentifier(ident, it) }
                // Scryfall may answer repeated identifiers once, so reuse an earlier match if needed.
                out += if (i >= 0) pool.removeAt(i) else found.firstOrNull { matchesIdentifier(ident, it) }
            }
        }
        return out
    }

    private fun matchesIdentifier(ident: JsonObject, card: ScryfallCard): Boolean {
        fun field(k: String) = (ident[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
        field("id")?.let { return it == card.id }
        val set = field("set")
        val num = field("collector_number")
        if (set != null && num != null) return set.equals(card.set, true) && num.equals(card.collectorNumber, true)
        val name = field("name") ?: return false
        val names = listOf(card.name) + card.name.split(" // ") + card.cardFaces.orEmpty().map { it.name }
        val nameOk = names.any { it.equals(name, ignoreCase = true) }
        return nameOk && (set == null || set.equals(card.set, true))
    }

    /** Every English card name, about 30k. Used to correct what the camera reads, offline. */
    suspend fun cardNames(): List<String> {
        val body = request(Request.Builder().url(base.newBuilder().addPathSegments("catalog/card-names").build()).build())
            ?: return emptyList()
        return json.decodeFromString(Catalog.serializer(), body).data
    }

    /** Every card matching [query] (all pages, up to [maxPages]). */
    suspend fun searchAll(query: String, unique: String = "cards", maxPages: Int = 6): List<ScryfallCard> {
        val out = mutableListOf<ScryfallCard>()
        var page = 1
        while (page <= maxPages) {
            val list = searchPage(query, page = page, unique = unique)
            out += list.data
            if (!list.hasMore) break
            page++
        }
        return out
    }

    private suspend fun getOrNull(url: HttpUrl): ScryfallCard? {
        val body = request(Request.Builder().url(url).build()) ?: return null
        return json.decodeFromString(ScryfallCard.serializer(), body)
    }

    /** Returns the body, or null on 404. Throws on network errors and other failures. */
    private suspend fun request(base: Request): String? = withContext(Dispatchers.IO) {
        rateLock.withLock {
            val wait = 110 - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        val req = base.newBuilder()
            .header("User-Agent", "MTGScanner/0.1 (personal collection app)")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.isSuccessful -> resp.body?.string()
                resp.code == 404 -> null
                resp.code == 429 -> throw ScryfallException("Scryfall is rate limiting; slow down a moment")
                else -> throw ScryfallException("Scryfall error ${resp.code}")
            }
        }
    }
}

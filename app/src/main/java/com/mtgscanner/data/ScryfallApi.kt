package com.mtgscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    suspend fun collection(ids: List<String>): List<ScryfallCard> {
        val out = mutableListOf<ScryfallCard>()
        for (chunk in ids.distinct().chunked(75)) {
            val payload = buildJsonObject {
                put("identifiers", buildJsonArray {
                    chunk.forEach { add(buildJsonObject { put("id", it) }) }
                })
            }.toString()
            val req = Request.Builder()
                .url(base.newBuilder().addPathSegments("cards/collection").build())
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val body = request(req) ?: continue
            out += json.decodeFromString(CardList.serializer(), body).data
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

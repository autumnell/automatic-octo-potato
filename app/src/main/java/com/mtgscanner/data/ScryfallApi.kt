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

open class ScryfallException(message: String) : IOException(message)

/** Scryfall couldn't understand a search (HTTP 400). [message] is Scryfall's own explanation, fit to show. */
class ScryfallQueryException(message: String) : ScryfallException(message)

/** [name] as an exact-name search term, with quotes escaped (Henzie "Toolbox" Torre). */
fun exactNameQuery(name: String): String = "!\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** One connection pool for the app's HTTP clients; each derives its own timeouts with newBuilder(). */
internal val sharedHttpClient: OkHttpClient by lazy { OkHttpClient() }

/**
 * Small client for https://scryfall.com/docs/api.
 * Scryfall asks for a User-Agent, an Accept header, and no more than ~10 requests a second.
 */
class ScryfallApi(
    private val client: OkHttpClient = sharedHttpClient.newBuilder()
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

    /**
     * Where Scryfall's bulk file of [type] ("default_cards") can be downloaded, or null. Read from the list of
     * bulk files: asking for one by type wants "default-cards" in the address, and "default_cards" there is a 404.
     */
    suspend fun bulkDownloadUri(type: String): String? {
        val url = base.newBuilder().addPathSegments("bulk-data").build()
        val body = request(Request.Builder().url(url).build()) ?: return null
        return bulkUriIn(body, type)
    }

    /** The download address of the bulk file of [type] in Scryfall's list of bulk files ([body]), or null. */
    internal fun bulkUriIn(body: String, type: String): String? {
        val list = (json.parseToJsonElement(body) as? JsonObject)?.get("data") as? kotlinx.serialization.json.JsonArray ?: return null
        val o = list.firstOrNull { ((it as? JsonObject)?.get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content == type } as? JsonObject
        return (o?.get("download_uri") as? kotlinx.serialization.json.JsonPrimitive)?.content
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
    suspend fun printings(card: ScryfallCard): List<ScryfallCard> = printingsOf(card.oracleId, card.name)

    /**
     * All printings of the card with this oracle id (or exact [name] when there's none), newest first. Every page.
     * [narrow] adds search terms ("year>=1997 year<=1999", "cn:241") so only the likely printings come back.
     */
    suspend fun printingsOf(oracleId: String?, name: String, narrow: String = ""): List<ScryfallCard> {
        val q = oracleId?.let { "oracleid:$it" } ?: exactNameQuery(name)
        return searchAll("$q $narrow include:extras".replace("  ", " "), unique = "prints", maxPages = 12, order = "released")
    }

    /** Every card in a set, tokens and extras included, in set order. */
    suspend fun setCards(code: String): List<ScryfallCard> =
        searchAll("e:${code.lowercase()} include:extras", unique = "prints", maxPages = 12, order = "set")

    /** Official rulings for a card, oldest first. Empty when there are none. */
    suspend fun rulings(id: String): List<ScryfallRuling> {
        val url = base.newBuilder().addPathSegment("cards").addPathSegment(id).addPathSegment("rulings").build()
        val body = request(Request.Builder().url(url).build()) ?: return emptyList()
        return decode(RulingList.serializer(), body).data
    }

    suspend fun search(query: String, unique: String = "cards", order: String = "name", dir: String? = null): List<ScryfallCard> {
        val url = base.newBuilder().addPathSegments("cards/search")
            .addQueryParameter("q", query)
            .addQueryParameter("unique", unique)
            .addQueryParameter("order", order)
            .apply { if (dir != null) addQueryParameter("dir", dir) }
            .build()
        val body = request(Request.Builder().url(url).build()) ?: return emptyList()
        return decode(CardList.serializer(), body).data
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
        return decode(CardList.serializer(), body)
    }

    /**
     * Printings in other languages whose printed name contains [text] ("稲妻"), newest first: a search with
     * include_multilingual, kept to [lang] (a Scryfall code such as "ja") when it's known. Empty when none match.
     */
    suspend fun searchPrinted(text: String, lang: String? = null): List<ScryfallCard> {
        val quoted = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val url = base.newBuilder().addPathSegments("cards/search")
            .addQueryParameter("q", "lang:${lang ?: "any"} $quoted")
            .addQueryParameter("unique", "prints")
            .addQueryParameter("order", "released")
            .addQueryParameter("include_multilingual", "true")
            .build()
        val body = request(Request.Builder().url(url).build()) ?: return emptyList()
        return decode(CardList.serializer(), body).data
    }

    /** Paper sets, newest first. */
    suspend fun sets(): List<ScryfallSet> {
        val body = request(Request.Builder().url(base.newBuilder().addPathSegment("sets").build()).build())
            ?: return emptyList()
        return decode(SetList.serializer(), body).data.filter { !it.digital && it.cardCount > 0 }
    }

    /** Name suggestions; [extras] adds token and emblem names. */
    suspend fun autocomplete(text: String, extras: Boolean = false): List<String> {
        if (text.length < 2) return emptyList()
        val url = base.newBuilder().addPathSegments("cards/autocomplete")
            .addQueryParameter("q", text)
            .apply { if (extras) addQueryParameter("include_extras", "true") }
            .build()
        val body = request(Request.Builder().url(url).build()) ?: return emptyList()
        return decode(Catalog.serializer(), body).data
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
            val found = decode(CardList.serializer(), body).data
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
        // The full name, either half, or the same with other spacing ("Fire/Ice") or accents.
        val names = listOf(card.name) + card.name.split(" // ") + card.cardFaces.orEmpty().map { it.name }
        val want = com.mtgscanner.scan.CardTextParser.normalize(com.mtgscanner.util.CardNames.full(name))
        val nameOk = names.any { it.equals(name, ignoreCase = true) || com.mtgscanner.scan.CardTextParser.normalize(it) == want }
        return nameOk && (set == null || set.equals(card.set, true))
    }

    /** Every English card name, about 30k. Used to correct what the camera reads, offline. */
    suspend fun cardNames(): List<String> {
        val body = request(Request.Builder().url(base.newBuilder().addPathSegments("catalog/card-names").build()).build())
            ?: return emptyList()
        return decode(Catalog.serializer(), body).data
    }

    /** Every token and emblem name (the card names catalog leaves them out), for reading tokens with the camera. */
    suspend fun tokenNames(): List<String> =
        searchAll("(t:token or t:emblem) include:extras", maxPages = 40).flatMap { c ->
            listOf(c.name) + c.cardFaces.orEmpty().map { it.name }
        }.filter { it.isNotBlank() }.distinct()

    /** Every card matching [query] (all pages, up to [maxPages]). */
    suspend fun searchAll(query: String, unique: String = "cards", maxPages: Int = 6, order: String = "name"): List<ScryfallCard> {
        val out = mutableListOf<ScryfallCard>()
        var page = 1
        while (page <= maxPages) {
            val list = searchPage(query, order = order, page = page, unique = unique)
            out += list.data
            if (!list.hasMore) break
            page++
        }
        return out
    }

    private suspend fun getOrNull(url: HttpUrl): ScryfallCard? {
        val body = request(Request.Builder().url(url).build()) ?: return null
        return decode(ScryfallCard.serializer(), body)
    }

    /** Big responses (175 full cards) take a while to parse, so it never happens on the main thread. */
    private suspend fun <T> decode(serializer: kotlinx.serialization.DeserializationStrategy<T>, body: String): T =
        withContext(Dispatchers.Default) { json.decodeFromString(serializer, body) }

    /** Scryfall's explanation in an error response, like "All of your terms were ignored." */
    private fun errorDetails(body: String?): String? = runCatching {
        val o = json.parseToJsonElement(body ?: return null) as? JsonObject ?: return null
        val details = (o["details"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val warnings = (o["warnings"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
        listOfNotNull(details?.takeIf { it.isNotBlank() }).plus(warnings).firstOrNull()
    }.getOrNull()

    /**
     * Returns the body, or null on 404. Throws on network errors and other failures. "Too many requests" (429)
     * and server errors are tried again twice, after Scryfall's Retry-After or a growing pause.
     */
    private suspend fun request(base: Request): String? = withContext(Dispatchers.IO) {
        val req = base.newBuilder()
            .header("User-Agent", "MTGScanner/0.1 (personal collection app)")
            .header("Accept", "application/json")
            .build()
        var attempt = 0
        var r = fetchOnce(req, attempt)
        while (r.retryAfterMs > 0) {
            delay(r.retryAfterMs)
            attempt++
            r = fetchOnce(req, attempt)
        }
        r.body
    }

    /** One response: its body (null on 404), or a pause before asking again when [retryAfterMs] > 0. */
    private class Attempt(val body: String?, val retryAfterMs: Long = 0)

    private suspend fun fetchOnce(req: Request, attempt: Int): Attempt {
        rateLock.withLock {
            val wait = 110 - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        return client.newCall(req).execute().use { resp ->
            when {
                resp.isSuccessful -> Attempt(resp.body?.string())
                resp.code == 404 -> Attempt(null)
                resp.code == 400 -> throw ScryfallQueryException(errorDetails(resp.body?.string()) ?: "Scryfall couldn't read that search")
                (resp.code == 429 || resp.code >= 500) && attempt < 2 -> Attempt(
                    null,
                    resp.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)?.coerceIn(500L, 10_000L) ?: (1000L shl attempt),
                )
                resp.code == 429 -> throw ScryfallException("Scryfall is rate limiting; slow down a moment")
                else -> throw ScryfallException("Scryfall error ${resp.code}")
            }
        }
    }
}

/**
 * Runs [block], trying again after a pause (2 s, then 6 s) when it fails with a network error.
 * For background jobs; screens should fail fast instead.
 */
internal suspend fun <T> withBackoff(tries: Int = 3, block: suspend () -> T): T {
    var pause = 2_000L
    repeat(tries - 1) {
        try {
            return block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ScryfallQueryException) {
            throw e
        } catch (e: IOException) {
            delay(pause)
            pause *= 3
        }
    }
    return block()
}

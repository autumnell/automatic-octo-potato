package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Optional per-condition prices from JustTCG (https://justtcg.com), using the person's own free key.
 * Without a key the app uses Scryfall's near-mint price.
 */
object JustTcg {
    /** The free plan's monthly allowance; shown in Settings so the person can pace refreshes. */
    const val MONTHLY_LIMIT = 1000
    private const val BATCH = 20
    private const val URL = "https://api.justtcg.com/v1/cards"

    private var prefs: SharedPreferences? = null

    var apiKey by mutableStateOf("")
        private set
    var usedThisMonth by mutableIntStateOf(0)
        private set

    val enabled: Boolean get() = apiKey.isNotBlank()

    fun init(context: Context) {
        val p = context.getSharedPreferences("justtcg", Context.MODE_PRIVATE)
        prefs = p
        apiKey = p.getString("key", "").orEmpty()
        usedThisMonth = if (p.getString("month", "") == YearMonth.now().toString()) p.getInt("used", 0) else 0
    }

    fun updateKey(key: String) {
        apiKey = key.trim()
        prefs?.edit()?.putString("key", apiKey)?.apply()
    }

    /** When per-condition prices were last refreshed for the whole collection. */
    var lastFullRefresh: Long
        get() = prefs?.getLong("fullAt", 0L) ?: 0L
        set(v) { prefs?.edit()?.putLong("fullAt", v)?.apply() }

    private fun countRequest() {
        val month = YearMonth.now().toString()
        val p = prefs
        val used = if (p?.getString("month", "") == month) (p.getInt("used", 0) + 1) else 1
        usedThisMonth = used
        p?.edit()?.putString("month", month)?.putInt("used", used)?.apply()
    }

    open class KeyException(message: String) : Exception(message)

    /**
     * JustTCG said "too many requests" partway through. [partial] holds the prices fetched before that,
     * which are still good to save. The weekly refresh waits a week before trying again.
     */
    class LimitException(val partial: Map<String, List<Variant>>) : KeyException("JustTCG limit reached for now")

    /** One sellable version of a card: condition (NM, LP…), foil or not, and its price in dollars. */
    data class Variant(val condition: String, val foil: Boolean, val price: Double)

    private val client = sharedHttpClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Prices for each Scryfall id, every condition and finish JustTCG lists. Cards it doesn't know are left out.
     * Uses one request per [BATCH] cards; [onBatch] gets each batch's prices as they arrive, so they can be
     * saved even if a later batch fails. Throws [LimitException] (with what was fetched) when the limit is hit.
     */
    suspend fun prices(
        scryfallIds: List<String>,
        key: String = apiKey,
        onBatch: (suspend (Map<String, List<Variant>>) -> Unit)? = null,
    ): Map<String, List<Variant>> = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw KeyException("No JustTCG key")
        val out = HashMap<String, List<Variant>>()
        for (chunk in scryfallIds.distinct().chunked(BATCH)) {
            val body = buildJsonArray { chunk.forEach { add(buildJsonObject { put("scryfallId", it) }) } }.toString()
            val req = Request.Builder()
                .url(URL)
                .header("x-api-key", key)
                .header("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val text = client.newCall(req).execute().use { resp ->
                countRequest()
                when (resp.code) {
                    401, 403 -> throw KeyException("JustTCG didn't accept that key")
                    429 -> {
                        // Out of lookups: the weekly refresh waits until next week instead of retrying every launch.
                        lastFullRefresh = System.currentTimeMillis()
                        throw LimitException(HashMap(out))
                    }
                }
                if (!resp.isSuccessful) throw java.io.IOException("JustTCG error ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val batch = parse(json.parseToJsonElement(text), chunk)
            out += batch
            onBatch?.invoke(batch)
        }
        out
    }

    /** Reads cards out of a response. Matches them back by Scryfall id, or by order when ids aren't echoed. */
    internal fun parse(root: JsonElement, asked: List<String>): Map<String, List<Variant>> {
        val cards = cardObjects(root)
        val out = HashMap<String, List<Variant>>()
        cards.forEachIndexed { i, card ->
            val id = card.string("scryfallId") ?: card.string("scryfall_id")
                ?: if (cards.size == asked.size) asked[i] else null
            if (id != null) out[id] = variants(card)
        }
        return out
    }

    private fun cardObjects(e: JsonElement): List<JsonObject> = when (e) {
        is JsonObject -> if ("variants" in e) listOf(e) else (e["data"] ?: e["cards"] ?: e["results"])?.let(::cardObjects).orEmpty()
        is JsonArray -> e.flatMap(::cardObjects)
        else -> emptyList()
    }

    private fun variants(card: JsonObject): List<Variant> =
        (card["variants"] as? JsonArray).orEmpty().mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val cond = conditionCode(o.string("condition") ?: return@mapNotNull null) ?: return@mapNotNull null
            val price = o.number("price") ?: o.number("marketPrice") ?: o.number("market_price") ?: return@mapNotNull null
            val printing = o.string("printing").orEmpty()
            Variant(cond, printing.contains("foil", ignoreCase = true), price)
        }

    /** JustTCG writes conditions out in full; the app uses short codes. */
    fun conditionCode(s: String): String? = when (s.trim().lowercase()) {
        "near mint", "nm", "mint" -> "NM"
        "lightly played", "lp" -> "LP"
        "moderately played", "mp" -> "MP"
        "heavily played", "hp" -> "HP"
        "damaged", "dmg" -> "DMG"
        else -> null
    }

    /** The price for a copy in [condition], foil or not, from a card's [variants]. */
    fun pick(variants: List<Variant>, condition: String, foil: Boolean): Double? =
        variants.firstOrNull { it.condition == condition && it.foil == foil }?.price

    private fun JsonObject.string(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.number(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
}

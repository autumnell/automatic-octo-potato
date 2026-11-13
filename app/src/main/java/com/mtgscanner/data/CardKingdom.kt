package com.mtgscanner.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Card Kingdom's buylist: what the store pays for a card. Their public price list is downloaded
 * at most once a day, only when a screen that shows buy prices opens ([init]), and kept as a small
 * "scryfallId, foil, price" file. Everything fails quietly: with no list, [buyPrice] just returns null.
 */
object CardKingdom {
    private const val URL = "https://api.cardkingdom.com/api/v2/pricelist"
    private const val FILE = "ck-buylist.tsv"
    private const val DAY = 24 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val client = sharedHttpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.MINUTES)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var app: Context? = null
    @Volatile private var prices: Map<String, Double> = emptyMap()
    @Volatile private var started = false

    /** Goes up each time a list is loaded, so screens reading it redraw. */
    var version by mutableIntStateOf(0)
        private set

    @Volatile private var loadStarted = false

    /** Loads the saved list and refreshes it when it's a day old. For screens that show buy prices. Safe to call often. */
    fun init(context: Context) {
        if (started) return
        started = true
        loadSaved(context)
        scope.launch { runCatching { refreshIfStale() } }
    }

    /** Loads the saved list without downloading anything, for screens where buy prices are only an extra. */
    fun loadSaved(context: Context) {
        app = context.applicationContext
        if (loadStarted) return
        loadStarted = true
        scope.launch { runCatching { load() } }
    }

    /**
     * What Card Kingdom pays for one copy, or null when unknown or not buying.
     * Reading it in a composable redraws once the list loads.
     */
    fun buyPrice(scryfallId: String, foil: Boolean): Double? {
        if (version < 0) return null // reads the state so Compose callers update after a load
        return prices[key(scryfallId, foil)]
    }

    private fun key(id: String, foil: Boolean) = id.lowercase() + if (foil) "|f" else "|n"

    private fun file(): File? = app?.filesDir?.let { File(it, FILE) }

    private suspend fun load() {
        lock.withLock { read() }
    }

    private fun read() {
        val f = file() ?: return
        if (!f.exists()) return
        val map = HashMap<String, Double>()
        f.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split('\t')
                if (parts.size == 3) parts[2].toDoubleOrNull()?.let { map[key(parts[0], parts[1] == "f")] = it }
            }
        }
        prices = map
        version++
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun refreshIfStale() {
        val ctx = app ?: return
        val prefs = ctx.getSharedPreferences("cardkingdom", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        // At most one try a day, even when it fails, so a blocked site isn't hit over and over.
        if (now - prefs.getLong("triedAt", 0L) < DAY) return
        prefs.edit().putLong("triedAt", now).apply()
        try {
            val req = Request.Builder()
                .url(URL)
                .header("User-Agent", com.mtgscanner.util.UserAgent.value)
                .header("Accept", "application/json")
                .build()
            val out = file() ?: return
            val part = File(out.parentFile, "$FILE.part")
            // Read straight from the stream, one row at a time: the list is large, and only three fields per row are kept.
            val written = client.newCall(req).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) 0
                else part.bufferedWriter().use { w ->
                    var n = 0
                    body.byteStream().buffered().use { input ->
                        json.decodeFromStream(PriceListReader { r ->
                            row(r)?.let { (id, foil, price) -> w.write("$id\t${if (foil) "f" else "n"}\t$price\n"); n++ }
                        }, input)
                    }
                    n
                }
            }
            if (written == 0) { part.delete(); return }
            if (!part.renameTo(out)) { out.delete(); part.renameTo(out) }
            load()
        } catch (_: Throwable) {
            // Offline, blocked, or a format change: keep the old list.
        }
    }

    /** One row of the price list. Values are read loosely: numbers and booleans may come as strings. */
    @Serializable
    internal class Row(
        @SerialName("scryfall_id") val scryfallId: JsonPrimitive? = null,
        @SerialName("is_foil") val isFoil: JsonPrimitive? = null,
        @SerialName("price_buy") val priceBuy: JsonPrimitive? = null,
        @SerialName("qty_buying") val qtyBuying: JsonPrimitive? = null,
    )

    /** (scryfallId, foil, buy price) for every card Card Kingdom is buying. */
    internal fun rows(data: List<Row>): List<Triple<String, Boolean, Double>> = data.mapNotNull(::row)

    /** (scryfallId, foil, buy price), or null when Card Kingdom isn't buying it. */
    internal fun row(r: Row): Triple<String, Boolean, Double>? {
        val id = r.scryfallId?.takeIf { it.isString }?.content?.trim()
        if (id.isNullOrEmpty()) return null
        val price = r.priceBuy.num() ?: return null
        if (price <= 0.0) return null
        val qty = r.qtyBuying.num()
        if (qty != null && qty <= 0.0) return null
        return Triple(id, r.isFoil.bool() ?: false, price)
    }

    /**
     * Reads `{"data": [row, row, …]}` handing each row to [onRow] as it's read, so the whole list is
     * never in memory at once. Other keys are skipped.
     */
    internal class PriceListReader(private val onRow: (Row) -> Unit) : DeserializationStrategy<Unit> {
        private val rowList = ListSerializer(Row.serializer()).descriptor
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CardKingdomPriceList") {
            element("data", rowList)
        }

        override fun deserialize(decoder: Decoder) {
            val obj = decoder.beginStructure(descriptor)
            while (true) {
                when (val i = obj.decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> obj.decodeSerializableElement(descriptor, i, RowStream(rowList, onRow))
                    else -> error("Unexpected element $i")
                }
            }
            obj.endStructure(descriptor)
        }
    }

    private class RowStream(override val descriptor: SerialDescriptor, private val onRow: (Row) -> Unit) : DeserializationStrategy<Unit> {
        override fun deserialize(decoder: Decoder) {
            val list = decoder.beginStructure(descriptor)
            while (true) {
                val i = list.decodeElementIndex(descriptor)
                if (i == CompositeDecoder.DECODE_DONE) break
                onRow(list.decodeSerializableElement(descriptor, i, Row.serializer()))
            }
            list.endStructure(descriptor)
        }
    }

    private fun JsonPrimitive?.num(): Double? = this?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
    private fun JsonPrimitive?.bool(): Boolean? = this?.let { it.booleanOrNull ?: it.content.toIntOrNull()?.let { n -> n != 0 } }
}

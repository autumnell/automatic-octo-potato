package com.mtgscanner.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A deck's salt: the sum over its cards, and its saltiest few. */
data class DeckSalt(val total: Double, val top: List<Pair<String, Double>>) {
    /** "Salt 12.4 · Stasis, Armageddon". */
    val label: String get() = "Salt %.1f".format(total) + if (top.isNotEmpty()) " · " + top.joinToString(", ") { it.first } else ""
}

/** Reading EDHREC's salt pages, and a deck's total. Pure, for tests. */
object SaltMath {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Every (name, salt) in one EDHREC salt page, wherever its card list sits. */
    fun parse(text: String): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> {
                    val name = (e["name"])?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                    val salt = (e["salt"])?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }
                    if (name != null && salt != null) out += name to salt
                    else e.values.forEach(::walk)
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        runCatching { walk(json.parseToJsonElement(text)) }
        return out
    }

    /**
     * The salt of a deck's played cards ([DeckCheck.PLAYED]), each card once as EDHREC counts it, with its
     * [top] saltiest. Cards missing from [salt] (by [CardNames.key]) count 0. Null when no card has a score.
     */
    fun deck(lines: List<DeckLine>, salt: Map<String, Double>, top: Int = 3): DeckSalt? {
        val scored = lines.filter { it.section in DeckCheck.PLAYED }
            .distinctBy { CardNames.key(it.name) }
            .mapNotNull { l -> salt[CardNames.key(l.name)]?.let { CardNames.full(l.name) to it } }
        if (scored.isEmpty()) return null
        return DeckSalt(scored.sumOf { it.second }, scored.sortedByDescending { it.second }.take(top).filter { it.second >= 1.0 })
    }
}

/**
 * EDHREC's salt scores (how much a card annoys the other players, 0 to about 3) for its saltiest [PAGES] hundred
 * cards, kept on the phone and fetched again weekly. Cards not listed aren't salty enough to show.
 */
object SaltScores {
    private const val PAGES = 5
    private const val DAYS = 7
    private val client by lazy { sharedHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    /** Salt by [CardNames.key]; empty until loaded. */
    var scores by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    @Volatile private var loading = false

    fun of(name: String): Double? = scores[CardNames.key(name)]

    /** Loads the saved scores, fetching them first when they're missing or a week old. */
    suspend fun load(ctx: Context) {
        if (loading || scores.isNotEmpty()) return
        loading = true
        try {
            val f = File(ctx.applicationContext.filesDir, "salt.tsv")
            val stale = !f.exists() || System.currentTimeMillis() - f.lastModified() > DAYS * 86_400_000L
            val fresh = if (stale) withContext(Dispatchers.IO) { fetch() } else null
            if (!fresh.isNullOrEmpty()) withContext(Dispatchers.IO) {
                runCatching { f.writeText(fresh.joinToString("\n") { "${it.first}\t${it.second}" }) }
            }
            val pairs = fresh?.takeIf { it.isNotEmpty() } ?: withContext(Dispatchers.IO) {
                runCatching {
                    f.readLines().mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { (n, s) -> s.toDoubleOrNull()?.let { n to it } } }
                }.getOrDefault(emptyList())
            }
            scores = pairs.associate { CardNames.key(it.first) to it.second }
        } finally {
            loading = false
        }
    }

    private fun fetch(): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        for (page in 0 until PAGES) {
            val path = if (page == 0) "top/salt" else "top/salt--$page"
            val req = Request.Builder().url("https://json.edhrec.com/pages/$path.json")
                .header("User-Agent", "MTGScanner/0.1 (personal collection app)").build()
            val text = runCatching { client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull() ?: break
            out += SaltMath.parse(text)
        }
        return out
    }
}

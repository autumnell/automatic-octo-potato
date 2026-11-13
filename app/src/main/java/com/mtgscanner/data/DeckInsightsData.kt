package com.mtgscanner.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.Request

/** EDHREC's average deck for a commander: its commanders and its other cards with their copies. */
data class AverageDeck(val commanders: List<String>, val cards: List<Pair<String, Int>>)

/** Reading EDHREC's average deck page. Pure, for tests. */
object AverageDeckMath {
    private val json = Json { ignoreUnknownKeys = true }

    /** deck.commander is a list of names; deck.cards maps a card type to [name, copies] pairs. */
    fun parse(text: String): AverageDeck? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val deck = root["deck"] as? JsonObject ?: return null
        val leads = (deck["commander"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val cards = (deck["cards"] as? JsonObject)?.values.orEmpty().flatMap { v ->
            (v as? JsonArray).orEmpty().mapNotNull { pair ->
                val a = pair as? JsonArray ?: return@mapNotNull null
                val name = (a.getOrNull(0) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
                name to ((a.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: 1)
            }
        }
        return if (cards.isEmpty()) null else AverageDeck(leads, cards)
    }
}

private val insightClient by lazy { sharedHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

/** EDHREC's average deck for [commanders]; null when it can't be reached or has none. */
suspend fun edhrecAverageDeck(commanders: List<String>): AverageDeck? = withContext(Dispatchers.IO) {
    val slugs = commanders.map(Edhrec::slug).filter { it.isNotEmpty() }.distinct().take(2).sorted()
    if (slugs.isEmpty()) return@withContext null
    val req = Request.Builder().url("https://json.edhrec.com/pages/average-decks/${slugs.joinToString("-")}.json")
        .header("User-Agent", com.mtgscanner.util.UserAgent.value).build()
    runCatching { insightClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }
        .getOrNull()?.let(AverageDeckMath::parse)
}

/** Wizards' Game Changers, from Scryfall (is:gamechanger), kept for the app's lifetime. */
object GameChangers {
    private val lock = Mutex()
    @Volatile private var names: Set<String>? = null

    /** Scryfall's list, or null when it can't be reached (the caller falls back to the built-in list). */
    suspend fun fetch(api: ScryfallApi): Set<String>? = lock.withLock {
        names ?: runCatching { api.searchAll("is:gamechanger", maxPages = 2).map { it.name }.toSet() }
            .getOrNull()?.takeIf { it.size >= 20 }?.also { names = it }
    }
}

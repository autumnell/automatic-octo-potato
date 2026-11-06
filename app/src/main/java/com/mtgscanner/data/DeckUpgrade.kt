package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.util.CardNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A card you own that would suit a commander deck: how often EDHREC decks play it (0..1, null when unknown), and where it is. */
data class UpgradeIdea(val name: String, val share: Double?, val copies: Int, val where: List<String>, val imageUrl: String?)

/** Upgrade a deck from your boxes. Pure parts, for tests. */
object DeckUpgradeMath {
    /**
     * Card names on an EDHREC commander page with the share of decks that play them, in page order.
     * Read loosely: any object with a "name" and an "inclusion" (decks playing it) counts; "potential_decks"
     * turns that into a share. Names seen twice keep their first entry.
     */
    fun edhrecCards(json: String): List<Pair<String, Double?>> {
        val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, Pair<String, Double?>>()
        fun num(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull
        fun walk(e: JsonElement, depth: Int) {
            if (depth > 12) return
            when (e) {
                is JsonObject -> {
                    val name = (e["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val inc = num(e, "inclusion") ?: num(e, "num_decks")
                    if (name != null && inc != null) {
                        val pot = num(e, "potential_decks")
                        val share = if (pot != null && pot > 0) (inc / pot).coerceIn(0.0, 1.0) else null
                        val k = CardNames.key(name)
                        if (k !in out) out[k] = name to share
                    }
                    e.values.forEach { walk(it, depth + 1) }
                }
                is JsonArray -> e.forEach { walk(it, depth + 1) }
                else -> {}
            }
        }
        walk(root, 0)
        return out.values.toList()
    }

    /** Color identity letters [card] needs fit inside [identity] ("" is colorless and fits any deck). */
    fun fits(card: String, identity: String): Boolean = card.uppercase().all { it !in "WUBRG" || it in identity.uppercase() }

    /**
     * Owned cards from [ranked] (best first) that aren't in the deck and fit its colors, at most [limit].
     * [where] names a row's box. Basic lands are left out.
     */
    fun ideas(
        ranked: List<Pair<String, Double?>>,
        owned: List<CollectionCard>,
        inDeck: Set<String>,
        identity: String,
        where: (CollectionCard) -> String?,
        limit: Int = 60,
    ): List<UpgradeIdea> {
        val byName = owned.filter { it.quantity > 0 && !it.typeLine.startsWith("Basic Land") }.groupBy { CardNames.key(it.name) }
        return ranked.asSequence()
            .map { (n, share) -> Triple(CardNames.key(n), n, share) }
            .filter { (k) -> k !in inDeck }
            .mapNotNull { (k, _, share) ->
                val rows = byName[k] ?: return@mapNotNull null
                if (!fits(rows.first().colorIdentity, identity)) return@mapNotNull null
                UpgradeIdea(
                    name = rows.first().name,
                    share = share,
                    copies = rows.sumOf { it.quantity },
                    where = rows.mapNotNull(where).distinct(),
                    imageUrl = rows.first().smallImageUrl ?: rows.first().imageUrl,
                )
            }
            .take(limit).toList()
    }
}

private val edhrecClient by lazy { sharedHttpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

/** The JSON behind an EDHREC commander page; null when it can't be read. */
internal suspend fun edhrecPage(commanders: List<String>): String? = withContext(Dispatchers.IO) {
    val slugs = commanders.map(Edhrec::slug).filter { it.isNotEmpty() }.distinct().take(2).sorted()
    if (slugs.isEmpty()) return@withContext null
    val req = Request.Builder().url("https://json.edhrec.com/pages/commanders/${slugs.joinToString("-")}.json")
        .header("User-Agent", "MTGScanner/0.1 (personal collection app)").build()
    runCatching { edhrecClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
}

/**
 * Cards you own that EDHREC decks for [commanders] play and [deckNames] doesn't have yet, most played first.
 * Without EDHREC, Scryfall's popularity order for the deck's colors stands in. Throws when neither can be reached.
 */
suspend fun CollectionRepository.upgradeIdeas(commanders: List<String>, identity: String, deckNames: Set<String>): List<UpgradeIdea> {
    val inDeck = deckNames.map(CardNames::key).toSet() + commanders.map(CardNames::key)
    val owned = observeAll().first()
    val places = dao.allPlaces().associateBy { it.id }
    val where = { c: CollectionCard -> places[c.boxId]?.let { b -> if (b.deckId != 0L) "Deck: ${b.name}" else b.name } }
    var ranked = edhrecPage(commanders)?.let(DeckUpgradeMath::edhrecCards).orEmpty()
    if (ranked.size < 20) {
        val colors = identity.lowercase().ifEmpty { "c" }
        ranked = api.searchAll("id<=$colors f:commander -t:basic game:paper", maxPages = 4, order = "edhrec")
            .map { it.name to null }
    }
    return withContext(Dispatchers.Default) { DeckUpgradeMath.ideas(ranked, owned, inDeck, identity, where) }
}

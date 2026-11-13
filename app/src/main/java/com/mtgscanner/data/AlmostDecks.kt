package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.util.CardNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A Commander deck you could nearly build from your collection: EDHREC's most played cards for [commander]. */
data class AlmostDeck(
    val commander: String,
    /** You own the commander itself. */
    val ownCommander: Boolean,
    /** The deck's cards (not the commander), most played first. */
    val cards: List<String>,
    /** Which of [cards] you own (by name, any printing). */
    val owned: Set<String>,
) {
    val missing: List<String> get() = cards.filter { CardNames.key(it) !in owned }
    val share: Double get() = if (cards.isEmpty()) 0.0 else (cards.size - missing.size).toDouble() / cards.size

    /** The deck as a list to save: the commander, then one of each card. */
    fun asText(): String = "Commander\n1 $commander\n\nDeck\n" + cards.joinToString("\n") { "1 $it" }
}

/** Pure parts of [almostDecks], for tests. */
object AlmostDeckMath {
    /** Cards in a typical deck: the most played, at most this many (99 with the commander makes 100). */
    const val DECK_SIZE = 99
    /** Cards fewer decks than this play aren't part of the typical deck. */
    const val MIN_SHARE = 0.15

    /**
     * The typical deck for [commander] from its EDHREC [ranked] cards (most played first, with the share of decks
     * that play each): the commander, basic lands and rarely played cards left out.
     */
    fun typical(commander: String, ranked: List<Pair<String, Double?>>): List<String> {
        val skip = CardNames.key(commander)
        return ranked.asSequence()
            .filter { (n, share) -> CardNames.key(n) != skip && share != null && share >= MIN_SHARE && n !in BASICS }
            .map { it.first }
            .distinctBy(CardNames::key)
            .take(DECK_SIZE).toList()
    }

    /** [decks] best first: most owned, then owning the commander. Decks of fewer than 40 cards are left out. */
    fun rank(decks: List<AlmostDeck>): List<AlmostDeck> =
        decks.filter { it.cards.size >= 40 }.sortedWith(compareByDescending<AlmostDeck> { it.share }.thenByDescending { it.ownCommander })

    /** Legendary creatures (and planeswalkers that say they can be your commander) in [owned]: the commanders you have. */
    fun ownedCommanders(owned: List<CollectionCard>): List<String> = owned.asSequence()
        .filter { c ->
            val t = c.typeLine
            ("Legendary" in t && "Creature" in t) || c.oracleText.contains("can be your commander", ignoreCase = true)
        }
        .sortedByDescending { it.quantity }
        .map { it.name }.distinctBy(CardNames::key).toList()

    private val BASICS = setOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes")
}

private val almostClient by lazy { sharedHttpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

private suspend fun edhrecJson(path: String): String? = withContext(Dispatchers.IO) {
    val req = Request.Builder().url("https://json.edhrec.com/pages/$path.json")
        .header("User-Agent", com.mtgscanner.util.UserAgent.value).build()
    runCatching { almostClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
}

/**
 * Commander decks you could nearly build from what you own, best first: for the commanders you own and the week's
 * most popular ones on EDHREC, each one's typical deck and how much of it you have. [onProgress] gets 0 to 1.
 * Empty when EDHREC can't be reached.
 */
suspend fun CollectionRepository.almostDecks(onProgress: (Float) -> Unit = {}): List<AlmostDeck> = coroutineScope {
    val owned = observeAll().first()
    val ownedKeys = owned.filter { it.quantity > 0 }.map { CardNames.key(it.name) }.toHashSet()
    val mine = AlmostDeckMath.ownedCommanders(owned).take(MAX_MINE)
    val popular = edhrecJson("commanders/week")?.let(DeckUpgradeMath::edhrecCards).orEmpty().map { it.first }.take(MAX_POPULAR)
    val commanders = (mine + popular).distinctBy(CardNames::key)
    if (commanders.isEmpty()) return@coroutineScope emptyList()
    val gate = Semaphore(4)
    var done = 0
    val decks = commanders.map { name ->
        async {
            gate.withPermit {
                val page = edhrecJson("commanders/${Edhrec.slug(name)}")
                synchronized(this@coroutineScope) { done++; onProgress(done.toFloat() / commanders.size) }
                val cards = page?.let { AlmostDeckMath.typical(name, DeckUpgradeMath.edhrecCards(it)) } ?: return@withPermit null
                AlmostDeck(name, CardNames.key(name) in ownedKeys, cards, cards.map(CardNames::key).filter { it in ownedKeys }.toSet())
            }
        }
    }.awaitAll().filterNotNull()
    AlmostDeckMath.rank(decks)
}

/** Commanders you own looked at, at most. */
private const val MAX_MINE = 25
/** Popular commanders looked at, at most. */
private const val MAX_POPULAR = 25

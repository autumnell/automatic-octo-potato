package com.mtgscanner.data

import com.mtgscanner.util.CardNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A card in a commander deck that EDHREC's decks for the same commander rarely play: [share] of decks (0..1; null
 * when the card isn't on EDHREC's page at all) and its [synergy] with the commander.
 */
data class CutIdea(val name: String, val share: Double?, val synergy: Double?, val typeLine: String) {
    val onPage: Boolean get() = share != null
}

/** "What to cut": the opposite of Upgrade. Pure, for tests. */
object DeckCutMath {
    private val BASICS = setOf("plains", "island", "swamp", "mountain", "forest", "wastes")

    /**
     * The deck's cards, least played by EDHREC's decks first: cards not on the page at all, then by share, then by
     * synergy. Commanders and basic lands are never offered. [deck] is name to type line; [shares] and [synergy] are
     * by [CardNames.key].
     */
    fun cuts(
        deck: List<Pair<String, String>>, commanders: List<String>, shares: Map<String, Double?>, synergy: Map<String, Double?>, limit: Int = 30,
    ): List<CutIdea> {
        val leads = commanders.map(CardNames::key).toSet()
        return deck.asSequence()
            .distinctBy { CardNames.key(it.first) }
            .filter { (n, type) -> CardNames.key(n) !in leads && n.lowercase() !in BASICS && !type.startsWith("Basic Land") }
            .map { (n, type) -> val k = CardNames.key(n); CutIdea(n, shares[k], synergy[k], type) }
            .sortedWith(compareBy<CutIdea>({ it.onPage }, { it.share ?: 0.0 }, { it.synergy ?: 0.0 }))
            .take(limit)
            .toList()
    }
}

/**
 * The cards of [deck] (name to type line) that EDHREC's decks for [commanders] play least. Null when EDHREC can't be
 * reached, or has too little on the commander to say.
 */
suspend fun cutIdeas(commanders: List<String>, deck: List<Pair<String, String>>): List<CutIdea>? {
    val page = edhrecPage(commanders) ?: return null
    return withContext(Dispatchers.Default) {
        val shares = DeckUpgradeMath.edhrecCards(page)
        if (shares.size < 20) return@withContext null
        val synergy = HashMap<String, Double?>()
        DeckIdeasMath.edhrecGroups(page).forEach { g -> g.cards.forEach { synergy.putIfAbsent(CardNames.key(it.name), it.synergy) } }
        DeckCutMath.cuts(deck, commanders, shares.associate { CardNames.key(it.first) to (it.second ?: 0.0) }, synergy)
    }
}

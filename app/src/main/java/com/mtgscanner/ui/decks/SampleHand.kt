package com.mtgscanner.ui.decks

import kotlin.random.Random

/** A dealt hand: the cards in hand, the rest of the library in order, and how many mulligans were taken. */
data class SampleHand<T>(val hand: List<T>, val library: List<T>, val mulligans: Int = 0) {
    val canDraw: Boolean get() = library.isNotEmpty()
}

/** Shuffle, deal 7, mulligan and draw for a deck's sample hand; pure so it can be tested with a seeded [Random]. */
object SampleHandMath {
    const val HAND = 7

    /** One entry per copy: ("Island", 3) becomes three Islands. */
    fun <T> copies(lines: List<Pair<T, Int>>): List<T> = lines.flatMap { (c, n) -> List(n.coerceAtLeast(0)) { c } }

    /** A fresh shuffle of [deck] with the top [HAND] in hand (fewer when the deck is smaller). */
    fun <T> deal(deck: List<T>, random: Random, mulligans: Int = 0): SampleHand<T> {
        val shuffled = deck.shuffled(random)
        val n = minOf(HAND, shuffled.size)
        return SampleHand(shuffled.take(n), shuffled.drop(n), mulligans)
    }

    /** Shuffles everything back and deals a new 7, counting one more mulligan. */
    fun <T> mulligan(state: SampleHand<T>, random: Random): SampleHand<T> =
        deal(state.hand + state.library, random, state.mulligans + 1)

    /** The next card from the library into the hand; unchanged when the library is empty. */
    fun <T> draw(state: SampleHand<T>): SampleHand<T> {
        if (state.library.isEmpty()) return state
        return state.copy(hand = state.hand + state.library.first(), library = state.library.drop(1))
    }
}

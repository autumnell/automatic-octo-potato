package com.mtgscanner.ui.collection

import kotlin.random.Random

/** What the quiz needs to know about a card. */
data class QuizCard(val id: Long, val name: String, val boxId: Long, val price: Double?, val image: String? = null)

sealed interface QuizQuestion {
    val card: QuizCard

    /** "Which box is it in?" [options] are box ids, one of them [card]'s. */
    data class WhichBox(override val card: QuizCard, val options: List<Long>) : QuizQuestion

    /** [card] against [shown], whose price is on screen: is [card] worth more? */
    data class HigherLower(override val card: QuizCard, val shown: QuizCard) : QuizQuestion {
        val higher: Boolean get() = (card.price ?: 0.0) > (shown.price ?: 0.0)
    }
}

enum class QuizMode(val label: String) { MIX("Mix"), BOX("Box"), PRICE("Price") }

data class QuizScore(val right: Int = 0, val asked: Int = 0, val streak: Int = 0, val best: Int = 0) {
    fun answer(correct: Boolean): QuizScore {
        val s = if (correct) streak + 1 else 0
        return QuizScore(right + if (correct) 1 else 0, asked + 1, s, maxOf(best, s))
    }
}

object QuizMath {
    /** Prices too close to call aren't asked: they must differ by 10% and 5¢. */
    fun farEnough(a: Double, b: Double): Boolean {
        val d = kotlin.math.abs(a - b)
        return d >= 0.05 && d >= 0.1 * maxOf(a, b)
    }

    /** A box question, with up to [choices] boxes; null with fewer than two boxes. */
    fun whichBox(cards: List<QuizCard>, boxIds: List<Long>, random: Random, choices: Int = 4): QuizQuestion.WhichBox? {
        val boxes = boxIds.distinct()
        if (boxes.size < 2) return null
        val pool = cards.filter { it.boxId in boxes }
        if (pool.isEmpty()) return null
        val card = pool[random.nextInt(pool.size)]
        val others = boxes.filter { it != card.boxId }.shuffled(random).take(choices - 1)
        return QuizQuestion.WhichBox(card, (others + card.boxId).shuffled(random))
    }

    /** A higher-or-lower pair of different cards with known prices far enough apart; null when there's none. */
    fun higherLower(cards: List<QuizCard>, random: Random, tries: Int = 40): QuizQuestion.HigherLower? {
        val priced = cards.filter { (it.price ?: 0.0) > 0.0 }
        if (priced.size < 2) return null
        repeat(tries) {
            val a = priced[random.nextInt(priced.size)]
            val b = priced[random.nextInt(priced.size)]
            if (a.name != b.name && farEnough(a.price!!, b.price!!)) return QuizQuestion.HigherLower(b, a)
        }
        return null
    }

    /** The next question for [mode]; MIX picks either kind at random and falls back to the other. */
    fun next(mode: QuizMode, cards: List<QuizCard>, boxIds: List<Long>, random: Random): QuizQuestion? = when (mode) {
        QuizMode.BOX -> whichBox(cards, boxIds, random)
        QuizMode.PRICE -> higherLower(cards, random)
        QuizMode.MIX ->
            if (random.nextBoolean()) whichBox(cards, boxIds, random) ?: higherLower(cards, random)
            else higherLower(cards, random) ?: whichBox(cards, boxIds, random)
    }
}

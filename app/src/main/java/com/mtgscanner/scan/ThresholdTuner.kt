package com.mtgscanner.scan

import kotlin.math.abs

/**
 * How sure the picture match must be before it names a card ([CardGallery.match]): how far the best card must stand
 * out from every printing, and lead the next card with another name. The defaults come from Scryfall's pictures
 * made blurry and dark; [ThresholdTuner] fits them to your own scans.
 */
data class MatchThresholds(val standOut: Float = CardGallery.MIN_STAND_OUT, val lead: Float = CardGallery.MIN_LEAD) {
    val isDefault: Boolean get() = this == MatchThresholds()
}

/** One kept scan looked up with no thresholds: how its best card stood out and led, and whether it was right. */
data class TuneSample(val standOut: Float, val lead: Float, val right: Boolean)

/** What tuning found: the thresholds, and how many of the scans they get right and wrong, before and after. */
data class TuneResult(
    val thresholds: MatchThresholds,
    val total: Int, val rightBefore: Int, val wrongBefore: Int, val rightAfter: Int, val wrongAfter: Int,
)

/** Fits [MatchThresholds] to real scans: as many right as possible while hardly any are wrong. Pure. */
object ThresholdTuner {
    /** Fewer kept scans than this aren't enough to tune on. */
    const val MIN_SAMPLES = 20
    /** Wrong answers allowed, as a share of the scans: a wrong card is worse than none. */
    const val MAX_WRONG = 0.02

    private val STAND_OUTS = (0..24).map { 4f + it * 0.25f }
    private val LEADS = (0..12).map { 0.01f + it * 0.01f }

    fun counts(samples: List<TuneSample>, t: MatchThresholds): Pair<Int, Int> {
        var right = 0
        var wrong = 0
        for (s in samples) if (s.standOut >= t.standOut && s.lead >= t.lead) { if (s.right) right++ else wrong++ }
        return right to wrong
    }

    /** The best thresholds for [samples]; null when there are too few. Never looser than needed: ties go to the stricter. */
    fun tune(samples: List<TuneSample>): TuneResult? {
        if (samples.size < MIN_SAMPLES) return null
        val allowed = (samples.size * MAX_WRONG).toInt()
        val (rb, wb) = counts(samples, MatchThresholds())
        var best = MatchThresholds()
        var bestRight = -1
        var bestWrong = Int.MAX_VALUE
        for (so in STAND_OUTS) for (l in LEADS) {
            val t = MatchThresholds(so, l)
            val (r, w) = counts(samples, t)
            if (w > maxOf(allowed, wb)) continue
            val better = r > bestRight || (r == bestRight && w < bestWrong) ||
                (r == bestRight && w == bestWrong && strictness(t) > strictness(best))
            if (better) { best = t; bestRight = r; bestWrong = w }
        }
        // No better than the defaults: keep them.
        if (bestRight <= rb && bestWrong >= wb) best = MatchThresholds()
        val (ra, wa) = counts(samples, best)
        return TuneResult(best, samples.size, rb, wb, ra, wa)
    }

    private fun strictness(t: MatchThresholds) = t.standOut / 9f + t.lead / 0.13f - abs(t.standOut - CardGallery.MIN_STAND_OUT) * 0.001f
}

package com.mtgscanner.ui.scan

/**
 * Layout of the scan session fan, in dp. Cards cascade like a solitaire column: each newer card sits
 * [step] lower and [dx] further right, in front of the last, so only the name bar of older ones shows.
 */
data class FanLayout(
    val cardWidth: Float = 104f,
    val step: Float = 16f,
    val dx: Float = 5f,
    /** How much of the front card shows below its top edge. */
    val front: Float = 40f,
    val max: Int = 8,
) {
    /** How many cards show for a session of [count]. */
    fun shown(count: Int): Int = count.coerceIn(0, max)

    /** Top-left of slot [i], 0 being the oldest shown (at the back). */
    fun slotX(i: Float): Float = i * dx
    fun slotY(i: Float): Float = i * step

    fun width(count: Int): Float = if (shown(count) == 0) 0f else cardWidth + (shown(count) - 1) * dx
    fun height(count: Int): Float = if (shown(count) == 0) 0f else (shown(count) - 1) * step + front
}

/** The keys shown in the fan, back to front, for a session list that's newest first. */
fun fanKeys(sessionKeysNewestFirst: List<Long>, max: Int): List<Long> = sessionKeysNewestFirst.take(max).reversed()

/**
 * Whether an add should fly a card toward its box: only when the newest line is new or got more copies,
 * not when a pulse came from something else (saving an offline read, a box change).
 */
fun isNewAdd(before: Pair<Long, Int>?, now: Pair<Long, Int>?): Boolean =
    now != null && (before == null || before.first != now.first || now.second > before.second)

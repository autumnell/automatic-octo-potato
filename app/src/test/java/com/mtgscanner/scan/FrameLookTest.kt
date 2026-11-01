package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class FrameLookTest {
    private val w = CardPrintPrep.CARD_W
    private val h = CardPrintPrep.CARD_H

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** A made-up card: one shared art, a [border] colour and a footer pattern from [footerSeed]. */
    private fun card(border: Int, footerSeed: Int): IntArray {
        val art = Random(1)
        val artPx = IntArray(64) { rgb(art.nextInt(256), art.nextInt(256), art.nextInt(256)) }
        val foot = Random(footerSeed)
        val marks = BooleanArray(40) { foot.nextBoolean() }
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val fx = x.toFloat() / w; val fy = y.toFloat() / h
            when {
                fx < 0.04f || fx > 0.96f || fy < 0.035f || fy > 0.97f -> border
                fy in 0.12f..0.54f && fx in 0.09f..0.91f -> artPx[((fy - 0.12f) / 0.42f * 8).toInt().coerceIn(0, 7) * 8 + ((fx - 0.09f) / 0.82f * 8).toInt().coerceIn(0, 7)]
                fy in 0.905f..0.965f && fx in 0.04f..0.56f -> if (marks[((fx - 0.04f) / 0.52f * 40).toInt().coerceIn(0, 39)]) rgb(240, 240, 240) else rgb(20, 20, 20)
                else -> rgb(200, 190, 170)
            }
        }
    }

    /** [px] a little darker, with noise, as a camera sees it. */
    private fun photo(px: IntArray, seed: Int): IntArray {
        val r = Random(seed)
        return IntArray(px.size) { i ->
            val c = px[i]
            fun ch(v: Int) = (v * 0.8 + r.nextInt(-12, 13)).toInt()
            rgb(ch((c shr 16) and 0xFF), ch((c shr 8) and 0xFF), ch(c and 0xFF))
        }
    }

    @Test fun picksThePrintingByFooterAndBorder() {
        val black = FrameLook.of(card(rgb(10, 10, 10), 5), w, h)
        val white = FrameLook.of(card(rgb(235, 235, 235), 5), w, h)
        val promo = FrameLook.of(card(rgb(10, 10, 10), 99), w, h)
        val candidates = listOf("black" to black, "white" to white, "promo" to promo)
        assertEquals("black", FrameLook.pick(FrameLook.of(photo(card(rgb(10, 10, 10), 5), 3), w, h), candidates))
        assertEquals("white", FrameLook.pick(FrameLook.of(photo(card(rgb(235, 235, 235), 5), 4), w, h), candidates))
        assertEquals("promo", FrameLook.pick(FrameLook.of(photo(card(rgb(10, 10, 10), 99), 5), w, h), candidates))
    }

    @Test fun twoLookalikesGiveNoAnswer() {
        val a = FrameLook.of(card(rgb(10, 10, 10), 5), w, h)
        val b = FrameLook.of(photo(card(rgb(10, 10, 10), 5), 9), w, h)
        assertNull(FrameLook.pick(FrameLook.of(photo(card(rgb(10, 10, 10), 5), 3), w, h), listOf("a" to a, "b" to b)))
    }
}

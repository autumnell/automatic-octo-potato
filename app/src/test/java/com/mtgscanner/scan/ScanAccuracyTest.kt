package com.mtgscanner.scan

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File

/**
 * How well cards are known by their picture, measured on the build server with the real models and gallery
 * ([CardPrints]). Skipped unless SCAN_MODELS names a folder holding card.onnx, corners.onnx, the published gallery
 * (gallery_f16.bin, gallery_ids.json, meta.json) and samples/, which the build's "Scan accuracy" job makes.
 *
 * Android's unit tests have no image decoding, so the job hands over plain pixels: samples/<scryfall id>__<kind>.rgb,
 * each the width and height (two big-endian ints) then red, green, blue bytes. Kinds: Clean, Blurred, Dark, Glare
 * (whole card pictures), Photo (the card lying turned on a table: found by the corner model and straightened here)
 * and Your scans (real scans from src/test/resources/scan-photos). A table of results goes to build/scan-accuracy.md.
 */
class ScanAccuracyTest {
    private val dir = System.getenv("SCAN_MODELS")?.let { File(it) }

    private class Picture(val w: Int, val h: Int, val px: IntArray)

    @Test fun cardsAreKnownByTheirPicture() {
        assumeTrue("SCAN_MODELS not set", dir != null && File(dir, "card.onnx").exists() && File(dir, "gallery_f16.bin").exists())
        val d = dir!!
        val packed = ByteArrayOutputStream()
        File(d, "gallery_ids.json").reader().use { ids ->
            File(d, "meta.json").reader().use { meta ->
                File(d, "gallery_f16.bin").inputStream().use { CardGallery.Published.convert(ids, meta, it, packed) }
            }
        }
        val gallery = CardGallery.Codec.read(ByteArrayInputStream(packed.toByteArray()))
        val card = OnnxNet(File(d, "card.onnx"))
        val corners = File(d, "corners.onnx").takeIf { it.exists() }?.let { OnnxNet(it) }

        fun print(p: Picture): FloatArray {
            val px = resize(p, CardPrintPrep.CARD_W, CardPrintPrep.CARD_H)
            return CardGallery.unit(card.run(CardPrintPrep.cardInput(px), CardPrintPrep.cardShape).values.first())
        }

        // Kind of picture -> right, wrong, no answer
        val tally = LinkedHashMap<String, IntArray>()
        val ms = LinkedHashMap<String, Long>()
        val samples = File(d, "samples").listFiles().orEmpty().filter { it.extension == "rgb" }.sortedBy { it.name }
        for (f in samples) {
            val id = f.name.substringBefore("__")
            val kind = f.nameWithoutExtension.substringAfter("__")
            val t = tally.getOrPut(kind) { IntArray(3) }
            val pic = read(f)
            val start = System.nanoTime()
            val straight = if (kind == "Photo") corners?.let { findAndStraighten(pic, it) } else pic
            val hit = straight?.let { gallery.match(print(it)) }
            ms[kind] = (ms[kind] ?: 0L) + (System.nanoTime() - start) / 1_000_000
            val row = gallery.rowOf(id)
            when {
                hit == null -> t[2]++
                hit.id == id || (row >= 0 && hit.name == gallery.nameAt(row)) -> t[0]++
                else -> t[1]++
            }
        }
        val detail = yourScans(samples, gallery, ::print)
        card.close()
        corners?.close()

        val report = StringBuilder("| Pictures | Right | Wrong | No answer | ms each |\n|---|---|---|---|---|\n")
        for ((kind, t) in tally) {
            val n = t.sum().coerceAtLeast(1)
            report.append("| $kind | ${t[0]} of $n (${t[0] * 100 / n}%) | ${t[1]} | ${t[2]} | ${(ms[kind] ?: 0) / n} |\n")
        }
        println(report)
        File("build").mkdirs()
        File("build/scan-accuracy.md").writeText("### Knowing cards by picture\n\n$report\n$detail")

        // Clean pictures should all be right; a wrong answer is worse than none, so few wrong anywhere.
        tally["Clean"]?.let { t -> assertTrue("clean: ${t.toList()}", t[0] * 100 >= t.sum() * 95) }
        if (tally.isNotEmpty()) {
            val all = tally.values.reduce { a, b -> IntArray(3) { a[it] + b[it] } }
            assertTrue("wrong answers: ${all.toList()}", all[1] * 100 <= all.sum() * 3)
        }
        tally["Your scans"]?.let { t -> if (t.sum() >= 10) assertTrue("your scans: ${t.toList()}", t[0] * 100 >= t.sum() * 80) }
    }

    /**
     * Each real scan in detail, plainly and with a few fixes tried: where the right card ranked, its score, and
     * what the match gave instead. For finding why real scans miss.
     */
    private fun yourScans(samples: List<File>, g: CardGallery, print: (Picture) -> FloatArray): String {
        val scans = samples.filter { it.nameWithoutExtension.endsWith("__Your scans") }
        if (scans.isEmpty()) return ""
        val fixes = linkedMapOf<String, (Picture) -> Picture>(
            "as is" to { it },
            "turned" to { turned(it) },
            "inset 3%" to { inset(it, 0.03f) },
            "inset 6%" to { inset(it, 0.06f) },
            "outset 3%" to { inset(it, -0.03f) },
            "levels" to { levels(it) },
            "levels+inset 3%" to { levels(inset(it, 0.03f)) },
        )
        val right = IntArray(fixes.size)
        val wrong = IntArray(fixes.size)
        val rows = StringBuilder("\n#### Your scans, one by one (as is)\n\n| Card | Got | Score | Right score | Rank | Stand out | Lead | Best fix |\n|---|---|---|---|---|---|---|---|\n")
        for (f in scans) {
            val id = f.name.substringBefore("__")
            val row = g.rowOf(id)
            val name = if (row >= 0) g.nameAt(row) else "(not in gallery)"
            val pic = read(f)
            var bestFix = ""
            var bestFixScore = -2f
            var line = ""
            fixes.entries.forEachIndexed { i, (label, fix) ->
                val q = print(fix(pic))
                val hit = g.match(q)
                val ok = hit != null && (hit.id == id || (row >= 0 && hit.name == name))
                if (ok) right[i]++ else if (hit != null) wrong[i]++
                val own = if (row >= 0) bestOfName(g, q, name) else -2f
                if (own > bestFixScore) { bestFixScore = own; bestFix = label }
                if (i == 0) {
                    val loose = g.match(q, minScore = 0f, minStandOut = 0f, minLead = -2f)
                    val rank = if (row >= 0) rankOf(g, q, name) else -1
                    val got = if (ok) "right" else hit?.name ?: "none (best ${loose?.name})"
                    line = "| $name | $got | ${"%.2f".format(loose?.score ?: 0f)} | ${"%.2f".format(own)} | $rank | " +
                        "${"%.1f".format(loose?.standOut ?: 0f)} | ${"%.2f".format((loose?.score ?: 0f) - (loose?.runnerUp ?: 0f))} | "
                }
            }
            rows.append(line).append("$bestFix |\n")
        }
        val sum = StringBuilder("\n#### Your scans with fixes\n\n| Fix | Right | Wrong |\n|---|---|---|\n")
        fixes.keys.forEachIndexed { i, label -> sum.append("| $label | ${right[i]} of ${scans.size} | ${wrong[i]} |\n") }
        return sum.toString() + rows
    }

    /** The best score of any printing named [name]. */
    private fun bestOfName(g: CardGallery, q: FloatArray, name: String): Float {
        var best = -2f
        for (r in 0 until g.size) if (g.nameAt(r) == name) best = maxOf(best, g.score(q, r))
        return best
    }

    /** 1 when a printing named [name] is the closest card. */
    private fun rankOf(g: CardGallery, q: FloatArray, name: String): Int {
        val own = bestOfName(g, q, name)
        val better = HashSet<String>()
        for (r in 0 until g.size) if (g.nameAt(r) != name && g.score(q, r) > own) better += g.nameAt(r)
        return better.size + 1
    }

    private fun turned(p: Picture) = Picture(p.w, p.h, IntArray(p.px.size) { p.px[p.px.size - 1 - it] })

    /** The middle of [p] without [f] of each side (or with that much more, edge pixels repeated, when negative). */
    private fun inset(p: Picture, f: Float): Picture {
        val dx = p.w * f
        val dy = p.h * f
        return Picture(p.w, p.h, IntArray(p.w * p.h) { i ->
            val x = dx + ((i % p.w) + 0.5f) * (p.w - 2 * dx) / p.w - 0.5f
            val y = dy + ((i / p.w) + 0.5f) * (p.h - 2 * dy) / p.h - 0.5f
            sample(p, x, y)
        })
    }

    /** Each colour stretched so its darkest 1% is black and brightest 1% white (white balance and contrast). */
    private fun levels(p: Picture): Picture {
        val lo = IntArray(3)
        val hi = IntArray(3)
        for ((k, shift) in intArrayOf(16, 8, 0).withIndex()) {
            val hist = IntArray(256)
            for (c in p.px) hist[(c shr shift) and 0xFF]++
            val cut = p.px.size / 100
            var acc = 0
            lo[k] = (0..255).first { acc += hist[it]; acc > cut }
            acc = 0
            hi[k] = (255 downTo 0).first { acc += hist[it]; acc > cut }
        }
        return Picture(p.w, p.h, IntArray(p.px.size) { i ->
            var out = 0xFF shl 24
            for ((k, shift) in intArrayOf(16, 8, 0).withIndex()) {
                val v = (p.px[i] shr shift) and 0xFF
                val span = (hi[k] - lo[k]).coerceAtLeast(1)
                out = out or (((v - lo[k]) * 255 / span).coerceIn(0, 255) shl shift)
            }
            out
        })
    }

    private fun read(f: File): Picture = DataInputStream(f.inputStream().buffered()).use { s ->
        val w = s.readInt()
        val h = s.readInt()
        val bytes = ByteArray(w * h * 3).also { s.readFully(it) }
        Picture(w, h, IntArray(w * h) { i ->
            (0xFF shl 24) or ((bytes[i * 3].toInt() and 0xFF) shl 16) or ((bytes[i * 3 + 1].toInt() and 0xFF) shl 8) or (bytes[i * 3 + 2].toInt() and 0xFF)
        })
    }

    /** The corner model's card in [photo], straightened at the card model's size; null when it finds none. */
    private fun findAndStraighten(photo: Picture, net: OnnxNet): Picture? {
        val px = resize(photo, CardPrintPrep.POSE, CardPrintPrep.POSE)
        val out = net.run(CardPrintPrep.poseInput(px), CardPrintPrep.poseShape)
        val q = CardPrintPrep.corners(out["scores"] ?: return null, out["keypoints"] ?: return null, photo.w, photo.h) ?: return null
        return warp(photo, q, CardPrintPrep.CARD_W, CardPrintPrep.CARD_H)
    }

    /** [p] stretched to [w] x [h], bilinear. */
    private fun resize(p: Picture, w: Int, h: Int): IntArray {
        if (p.w == w && p.h == h) return p.px
        return IntArray(w * h) { i ->
            val x = ((i % w) + 0.5f) * p.w / w - 0.5f
            val y = ((i / w) + 0.5f) * p.h / h - 0.5f
            sample(p, x, y)
        }
    }

    private fun sample(p: Picture, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, p.w - 1); val y0 = y.toInt().coerceIn(0, p.h - 1)
        val x1 = (x0 + 1).coerceAtMost(p.w - 1); val y1 = (y0 + 1).coerceAtMost(p.h - 1)
        val fx = (x - x0).coerceIn(0f, 1f); val fy = (y - y0).coerceIn(0f, 1f)
        var out = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            fun c(xx: Int, yy: Int) = (p.px[yy * p.w + xx] shr shift) and 0xFF
            val top = c(x0, y0) * (1 - fx) + c(x1, y0) * fx
            val bottom = c(x0, y1) * (1 - fx) + c(x1, y1) * fx
            out = out or ((top * (1 - fy) + bottom * fy).toInt().coerceIn(0, 255) shl shift)
        }
        return out
    }

    /** The quad [q] (top-left first, clockwise) of [src] drawn straight at [w] x [h], by a perspective transform. */
    private fun warp(src: Picture, q: FloatArray, w: Int, h: Int): Picture {
        // The transform from the output rectangle to the quad.
        val m = homography(doubleArrayOf(0.0, 0.0, w.toDouble(), 0.0, w.toDouble(), h.toDouble(), 0.0, h.toDouble()), DoubleArray(8) { q[it].toDouble() })
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val z = m[6] * x + m[7] * y + 1
            px[y * w + x] = sample(src, ((m[0] * x + m[1] * y + m[2]) / z).toFloat(), ((m[3] * x + m[4] * y + m[5]) / z).toFloat())
        }
        return Picture(w, h, px)
    }

    /** The 8 numbers of the perspective transform taking the four points [from] to [to]. */
    private fun homography(from: DoubleArray, to: DoubleArray): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = from[i * 2]; val y = from[i * 2 + 1]; val u = to[i * 2]; val v = to[i * 2 + 1]
            a[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Gaussian elimination with partial pivoting.
        for (c in 0 until 8) {
            val p = (c until 8).maxBy { kotlin.math.abs(a[it][c]) }
            val tmp = a[c]; a[c] = a[p]; a[p] = tmp
            for (r in 0 until 8) if (r != c) {
                val f = a[r][c] / a[c][c]
                for (k in c..8) a[r][k] -= f * a[c][k]
            }
        }
        return DoubleArray(8) { a[it][8] / a[it][it] }
    }
}

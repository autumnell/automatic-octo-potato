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
        val scanNames = File(d, "samples/scan-names.json").takeIf { it.exists() }?.readText()
            ?.let { Regex(""""([0-9a-f-]{36})"\s*:\s*"((?:[^"\\]|\\.)*)"""").findAll(it).associate { m -> m.groupValues[1] to m.groupValues[2] } }
            .orEmpty()
        // The gallery that goes in the app when there is one, else the published one it starts from.
        val built = File(d, "card-gallery.bin").takeIf { it.exists() }
        val gallery = if (built != null) built.inputStream().use { CardGallery.Codec.read(it) } else {
            File(d, "gallery_ids.json").reader().use { ids ->
                File(d, "meta.json").reader().use { meta ->
                    File(d, "gallery_f16.bin").inputStream().use { CardGallery.Published.convert(ids, meta, it, packed) }
                }
            }
            CardGallery.Codec.read(ByteArrayInputStream(packed.toByteArray()))
        }
        println("Gallery: ${gallery.size} printings" + if (built != null) " (built)" else " (published)")
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
            val kind = f.nameWithoutExtension.substringAfter("__").substringBefore("__")
            val t = tally.getOrPut(kind) { IntArray(3) }
            val pic = read(f)
            val start = System.nanoTime()
            val straight = if (kind == "Photo") corners?.let { findAndStraighten(pic, it) } else pic
            // Real scans are tried both ways up, as the scanner does.
            val tries = straight?.let { if (kind == "Your scans") listOf(it, turned(it)) else listOf(it) }.orEmpty()
            val prints = tries.map { print(it) }
            if (kind == "Your scans") scanPrints[f.name] = prints
            val hit = prints.mapNotNull { gallery.match(it) }.maxByOrNull { it.standOut }
            ms[kind] = (ms[kind] ?: 0L) + (System.nanoTime() - start) / 1_000_000
            val row = gallery.rowOf(id)
            val name = if (row >= 0) gallery.nameAt(row) else scanNames[id]
            // A card newer than the published gallery can't be known by it here (the phone adds new printings).
            if (kind == "Your scans" && row < 0 && name != null && keysOf(gallery).none { it == com.mtgscanner.util.CardNames.key(name) }) {
                tally.getOrPut("Your scans of cards not in the gallery") { IntArray(3) }[2]++
                continue
            }
            when {
                hit == null -> t[2]++
                hit.id == id || (name != null && sameCard(hit.name, name)) -> t[0]++
                else -> t[1]++
            }
        }
        val detail = yourScans(samples, gallery, scanNames, ::print)
        val adapterReport = runCatching { adapterTrial(samples, gallery) }.getOrElse { "\nAdapter trial failed: $it\n" }
        card.close()
        corners?.close()

        val report = StringBuilder("| Pictures | Right | Wrong | No answer | ms each |\n|---|---|---|---|---|\n")
        for ((kind, t) in tally) {
            val n = t.sum().coerceAtLeast(1)
            report.append("| $kind | ${t[0]} of $n (${t[0] * 100 / n}%) | ${t[1]} | ${t[2]} | ${(ms[kind] ?: 0) / n} |\n")
        }
        println(report)
        File("build").mkdirs()
        File("build/scan-accuracy.md").writeText("### Knowing cards by picture\n\nGallery: ${gallery.size} printings${if (built != null) " (built)" else " (published)"}\n\n$report\n$adapterReport\n$detail")

        // Clean pictures should all be right; a wrong answer is worse than none, so few wrong anywhere.
        tally["Clean"]?.let { t -> assertTrue("clean: ${t.toList()}", t[0] * 100 >= t.sum() * 95) }
        if (tally.isNotEmpty()) {
            val all = tally.values.reduce { a, b -> IntArray(3) { a[it] + b[it] } }
            assertTrue("wrong answers: ${all.toList()}", all[1] * 100 <= all.sum() * 3)
        }
        // Real scans in dim, warm light, and showcase frames cropped to a panel inside the card, are harder: the
        // picture alone gets about three in four; with the name read
        // it backs up the right card more often, which is what lets a card be added on its first frame.
        tally["Your scans"]?.let { t -> if (t.sum() >= 10) assertTrue("your scans: ${t.toList()}", t[0] * 100 >= t.sum() * 70) }
        if (scansSeen >= 10) assertTrue("picture agrees with the name: $scansAgreed of $scansSeen", scansAgreed * 100 >= scansSeen * 80)
    }

    /**
     * Each real scan in detail: what the match gave, the best and right cards' scores, where the right card ranked,
     * and how far the best stood out and led. A right card far down names the file: it was likely saved under the
     * wrong card.
     */
    private fun yourScans(samples: List<File>, g: CardGallery, scanNames: Map<String, String>, print: (Picture) -> FloatArray): String {
        val scans = samples.filter { it.nameWithoutExtension.contains("__Your scans") }
        if (scans.isEmpty()) return ""
        val out = StringBuilder("\n#### Your scans, one by one\n\n| Card | Got | Score | Right score | Rank | Stand out | Lead |\n|---|---|---|---|---|---|---|\n")
        for (f in scans) {
            val id = f.name.substringBefore("__")
            val row = g.rowOf(id)
            val name = if (row >= 0) g.nameAt(row) else scanNames[id] ?: "(unknown)"
            val q = scanPrints[f.name]?.firstOrNull() ?: print(read(f))
            val hit = g.match(q)
            val loose = g.match(q, minScore = 0f, minStandOut = 0f, minLead = -2f)
            val rank = rankOf(g, q, name)
            scansSeen++
            if (g.agreesWith(q, listOf(name)) != null) scansAgreed++
            // A rough read: every fourth letter misread. Can it still pick the right card among the picture's best?
            g.agreesWith(q, listOf(rough(name)))?.let { if (sameCard(it.name, name)) roughRight++ else roughWrong++ }
            val file = if (rank > 50) " (${f.nameWithoutExtension.substringAfterLast("__")}, best ${loose?.id})" else ""
            val got = when {
                hit == null -> "none (best ${loose?.name})"
                hit.id == id || sameCard(hit.name, name) -> "right"
                else -> hit.name
            } + file
            out.append("| $name | $got | ${"%.2f".format(loose?.score ?: 0f)} | ${"%.2f".format(bestOfName(g, q, name))} | $rank | " +
                "${"%.1f".format(loose?.standOut ?: 0f)} | ${"%.2f".format((loose?.score ?: 0f) - (loose?.runnerUp ?: 0f))} |\n")
        }
        // With the right name read, how often the picture backs it up so the card is looked up on its first frame.
        return "\nPicture agrees with the right name read: $scansAgreed of $scansSeen (${scansAgreed * 100 / scansSeen}%)\n" +
            "\nWith a rough name read (every fourth letter wrong): right $roughRight, wrong $roughWrong of $scansSeen\n" + out
    }

    private var keys: Array<String>? = null
    private var scansSeen = 0
    private var scansAgreed = 0
    private var roughRight = 0
    private var roughWrong = 0
    /** Each real scan's fingerprints, as it lies and turned round, by file name. */
    private val scanPrints = HashMap<String, List<FloatArray>>()

    /** [name] with every fourth letter read wrong. */
    private fun rough(name: String): String {
        var letters = 0
        return name.map { c -> if (c.isLetter() && ++letters % 4 == 0) 'x' else c }.joinToString("")
    }

    /**
     * Fits the phone-photo correction ([CardAdapter]) on the real scans and checks it on scans it wasn't fitted on
     * (5 rounds, each holding out a fifth). The one fitted on every scan with the best setting is written to
     * build/card-adapter.bin, for the app if it does better.
     */
    private fun adapterTrial(samples: List<File>, g: CardGallery): String {
        val data = samples.filter { it.nameWithoutExtension.contains("__Your scans") }.mapNotNull { f ->
            val row = g.rowOf(f.name.substringBefore("__"))
            val prints = scanPrints[f.name]
            if (row < 0 || prints.isNullOrEmpty()) null else Triple(prints, g.vectorAt(row), g.nameAt(row))
        }
        if (data.size < 50) return ""
        fun tally(rows: List<Triple<List<FloatArray>, FloatArray, String>>, fix: (FloatArray) -> FloatArray): IntArray {
            val t = IntArray(3)
            for ((prints, _, name) in rows) {
                val hit = prints.mapNotNull { g.match(fix(it)) }.maxByOrNull { it.standOut }
                when {
                    hit == null -> t[2]++
                    sameCard(hit.name, name) -> t[0]++
                    else -> t[1]++
                }
            }
            return t
        }
        val folds = 5
        val out = StringBuilder("\n#### Phone-photo correction, checked on scans it wasn't fitted on\n\n| Setting | Right | Wrong | No answer |\n|---|---|---|---|\n")
        val base = tally(data) { it }
        out.append("| None | ${base[0]} | ${base[1]} | ${base[2]} |\n")
        var best: Pair<Double, IntArray>? = null
        for (lambda in listOf(1.0, 4.0, 16.0)) {
            val t = IntArray(3)
            for (k in 0 until folds) {
                val train = data.filterIndexed { i, _ -> i % folds != k }
                val test = data.filterIndexed { i, _ -> i % folds == k }
                val a = CardAdapter.fit(train.map { it.first.first() }, train.map { CardGallery.unit(it.second) }, lambda)
                tally(test) { a.apply(it) }.forEachIndexed { i, v -> t[i] += v }
            }
            out.append("| λ $lambda | ${t[0]} | ${t[1]} | ${t[2]} |\n")
            if (best == null || t[0] - 3 * t[1] > best.second[0] - 3 * best.second[1]) best = lambda to t
        }
        val (lambda, t) = best!!
        val all = CardAdapter.fit(data.map { it.first.first() }, data.map { CardGallery.unit(it.second) }, lambda)
        File("build").mkdirs()
        File("build/card-adapter.bin").outputStream().use { all.write(it) }
        out.append("\nBest: λ $lambda, ${t[0] - base[0]} more right, ${t[1] - base[1]} more wrong than none. Fitted on all ${data.size} scans: build/card-adapter.bin\n")
        return out.toString()
    }

    /** Each gallery row's name key, worked out once. */
    private fun keysOf(g: CardGallery): Array<String> =
        keys ?: Array(g.size) { com.mtgscanner.util.CardNames.key(g.nameAt(it)) }.also { keys = it }

    /** "A // A" (a reversible printing) and "A" are the same card. */
    private fun sameCard(a: String, b: String) = com.mtgscanner.util.CardNames.key(a) == com.mtgscanner.util.CardNames.key(b)

    /** The best score of any printing named [name]. */
    private fun bestOfName(g: CardGallery, q: FloatArray, name: String): Float {
        var best = -2f
        val k = com.mtgscanner.util.CardNames.key(name)
        val keys = keysOf(g)
        for (r in 0 until g.size) if (keys[r] == k) best = maxOf(best, g.score(q, r))
        return best
    }

    /** 1 when a printing named [name] is the closest card. */
    private fun rankOf(g: CardGallery, q: FloatArray, name: String): Int {
        val own = bestOfName(g, q, name)
        val better = HashSet<String>()
        val k = com.mtgscanner.util.CardNames.key(name)
        val keys = keysOf(g)
        for (r in 0 until g.size) if (keys[r] != k && g.score(q, r) > own) better += keys[r]
        return better.size + 1
    }

    private fun turned(p: Picture) = Picture(p.w, p.h, IntArray(p.px.size) { p.px[p.px.size - 1 - it] })

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

package com.mtgscanner.scan

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.exp
import kotlin.random.Random

/**
 * How well cards are known by their picture, measured on the build server with the real models and gallery
 * ([CardPrints]). Skipped unless SCAN_MODELS names a folder holding card.onnx, corners.onnx, the published gallery
 * (gallery_f16.bin, gallery_ids.json, meta.json) and samples/ (Scryfall pictures named by Scryfall id), which the
 * build's "Scan accuracy" job downloads.
 *
 * Each sample is tried clean, blurred, dark, with glare, and as a photo: lying turned on a table, found by the
 * corner model and straightened. Real scans in src/test/resources/scan-photos (saved from the app's Settings › Data
 * › Keep test photos, named by Scryfall id) are tried too. A table of results goes to build/scan-accuracy.md.
 */
class ScanAccuracyTest {
    private val dir = System.getenv("SCAN_MODELS")?.let { File(it) }

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
        val rnd = Random(5)

        fun print(img: BufferedImage): FloatArray {
            val px = pixels(resize(img, CardPrintPrep.CARD_W, CardPrintPrep.CARD_H))
            return CardGallery.unit(card.run(CardPrintPrep.cardInput(px), CardPrintPrep.cardShape).values.first())
        }

        // Kind of picture -> (right, wrong, no answer, milliseconds)
        val tally = LinkedHashMap<String, IntArray>()
        val ms = LinkedHashMap<String, Long>()
        fun count(kind: String, id: String, img: BufferedImage?) {
            val t = tally.getOrPut(kind) { IntArray(3) }
            if (img == null) { t[2]++; return }
            val start = System.nanoTime()
            val hit = gallery.match(print(img))
            ms[kind] = (ms[kind] ?: 0L) + (System.nanoTime() - start) / 1_000_000
            val row = gallery.rowOf(id)
            when {
                hit == null -> t[2]++
                hit.id == id || (row >= 0 && hit.name == gallery.nameAt(row)) -> t[0]++
                else -> t[1]++
            }
        }

        val samples = File(d, "samples").listFiles().orEmpty().filter { it.extension in setOf("jpg", "jpeg", "png") }.sortedBy { it.name }
        for (f in samples) {
            val img = ImageIO.read(f) ?: continue
            val id = f.nameWithoutExtension
            count("Clean", id, img)
            count("Blurred", id, blur(img))
            count("Dark", id, brightness(img, 0.5f))
            count("Glare", id, glare(img))
            if (corners != null) {
                count("Photo, corner model", id, findAndStraighten(onTable(img, rnd), corners))
            }
        }
        val real = javaClass.classLoader?.getResource("scan-photos")?.let { File(it.toURI()) }?.listFiles().orEmpty()
            .filter { it.extension in setOf("jpg", "jpeg", "png") }
        for (f in real) count("Your scans", f.name.substringBefore('_'), ImageIO.read(f))

        val report = StringBuilder("| Pictures | Right | Wrong | No answer | ms each |\n|---|---|---|---|---|\n")
        for ((kind, t) in tally) {
            val n = t.sum().coerceAtLeast(1)
            report.append("| $kind | ${t[0]} of $n (${t[0] * 100 / n}%) | ${t[1]} | ${t[2]} | ${(ms[kind] ?: 0) / n} |\n")
        }
        println(report)
        File("build").mkdirs()
        File("build/scan-accuracy.md").writeText("### Knowing cards by picture\n\n$report")
        card.close()
        corners?.close()

        // Clean pictures should all be right; a wrong answer is worse than none, so few wrong anywhere.
        tally["Clean"]?.let { t -> assertTrue("clean: ${t.toList()}", t[0] * 100 >= t.sum() * 95) }
        val all = tally.values.reduce { a, b -> IntArray(3) { a[it] + b[it] } }
        assertTrue("wrong answers: ${all.toList()}", all[1] * 100 <= all.sum() * 3)
        tally["Your scans"]?.let { t -> if (t.sum() >= 10) assertTrue("your scans: ${t.toList()}", t[0] * 100 >= t.sum() * 80) }
    }

    /** The card lying turned a little on a plain table, as in a phone photo. */
    private fun onTable(card: BufferedImage, rnd: Random): BufferedImage {
        val w = 900
        val h = 1200
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = Color(30 + rnd.nextInt(40), 60 + rnd.nextInt(60), 50 + rnd.nextInt(40))
        g.fillRect(0, 0, w, h)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val scale = (h * (0.55 + rnd.nextDouble() * 0.2)) / card.height
        val t = AffineTransform()
        t.translate(w / 2.0 + rnd.nextInt(-80, 80), h / 2.0 + rnd.nextInt(-80, 80))
        t.rotate(Math.toRadians(rnd.nextDouble(-15.0, 15.0)))
        t.scale(scale, scale)
        t.translate(-card.width / 2.0, -card.height / 2.0)
        g.drawImage(card, t, null)
        g.dispose()
        return out
    }

    /** The corner model's card in [photo], straightened at the card model's size; null when it finds none. */
    private fun findAndStraighten(photo: BufferedImage, net: OnnxNet): BufferedImage? {
        val px = pixels(resize(photo, CardPrintPrep.POSE, CardPrintPrep.POSE))
        val out = net.run(CardPrintPrep.poseInput(px), CardPrintPrep.poseShape)
        val q = CardPrintPrep.corners(out["scores"] ?: return null, out["keypoints"] ?: return null, photo.width, photo.height) ?: return null
        return warp(photo, q, CardPrintPrep.CARD_W, CardPrintPrep.CARD_H)
    }

    private fun resize(img: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    private fun pixels(img: BufferedImage): IntArray = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)

    private fun rgb(img: BufferedImage): BufferedImage = resize(img, img.width, img.height)

    private fun blur(img: BufferedImage): BufferedImage {
        val r = 3
        val k = FloatArray((2 * r + 1) * (2 * r + 1)) { i ->
            val x = i % (2 * r + 1) - r; val y = i / (2 * r + 1) - r
            exp(-(x * x + y * y) / 4.5).toFloat()
        }
        val s = k.sum()
        return ConvolveOp(Kernel(2 * r + 1, 2 * r + 1, FloatArray(k.size) { k[it] / s }), ConvolveOp.EDGE_NO_OP, null).filter(rgb(img), null)
    }

    private fun brightness(img: BufferedImage, f: Float): BufferedImage {
        val out = rgb(img)
        val px = pixels(out)
        for (i in px.indices) {
            val p = px[i]
            val r = (((p shr 16) and 0xFF) * f).toInt(); val g = (((p shr 8) and 0xFF) * f).toInt(); val b = ((p and 0xFF) * f).toInt()
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setRGB(0, 0, out.width, out.height, px, 0, out.width)
        return out
    }

    /** A bright patch across the art, as from a lamp. */
    private fun glare(img: BufferedImage): BufferedImage {
        val out = rgb(img)
        val w = out.width; val h = out.height
        val px = pixels(out)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - w * 0.6) / (w * 0.2); val dy = (y - h * 0.35) / (h * 0.15)
            val add = (exp(-(dx * dx + dy * dy)) * 180).toInt()
            val p = px[y * w + x]
            fun c(v: Int) = (v + add).coerceAtMost(255)
            px[y * w + x] = (0xFF shl 24) or (c((p shr 16) and 0xFF) shl 16) or (c((p shr 8) and 0xFF) shl 8) or c(p and 0xFF)
        }
        out.setRGB(0, 0, w, h, px, 0, w)
        return out
    }

    /** The quad [q] (top-left first, clockwise) of [src] drawn straight at [w] x [h], by a perspective transform. */
    private fun warp(src: BufferedImage, q: FloatArray, w: Int, h: Int): BufferedImage {
        // The transform from the output rectangle to the quad.
        val m = homography(doubleArrayOf(0.0, 0.0, w.toDouble(), 0.0, w.toDouble(), h.toDouble(), 0.0, h.toDouble()), q.map { it.toDouble() }.toDoubleArray())
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val z = m[6] * x + m[7] * y + 1
            val sx = ((m[0] * x + m[1] * y + m[2]) / z).toInt().coerceIn(0, src.width - 1)
            val sy = ((m[3] * x + m[4] * y + m[5]) / z).toInt().coerceIn(0, src.height - 1)
            out.setRGB(x, y, src.getRGB(sx, sy))
        }
        return out
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

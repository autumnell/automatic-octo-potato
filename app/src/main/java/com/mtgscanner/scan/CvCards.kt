package com.mtgscanner.scan

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Card finding with OpenCV, used when its native library loads: edges (Canny), outlines (findContours), each
 * simplified to corners (approxPolyDP), the card picked by [CardOutline], its corners refined to the sub-pixel
 * (cornerSubPix), and parts of it straightened with warpPerspective. When OpenCV can't load or fails, [ready] is
 * false and the hand-written [CardQuad] and [CardWarp] are used instead.
 */
object CvCards {
    /** 0 not tried yet, 1 loaded, -1 unavailable. */
    @Volatile private var state = 0

    /** Loads OpenCV the first time it's asked; false for good when that fails or OpenCV breaks later. */
    val ready: Boolean
        get() {
            if (state == 0) {
                synchronized(this) {
                    if (state == 0) state = if (runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)) 1 else -1
                }
            }
            return state == 1
        }

    /** OpenCV failed in a way that will happen again (a missing native method): stop using it. */
    fun disable() { state = -1 }

    /**
     * Converts a camera frame straight into [reuse] (RGBA), with no Bitmap in between: the Y plane and the
     * interleaved chroma are read in place, as OpenCV's own camera view does. False when the frame isn't laid out
     * that way (the caller then makes a Bitmap as before). Call before the frame is closed.
     */
    fun yuvInto(image: androidx.camera.core.ImageProxy, reuse: Mat): Boolean {
        if (image.format != android.graphics.ImageFormat.YUV_420_888) return false
        val planes = image.planes
        if (planes.size < 3 || planes[0].pixelStride != 1 || planes[1].pixelStride != 2 || planes[2].pixelStride != 2) return false
        val w = image.width
        val h = image.height
        if (w % 2 != 0 || h % 2 != 0) return false
        val made = ArrayList<Mat>(3)
        try {
            val y = Mat(h, w, CvType.CV_8UC1, planes[0].buffer, planes[0].rowStride.toLong()).also { made += it }
            val uv1 = Mat(h / 2, w / 2, CvType.CV_8UC2, planes[1].buffer, planes[1].rowStride.toLong()).also { made += it }
            val uv2 = Mat(h / 2, w / 2, CvType.CV_8UC2, planes[2].buffer, planes[2].rowStride.toLong()).also { made += it }
            when (uv2.dataAddr() - uv1.dataAddr()) {
                1L -> Imgproc.cvtColorTwoPlane(y, uv1, reuse, Imgproc.COLOR_YUV2RGBA_NV12)
                -1L -> Imgproc.cvtColorTwoPlane(y, uv2, reuse, Imgproc.COLOR_YUV2RGBA_NV21)
                else -> return false
            }
            return true
        } finally {
            made.forEach { runCatching { it.release() } }
        }
    }

    /** Longest side of the picture the outline is searched in; corners are then refined at full size. */
    private const val FIND_PX = 480.0

    /**
     * One camera frame copied into OpenCV, for finding the card and drawing parts of it. [reuse] is a Mat kept
     * between frames so a new 8 MB buffer isn't made each time; it's filled, not released, here.
     */
    class Frame(src: Bitmap?, private val reuse: Mat) {
        init {
            // Null: [reuse] already holds the frame (see [yuvInto]).
            if (src != null) Utils.bitmapToMat(src, reuse)
        }

        val width: Int get() = reuse.cols()
        val height: Int get() = reuse.rows()

        /** The frame as a new Bitmap, for the few reads that need one. */
        fun toBitmap(): Bitmap = Bitmap.createBitmap(reuse.cols(), reuse.rows(), Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(reuse, it) }

        private val mats = ArrayList<Mat>()
        private fun <T : Mat> keep(m: T): T = m.also { mats += it }

        /**
         * The card's corners in the frame's pixels, in [CardQuad]'s order, searched for inside [region]. Null when
         * no card-shaped outline is there.
         */
        fun findQuad(region: OcrBox): FloatArray? {
            val r = Rect(region.left, region.top, region.width, region.height)
            if (r.width < 64 || r.height < 64 || r.x < 0 || r.y < 0 || r.x + r.width > reuse.cols() || r.y + r.height > reuse.rows()) return null
            val sub = keep(reuse.submat(r))
            val gray = keep(Mat())
            Imgproc.cvtColor(sub, gray, Imgproc.COLOR_RGBA2GRAY)
            val scale = minOf(1.0, FIND_PX / maxOf(r.width, r.height))
            val small = keep(Mat())
            Imgproc.resize(gray, small, Size(r.width * scale, r.height * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
            // Edge thresholds follow the picture: half and all of Otsu's split between dark and light.
            val bin = keep(Mat())
            val otsu = Imgproc.threshold(small, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            val edges = keep(Mat())
            Imgproc.Canny(small, edges, maxOf(10.0, otsu * 0.5), maxOf(30.0, otsu))
            // Close small gaps in the outline (glare on a sleeve edge, a rounded corner).
            val kernel = keep(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
            Imgproc.dilate(edges, edges, kernel)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = keep(Mat())
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.forEach { keep(it) }
            val minArea = small.rows() * small.cols() * MIN_FILL
            val quads = ArrayList<FloatArray>()
            for (c in contours) {
                if (Imgproc.contourArea(c) < minArea) continue
                quadOf(c)?.let { quads += it }
                // A rounded or nicked outline may only simplify to four corners once its hull is taken.
                if (quads.size < MAX_QUADS) hullQuad(c)?.let { quads += it }
                if (quads.size >= MAX_QUADS) break
            }
            val picked = CardOutline.pick(quads, minArea.toFloat()) ?: return null
            // Back to full-size region pixels, then refined there.
            val full = FloatArray(8) { (picked[it] / scale).toFloat() }
            val refined = refine(gray, full, scale)
            return FloatArray(8) { i -> refined[i] + if (i % 2 == 0) r.x else r.y }
        }

        /**
         * Every card in the whole picture, for a photo with many cards (see [PhotoQuads]): each card's corners in
         * the picture's pixels, refined, in [CardQuad]'s order, top row first. A card must cover at least [minFill]
         * of the picture.
         */
        fun findAll(minFill: Double = 0.003): List<FloatArray> {
            val gray = keep(Mat())
            Imgproc.cvtColor(reuse, gray, Imgproc.COLOR_RGBA2GRAY)
            val scale = minOf(1.0, ALL_PX / maxOf(gray.cols(), gray.rows()))
            val small = keep(Mat())
            Imgproc.resize(gray, small, Size(gray.cols() * scale, gray.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
            val bin = keep(Mat())
            val otsu = Imgproc.threshold(small, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            val edges = keep(Mat())
            Imgproc.Canny(small, edges, maxOf(10.0, otsu * 0.5), maxOf(30.0, otsu))
            val kernel = keep(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
            Imgproc.dilate(edges, edges, kernel)
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = keep(Mat())
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.forEach { keep(it) }
            val total = small.rows().toDouble() * small.cols()
            val minArea = total * minFill
            val quads = ArrayList<FloatArray>()
            for (c in contours) {
                if (Imgproc.contourArea(c) < minArea) continue
                quadOf(c)?.let { quads += it }
                hullQuad(c)?.let { quads += it }
                if (quads.size >= MAX_ALL_QUADS) break
            }
            return PhotoQuads.pick(quads, minArea.toFloat(), (total * 0.99).toFloat()).map { q ->
                refine(gray, FloatArray(8) { (q[it] / scale).toFloat() }, scale)
            }
        }

        /** Four corners of [c] simplified, or null when it doesn't simplify to four. */
        private fun quadOf(c: MatOfPoint): FloatArray? {
            val curve = keep(MatOfPoint2f(*c.toArray()))
            val approx = keep(MatOfPoint2f())
            Imgproc.approxPolyDP(curve, approx, Imgproc.arcLength(curve, true) * 0.02, true)
            val pts = approx.toArray()
            if (pts.size != 4) return null
            return FloatArray(8) { i -> (if (i % 2 == 0) pts[i / 2].x else pts[i / 2].y).toFloat() }
        }

        private fun hullQuad(c: MatOfPoint): FloatArray? {
            val hull = keep(org.opencv.core.MatOfInt())
            Imgproc.convexHull(c, hull)
            val all = c.toArray()
            val pts = hull.toArray().map { all[it] }
            if (pts.size < 4) return null
            return quadOf(keep(MatOfPoint(*pts.toTypedArray())))
        }

        /**
         * Corners refined by cornerSubPix in the full-size [gray] region. A corner that moves further than the
         * search window allows (it locked onto something else) keeps its first place.
         */
        private fun refine(gray: Mat, q: FloatArray, scale: Double): FloatArray {
            val win = (ceil(1 / scale).toInt() + 2).coerceIn(3, 12)
            val pts = MatOfPoint2f(*Array(4) { Point(q[it * 2].toDouble(), q[it * 2 + 1].toDouble()) })
            keep(pts)
            val inside = (0 until 4).all {
                q[it * 2] >= win && q[it * 2 + 1] >= win && q[it * 2] < gray.cols() - win && q[it * 2 + 1] < gray.rows() - win
            }
            if (!inside) return q
            Imgproc.cornerSubPix(
                gray, pts, Size(win.toDouble(), win.toDouble()), Size(-1.0, -1.0),
                TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 20, 0.05),
            )
            val out = pts.toArray()
            return FloatArray(8) { i ->
                val p = out[i / 2]
                val v = (if (i % 2 == 0) p.x else p.y).toFloat()
                if (abs(v - q[i]) > win * 2) q[i] else v
            }
        }

        /**
         * [part] of the card (fractions of the upright card) drawn at [outW] x [outH] with warpPerspective, from
         * [quad] (the card's corners in the card's own order, see [CardQuad.ordered]). Null for a bad shape.
         */
        fun render(quad: FloatArray, part: FloatArray, outW: Int, outH: Int): Bitmap? {
            if (outW < 4 || outH < 4) return null
            val (l, t, r, b) = part.toList()
            val pw = r - l
            val ph = b - t
            fun x(u: Float) = ((u - l) / pw * outW).toDouble()
            fun y(v: Float) = ((v - t) / ph * outH).toDouble()
            val from = keep(MatOfPoint2f(*Array(4) { Point(quad[it * 2].toDouble(), quad[it * 2 + 1].toDouble()) }))
            val to = keep(MatOfPoint2f(Point(x(0f), y(0f)), Point(x(1f), y(0f)), Point(x(1f), y(1f)), Point(x(0f), y(1f))))
            val m = keep(Imgproc.getPerspectiveTransform(from, to))
            val out = keep(Mat(outH, outW, CvType.CV_8UC4))
            Imgproc.warpPerspective(reuse, out, m, Size(outW.toDouble(), outH.toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, bmp)
            return bmp
        }

        /** Frees what this frame made (not the reused Mat). */
        fun release() {
            mats.forEach { runCatching { it.release() } }
            mats.clear()
        }
    }

    /** The card must cover this share of the searched region. */
    private const val MIN_FILL = 0.15
    /** Outlines tried at most per frame. */
    private const val MAX_QUADS = 24
    /** Longest side of the picture a photo's cards are searched in. */
    private const val ALL_PX = 1400.0
    /** Outlines tried at most in a photo. */
    private const val MAX_ALL_QUADS = 600

    /** The bounding box of [quad], rounded outwards. */
    fun boundsOf(quad: FloatArray): OcrBox {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until 4) {
            l = minOf(l, quad[i * 2]); r = maxOf(r, quad[i * 2])
            t = minOf(t, quad[i * 2 + 1]); b = maxOf(b, quad[i * 2 + 1])
        }
        return OcrBox(l.toInt(), t.toInt(), ceil(r).roundToInt(), ceil(b).roundToInt())
    }
}

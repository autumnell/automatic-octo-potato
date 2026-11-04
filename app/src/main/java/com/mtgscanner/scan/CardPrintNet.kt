package com.mtgscanner.scan

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * An ONNX model run with ONNX Runtime: float numbers in, float numbers out, whether the model works in full or
 * half precision. The same code runs on the phone and in the tests on the build server.
 */
class OnnxNet(file: File, threads: Int = 2, xnnpack: Boolean = false) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        file.path,
        OrtSession.SessionOptions().apply {
            if (xnnpack) {
                // XNNPACK does the work on its own threads; ONNX Runtime's own pool would only compete with them.
                addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                setIntraOpNumThreads(1)
            } else {
                setIntraOpNumThreads(threads)
            }
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )

    /** How a model is run: ONNX Runtime threads, and whether XNNPACK (fast on ARM phones) does the work. */
    data class Setup(val threads: Int, val xnnpack: Boolean) {
        override fun toString() = "$threads:$xnnpack"

        companion object {
            fun parse(s: String?): Setup? = s?.split(":")?.takeIf { it.size == 2 }?.let { p ->
                p[0].toIntOrNull()?.let { Setup(it, p[1] == "true") }
            }
        }
    }

    companion object {
        /** The setups worth trying on a phone, the plain one first. */
        fun setups(cores: Int): List<Setup> {
            val many = cores.coerceIn(2, 4)
            return listOfNotNull(Setup(2, false), Setup(many, false).takeIf { many > 2 }, Setup(many, true)).distinct()
        }

        /**
         * Tries each of [setups] on [input] and keeps the quickest (each warmed up once, then timed over two runs).
         * A setup that fails to load or run is skipped. Gives the net and the setup it uses.
         */
        fun fastest(file: File, setups: List<Setup>, input: FloatArray, shape: LongArray): Pair<OnnxNet, Setup>? {
            var best: Triple<OnnxNet, Setup, Long>? = null
            for (s in setups) {
                val net = runCatching { OnnxNet(file, s.threads, s.xnnpack) }.getOrNull() ?: continue
                val took = runCatching {
                    net.run(input, shape)
                    val t0 = System.nanoTime()
                    repeat(2) { net.run(input, shape) }
                    System.nanoTime() - t0
                }.getOrNull()
                if (took == null) { net.close(); continue }
                val b = best
                if (b == null || took < b.third) { b?.first?.close(); best = Triple(net, s, took) } else net.close()
            }
            return best?.let { it.first to it.second }
        }
    }
    private val inputName = session.inputNames.first()
    private val inputHalf = (session.inputInfo[inputName]?.info as? TensorInfo)?.type == OnnxJavaType.FLOAT16

    /** Runs [input] shaped [shape]; each output as floats, by name. One call at a time. */
    @Synchronized
    fun run(input: FloatArray, shape: LongArray): Map<String, FloatArray> {
        val tensor = if (inputHalf) {
            val buf = ByteBuffer.allocateDirect(input.size * 2).order(ByteOrder.nativeOrder())
            for (v in input) buf.putShort(Half.fromFloat(v))
            buf.rewind()
            OnnxTensor.createTensor(env, buf, shape, OnnxJavaType.FLOAT16)
        } else {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape)
        }
        tensor.use { t ->
            session.run(mapOf(inputName to t)).use { result ->
                val out = LinkedHashMap<String, FloatArray>()
                for ((name, value) in result) {
                    val v = value as? OnnxTensor ?: continue
                    out[name] = floats(v)
                }
                return out
            }
        }
    }

    private fun floats(t: OnnxTensor): FloatArray = if (t.info.type == OnnxJavaType.FLOAT16) {
        val shorts = t.byteBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        FloatArray(shorts.remaining()) { Half.toFloat(shorts.get(it).toInt() and 0xFFFF) }
    } else {
        val fb = t.floatBuffer
        FloatArray(fb.remaining()).also { fb.get(it) }
    }

    override fun close() {
        runCatching { session.close() }
    }
}

/**
 * Getting pictures in and out of the two card models. Pictures are ARGB pixels, row by row. Pure Kotlin.
 *
 * - The card picture model (dhvazquez/mtg_card_identification_embeddings) takes a straightened card, 320 x 448,
 *   in ImageNet's usual colour scaling, and gives 256 numbers.
 * - The corner model (dhvazquez/mtg_card_pose_estimation) takes the whole picture squashed to 640 x 640, colours
 *   0 to 1, and gives each guess's score and the four corners as fractions of the picture.
 */
object CardPrintPrep {
    const val CARD_W = 320
    const val CARD_H = 448
    const val POSE = 640
    /** A corner guess must score this much (0 to 1) to count. */
    const val POSE_MIN = 0.6f

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    val cardShape = longArrayOf(1, 3, CARD_H.toLong(), CARD_W.toLong())
    val poseShape = longArrayOf(1, 3, POSE.toLong(), POSE.toLong())

    /** The card model's input for a [CARD_W] x [CARD_H] picture. */
    fun cardInput(px: IntArray): FloatArray {
        val n = CARD_W * CARD_H
        require(px.size == n)
        val out = FloatArray(3 * n)
        for (i in 0 until n) {
            val p = px[i]
            out[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
            out[n + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
            out[2 * n + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return out
    }

    /** The corner model's input for a [POSE] x [POSE] picture. */
    fun poseInput(px: IntArray): FloatArray {
        val n = POSE * POSE
        require(px.size == n)
        val out = FloatArray(3 * n)
        for (i in 0 until n) {
            val p = px[i]
            out[i] = ((p shr 16) and 0xFF) / 255f
            out[n + i] = ((p shr 8) and 0xFF) / 255f
            out[2 * n + i] = (p and 0xFF) / 255f
        }
        return out
    }

    /**
     * The best guess's four corners in a [w] x [h] picture, in [CardOutline.order], when it scores at least
     * [minScore] and is card-shaped. [scores] has one raw score per guess; [keypoints] four x, y pairs per guess.
     */
    fun corners(scores: FloatArray, keypoints: FloatArray, w: Int, h: Int, minScore: Float = POSE_MIN): FloatArray? {
        if (scores.isEmpty() || keypoints.size < scores.size * 8) return null
        var best = 0
        for (i in scores.indices) if (scores[i] > scores[best]) best = i
        val p = 1f / (1f + exp(-scores[best]))
        if (p < minScore) return null
        val q = FloatArray(8) { k -> keypoints[best * 8 + k] * (if (k % 2 == 0) w else h) }
        val ordered = CardOutline.order(q)
        if (!CardOutline.isConvex(ordered) || !CardOutline.cardLike(ordered)) return null
        return ordered
    }
}

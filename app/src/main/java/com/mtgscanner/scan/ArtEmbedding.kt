package com.mtgscanner.scan

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Art embeddings from the on-device image model, kept as signed bytes: the vector is scaled to unit length
 * and each value to -127..127, so 1024 numbers take 1 KB. Compared by cosine similarity. Pure Kotlin for tests.
 */
object ArtEmbedding {
    /** Least cosine similarity for two pictures to be the same art (the standard model's; see [ArtModel]). */
    val MIN_COS: Float get() = ArtModel.STANDARD.minCos
    /** How far ahead of the next different art the best one must be. */
    val MIN_LEAD: Float get() = ArtModel.STANDARD.minLead

    fun quantize(v: FloatArray): ByteArray {
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x
        val norm = sqrt(sum).toFloat()
        if (norm <= 0f || norm.isNaN()) return ByteArray(v.size)
        return ByteArray(v.size) { (v[it] / norm * 127f).roundToInt().coerceIn(-127, 127).toByte() }
    }

    /** -1..1; 0 for vectors of different lengths or all zeros. */
    fun cosine(a: ByteArray, b: ByteArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0L
        var na = 0L
        var nb = 0L
        for (i in a.indices) {
            val x = a[i].toInt()
            val y = b[i].toInt()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na == 0L || nb == 0L) return 0f
        return (dot / sqrt(na.toDouble() * nb.toDouble())).toFloat()
    }

    /** The best cosine of [entry] against any of [targets] (the same picture embedded a few ways up). */
    fun best(entry: ByteArray, targets: List<ByteArray>): Float = targets.maxOfOrNull { cosine(entry, it) } ?: 0f

    /**
     * The candidate whose art is clearly closest to [targets]: at least [minCos] alike and [minLead] ahead of
     * the best candidate in another [group] (the same art reprinted doesn't compete). Null otherwise.
     */
    fun <T> pickBest(
        targets: List<ByteArray>, candidates: List<Pair<T, ByteArray>>,
        minCos: Float = MIN_COS, minLead: Float = MIN_LEAD, group: (T) -> Any? = { it },
    ): T? {
        if (targets.isEmpty() || candidates.isEmpty()) return null
        val scored = candidates.map { it.first to best(it.second, targets) }.sortedByDescending { it.second }
        val top = scored.first()
        if (top.second < minCos) return null
        val g = group(top.first)
        val other = scored.firstOrNull { group(it.first) != g }?.second ?: return top.first
        return if (top.second - other >= minLead) top.first else null
    }
}

/**
 * The art models to choose from, with everything that differs between them in one place. Embeddings of
 * different models can't be compared, so each embedding (and each art index built with one) carries its [id].
 * Both are pinned to version 1 of MediaPipe's image embedder models.
 */
enum class ArtModel(
    val id: String,
    val label: String,
    val fileName: String,
    val url: String,
    /** The file's size, to show before downloading and to spot a cut-off download. */
    val bytes: Long,
    /** Least cosine similarity for two pictures to be the same art. */
    val minCos: Float,
    /** How far ahead of the next different art the best one must be. */
    val minLead: Float,
) {
    STANDARD(
        "mnv3s-1", "Standard", "mobilenet_v3_small.tflite",
        "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite",
        4_117_670L, 0.72f, 0.04f,
    ),
    ADVANCED(
        "mnv3l-1", "Advanced", "mobilenet_v3_large.tflite",
        "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/1/mobilenet_v3_large.tflite",
        10_889_458L, 0.72f, 0.04f,
    );

    /** "4 MB", "11 MB". */
    val sizeLabel: String get() = "${(bytes + 500_000) / 1_000_000} MB"

    companion object {
        /** What index files written before models were tagged hold: only the standard model existed then. */
        val LEGACY: ArtModel = STANDARD

        fun byId(id: String?): ArtModel? = entries.firstOrNull { it.id == id }
    }
}

/** An embedding, and the id of the [ArtModel] that made it: embeddings of different models can't be compared. */
class ArtVector(val model: String, val values: ByteArray)

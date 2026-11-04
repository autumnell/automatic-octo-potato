package com.mtgscanner.scan

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A small correction for phone photos: the picture model was taught on clean card images, and a phone photo's
 * fingerprint sits a little off from its card's (warm light, sleeves, foil). This 256 x 256 matrix, fitted on real
 * scans against their cards' gallery fingerprints, moves a photo's fingerprint toward its card's. Only the photo's
 * fingerprint is changed; the gallery stays as it is. Pure Kotlin for tests.
 */
class CardAdapter(private val m: FloatArray) {
    init { require(m.size == DIM * DIM) }

    /** [v] corrected, at unit length. */
    fun apply(v: FloatArray): FloatArray {
        if (v.size != DIM) return v
        val out = FloatArray(DIM)
        for (i in 0 until DIM) {
            val x = v[i]
            if (x == 0f) continue
            val row = i * DIM
            for (j in 0 until DIM) out[j] += x * m[row + j]
        }
        return CardGallery.unit(out)
    }

    fun write(out: OutputStream) {
        val d = DataOutputStream(out)
        d.writeInt(MAGIC)
        d.writeInt(DIM)
        for (x in m) d.writeFloat(x)
        d.flush()
    }

    companion object {
        const val DIM = CardGallery.DIM
        private const val MAGIC = 0x47414431 // "GAD1"

        fun read(input: InputStream): CardAdapter? {
            val d = DataInputStream(input.buffered())
            if (d.readInt() != MAGIC || d.readInt() != DIM) return null
            return CardAdapter(FloatArray(DIM * DIM) { d.readFloat() })
        }

        /**
         * Fits the matrix W that best takes each photo fingerprint in [xs] to its card's fingerprint in [ys]
         * (least squares), pulled toward leaving fingerprints as they are by [lambda]:
         * W = (XᵀX + λI)⁻¹ (XᵀY + λI).
         */
        fun fit(xs: List<FloatArray>, ys: List<FloatArray>, lambda: Double): CardAdapter {
            require(xs.size == ys.size)
            val n = DIM
            val a = Array(n) { DoubleArray(n) }
            val b = Array(n) { DoubleArray(n) }
            for (k in xs.indices) {
                val x = xs[k]
                val y = ys[k]
                for (i in 0 until n) {
                    val xi = x[i].toDouble()
                    if (xi == 0.0) continue
                    val ai = a[i]
                    val bi = b[i]
                    for (j in 0 until n) {
                        ai[j] += xi * x[j]
                        bi[j] += xi * y[j]
                    }
                }
            }
            for (i in 0 until n) { a[i][i] += lambda; b[i][i] += lambda }
            val w = solve(a, b)
            return CardAdapter(FloatArray(n * n) { w[it / n][it % n].toFloat() })
        }

        /** X with A X = B, by Gauss-Jordan elimination with partial pivoting (A is changed). */
        private fun solve(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
            val n = a.size
            for (c in 0 until n) {
                val p = (c until n).maxBy { kotlin.math.abs(a[it][c]) }
                if (p != c) {
                    val t = a[c]; a[c] = a[p]; a[p] = t
                    val u = b[c]; b[c] = b[p]; b[p] = u
                }
                val pivot = a[c][c]
                for (k in 0 until n) { a[c][k] /= pivot; b[c][k] /= pivot }
                for (r in 0 until n) {
                    if (r == c) continue
                    val f = a[r][c]
                    if (f == 0.0) continue
                    for (k in 0 until n) { a[r][k] -= f * a[c][k]; b[r][k] -= f * b[c][k] }
                }
            }
            return b
        }
    }
}

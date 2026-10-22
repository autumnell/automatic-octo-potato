package com.mtgscanner.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter

/** QR codes for box labels, and reading them from camera frames. No Android types, for tests. */
object QrCodes {
    /** Dark modules row by row, with a 1-module quiet zone; null when [text] can't be encoded. */
    fun modules(text: String): List<BooleanArray>? = runCatching {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        List(m.height) { row -> BooleanArray(m.width) { col -> m.get(col, row) } }
    }.getOrNull()

    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    /**
     * The text of a QR code in a camera frame's brightness plane (the Y plane of a YUV image), or null when there
     * isn't one. [rowStride] is bytes per row in [y], which can be more than [width].
     */
    fun decode(y: ByteArray, rowStride: Int, width: Int, height: Int): String? = try {
        val source = PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false)
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (e: Exception) {
        null
    }
}

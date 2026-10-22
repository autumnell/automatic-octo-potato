package com.mtgscanner.data

/**
 * Links that open a box: printed as a QR code on its label and written to an NFC tag stuck on the real box.
 * "grimoire://box/12". Pure, for tests.
 */
object BoxLinks {
    const val SCHEME = "grimoire"
    const val HOST = "box"

    fun link(boxId: Long): String = "$SCHEME://$HOST/$boxId"

    /** The box id in a link, or null when [text] isn't a box link. */
    fun boxId(text: String?): Long? {
        val t = text?.trim() ?: return null
        val prefix = "$SCHEME://$HOST/"
        if (!t.startsWith(prefix, ignoreCase = true)) return null
        return t.substring(prefix.length).trimEnd('/').takeWhile { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
    }

    /** The app screen a box link opens ("browse/12"), or null. */
    fun route(text: String?): String? = boxId(text)?.let { "browse/$it" }
}

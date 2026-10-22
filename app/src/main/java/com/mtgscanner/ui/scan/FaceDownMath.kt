package com.mtgscanner.ui.scan

/**
 * Tells when the phone has been put face down (screen to the table) and when it's picked up again,
 * from the gravity sensor's z reading. Pure, for tests.
 */
class FaceDownDetector(private val holdMs: Long = 1000L) {
    var down = false
        private set
    private var since = -1L

    /** True when the phone has just been face down for [holdMs], false when just lifted, null otherwise. */
    fun feed(z: Float, nowMs: Long): Boolean? {
        if (!down) {
            if (z < DOWN_Z) {
                if (since < 0) since = nowMs
                if (nowMs - since >= holdMs) {
                    down = true
                    since = -1
                    return true
                }
            } else {
                since = -1
            }
        } else if (z > UP_Z) {
            down = false
            return false
        }
        return null
    }

    fun reset() {
        down = false
        since = -1
    }

    companion object {
        /** Gravity pulls through the back of the screen: lying face down. */
        const val DOWN_Z = -8f
        /** Tilted well away from flat face down: picked up. */
        const val UP_Z = -6f
    }
}

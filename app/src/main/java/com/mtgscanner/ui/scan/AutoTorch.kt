package com.mtgscanner.ui.scan

import com.mtgscanner.scan.LightCheck

/**
 * Turns the flashlight on in low light and off again when it's bright, for the scanner. Gives up after three
 * rounds (so it doesn't keep flickering), waits a little before coming back on, and leaves the light alone for
 * good once it's been turned by hand. Pure, for tests.
 */
class AutoTorch {
    private var darkRun = 0
    private var brightRun = 0
    /** The flashlight was turned on by low light, not by hand. */
    private var autoOn = false
    /** Turned by hand this session: leave it alone from then on. */
    private var byHand = false
    private var cooldownUntil = 0L
    private var cycles = 0

    /**
     * One frame's brightness. Returns true to turn the light on, false to turn it off, null to leave it.
     * [enabled] is the Auto flashlight setting; [paused] the scanner being paused.
     */
    fun onFrame(luma: Int, torchOn: Boolean, enabled: Boolean, paused: Boolean, now: Long): Boolean? {
        darkRun = if (luma < LightCheck.DARK) darkRun + 1 else 0
        brightRun = if (luma > LightCheck.BRIGHT) brightRun + 1 else 0
        if (!enabled || byHand || paused) return null
        if (!torchOn && darkRun >= DARK_FRAMES && now >= cooldownUntil && cycles < MAX_CYCLES) {
            autoOn = true
            darkRun = 0
            return true
        }
        if (torchOn && autoOn && brightRun >= BRIGHT_FRAMES) {
            // Bright again. Wait a bit before turning it back on, so it doesn't flicker.
            autoOn = false
            cycles++
            cooldownUntil = now + COOLDOWN_MS
            brightRun = 0
            return false
        }
        return null
    }

    /** The light was turned by hand. */
    fun turnedByHand() {
        byHand = true
        autoOn = false
    }

    companion object {
        const val DARK_FRAMES = 10
        const val BRIGHT_FRAMES = 15
        const val COOLDOWN_MS = 10_000L
        const val MAX_CYCLES = 3
    }
}

/** "Tilt to reduce glare": shown after two frames with glare over the name or footer, hidden after two without. */
class GlareHint {
    private var glareRun = 0
    private var clearRun = 0

    /** True to show the hint, false to hide it, null to leave it as it is. */
    fun onFrame(glare: Boolean): Boolean? {
        if (glare) { glareRun++; clearRun = 0 } else { clearRun++; glareRun = 0 }
        return when {
            glareRun >= 2 -> true
            clearRun >= 2 -> false
            else -> null
        }
    }
}

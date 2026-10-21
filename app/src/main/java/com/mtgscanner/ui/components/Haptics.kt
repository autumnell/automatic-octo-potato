package com.mtgscanner.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Three buzzes the scanner can tell apart without looking: one crisp tick when a card is added,
 * two light taps when one is set aside, and a heavy double knock when the box it went to is full.
 *
 * Uses the view's own haptics, so it needs no vibrate permission and follows the phone's
 * touch-feedback setting. Safe to call from the main thread at any time.
 */
object Haptics {
    fun added(view: View) {
        pulse(view, if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun setAside(view: View) {
        val light = if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        pulse(view, light)
        pulse(view, light, delayMs = 110)
    }

    fun boxFull(view: View) {
        val heavy = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        pulse(view, heavy)
        pulse(view, HapticFeedbackConstants.LONG_PRESS, delayMs = 180)
    }

    private fun pulse(view: View, type: Int, delayMs: Long = 0) {
        val run = Runnable { runCatching { view.performHapticFeedback(type) } }
        if (delayMs > 0) view.postDelayed(run, delayMs) else view.post(run)
    }
}

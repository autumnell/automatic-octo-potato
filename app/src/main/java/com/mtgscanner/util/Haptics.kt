package com.mtgscanner.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.lang.ref.WeakReference

/**
 * Light haptic ticks for add, move and delete. Uses the screen's own view, so it needs no
 * vibrate permission and follows the phone's touch-feedback setting. The view is held weakly.
 */
object Haptics {
    @Volatile private var view: WeakReference<View>? = null

    /** Remembers the app's view so [tick] works from view models too. Call once from any screen. */
    @Composable
    fun Attach() {
        val v = LocalView.current
        DisposableEffect(v) {
            view = WeakReference(v)
            // Kept after the screen closes: every screen shares the activity's view, and it's held weakly.
            onDispose { }
        }
    }

    /** A short confirmation tick. Safe from any thread; does nothing before [Attach]. */
    fun tick() {
        if (!com.mtgscanner.ui.theme.AppSettings.haptics) return
        val v = view?.get() ?: return
        val type = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        v.post { runCatching { v.performHapticFeedback(type) } }
    }

    /** A very light tick, for each card added while dragging to select. */
    fun step() {
        if (!com.mtgscanner.ui.theme.AppSettings.haptics) return
        val v = view?.get() ?: return
        val type = if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK
        v.post { runCatching { v.performHapticFeedback(type) } }
    }

    /** A press held long enough to open something (a tab's shortcuts). */
    fun hold() {
        if (!com.mtgscanner.ui.theme.AppSettings.haptics) return
        val v = view?.get() ?: return
        v.post { runCatching { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } }
    }
}

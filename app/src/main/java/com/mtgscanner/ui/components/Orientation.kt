package com.mtgscanner.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the phone upright while this screen shows (Scan, Game and the playtest table are laid out for it); the
 * rest of the app turns with the phone. Counted, not saved and restored: during a page change two upright screens
 * overlap, and the one leaving must not put back a value the one arriving set.
 */
@Composable
fun KeepUpright() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        UprightCount.hold(activity)
        onDispose { UprightCount.release(activity) }
    }
}

/** How many shown screens want the phone upright. Main thread only (composition and its disposal). */
private object UprightCount {
    private var holds = 0

    fun hold(activity: Activity) {
        holds++
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun release(activity: Activity) {
        holds = (holds - 1).coerceAtLeast(0)
        if (holds == 0) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

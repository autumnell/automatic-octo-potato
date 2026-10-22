package com.mtgscanner.ui.scan

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Volume buttons on the Scan screen: up adds the card showing, down skips it. MainActivity hands key
 * presses here; the Scan screen sets [handler] while it's showing. When nothing takes the press, volume
 * works as usual.
 */
object VolumeKeys {
    private var prefs: SharedPreferences? = null

    /** "Volume buttons scan" in the scan settings. */
    var enabled by mutableStateOf(true)
        private set

    /** Set by the Scan screen while it's open. Returns true when it used the press. */
    @Volatile var handler: ((up: Boolean) -> Boolean)? = null

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("volumeScan", true)
    }

    fun update(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("volumeScan", on)?.apply()
    }

    /** From MainActivity.onKeyDown. A held button acts once; its repeats are swallowed so volume doesn't change either. */
    fun press(up: Boolean, repeatCount: Int): Boolean {
        if (!enabled) return false
        val h = handler ?: return false
        if (repeatCount > 0) return lastUsed
        lastUsed = h(up)
        return lastUsed
    }

    private var lastUsed = false
}

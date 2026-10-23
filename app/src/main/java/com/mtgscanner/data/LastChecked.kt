package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * When each box's last Box check ended, and whether the box list is ordered by it. Kept in prefs by box id.
 * [BoxPrefs.checks] is when a check started (or the box was first seen), for the reminder nudge.
 */
object LastChecked {
    private var prefs: SharedPreferences? = null

    /** Box id → when its last Box check ended, in millis. */
    var times by mutableStateOf<Map<Long, Long>>(emptyMap())
        private set
    /** Boxes listed longest-unchecked first, instead of in Auto-sort order. */
    var oldestFirst by mutableStateOf(false)
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("box_checked", Context.MODE_PRIVATE)
        prefs = p
        times = BoxReminders.decode(p.getString("done", null))
        oldestFirst = p.getBoolean("oldestFirst", false)
    }

    /** A Box check of [boxId] just ended. */
    fun mark(context: Context, boxId: Long, now: Long = System.currentTimeMillis()) {
        load(context)
        times = times + (boxId to now)
        prefs?.edit()?.putString("done", BoxReminders.encode(times))?.apply()
    }

    fun setOldestFirst(context: Context, on: Boolean) {
        load(context)
        oldestFirst = on
        prefs?.edit()?.putBoolean("oldestFirst", on)?.apply()
    }
}

package com.mtgscanner.work

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/** When to nudge about backups: none for over [STALE_DAYS] days (or ever), once there are cards. */
object DriveBackupReminder {
    const val STALE_DAYS = 14L
    const val SNOOZE_DAYS = 7L
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val PREFS = "backup"
    private const val KEY_SNOOZE = "remind_after"

    /** "Later" hides the reminder until this time (0 = not snoozed). */
    var snoozedUntil by mutableLongStateOf(0L)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        snoozedUntil = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SNOOZE, 0L)
        loaded = true
    }

    /** Hides the reminder for [SNOOZE_DAYS] days. */
    fun snooze(context: Context, now: Long = System.currentTimeMillis()) {
        snoozedUntil = now + SNOOZE_DAYS * DAY_MS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_SNOOZE, snoozedUntil).apply()
    }

    /** Pure rule: show it when there are cards, it isn't snoozed, and the last backup is missing or too old. */
    fun due(now: Long, lastBackup: Long, snoozedUntil: Long, hasCards: Boolean): Boolean {
        if (!hasCards || now < snoozedUntil) return false
        return lastBackup <= 0L || now - lastBackup > STALE_DAYS * DAY_MS
    }

    /** Whole days since [lastBackup], or null when there's never been one. */
    fun daysSince(now: Long, lastBackup: Long): Long? = if (lastBackup <= 0L) null else ((now - lastBackup) / DAY_MS).coerceAtLeast(0)
}

package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Every notification the app has shown, newest first, for the bell on Home. Kept in prefs "notifications"
 * (the newest [NotificationLogMath.MAX]); each can be cleared on its own or all at once. Read as Compose state.
 */
object NotificationLog {
    private var prefs: SharedPreferences? = null

    var entries by mutableStateOf<List<NotificationEntry>>(emptyList())
        private set
    /** When the bell's list was last opened; newer entries count as unread. */
    var seenAt by mutableStateOf(0L)
        private set

    val unread: Int get() = entries.count { it.at > seenAt }

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("notifications", Context.MODE_PRIVATE)
        prefs = p
        entries = NotificationLogMath.decode(p.getString("log", null))
        seenAt = p.getLong("seenAt", 0L)
    }

    /** Notes a notification just shown. [where] is where tapping it goes ("home", "card/12", "settings"…). */
    @Synchronized
    fun add(context: Context, title: String, text: String, where: String, now: Long = System.currentTimeMillis()) {
        load(context)
        save(NotificationLogMath.add(entries, NotificationEntry(now, where, title, text)))
    }

    @Synchronized
    fun remove(context: Context, entry: NotificationEntry) {
        load(context)
        save(entries - entry)
    }

    @Synchronized
    fun clear(context: Context) {
        load(context)
        save(emptyList())
    }

    fun markSeen(context: Context, now: Long = System.currentTimeMillis()) {
        load(context)
        seenAt = now
        prefs?.edit()?.putLong("seenAt", now)?.apply()
    }

    private fun save(list: List<NotificationEntry>) {
        entries = list
        prefs?.edit()?.putString("log", NotificationLogMath.encode(list))?.apply()
    }
}

/** One notification: when, where tapping it goes, its title and its text (lines joined by newlines). */
data class NotificationEntry(val at: Long, val where: String, val title: String, val text: String)

/** Pure parts of [NotificationLog]: the saved form and the size limit. */
object NotificationLogMath {
    const val MAX = 100

    /** [e] first, keeping the newest [MAX]. */
    fun add(list: List<NotificationEntry>, e: NotificationEntry): List<NotificationEntry> = (listOf(e) + list).take(MAX)

    /** One entry per line: "at<TAB>where<TAB>title<TAB>text", newlines in the text written as \u001F. */
    fun encode(list: List<NotificationEntry>): String = list.joinToString("\n") { e ->
        listOf(e.at.toString(), clean(e.where), clean(e.title), e.text.replace('\t', ' ').replace("\r", "").replace('\n', SEP)).joinToString("\t")
    }

    fun decode(s: String?): List<NotificationEntry> = s.orEmpty().lineSequence().mapNotNull { line ->
        val p = line.split('\t', limit = 4)
        if (p.size < 4) return@mapNotNull null
        val at = p[0].toLongOrNull() ?: return@mapNotNull null
        NotificationEntry(at, p[1], p[2], p[3].replace(SEP, '\n'))
    }.toList()

    private const val SEP = '\u001F'
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace("\r", "")
}

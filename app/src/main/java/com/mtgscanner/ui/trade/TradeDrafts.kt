package com.mtgscanner.ui.trade

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Both sides of an unfinished trade. */
@Serializable
data class TradeDraft(val give: List<TradeItem> = emptyList(), val get: List<TradeItem> = emptyList()) {
    val isEmpty: Boolean get() = give.isEmpty() && get.isEmpty()
}

/**
 * Unfinished trades per trade binder, so leaving the screen (or the app) doesn't lose one.
 * Kept in SharedPreferences as JSON; a draft that can't be read is dropped.
 */
object TradeDrafts {
    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null
    private val cache = HashMap<Long, TradeDraft>()

    @Synchronized
    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("trade_drafts", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun get(binderId: Long): TradeDraft {
        cache[binderId]?.let { return it }
        val text = try { prefs?.getString(binderId.toString(), null) } catch (e: Exception) { null }
        val d = text?.let { runCatching { json.decodeFromString(TradeDraft.serializer(), it) }.getOrNull() } ?: TradeDraft()
        cache[binderId] = d
        return d
    }

    @Synchronized
    fun put(binderId: Long, draft: TradeDraft) {
        if (cache[binderId] == draft) return
        cache[binderId] = draft
        val p = prefs ?: return
        if (draft.isEmpty) p.edit().remove(binderId.toString()).apply()
        else runCatching { json.encodeToString(TradeDraft.serializer(), draft) }.getOrNull()?.let { p.edit().putString(binderId.toString(), it).apply() }
    }
}

package com.mtgscanner.ui.search

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Recent searches and recently viewed cards, kept in preferences. */
class SearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val viewedSerializer = ListSerializer(Viewed.serializer())

    fun recent(): List<String> =
        prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun viewed(): List<Viewed> =
        runCatching { json.decodeFromString(viewedSerializer, prefs.getString("viewed", "[]") ?: "[]") }.getOrDefault(emptyList())

    /** Puts [q] first and returns the new list. */
    fun addRecent(q: String): List<String> {
        val text = q.replace('\n', ' ').trim()
        if (text.isEmpty()) return recent()
        val list = (listOf(text) + recent().filterNot { it.equals(text, ignoreCase = true) }).take(MAX_RECENT)
        prefs.edit().putString("recent", list.joinToString("\n")).apply()
        return list
    }

    /** Puts [v] first and returns the new list. */
    fun addViewed(v: Viewed): List<Viewed> {
        val list = (listOf(v) + viewed().filterNot { it.id == v.id }).take(MAX_VIEWED)
        prefs.edit().putString("viewed", json.encodeToString(viewedSerializer, list)).apply()
        return list
    }

    fun clear() = prefs.edit().remove("recent").remove("viewed").apply()

    private companion object {
        const val MAX_RECENT = 8
        const val MAX_VIEWED = 12
    }
}

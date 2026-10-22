package com.mtgscanner.ui.search

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Recent searches and recently viewed cards, kept in preferences. */
class SearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val viewedSerializer = ListSerializer(Viewed.serializer())

    private val recentSerializer = ListSerializer(RecentSearch.serializer())

    init {
        // Saved searches are gone from the Search tab: drop what older versions kept.
        if (prefs.contains("saved")) prefs.edit().remove("saved").apply()
    }

    /** Recent searches with their filters. Older plain-text entries come back as typed searches with no filters. */
    fun recent(): List<RecentSearch> {
        prefs.getString("recent2", null)?.let { raw ->
            return runCatching { json.decodeFromString(recentSerializer, raw) }.getOrDefault(emptyList())
        }
        return prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }.map { RecentSearch(it) }
    }

    fun viewed(): List<Viewed> =
        runCatching { json.decodeFromString(viewedSerializer, prefs.getString("viewed", "[]") ?: "[]") }.getOrDefault(emptyList())

    /** Puts [r] first and returns the new list. */
    fun addRecent(r: RecentSearch): List<RecentSearch> {
        val item = r.copy(text = r.text.replace('\n', ' ').trim())
        if (item.text.isEmpty() && !item.filters.narrows) return recent()
        val list = (listOf(item) + recent().filterNot { it.sameAs(item) }).take(MAX_RECENT)
        prefs.edit().putString("recent2", json.encodeToString(recentSerializer, list)).remove("recent").apply()
        return list
    }

    /** Puts [v] first and returns the new list. */
    fun addViewed(v: Viewed): List<Viewed> {
        val list = (listOf(v) + viewed().filterNot { it.id == v.id }).take(MAX_VIEWED)
        prefs.edit().putString("viewed", json.encodeToString(viewedSerializer, list)).apply()
        return list
    }

    fun clear() = prefs.edit().remove("recent").remove("recent2").remove("viewed").apply()

    private companion object {
        const val MAX_RECENT = 8
        const val MAX_VIEWED = 12
    }
}

/** A recent search: the typed words and the filters used, or an exact card name picked from suggestions. */
@kotlinx.serialization.Serializable
data class RecentSearch(
    val text: String,
    val filters: SearchFilters = SearchFilters(),
    /** Picked from suggestions: reopens every printing of that exact name. */
    val exactName: Boolean = false,
) {
    /** The filters as Scryfall terms, shown under the words; empty for none. */
    val filterLabel: String get() = if (exactName) "" else filters.toQuery()

    fun sameAs(o: RecentSearch): Boolean =
        text.equals(o.text, ignoreCase = true) && exactName == o.exactName && (exactName || filters == o.filters)
}

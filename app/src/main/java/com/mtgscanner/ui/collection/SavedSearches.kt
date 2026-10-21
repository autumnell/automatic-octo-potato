package com.mtgscanner.ui.collection

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A named search with its filters, like "Red rares under $5". [boxId] is set when it was saved inside a box. */
@Serializable
data class SavedSearch(val name: String, val filters: Filters, val boxId: Long? = null)

/**
 * Saved searches and recent search words for browsing the collection.
 * Every instance shares the same lists, so Browse and the smart lists on Collection stay in step.
 */
class SavedSearchStore(context: Context) {
    private val prefs = context.getSharedPreferences("searches", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val savedSer = ListSerializer(SavedSearch.serializer())
    private val recentSer = ListSerializer(String.serializer())

    private val _saved: MutableStateFlow<List<SavedSearch>> = shared("saved") { load(savedSer, "saved") }
    private val _recent: MutableStateFlow<List<String>> = shared("recent") { load(recentSer, "recent") }
    val saved: StateFlow<List<SavedSearch>> = _saved
    val recent: StateFlow<List<String>> = _recent

    private fun <T> load(ser: kotlinx.serialization.KSerializer<List<T>>, key: String): List<T> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()

    fun save(name: String, filters: Filters, boxId: Long? = null) {
        val n = name.trim().ifEmpty { return }
        val list = listOf(SavedSearch(n, filters, boxId)) + _saved.value.filterNot { it.name.equals(n, ignoreCase = true) }
        _saved.value = list
        prefs.edit().putString("saved", json.encodeToString(savedSer, list)).apply()
    }

    fun delete(name: String) {
        val list = _saved.value.filterNot { it.name == name }
        _saved.value = list
        prefs.edit().putString("saved", json.encodeToString(savedSer, list)).apply()
    }

    fun addRecent(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val list = (listOf(q) + _recent.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENT)
        _recent.value = list
        prefs.edit().putString("recent", json.encodeToString(recentSer, list)).apply()
    }

    fun clearRecent() {
        _recent.value = emptyList()
        prefs.edit().remove("recent").apply()
    }

    companion object {
        const val MAX_RECENT = 8
        private val flows = HashMap<String, MutableStateFlow<*>>()

        @Suppress("UNCHECKED_CAST")
        private fun <T> shared(key: String, load: () -> List<T>): MutableStateFlow<List<T>> = synchronized(flows) {
            flows.getOrPut(key) { MutableStateFlow(load()) } as MutableStateFlow<List<T>>
        }
    }
}

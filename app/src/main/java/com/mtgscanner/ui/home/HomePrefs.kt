package com.mtgscanner.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A section of the Home screen that can be shown, hidden and moved. */
enum class HomeSection(val key: String, val label: String) {
    TO_SORT("sort", "To be sorted"),
    GOALS("goals", "Goals"),
    RECENT("recent", "Recently added"),
    MOVERS("movers", "Price moves this week"),
    VALUABLE("valuable", "Most valuable"),
    GAINERS("gainers", "Biggest gains"),
    BOX_VALUES("boxes", "Value by box"),
    BULK("bulk", "Bulk estimate"),
    COLORS("colors", "Colors"),
    ALERTS("alerts", "Price alerts"),
    ;

    companion object {
        fun of(key: String): HomeSection? = entries.firstOrNull { it.key == key }
    }
}

/** Which Home sections and stat tiles show, in what order, and the bulk buy rate. Saved on the phone. */
object HomePrefs {
    private var prefs: SharedPreferences? = null

    /** Shown sections, in order. */
    var sections by mutableStateOf(HomeSection.entries.toList())
        private set

    /** Dollars a bulk buyer pays per 1000 commons and uncommons. */
    var bulkRate by mutableDoubleStateOf(5.0)
        private set

    /** Stat tiles under the value card, in order (2 to 4). */
    var tiles by mutableStateOf(TileMetric.DEFAULT)
        private set

    /** The saved search the "Saved list" tile counts, by name. */
    var tileList by mutableStateOf<String?>(null)
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString("homeSections", null)
        if (saved != null) {
            val shown = saved.split(',').mapNotNull { HomeSection.of(it.trim()) }.distinct()
            // Sections added in later versions show up at the end until hidden.
            val known = p.getString("homeKnown", "").orEmpty().split(',').toSet()
            val added = HomeSection.entries.filter { it.key !in known && it !in shown }
            sections = shown + added
        }
        bulkRate = p.getFloat("bulkRate", 5f).toDouble()
        p.getString("homeTiles", null)?.let { saved ->
            val list = saved.split(',').mapNotNull { TileMetric.of(it.trim()) }.distinct().take(TileMetric.MAX)
            if (list.size >= TileMetric.MIN) tiles = list
        }
        tileList = p.getString("homeTileList", null)
    }

    /** Saves the tiles and, for the "Saved list" tile, which search it counts. */
    fun updateTiles(list: List<TileMetric>, listName: String?) {
        val clean = list.distinct().take(TileMetric.MAX)
        if (clean.size < TileMetric.MIN) return
        tiles = clean
        tileList = listName?.takeIf { TileMetric.CUSTOM in clean }
        prefs?.edit()
            ?.putString("homeTiles", clean.joinToString(",") { it.key })
            ?.putString("homeTileList", tileList)
            ?.apply()
    }

    fun updateSections(list: List<HomeSection>) {
        sections = list.distinct()
        prefs?.edit()
            ?.putString("homeSections", sections.joinToString(",") { it.key })
            ?.putString("homeKnown", HomeSection.entries.joinToString(",") { it.key })
            ?.apply()
    }

    fun updateBulkRate(v: Double) {
        bulkRate = v
        prefs?.edit()?.putFloat("bulkRate", v.toFloat())?.apply()
    }
}

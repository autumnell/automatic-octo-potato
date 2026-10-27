package com.mtgscanner.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which Home sections and stat tiles show, in what order, and the bulk buy rate. Saved on the phone. */
object HomePrefs {
    private var prefs: SharedPreferences? = null

    /** Shown sections, in order. */
    var sections by mutableStateOf(HomeOrder.DEFAULT)
        private set

    /** Dollars a bulk buyer pays per 1000 commons and uncommons. */
    var bulkRate by mutableDoubleStateOf(5.0)
        private set

    /** Stat tiles in their Home section, in order (2 to 4). */
    var tiles by mutableStateOf(TileMetric.DEFAULT)
        private set

    /** The color bar under the value card. */
    var colorBar by mutableStateOf(true)
        private set

    /** The saved search the "Saved list" tile counts, by name. */
    var tileList by mutableStateOf<String?>(null)
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        prefs = p
        // Sections added in later versions show up at the end until hidden; the newest-cards strip goes first, once.
        sections = HomeOrder.load(p.getString("homeSections", null), p.getString("homeKnown", null))
        bulkRate = p.getFloat("bulkRate", 5f).toDouble()
        p.getString("homeTiles", null)?.let { saved ->
            val list = saved.split(',').mapNotNull { TileMetric.of(it.trim()) }.distinct().take(TileMetric.MAX)
            if (list.size >= TileMetric.MIN) tiles = list
        }
        tileList = p.getString("homeTileList", null)
        colorBar = p.getBoolean("homeColorBar", true)
    }

    fun updateColorBar(on: Boolean) {
        colorBar = on
        prefs?.edit()?.putBoolean("homeColorBar", on)?.apply()
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

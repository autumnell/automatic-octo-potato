package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Wishlist target prices ("tell me when it's under $40"), by oracle id. The daily price job checks them
 * (work/WishTargetAlerts.kt). Kept in prefs, which backups carry.
 */
object WishTargets {
    const val PREFS = "wish_targets"
    private const val ALERTED = "alerted:"
    private var prefs: SharedPreferences? = null

    /** Target price in dollars, by oracle id. */
    var targets by mutableStateOf<Map<String, Double>>(emptyMap())
        private set

    @Synchronized
    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        targets = p.all.mapNotNull { (k, v) -> if (k.startsWith(ALERTED)) null else (v as? String)?.toDoubleOrNull()?.let { k to it } }.toMap()
    }

    /** Sets [oracleId]'s target, or clears it when [price] is null or not above zero. */
    fun set(oracleId: String, price: Double?) {
        val p = prefs ?: return
        val e = p.edit().remove(ALERTED + oracleId)
        if (price == null || price <= 0) { e.remove(oracleId); targets = targets - oracleId }
        else { e.putString(oracleId, price.toString()); targets = targets + (oracleId to price) }
        e.apply()
    }

    /** Drops targets for cards no longer on the wishlist. */
    fun keepOnly(oracleIds: Set<String>) {
        val gone = targets.keys.filter { it !in oracleIds }
        if (gone.isEmpty()) return
        val e = prefs?.edit() ?: return
        gone.forEach { e.remove(it).remove(ALERTED + it) }
        e.apply()
        targets = targets - gone.toSet()
    }

    /**
     * Wished cards whose price is at or under their target and haven't been told about yet. Each is told once, until
     * its price goes back over the target. [prices] is today's price by oracle id.
     */
    fun due(context: Context, prices: Map<String, Double?>): List<Pair<String, Double>> {
        load(context)
        val p = prefs ?: return emptyList()
        val e = p.edit()
        val out = WishTargetMath.due(targets, prices, alerted = { p.getBoolean(ALERTED + it, false) }) { id, on ->
            if (on) e.putBoolean(ALERTED + id, true) else e.remove(ALERTED + id)
        }
        e.apply()
        return out
    }
}

/** The target check, kept apart from prefs so it can be tested. */
object WishTargetMath {
    /** Cards newly at or under target (oracle id to price); [mark] records who's been told, or clears it once over. */
    fun due(
        targets: Map<String, Double>, prices: Map<String, Double?>, alerted: (String) -> Boolean, mark: (String, Boolean) -> Unit,
    ): List<Pair<String, Double>> = targets.mapNotNull { (id, target) ->
        val price = prices[id] ?: return@mapNotNull null
        val under = price <= target
        val told = alerted(id)
        when {
            under && !told -> { mark(id, true); id to price }
            !under && told -> { mark(id, false); null }
            else -> null
        }
    }
}

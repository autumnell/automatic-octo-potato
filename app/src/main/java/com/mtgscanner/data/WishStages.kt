package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where each wishlist card stands: to buy (the default) or ordered, by oracle id, kept in prefs ("wish_stages").
 * Received isn't a stage: receiving a card adds it to the collection and takes it off the wishlist.
 */
object WishStages {
    private var prefs: SharedPreferences? = null

    /** Oracle ids of wishlist cards marked ordered. */
    var ordered by mutableStateOf<Set<String>>(emptySet())
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("wish_stages", Context.MODE_PRIVATE)
        prefs = p
        ordered = p.getString("ordered", null).orEmpty().split(',').filter { it.isNotBlank() }.toSet()
    }

    fun setOrdered(oracleId: String, on: Boolean) {
        ordered = if (on) ordered + oracleId else ordered - oracleId
        prefs?.edit()?.putString("ordered", ordered.joinToString(","))?.apply()
    }

    /** Drops marks for cards no longer on the wishlist. */
    fun keepOnly(oracleIds: Set<String>) {
        if (ordered.all { it in oracleIds }) return
        ordered = ordered.filter { it in oracleIds }.toSet()
        prefs?.edit()?.putString("ordered", ordered.joinToString(","))?.apply()
    }
}

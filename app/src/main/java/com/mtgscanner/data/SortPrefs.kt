package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App-wide sorting choices: keep-a-playset, bulk price floor and the scanner's set lock. */
object SortPrefs {
    private var prefs: SharedPreferences? = null

    /** Copies of each card to keep in main boxes; 0 = off. */
    var playsetLimit by mutableStateOf(0)
        private set
    var extrasBoxId by mutableStateOf<Long?>(null)
        private set
    /** Cards cheaper than this go to [bulkBoxId]; null = off. */
    var bulkFloor by mutableStateOf<Double?>(null)
        private set
    var bulkBoxId by mutableStateOf<Long?>(null)
        private set
    /** When set, the scanner only looks for cards from this set (lower-case code). */
    var setLock by mutableStateOf<String?>(null)
        private set

    val policy: SortPolicy
        get() = SortPolicy(playsetLimit, extrasBoxId.takeIf { playsetLimit > 0 }, bulkFloor, bulkBoxId.takeIf { bulkFloor != null })

    fun init(context: Context) {
        val p = context.getSharedPreferences("sorting", Context.MODE_PRIVATE)
        prefs = p
        playsetLimit = p.getInt("playset", 0)
        extrasBoxId = p.getLong("extrasBox", -1L).takeIf { it >= 0 }
        // Stored in whole cents, so $0.10 reads back as exactly 0.10. Older versions saved a Float.
        if (!p.contains("bulkFloorCents") && p.contains("bulkFloor")) {
            val cents = p.getFloat("bulkFloor", -1f).takeIf { it >= 0 }?.let { toCents(it.toDouble()) } ?: -1
            p.edit().putInt("bulkFloorCents", cents).remove("bulkFloor").apply()
        }
        bulkFloor = fromCents(p.getInt("bulkFloorCents", -1))
        bulkBoxId = p.getLong("bulkBox", -1L).takeIf { it >= 0 }
        setLock = p.getString("setLock", null)?.takeIf { it.isNotBlank() }
    }

    fun updatePlayset(limit: Int, boxId: Long?) {
        playsetLimit = limit; extrasBoxId = boxId
        prefs?.edit()?.putInt("playset", limit)?.putLong("extrasBox", boxId ?: -1L)?.apply()
    }

    fun updateBulk(floor: Double?, boxId: Long?) {
        val cents = floor?.let(::toCents) ?: -1
        bulkFloor = fromCents(cents); bulkBoxId = boxId
        prefs?.edit()?.putInt("bulkFloorCents", cents)?.putLong("bulkBox", boxId ?: -1L)?.apply()
    }

    internal fun toCents(dollars: Double): Int = Math.round(dollars * 100).toInt().coerceAtLeast(0)
    internal fun fromCents(cents: Int): Double? = if (cents < 0) null else cents / 100.0

    fun updateSetLock(code: String?) {
        setLock = code?.lowercase()?.takeIf { it.isNotBlank() }
        prefs?.edit()?.putString("setLock", setLock.orEmpty())?.apply()
    }

    /** A deleted box can't stay the extras or bulk box. */
    fun forgetBox(id: Long) {
        if (extrasBoxId == id) updatePlayset(0, null)
        if (bulkBoxId == id) updateBulk(null, null)
    }
}

package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Small per-box things kept in prefs, not the database: the shelf picture, when each box was last checked, the
 * reminder interval, and which new sets were already offered. Loads on first use; read as Compose state.
 */
object BoxPrefs {
    private var prefs: SharedPreferences? = null

    var shelf by mutableStateOf(ShelfLayout())
        private set
    /** Box id → last Box check (or when the app first saw the box), in millis. */
    var checks by mutableStateOf<Map<Long, Long>>(emptyMap())
        private set
    /** Months between Box check nudges; 0 = off. */
    var reminderMonths by mutableStateOf(3)
        private set
    /** Box check nudges stay hidden until then. */
    var snoozedUntil by mutableStateOf(0L)
        private set
    /** Lower-case set codes already offered by New set setup. */
    var offeredSets by mutableStateOf<Set<String>>(emptySet())
        private set
    /** The set being offered now, found by the daily check (saved as "code|name|type|date"). */
    var pendingSet by mutableStateOf<SetRelease?>(null)
        private set
    /** Box id → where the box physically is and how many rows it has. */
    var places by mutableStateOf<Map<Long, BoxPlace>>(emptyMap())
        private set
    /** Boxes marked "To be sorted": only cards in these show as waiting to be sorted. */
    var toSort by mutableStateOf<Set<Long>>(emptySet())
        private set
    /** Day (yyyy-MM-dd) of the last look at Scryfall's sets. */
    var setsCheckedDay: String = ""
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("boxes", Context.MODE_PRIVATE)
        prefs = p
        shelf = ShelfLayout.decode(p.getString("shelf", null))
        checks = BoxReminders.decode(p.getString("checks", null))
        reminderMonths = p.getInt("reminderMonths", 3).takeIf { it in BoxReminders.CHOICES } ?: 3
        snoozedUntil = p.getLong("checkSnooze", 0L)
        offeredSets = p.getString("offeredSets", null).orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        pendingSet = p.getString("pendingSet", null)?.split('|')?.takeIf { it.size == 4 }?.let { SetRelease(it[0], it[1], it[2], it[3]) }
        setsCheckedDay = p.getString("setsChecked", "").orEmpty()
        places = BoxPlace.decode(p.getString("places", null))
        toSort = p.getString("toSort", null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** [toSort] as a flow, so lists of cards to sort update when a box is marked. */
    fun toSortFlow(): kotlinx.coroutines.flow.Flow<Set<Long>> = androidx.compose.runtime.snapshotFlow { toSort }

    /** Marks [boxId] as a box of cards waiting to be sorted, or not. */
    fun setToSort(context: Context, boxId: Long, on: Boolean) {
        load(context)
        toSort = if (on) toSort + boxId else toSort - boxId
        prefs?.edit()?.putString("toSort", toSort.joinToString(","))?.apply()
    }

    /** Reads everything again, after a restore replaced the saved prefs. */
    fun reload(context: Context) {
        prefs = null
        load(context)
    }

    fun saveShelf(context: Context, layout: ShelfLayout) {
        load(context)
        shelf = layout
        prefs?.edit()?.putString("shelf", layout.encode())?.apply()
    }

    /** A Box check of [boxId] just started. */
    fun markChecked(context: Context, boxId: Long, now: Long = System.currentTimeMillis()) {
        load(context)
        saveChecks(checks + (boxId to now))
    }

    /** Boxes the app hasn't seen before count from today, so a new install doesn't nag about every box at once. */
    fun noteBoxes(context: Context, boxIds: List<Long>, now: Long = System.currentTimeMillis()) {
        load(context)
        val unseen = BoxReminders.unseen(boxIds, checks)
        if (unseen.isNotEmpty()) saveChecks(checks + unseen.associateWith { now })
    }

    private fun saveChecks(map: Map<Long, Long>) {
        checks = map
        prefs?.edit()?.putString("checks", BoxReminders.encode(map))?.apply()
    }

    fun setReminder(context: Context, months: Int) {
        load(context)
        reminderMonths = months
        prefs?.edit()?.putInt("reminderMonths", months)?.apply()
    }

    fun snooze(context: Context, days: Int = 7) {
        load(context)
        snoozedUntil = System.currentTimeMillis() + days * 24L * 60 * 60 * 1000
        prefs?.edit()?.putLong("checkSnooze", snoozedUntil)?.apply()
    }

    fun setPendingSet(context: Context, s: SetRelease?, day: String) {
        load(context)
        pendingSet = s
        setsCheckedDay = day
        prefs?.edit()
            ?.putString("pendingSet", s?.let { listOf(it.code, it.name.replace('|', ' '), it.type, it.releasedAt.orEmpty()).joinToString("|") })
            ?.putString("setsChecked", day)
            ?.apply()
    }

    /** The offer for [code] was seen and handled; it won't come back. */
    fun markOffered(context: Context, code: String) {
        load(context)
        offeredSets = offeredSets + code.lowercase()
        val e = prefs?.edit()?.putString("offeredSets", offeredSets.joinToString(","))
        if (pendingSet?.code.equals(code, ignoreCase = true)) {
            pendingSet = null
            e?.remove("pendingSet")
        }
        e?.apply()
    }

    /** Saves where [boxId] is kept and its rows; an empty place is forgotten. */
    fun setPlace(context: Context, boxId: Long, place: BoxPlace) {
        load(context)
        places = if (place.isEmpty) places - boxId else places + (boxId to place.clean())
        prefs?.edit()?.putString("places", BoxPlace.encode(places))?.apply()
    }

    fun place(boxId: Long): BoxPlace = places[boxId] ?: BoxPlace()
}

/** Where a box physically is ("Closet, top shelf") and how many rows of cards it holds. */
data class BoxPlace(val location: String = "", val rows: Int = 1) {
    val isEmpty: Boolean get() = location.isBlank() && rows <= 1

    fun clean() = BoxPlace(location.trim().replace('\n', ' ').replace('\t', ' ').take(60), rows.coerceIn(1, MAX_ROWS))

    companion object {
        const val MAX_ROWS = 8

        /** One box per line: "id<TAB>rows<TAB>location". */
        fun encode(map: Map<Long, BoxPlace>): String =
            map.entries.joinToString("\n") { (id, p) -> "$id\t${p.rows}\t${p.location}" }

        fun decode(s: String?): Map<Long, BoxPlace> = s.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            id to BoxPlace(parts.getOrNull(2).orEmpty(), parts.getOrNull(1)?.toIntOrNull() ?: 1).clean()
        }.filterNot { it.second.isEmpty }.toMap()
    }
}

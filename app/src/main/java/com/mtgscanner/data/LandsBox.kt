package com.mtgscanner.data

import android.content.Context
import androidx.room.withTransaction
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.data.db.facts

/**
 * Lands have their own box. Colorless boxes stopped taking lands, so once, for collections with a colorless box:
 * a "Lands" box is made right after it (unless a box for lands is already there), and the lands in colorless
 * boxes move into it. Returns how many card rows moved.
 */
object LandsBox {
    private const val PREFS = "boxes_once"
    private const val KEY = "lands_box_v1"

    suspend fun separateOnce(ctx: Context, repo: CollectionRepository): Int {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY, false)) return 0
        val moved = repo.separateLands()
        p.edit().putBoolean(KEY, true).apply()
        return moved
    }

    /** The box lands go to: one whose rules take lands and nothing broader, by type. */
    fun isLandsBox(rules: BoxRules): Boolean =
        rules.types.any { it.equals("Land", ignoreCase = true) } && rules.colors.isEmpty() && !rules.tokens
}

internal suspend fun CollectionRepository.separateLands(): Int {
    val boxes = dao.allBoxes()
    val colorless = boxes.filter { "C" in it.boxRules().colors }
    if (colorless.isEmpty()) return 0
    val landsIn = colorless.associateWith { b -> dao.inBox(b.id).filter { it.facts().isLand } }
    val count = landsIn.values.sumOf { it.size }
    if (count > 0) BackupIO.autoBackup(this, "lands box")
    return db.withTransaction {
        val landsId = boxes.firstOrNull { LandsBox.isLandsBox(it.boxRules()) }?.id ?: run {
            val first = colorless.minBy { it.sortOrder }
            val id = createBox("Lands", first.kind, BoxRules(types = setOf("Land")))
            // Right after the colorless box in the Auto-sort order.
            val order = dao.allBoxes().map { it.id }.toMutableList()
            order.remove(id)
            order.add(order.indexOf(first.id) + 1, id)
            reorderBoxes(order)
            id
        }
        val names = placeNames()
        for ((_, cards) in landsIn) for (c in cards) moveToBox(c.id, landsId, c, names)
        count
    }
}

package com.mtgscanner.data

import androidx.room.withTransaction

/** Whether [undoImport] has anything to take off. */
val ImportResult.canUndo: Boolean get() = rows.isNotEmpty()

/**
 * Undoes an import or a deck's "Add to collection": takes off exactly the copies it added, then the box it made,
 * when that box is empty again (cards added to it since keep it).
 */
suspend fun CollectionRepository.undoImport(r: ImportResult) {
    db.withTransaction { r.rows.forEach { (id, n) -> remove(id, n) } }
    val box = r.newBox ?: return
    if (dao.box(box) != null && dao.inBox(box).isEmpty()) deleteBox(box, moveTo = null)
}

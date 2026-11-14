package com.mtgscanner.ui.scan

import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.ScanBarPrefs
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.copiesOf
import com.mtgscanner.data.db.Box
import com.mtgscanner.ui.components.Undo
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Box check, for ScanViewModel. Its result flow is kept in the view model.

fun ScanViewModel.showBoxPick(show: Boolean) = _state.update { it.copy(showBoxPick = show) }

/** Starts checking a real box against what the app has in it. */
fun ScanViewModel.startBoxCheck(box: Box) {
    // For the "not checked in 3 months" nudge.
    com.mtgscanner.data.BoxPrefs.markChecked(repo.appContext, box.id)
    changeMode {
        it.copy(
            target = ScanTarget.BOX, deck = null, showBoxPick = false, current = null, awaiting = false, owned = null,
            boxCheck = BoxCheckRun(box.id, box.name), status = "Checking ${box.name} · scan its cards",
        )
    }
    _boxResult.value = null
}

fun ScanViewModel.showBoxCheck(show: Boolean) {
    _state.update { it.copy(showBoxCheck = show) }
    if (show) refreshBoxCheck()
}

/** Ends the box check and its list. */
fun ScanViewModel.endBoxCheck() {
    // "Checked 3w ago" on the box: only when something was scanned or ticked.
    _state.value.boxCheck?.takeIf { it.scans.isNotEmpty() || it.ticked.isNotEmpty() }?.let { com.mtgscanner.data.LastChecked.mark(repo.appContext, it.boxId) }
    changeMode {
        it.copy(
            target = if (it.target == ScanTarget.BOX) ScanTarget.COLLECTION else it.target, boxCheck = null, showBoxCheck = false,
            current = null, awaiting = false, status = "Point your camera at a card",
        )
    }
    _boxResult.value = null
}

fun ScanViewModel.refreshBoxCheck() {
    val run = _state.value.boxCheck ?: return
    boxCheckJob?.cancel()
    boxCheckJob = viewModelScope.launch {
        val rows = runCatching { repo.cardsInBox(run.boxId) }.getOrDefault(emptyList())
            .map { com.mtgscanner.data.BoxRow(it.id, it.scryfallId, it.oracleId, it.name, it.foil, it.quantity) }
        val cur = _state.value.boxCheck ?: return@launch
        val scans = cur.scans.map { com.mtgscanner.data.BoxScan(it.card.id, it.card.oracleId, it.card.name, it.foil, it.count) }
        _boxResult.value = com.mtgscanner.data.BoxCheck.compare(rows, scans, cur.ticked)
    }
}

/** Counts a card scanned from the box being checked. Nothing is added. */
internal fun ScanViewModel.recordBoxScan(card: ScryfallCard, foil: Boolean, qty: Int, fromReview: Boolean, auto: Boolean) {
    _state.update { st ->
        val run = st.boxCheck ?: return@update st.copy(status = "Pick a box to check")
        val i = run.scans.indexOfFirst { it.card.id == card.id && it.foil == foil }
        val scans = if (i >= 0) run.scans.mapIndexed { j, s -> if (j == i) s.copy(count = s.count + qty) else s }
        else listOf(CheckScan(card, foil, qty)) + run.scans
        st.copy(
            boxCheck = run.copy(scans = scans), addedPulse = st.addedPulse + 1, lastBoxName = null,
            status = "${card.name} · ${scans.sumOf { it.count }} scanned",
            current = if (fromReview || auto) st.current else null, cardFoil = if (fromReview || auto) st.cardFoil else null,
            awaiting = if (fromReview) st.awaiting else false,
        )
    }
    if (!_state.value.autoAdd && !fromReview) resetTracking()
    if (_state.value.showBoxCheck) refreshBoxCheck()
    if (!auto) Undo.offer("Counted ${card.name}") { uncountBoxScan(card.id, foil, qty) }
}

internal fun ScanViewModel.uncountBoxScan(id: String, foil: Boolean, qty: Int) {
    _state.update { st ->
        val run = st.boxCheck ?: return@update st
        val scans = run.scans.map { if (it.card.id == id && it.foil == foil) it.copy(count = it.count - qty) else it }.filter { it.count > 0 }
        st.copy(boxCheck = run.copy(scans = scans))
    }
    refreshBoxCheck()
}

/** Marks a missing row's copies as found by hand (ticked through the box), or takes the tick back. */
fun ScanViewModel.tickBoxRow(rowId: Long, copies: Int) {
    _state.update { st ->
        val run = st.boxCheck ?: return@update st
        val n = (run.ticked[rowId] ?: 0) + copies
        st.copy(boxCheck = run.copy(ticked = if (n > 0) run.ticked + (rowId to n) else run.ticked - rowId))
    }
    refreshBoxCheck()
}

/** Missing from the real box: takes those copies out of the collection, with Undo. */
fun ScanViewModel.removeMissing(row: com.mtgscanner.data.BoxRow, copies: Int) {
    viewModelScope.launch {
        val before = rowsBefore(listOf(row.rowId))
        repo.remove(row.rowId, copies)
        refreshBoxCheck()
        Undo.offer("Removed ${row.name}") {
            putBack(before, mapOf(row.rowId to copies))
            refreshBoxCheck()
        }
    }
}

/**
 * In the real box but not recorded there: moves copies of that printing here from another box, or adds them
 * when the collection has none. With Undo.
 */
fun ScanViewModel.fixNotRecorded(scan: com.mtgscanner.data.BoxScan, copies: Int) {
    val run = _state.value.boxCheck ?: return
    val card = run.scans.firstOrNull { it.card.id == scan.scryfallId && it.foil == scan.foil }?.card ?: return
    viewModelScope.launch {
        val elsewhere = runCatching { repo.copiesOf(card.oracleId, card.name) }.getOrDefault(emptyList())
            .filter { it.boxId != run.boxId && it.scryfallId == card.id && it.foil == scan.foil }
        val from = elsewhere.maxByOrNull { it.quantity }
        if (from != null) {
            val n = minOf(copies, from.quantity)
            val snapshot = repo.snapshot(listOf(from.id))
            repo.moveCopies(from.id, n, run.boxId)
            refreshBoxCheck()
            Undo.offer("Moved ${card.name} to ${run.boxName}") {
                // Every row of that printing back as it was.
                repo.restore(snapshot)
                refreshBoxCheck()
            }
        } else {
            val row = repo.add(card, scan.foil, copies, run.boxId, lang = rowLang(card, ScanBarPrefs.langOrNull), condition = ScanBarPrefs.condition)
            refreshBoxCheck()
            Undo.offer("Added ${card.name} to ${run.boxName}") {
                repo.remove(row, copies)
                refreshBoxCheck()
            }
        }
    }
}

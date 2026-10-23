package com.mtgscanner.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-wide "Undo" snackbar. Screens report a change with the code that reverses it; the app
 * root shows the message with an Undo button and runs the code if it's tapped.
 * The last 10 changes with Undo also stay in [recent] (memory only) for the Recent actions sheet.
 */
object Undo {
    /** [id] is the change's entry in [recent]; 0 for a plain message. */
    data class Event(val message: String, val undo: (suspend () -> Unit)?, val id: Long = 0)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val history = UndoHistory<suspend () -> Unit>()
    private val _recent = MutableStateFlow<List<UndoHistory.Entry<suspend () -> Unit>>>(emptyList())
    /** Changes that can still be undone, newest first. */
    val recent: StateFlow<List<UndoHistory.Entry<suspend () -> Unit>>> = _recent.asStateFlow()

    /** Set to open the Recent actions sheet (from Settings or a long press on the snackbar). */
    val showRecent = MutableStateFlow(false)

    fun offer(message: String, undo: (suspend () -> Unit)? = null) {
        // Undo is offered right after an add, move or delete: a light tick confirms it.
        if (undo != null) com.mtgscanner.util.Haptics.tick()
        val id = if (undo == null) 0L else synchronized(history) {
            history.record(message, System.currentTimeMillis(), undo).also { _recent.value = history.entries }
        }
        _events.tryEmit(Event(message, undo, id))
    }

    /** How many newer changes undoing [id] also undoes; null once it's gone. */
    fun newerThan(id: Long): Int? = synchronized(history) { history.newerThan(id) }

    /**
     * Undoes [id], first undoing every newer change still listed, newest first, so no older
     * snapshot lands on top of a newer change. Each step that fails is skipped.
     */
    suspend fun undoThrough(id: Long) {
        val steps = synchronized(history) { history.takeThrough(id).also { _recent.value = history.entries } }
        for (s in steps) runCatching { s.restore() }
    }

    /** Lives as long as the app, so an undo isn't cut short when a screen or the activity closes. */
    private val runner = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Runs [e]'s Undo to the end, even if the app is closed right after the tap: undos with
     * several steps (a trade, a CSV import) would otherwise stop partway.
     */
    fun perform(e: Event) {
        runner.launch {
            withContext(NonCancellable) {
                if (e.id != 0L) undoThrough(e.id) else e.undo?.let { u -> runCatching { u() } }
            }
        }
    }

    /** [undoThrough] in the app-wide scope (for the Recent actions sheet). */
    fun performThrough(id: Long) {
        runner.launch { withContext(NonCancellable) { undoThrough(id) } }
    }
}

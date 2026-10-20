package com.mtgscanner.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide "Undo" snackbar. Screens report a change with the code that reverses it; the app
 * root shows the message with an Undo button and runs the code if it's tapped.
 */
object Undo {
    data class Event(val message: String, val undo: (suspend () -> Unit)?)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun offer(message: String, undo: (suspend () -> Unit)? = null) {
        _events.tryEmit(Event(message, undo))
    }
}

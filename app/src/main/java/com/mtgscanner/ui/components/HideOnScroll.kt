package com.mtgscanner.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Watches the scrolling under it (put [connection] on a parent with Modifier.nestedScroll) and says whether
 * a bar should show: it hides after a short scroll down and shows again after a short scroll up. It only
 * watches, so the list and any header inside still get the whole scroll.
 */
class HideOnScroll(private val threshold: Float = 48f) {
    var shown by mutableStateOf(true)

    private var run = 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero
            // A change of direction starts the count again.
            if ((dy < 0) != (run < 0)) run = 0f
            run += dy
            if (run < -threshold) shown = false
            if (run > threshold) shown = true
            return Offset.Zero
        }
    }
}

/** Closes the keyboard as soon as the finger scrolls the list under it, so results aren't half hidden. */
@androidx.compose.runtime.Composable
fun rememberKeyboardCloser(): NestedScrollConnection {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    return androidx.compose.runtime.remember(focus, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 6f) {
                    keyboard?.hide()
                    focus.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
}

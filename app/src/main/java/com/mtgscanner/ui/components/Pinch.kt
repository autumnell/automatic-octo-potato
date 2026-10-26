package com.mtgscanner.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Pinch to change how big things are shown: spreading two fingers calls [onBigger], pinching them together
 * [onSmaller], once per gesture. One-finger scrolling and taps pass through untouched.
 */
fun Modifier.pinchToResize(onBigger: () -> Unit, onSmaller: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var done = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.count { it.pressed }
            if (down == 0) break
            if (done || down < 2) continue
            zoom *= event.calculateZoom()
            when {
                zoom > PINCH_OUT -> { done = true; onBigger() }
                zoom < PINCH_IN -> { done = true; onSmaller() }
            }
            // Two fingers on the list: keep it from scrolling while the pinch is read.
            event.changes.forEach { it.consume() }
        }
    }
}

private const val PINCH_OUT = 1.3f
private const val PINCH_IN = 0.77f

package com.mtgscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How fast a held + or − repeats. */
object RepeatTiming {
    /** Hold this long before repeating starts. */
    const val FIRST_MS = 400L

    /** Wait after the [n]th repeat (n from 1): steady at first, then faster. */
    fun delayFor(n: Int): Long = when {
        n <= 0 -> FIRST_MS
        n < 8 -> 120L
        n < 24 -> 70L
        else -> 40L
    }
}

/**
 * An icon button for steppers: a tap steps once, holding keeps stepping, faster the longer it's held.
 * Repeating stops while [repeat] is false (e.g. at the lowest count), so a hold can't run past it.
 */
@Composable
fun RepeatIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeat: Boolean = true,
    content: @Composable () -> Unit,
) {
    val step by rememberUpdatedState(onClick)
    val on by rememberUpdatedState(enabled)
    val again by rememberUpdatedState(repeat)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (pressed) Palette.Outline.copy(alpha = 0.35f) else Color.Transparent)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                role = Role.Button
                if (enabled) onClick { step(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!on) return@awaitEachGesture
                    pressed = true
                    var fired = false
                    val job = scope.launch {
                        delay(RepeatTiming.FIRST_MS)
                        var n = 0
                        while (isActive && on && again) {
                            fired = true
                            step()
                            com.mtgscanner.util.Haptics.tick()
                            n++
                            delay(RepeatTiming.delayFor(n))
                        }
                    }
                    val up = waitForUpOrCancellation()
                    job.cancel()
                    pressed = false
                    if (up != null && !fired) {
                        up.consume()
                        step()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

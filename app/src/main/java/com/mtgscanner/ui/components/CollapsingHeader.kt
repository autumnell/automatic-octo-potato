package com.mtgscanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * How far a [CollapsingHeader] has slid away. Its [connection] goes on a parent of both the header and the list
 * (Modifier.nestedScroll): scrolling down the list slides the header up and fades it out first, and scrolling
 * back up brings it back at once.
 */
@Stable
class CollapseState {
    /** 0 when shown in full, down to minus its height when gone. */
    var offset by mutableFloatStateOf(0f)
        internal set
    internal var height by mutableIntStateOf(0)

    fun show() { offset = 0f }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val h = height
            if (h == 0 || available.y == 0f) return Offset.Zero
            val next = (offset + available.y).coerceIn(-h.toFloat(), 0f)
            val used = next - offset
            offset = next
            return Offset(0f, used)
        }
    }
}

@Composable
fun rememberCollapseState(): CollapseState = remember { CollapseState() }

/** [content] that slides up and fades as the list under it scrolls ([CollapseState]). */
@Composable
fun CollapsingHeader(state: CollapseState, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .graphicsLayer {
                val h = state.height
                alpha = if (h == 0) 1f else (1f + state.offset / h).coerceIn(0f, 1f)
            }
            .clipToBounds()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                if (state.height != p.height) state.height = p.height
                val o = state.offset.roundToInt().coerceIn(-p.height, 0)
                layout(p.width, p.height + o) { p.placeRelative(0, o) }
            },
        content = content,
    )
}

package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette

/** What a swipe on a row does: its word and icon show under the row as it slides. */
class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val run: () -> Unit)

/**
 * A row that can be swiped: right for [start], left for [end] (either can be null). The row springs back after
 * the action, since the list updates itself (and an action with Undo can be taken back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActions(start: SwipeAction?, end: SwipeAction?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val s by rememberUpdatedState(start)
    val e by rememberUpdatedState(end)
    // One swipe can confirm more than once; act on the first only.
    val last = remember { longArrayOf(0L) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        val now = android.os.SystemClock.uptimeMillis()
        if (v != SwipeToDismissBoxValue.Settled && now - last[0] > 600) {
            last[0] = now
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> s?.run?.invoke()
                SwipeToDismissBoxValue.EndToStart -> e?.run?.invoke()
                else -> Unit
            }
        }
        false
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = start != null,
        enableDismissFromEndToStart = end != null,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val a = if (toEnd) start else end
            if (a != null) Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(Corners.Medium)).background(a.color.copy(alpha = 0.25f)).padding(horizontal = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(a.icon, null, tint = Palette.Text)
                Spacer(Modifier.width(Space.s2))
                Text(a.label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            }
        },
    ) { content() }
}

package com.mtgscanner.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Corners

/**
 * The app's pop-up message: one slim line with its action on the right, swiped away sideways.
 * [onLongPress] runs on a long press (the Undo host opens Recent actions with it).
 */
@Composable
fun SlimSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    SnackbarHost(state, modifier) { data -> key(data) { SlimSnackbar(data, onLongPress) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlimSnackbar(data: SnackbarData, onLongPress: (() -> Unit)?) {
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) data.dismiss()
        true
    })
    SwipeToDismissBox(swipe, backgroundContent = {}, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(Corners.Medium),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                .then(if (onLongPress != null) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) } else Modifier),
        ) {
            Row(Modifier.heightIn(min = 44.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.visuals.message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                data.visuals.actionLabel?.let { label ->
                    TextButton(onClick = { data.performAction() }) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }
}

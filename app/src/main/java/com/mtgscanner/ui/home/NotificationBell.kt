package com.mtgscanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.NotificationEntry
import com.mtgscanner.data.NotificationLog
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.UndoHistory
import kotlinx.coroutines.delay

/** The bell next to the logo: a dot when there are notifications not yet looked at. */
@Composable
internal fun NotificationBell(onClick: () -> Unit) {
    val ctx = LocalContext.current
    remember { NotificationLog.load(ctx) }
    val unread = NotificationLog.unread
    IconButton(onClick = onClick) {
        Box {
            Icon(Icons.Outlined.Notifications, if (unread > 0) "Notifications, $unread new" else "Notifications", tint = Palette.Icon)
            if (unread > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp).size(8.dp).background(Palette.Accent, CircleShape))
        }
    }
}

/** Every notification the app has shown, newest first; tap one to go where it pointed, × to clear it, or Clear all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsSheet(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    remember { NotificationLog.load(ctx) }
    val entries = NotificationLog.entries
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        NotificationLog.markSeen(ctx)
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) TextButton(onClick = { NotificationLog.clear(ctx) }) { Text("Clear all", color = Palette.AccentText) }
        }
        if (entries.isEmpty()) {
            EmptyState("No notifications", Modifier.padding(bottom = 32.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                items(entries, key = { "${it.at}|${it.title}" }) { e ->
                    NotificationRow(e, now, onOpen = { onDismiss(); onOpen(e.where) }, onClear = { NotificationLog.remove(ctx, e) })
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Palette.Rule)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(e: NotificationEntry, now: Long, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onOpen).padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.text.isNotBlank()) {
                Text(e.text, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Text(UndoHistory.ago(now, e.at), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
        IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Clear", tint = Palette.Icon) }
    }
}

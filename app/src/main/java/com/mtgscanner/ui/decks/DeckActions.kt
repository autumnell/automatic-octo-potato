package com.mtgscanner.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space

/**
 * The deck's actions as a sheet: short groups with a heading each, and one row per action with an icon, a name and
 * a line on what it does. Replaces a long plain drop-down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckActionsSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(
                title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.s4),
            )
            content()
            Spacer(Modifier.height(Space.s4))
        }
    }
}

/** A group's heading in [DeckActionsSheet]. */
@Composable
fun DeckActionGroup(label: String) {
    Text(
        label, style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
        modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s6, bottom = Space.s1),
    )
}

/** One action: icon, name and what it does. [danger] colors it for deleting. */
@Composable
fun DeckActionRow(icon: ImageVector, title: String, sub: String?, danger: Boolean = false, onClick: () -> Unit) {
    val ink: Color = if (danger) Palette.Danger else Palette.Text
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) Palette.Danger else Palette.Icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.s4))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

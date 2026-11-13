package com.mtgscanner.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space

/**
 * A run's chip over the camera: what's running ([title]), its count ([line]) and one action ("Review", "Finish").
 * The camera is dark in both modes, so the text is white on [Palette.CameraChip] whatever the theme.
 */
@Composable
fun CameraChip(
    title: String,
    line: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Palette.CameraChip) {
        Row(Modifier.padding(start = Space.s3, end = Space.s1, top = Space.s1, bottom = Space.s1), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onAction, enabled = enabled) { Text(action, color = if (enabled) Color.White else Palette.OnDarkMuted) }
        }
    }
}

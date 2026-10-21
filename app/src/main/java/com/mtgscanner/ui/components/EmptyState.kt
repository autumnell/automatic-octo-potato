package com.mtgscanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette

/**
 * A quiet, centered empty state: a small rule-and-diamond ornament, a short title in the heading serif,
 * and an optional text action. No icon bubble, no card. Keep [title] to a few words.
 * [action] shows only when [onAction] is given too.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    /** One short muted line under the title. */
    body: String? = null,
    /** Anything else under the title, such as a button. */
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ornament()
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onAction) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = Palette.AccentText)
            }
        }
        if (content != null) {
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** Two hairlines meeting a small accent diamond: ── ◆ ── */
@Composable
private fun Ornament() {
    val line = Palette.Outline
    val gem = Palette.Accent
    Canvas(Modifier.size(width = 96.dp, height = 10.dp)) {
        val cy = size.height / 2
        val cx = size.width / 2
        val r = size.height / 2
        val gap = r + 6.dp.toPx()
        val stroke = 1.dp.toPx()
        drawLine(line, Offset(0f, cy), Offset(cx - gap, cy), stroke)
        drawLine(line, Offset(cx + gap, cy), Offset(size.width, cy), stroke)
        val diamond = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }
        drawPath(diamond, gem)
    }
}

package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette

/**
 * A plain empty line in place, padded like a list row: the title, a short [body] after it (or under it when
 * long), and [action] as a text button on the right. [action] shows only when [onAction] is given too.
 * [fullScreen] adds a faded card back above the line, for a page with nothing else on it.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    /** One short muted line, after the title. */
    body: String? = null,
    /** Anything else under the line, such as a button. */
    content: (@Composable () -> Unit)? = null,
    /** The whole page is empty: show a faded card back above the line. */
    fullScreen: Boolean = false,
) {
    val inline = body != null && title.length + body.length <= 44
    Column(modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2)) {
        if (fullScreen) {
            CardBack(Modifier.padding(top = Space.s4, bottom = Space.s3).width(84.dp))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(title)
                    if (inline) withStyle(SpanStyle(color = Palette.Muted)) { append(" · $body") }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            if (action != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(action, style = MaterialTheme.typography.labelLarge, color = Palette.AccentText)
                }
            }
        }
        if (body != null && !inline) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
        if (content != null) {
            Spacer(Modifier.height(Space.s2))
            content()
        }
    }
}

/** The back of a Magic card, drawn: brown frame, dark field, a tan oval with rings. Faded, so it sits back. */
@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    val frame = Color(0xFF3B2A1E)
    val field = Color(0xFF6B4A2E)
    val oval = Color(0xFFB98A55)
    val ring = Color(0xFF2E2016)
    Canvas(modifier.aspectRatio(63f / 88f).alpha(if (Palette.dark) 0.32f else 0.22f)) {
        val corner = size.width * 0.05f
        drawRoundRect(frame, cornerRadius = CornerRadius(corner, corner))
        val inset = size.width * 0.06f
        val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
        drawRoundRect(
            Brush.radialGradient(listOf(field, frame), center = Offset(size.width / 2, size.height / 2), radius = size.height * 0.6f),
            topLeft = Offset(inset, inset), size = inner, cornerRadius = CornerRadius(corner / 2, corner / 2),
        )
        val ow = inner.width * 0.82f
        val oh = inner.height * 0.62f
        val o = Offset((size.width - ow) / 2, (size.height - oh) / 2)
        drawOval(oval, topLeft = o, size = Size(ow, oh))
        val stroke = size.width * 0.025f
        listOf(0.12f, 0.3f).forEach { f ->
            val w = ow * (1 - f)
            val h = oh * (1 - f)
            drawOval(ring, topLeft = Offset((size.width - w) / 2, (size.height - h) / 2), size = Size(w, h), style = Stroke(stroke))
        }
        drawOval(ring, topLeft = o, size = Size(ow, oh), style = Stroke(stroke * 1.4f))
    }
}

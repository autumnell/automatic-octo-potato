package com.mtgscanner.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * One line of text that shrinks to fit its width instead of being cut off: big values in wide fonts,
 * "RESTRICTED" badges, quantities of 100 or more. It steps down to [minScale] of [style]'s size; text
 * still too wide after that is clipped. Nothing is drawn until a size that fits is found.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    minScale: Float = 0.55f,
) {
    // Keyed by the style only: a counting number keeps the size it settled on instead of flickering.
    var scale by remember(style) { mutableFloatStateOf(1f) }
    var ready by remember(style) { mutableStateOf(false) }
    val fitted = if (scale >= 1f) style else style.copy(
        fontSize = style.fontSize * scale,
        lineHeight = if (style.lineHeight != TextUnit.Unspecified) style.lineHeight * scale else style.lineHeight,
    )
    Text(
        text,
        style = fitted,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        // Clip, not ellipsis: laid out at full width, so an overflow is reported and the size steps down.
        overflow = TextOverflow.Clip,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if (r.didOverflowWidth && scale > minScale) scale = (scale * 0.9f).coerceAtLeast(minScale)
            else ready = true
        },
    )
}

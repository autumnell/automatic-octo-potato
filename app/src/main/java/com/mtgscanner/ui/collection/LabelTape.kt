package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Label tape: off-white stock, near-black ink, a darker edge. The same in light and dark, like the real thing. */
object Tape {
    val Stock = Color(0xFFF1ECDF)
    val Ink = Color(0xFF1E1C19)
    val Edge = Color(0xFFC9C1AE)

    /** Bold caps, squeezed a little. */
    fun style(size: TextUnit = 15.sp) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * 1.15f,
        letterSpacing = 0.6.sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 0.86f),
        color = Ink,
    )
}

/** A box name on a strip of label tape. */
@Composable
fun LabelTape(text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp) {
    Box(
        modifier
            .background(Tape.Stock)
            .border(0.5.dp, Tape.Edge)
            // A faint light line along the top, where the tape catches the light.
            .drawBehind {
                val y = 1.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, y), Offset(this.size.width, y), y)
            }
            .padding(horizontal = Space.s2, vertical = Space.s1),
    ) {
        Text(text.uppercase(), style = Tape.style(size), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

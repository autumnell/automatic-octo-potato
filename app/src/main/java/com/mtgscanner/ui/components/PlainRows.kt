package com.mtgscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette

/**
 * A 1dp [Palette.Rule] line along the bottom edge: the divider under a plain list row (the "Small step" look,
 * where rows sit on the screen background instead of in rounded cards).
 */
fun Modifier.ruleBelow(): Modifier = drawBehind {
    val h = 1.dp.toPx()
    drawRect(Palette.Rule, Offset(0f, size.height - h), Size(size.width, h))
}

/** A 1dp [Palette.Rule] line along the top edge. */
fun Modifier.ruleAbove(): Modifier = drawBehind {
    drawRect(Palette.Rule, Offset.Zero, Size(size.width, 1.dp.toPx()))
}

/** A group of rows on a [Palette.Surface] panel, so it stands apart from the screen background. */
@Composable
fun PlainGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).background(Palette.Surface), content = content)
}

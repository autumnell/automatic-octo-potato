package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums

/**
 * A box divider seen from the front: a tab with its letter or name sticking up from the divider's top edge.
 * Tabs step across in [index] order, like a real set of dividers; the card count sits at the right.
 */
@Composable
fun DividerTabHead(title: String, count: Int, index: Int, modifier: Modifier = Modifier) {
    val fill = Palette.SurfaceHigh
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width((8 + (index % 4) * 44).dp))
            Box(
                Modifier.weight(1f, fill = false).widthIn(min = 44.dp)
                    .clip(RoundedCornerShape(topStart = Corners.Small, topEnd = Corners.Small))
                    .background(fill)
                    .padding(horizontal = Space.s3, vertical = Space.s1),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "$count", style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Palette.Muted,
                modifier = Modifier.padding(end = Space.s2, bottom = Space.s1),
            )
        }
        // The divider's top edge, as wide as the row of cards behind it.
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(topEnd = 2.dp)).background(fill))
    }
}

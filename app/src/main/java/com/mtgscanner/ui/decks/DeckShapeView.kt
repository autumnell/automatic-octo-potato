package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums

/** Mana curve bars (0 to 7+) and the color split of a deck's played spells, lands left out. */
@Composable
fun DeckShapeView(shape: DeckShape, modifier: Modifier = Modifier) {
    if (shape.isEmpty) return
    val top = shape.curve.maxOrNull()?.coerceAtLeast(1) ?: 1
    val barMax = 44
    Column(modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2)) {
        Row(
            Modifier.fillMaxWidth().semantics {
                contentDescription = "Mana curve: " + shape.curve.mapIndexed { i, n -> "${label(i)}: $n" }.joinToString(", ")
            },
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
            verticalAlignment = Alignment.Bottom,
        ) {
            shape.curve.forEachIndexed { i, n ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (n > 0) "$n" else "",
                        style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Palette.Muted,
                    )
                    Spacer(Modifier.height(Space.s1))
                    Box(
                        Modifier.fillMaxWidth().height((barMax * n / top).coerceAtLeast(if (n > 0) 3 else 1).dp)
                            .clip(RoundedCornerShape(topStart = Corners.Small, topEnd = Corners.Small))
                            .background(if (n > 0) Palette.Accent else Palette.Outline),
                    )
                    Spacer(Modifier.height(Space.s1))
                    Text(label(i), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
            }
        }
        if (shape.pipTotal > 0) {
            Spacer(Modifier.height(Space.s2))
            Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Corners.Small))) {
                shape.pips.forEach { (c, n) ->
                    Box(Modifier.weight(n.toFloat()).height(6.dp).background(MtgColors.barFor(c)))
                }
            }
            Spacer(Modifier.height(Space.s1))
            val pct = DeckShapeMath.percents(shape.pips.map { it.second })
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.CenterVertically) {
                shape.pips.forEachIndexed { i, (c, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorPip(c, 10.dp)
                        Spacer(Modifier.width(Space.s1))
                        Text("${pct.getOrElse(i) { 0 }}%", style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Palette.Muted)
                    }
                }
            }
        }
    }
}

private fun label(bucket: Int): String = if (bucket == DeckShape.BUCKETS - 1) "${bucket}+" else "$bucket"

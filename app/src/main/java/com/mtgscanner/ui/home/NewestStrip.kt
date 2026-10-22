package com.mtgscanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.ui.detail.sharedCard
import com.mtgscanner.ui.scan.SetIcon
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette

/** The newest cards as a strip of their art, newest first: name and set symbol over each. Tap opens the card. */
@Composable
internal fun NewestStrip(cards: List<CollectionCard>, onOpenCard: (Long) -> Unit, shared: Set<Long> = emptySet()) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cards, key = { it.id }) { c ->
            Box(
                Modifier.width(164.dp).height(120.dp)
                    .clip(RoundedCornerShape(Corners.Medium))
                    .background(Palette.SurfaceHigh)
                    .clickable { onOpenCard(c.id) },
            ) {
                AsyncImage(
                    model = artCropUrl(c.imageUrl),
                    contentDescription = c.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().then(if (c.id in shared) Modifier.sharedCard(c.id) else Modifier),
                )
                // A dark fade at the bottom so the name reads on any art.
                Box(
                    Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
                )
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 8.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        c.name, style = MaterialTheme.typography.labelLarge, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    SetIcon(c.setCode, size = 14.dp, tint = Color.White)
                }
                if (c.quantity > 1) {
                    Text(
                        "×${c.quantity}", style = MaterialTheme.typography.labelMedium, color = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

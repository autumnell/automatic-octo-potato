package com.mtgscanner.ui.collection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.PriceSparkline
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.foilShimmer
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** Cards behind the top one that still show. */
private const val BEHIND = 3

/**
 * A box as a real stack of cards, slightly fanned. Swipe left for the next card, right to put the
 * last one back; tap the top card to open it.
 */
@Composable
fun CardStack(
    cards: List<CollectionCard>,
    sparks: Map<Long, List<Double>>,
    onOpen: (CollectionCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    val i = index.coerceIn(0, cards.lastIndex)
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun next(widthPx: Float) = scope.launch {
        if (i >= cards.lastIndex) { drag.animateTo(0f, spring()); return@launch }
        drag.animateTo(-widthPx * 1.3f, tween(180))
        index = i + 1
        drag.snapTo(0f)
    }
    fun previous(widthPx: Float) = scope.launch {
        if (i <= 0) { drag.animateTo(0f, spring()); return@launch }
        // The card put back slides in from the side it was swiped toward.
        index = i - 1
        drag.snapTo(widthPx * 1.3f)
        drag.animateTo(0f, tween(220))
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            // As big as fits, leaving room for the fan on the right.
            val cardW = min(maxWidth.value * 0.70f, (maxHeight.value - 32f) * 63f / 88f).coerceAtLeast(80f).dp
            val pulled = (abs(drag.value) / widthPx).coerceIn(0f, 1f)
            val last = min(cards.lastIndex, i + BEHIND)
            // Deepest first so the top card draws last.
            for (k in last downTo i) {
                val c = cards[k]
                val top = k == i
                // How far back this card sits; cards move up as the top one is pulled away.
                val depth = if (top) 0f else (k - i) - pulled
                androidx.compose.runtime.key(c.id) {
                    Box(
                        Modifier.width(cardW)
                            .graphicsLayer {
                                if (top) {
                                    translationX = drag.value
                                    rotationZ = drag.value / widthPx * 14f
                                } else {
                                    translationX = depth * 14.dp.toPx()
                                    translationY = -depth * 6.dp.toPx()
                                    rotationZ = depth * 3.5f
                                    val s = 1f - depth * 0.035f
                                    scaleX = s
                                    scaleY = s
                                }
                            }
                            .shadow(if (top) 10.dp else 4.dp, RoundedCornerShape(12.dp))
                            .then(
                                if (top) Modifier
                                    .pointerInput(i, cards.size) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                val x = drag.value
                                                when {
                                                    x < -widthPx * 0.22f -> next(widthPx)
                                                    x > widthPx * 0.22f -> previous(widthPx)
                                                    else -> scope.launch { drag.animateTo(0f, spring()) }
                                                }
                                            },
                                            onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                                        ) { change, dx ->
                                            change.consume()
                                            scope.launch { drag.snapTo(drag.value + dx) }
                                        }
                                    }
                                    .clickable { onOpen(c) }
                                else Modifier,
                            ),
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).foilShimmer(c.foil && top)) {
                            CardImage(c.imageUrl ?: c.smallImageUrl, Modifier.fillMaxWidth(), corner = 12.dp)
                        }
                    }
                }
            }
        }
        val c = cards[i]
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            RarityMark(c.rarity)
            Spacer(Modifier.width(8.dp))
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${c.setCode.uppercase()} #${c.collectorNumber}" + (if (c.quantity > 1) " · ×${c.quantity}" else "") + (if (c.foil) " · Foil" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            if (AppSettings.showPrices) {
                Spacer(Modifier.width(10.dp))
                Text(cardPrice(c), style = MaterialTheme.typography.bodySmall, color = Palette.AccentText)
                Spacer(Modifier.width(6.dp))
                PriceSparkline(sparks[c.id].orEmpty())
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { previous(widthPx) }, enabled = i > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous card") }
                Text("${i + 1} / ${cards.size}", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.width(88.dp))
                IconButton(onClick = { next(widthPx) }, enabled = i < cards.lastIndex) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next card") }
            }
        }
    }
}

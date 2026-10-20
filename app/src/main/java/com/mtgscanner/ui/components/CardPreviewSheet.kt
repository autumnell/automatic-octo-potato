package com.mtgscanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette

/**
 * A half-screen look at one card: image, name, price and where it is. "Open" goes to the full page.
 * [where] is a spot like "Rares → Blue → after Counterspell"; when null the box name shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPreviewSheet(
    card: CollectionCard,
    box: CardBox?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    where: String? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        CardPreviewContent(card, box, where, Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            GradientButton("Open", onOpen, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The preview's body, also used beside the list on wide screens. [actions] go under the details. */
@Composable
fun CardPreviewContent(
    card: CollectionCard,
    box: CardBox?,
    where: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            CardImage(card.imageUrl ?: card.smallImageUrl, Modifier.width(150.dp), corner = 9.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                ManaCost(card.manaCost, size = 16.dp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RarityDot(card.rarity, 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("${card.setCode} #${card.collectorNumber}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("×${card.quantity}", style = MaterialTheme.typography.titleMedium)
                    if (card.foil) { Spacer(Modifier.width(6.dp)); FoilTag() }
                }
                if (AppSettings.showPrices) {
                    Spacer(Modifier.height(4.dp))
                    Text(formatUsd(card.unitPrice()), style = MaterialTheme.typography.titleMedium, color = Palette.AccentText)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(boxIcon(box?.kind ?: "box"), null, tint = boxColor(box?.color ?: 0), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(where ?: box?.name ?: "No box", style = MaterialTheme.typography.bodySmall, color = Palette.Text)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        actions()
    }
}

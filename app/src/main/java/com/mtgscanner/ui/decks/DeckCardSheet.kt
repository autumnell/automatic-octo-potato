package com.mtgscanner.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTextBlock
import com.mtgscanner.ui.components.CardTextInfo
import com.mtgscanner.ui.components.FaceText
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.SetSymbol
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums

/**
 * A deck card's details, sliding up when it's tapped on the deck page like a card found in Search: the picture,
 * cost, type, printing and price, how many you own and where, and its rules text. Other printings opens the picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckCardSheet(line: DeckCheckLine, info: DeckCardInfo?, onPrintings: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Row {
                CardImage(line.imageUrl, Modifier.width(130.dp), zoom = true)
                Spacer(Modifier.width(Space.s4))
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = MaterialTheme.typography.titleLarge)
                    if (info != null) {
                        Spacer(Modifier.height(Space.s1))
                        ManaCost(info.manaCost)
                        Spacer(Modifier.height(Space.s1))
                        Text(info.typeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    line.printing?.let { printing ->
                        Spacer(Modifier.height(Space.s2))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            line.setCode?.let { SetSymbol(it, modifier = Modifier.padding(end = Space.s1)) }
                            Text(printing, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (AppSettings.showPrices && (line.price != null || line.priceEur != null)) {
                        Spacer(Modifier.height(Space.s2))
                        Text(formatMoney(line.price, line.priceEur), style = MaterialTheme.typography.headlineSmall.merge(TabularNums))
                    }
                    TextButton(onClick = onPrintings, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s1))
                        Text("Other printings")
                    }
                }
            }
            Spacer(Modifier.height(Space.s3))
            // How many the deck needs, how many you own and where they are.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh)
                    .padding(horizontal = Space.s3, vertical = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (line.missing == 0) {
                    Icon(Icons.Outlined.Check, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s2))
                }
                Text(
                    listOf(
                        if (line.owned == 0) "Need ${line.needed}, none owned" else "You own ${minOf(line.owned, line.needed)} of ${line.needed}",
                    ).plus(listOfNotNull(line.whereText.ifEmpty { null })).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.weight(1f),
                )
            }
            if (info != null && info.oracleText.isNotBlank()) {
                Spacer(Modifier.height(Space.s3))
                val stats = if (info.power != null && info.toughness != null) "${info.power}/${info.toughness}" else null
                CardTextBlock(CardTextInfo(listOf(FaceText(info.name, info.manaCost, info.typeLine, info.oracleText, stats = stats))))
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

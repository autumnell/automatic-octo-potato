package com.mtgscanner.ui.trade

import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.components.formatMoney
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums

/** Details of a card you get: printing, foil, condition and language. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GetCardSheet(
    item: TradeItem,
    onPrintings: () -> Unit,
    onChange: (foil: Boolean, condition: String, lang: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val card = item.card ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Row {
                CardImage(card.imageUrl, Modifier.width(110.dp))
                Spacer(Modifier.width(Space.s4))
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Space.s2))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RarityDot(card.rarity, 8.dp)
                        Spacer(Modifier.width(Space.s2))
                        Text("${card.setName} · #${card.collectorNumber}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (AppSettings.showPrices) {
                        Spacer(Modifier.height(Space.s2))
                        Text(formatMoney(item.price, item.eur), style = MaterialTheme.typography.headlineSmall.merge(TabularNums), color = Palette.Text)
                        item.buyPrice?.let { Text("CK buys ${formatUsd(it)}", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted) }
                    }
                    TextButton(onClick = onPrintings, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s1))
                        Text("Other printings")
                    }
                }
            }
            Spacer(Modifier.height(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foil", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = item.foil,
                    onCheckedChange = { onChange(it, item.condition, item.lang) },
                    enabled = card.hasFoil && card.hasNonFoil,
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Text),
                )
            }
            Spacer(Modifier.height(Space.s2))
            Text("Condition", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                TRADE_CONDITIONS.forEach { c -> Pill(c, c == item.condition) { onChange(item.foil, c, item.lang) } }
            }
            Spacer(Modifier.height(Space.s4))
            Text("Language", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                TRADE_LANGS.forEach { l -> Pill(l.uppercase(), l == item.lang) { onChange(item.foil, item.condition, l) } }
            }
            GradientButton("Done", onDismiss, Modifier.fillMaxWidth().padding(vertical = Space.s4))
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Text,
        modifier = Modifier
            .clip(RoundedCornerShape(Corners.Small))
            .background(Palette.SurfaceHigh)
            .border(1.dp, if (selected) Palette.Text else Color.Transparent, RoundedCornerShape(Corners.Small))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s2),
    )
}

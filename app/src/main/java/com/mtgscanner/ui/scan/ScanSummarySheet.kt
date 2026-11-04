package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.PriceStyle
import com.mtgscanner.ui.theme.TabularNums

/** Shown once when a scan list is cleared, a sorting session ends or a pack is finished: what it added and where it went. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSummarySheet(summary: ScanSummary, onDismiss: () -> Unit) {
    val prices = AppSettings.showPrices
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4)) {
            Text(summary.title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Spacer(Modifier.height(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                summary.best?.let { CardImage(it.imageUrl, Modifier.width(72.dp), zoom = true) }
                if (summary.best != null) Spacer(Modifier.width(Space.s4))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (summary.cards == 1) "1 card added" else "${summary.cards} cards added",
                        style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = Palette.Text,
                    )
                    if (prices) Text(formatMoney(summary.value, summary.eur), style = PriceStyle, color = Palette.Text)
                    summary.paid?.let { paid ->
                        if (prices) Text("Paid ${formatUsd(paid)}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Muted)
                    }
                    summary.best?.let { b ->
                        Spacer(Modifier.height(Space.s2))
                        Text("Best card", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Text(
                            if (prices) "${b.name} · ${formatMoney(b.unitPrice, b.unitEur)}" else b.name,
                            style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.s4))
            summary.boxes.forEachIndexed { i, (box, n) ->
                if (i > 0) HorizontalDivider(color = Palette.Rule)
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(box, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$n", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Muted)
                }
            }
            Spacer(Modifier.height(Space.s4))
            GradientButton("Done", onDismiss, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.s4))
        }
    }
}

package com.mtgscanner.ui.scan

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
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.PriceStyle
import com.mtgscanner.ui.theme.TabularNums

/** Shown once when a scan list is cleared or a sorting session ends: what it added and where it went. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSummarySheet(summary: ScanSummary, onDismiss: () -> Unit) {
    val prices = AppSettings.showPrices
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Session done", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                summary.best?.let { CardImage(it.imageUrl, Modifier.width(72.dp), zoom = true) }
                if (summary.best != null) Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (summary.cards == 1) "1 card added" else "${summary.cards} cards added",
                        style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = Palette.Text,
                    )
                    if (prices) Text(formatUsd(summary.value), style = PriceStyle, color = Palette.Text)
                    summary.best?.let { b ->
                        Spacer(Modifier.height(6.dp))
                        Text("Best card", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Text(
                            if (prices) "${b.name} · ${formatUsd(b.unitPrice)}" else b.name,
                            style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            summary.boxes.forEachIndexed { i, (box, n) ->
                if (i > 0) HorizontalDivider(color = Palette.Outline)
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(box, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$n", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            GradientButton("Done", onDismiss, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}

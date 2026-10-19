package com.mtgscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.ui.theme.MtgColors
import java.text.NumberFormat
import java.util.Locale

private val usd = NumberFormat.getCurrencyInstance(Locale.US)
fun formatUsd(v: Double?): String = if (v == null) "—" else usd.format(v)
fun formatUsd(v: String?): String = formatUsd(v?.toDoubleOrNull())

/** Draws a mana cost like "{2}{U}{U}" as small colored circles. */
@Composable
fun ManaCost(cost: String, size: Dp = 16.dp) {
    if (cost.isBlank()) return
    val symbols = Regex("""\{([^}]+)\}|(//)""").findAll(cost).map { it.groupValues[1].ifEmpty { "//" } }.toList()
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        symbols.forEach { sym ->
            if (sym == "//") {
                Text("//", fontSize = 11.sp)
            } else {
                val letters = sym.filter { it in "WUBRG" }
                val bg = when {
                    letters.length > 1 -> MtgColors.Multi
                    letters.length == 1 -> MtgColors.forLetter(letters[0])
                    else -> MtgColors.C
                }
                val fg = if (bg == MtgColors.W || bg == MtgColors.C || bg == MtgColors.Multi) Color.Black else Color.White
                Box(
                    Modifier.size(size).clip(CircleShape).background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        sym.replace("/", ""),
                        color = fg,
                        fontSize = (size.value * 0.55f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun RarityDot(rarity: String, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MtgColors.rarity(rarity)))
}

@Composable
fun CardImage(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(RoundedCornerShape(6.dp)),
    )
}

/** Bottom sheet listing every printing of a card so the right set can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintingsSheet(
    printings: List<ScryfallCard>?,
    selectedId: String?,
    onPick: (ScryfallCard) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Choose the printing",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (printings == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                items(printings, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p) }
                            .background(
                                if (p.id == selectedId) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardImage(p.smallImageUrl, Modifier.width(48.dp).height(67.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.setName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RarityDot(p.rarity)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${p.set.uppercase()} #${p.collectorNumber} · ${p.releasedAt.orEmpty().take(4)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatUsd(p.prices.usd), style = MaterialTheme.typography.bodyMedium)
                            if (p.prices.usdFoil != null) {
                                Text("Foil ${formatUsd(p.prices.usdFoil)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

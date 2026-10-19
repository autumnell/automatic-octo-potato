package com.mtgscanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.StatTile
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUi(
    val loaded: Boolean = false,
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    val sets: Int = 0,
    val foils: Int = 0,
    val recent: List<CollectionCard> = emptyList(),
    val valuable: List<CollectionCard> = emptyList(),
    /** Copies per color letter (W, U, B, R, G), plus "M" multicolor and "C" colorless. */
    val colors: List<Pair<String, Int>> = emptyList(),
)

class HomeViewModel(repo: CollectionRepository) : ViewModel() {
    val ui: StateFlow<HomeUi> = repo.observeAll().map { all ->
        val colorCounts = linkedMapOf("W" to 0, "U" to 0, "B" to 0, "R" to 0, "G" to 0, "M" to 0, "C" to 0)
        all.forEach { c ->
            val key = when {
                c.colors.isEmpty() -> "C"
                c.colors.length > 1 -> "M"
                else -> c.colors
            }
            colorCounts[key] = (colorCounts[key] ?: 0) + c.quantity
        }
        HomeUi(
            loaded = true,
            totalCards = all.sumOf { it.quantity },
            uniqueCards = all.map { it.oracleId ?: it.name }.distinct().size,
            totalValue = all.sumOf { it.totalPrice() },
            sets = all.map { it.setCode }.distinct().size,
            foils = all.filter { it.foil }.sumOf { it.quantity },
            recent = all.sortedByDescending { it.addedAt }.take(12),
            valuable = all.filter { (it.unitPrice() ?: 0.0) > 0 }.sortedByDescending { it.unitPrice() }.take(12),
            colors = colorCounts.toList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onCollection: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        Header(onSettings)
        HeroCard(ui, onScan)
        Spacer(Modifier.height(12.dp))

        if (ui.loaded && ui.totalCards == 0) {
            EmptyState(
                icon = Icons.Filled.Style,
                title = "Start your collection",
                body = "Scan cards with the camera, or search for them by name. Every card is looked up on Scryfall with its set, rarity and price.",
                action = {
                    TextButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Search by name instead")
                    }
                },
            )
            return@Column
        }

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Unique", "${ui.uniqueCards}", Modifier.weight(1f))
            StatTile("Sets", "${ui.sets}", Modifier.weight(1f))
            StatTile("Foils", "${ui.foils}", Modifier.weight(1f))
        }

        if (ui.recent.isNotEmpty()) {
            SectionHeader("Recently added") { TextButton(onClick = onCollection) { Text("See all") } }
            CardCarousel(ui.recent, onOpenCard)
        }
        if (ui.valuable.isNotEmpty()) {
            SectionHeader("Most valuable")
            CardCarousel(ui.valuable, onOpenCard)
        }
        SectionHeader("Colors")
        ColorBreakdown(ui.colors)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.AccentGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Palette.OnAccent) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("MTG Scanner", style = MaterialTheme.typography.titleLarge)
            Text("Your collection at a glance", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
    }
}

@Composable
private fun HeroCard(ui: HomeUi, onScan: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Box(
            Modifier.background(Palette.HeroGradient),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Collection value", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                Text(
                    formatUsd(ui.totalValue),
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("${ui.totalCards} cards", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                Spacer(Modifier.height(16.dp))
                GradientButton("Scan cards", onScan, Modifier.fillMaxWidth(), icon = Icons.Filled.CameraAlt)
            }
        }
    }
}

@Composable
private fun CardCarousel(cards: List<CollectionCard>, onOpenCard: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(cards, key = { it.id }) { c ->
            CardTile(
                imageUrl = c.imageUrl,
                name = c.name,
                price = formatUsd(c.unitPrice()),
                quantity = c.quantity,
                foil = c.foil,
                modifier = Modifier.width(118.dp),
                onClick = { onOpenCard(c.id) },
            )
        }
    }
}

@Composable
private fun ColorBreakdown(colors: List<Pair<String, Int>>) {
    val total = colors.sumOf { it.second }.coerceAtLeast(1)
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                colors.filter { it.second > 0 }.forEach { (k, n) ->
                    Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(colorFor(k)))
                }
                if (colors.all { it.second == 0 }) Box(Modifier.weight(1f).fillMaxHeight().background(Palette.SurfaceHigh))
            }
            Spacer(Modifier.height(14.dp))
            colors.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEach { (k, n) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (k in listOf("W", "U", "B", "R", "G")) ColorPip(k[0])
                            else Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(colorFor(k)))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${label(k)} ${n * 100 / total}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (n > 0) Palette.Text else Palette.Muted,
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun colorFor(k: String): Color = when (k) {
    "M" -> MtgColors.Multi
    "C" -> MtgColors.C
    else -> MtgColors.forLetter(k[0])
}

private fun label(k: String) = when (k) {
    "W" -> "White"; "U" -> "Blue"; "B" -> "Black"; "R" -> "Red"; "G" -> "Green"; "M" -> "Multi"; else -> "Colorless"
}

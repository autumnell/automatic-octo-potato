package com.mtgscanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mtgscanner.MtgApp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.collection.BrowseLaunch
import com.mtgscanner.ui.collection.Filters
import com.mtgscanner.ui.collection.SortBy
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(repo: CollectionRepository) : ViewModel() {
    /** Null until the collection has loaded. */
    val stats: StateFlow<CollectionStats?> = repo.observeAll()
        .map { all -> val s: CollectionStats? = StatsMath.compute(all); s }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MtgApp).repository) }
        }
    }
}

/** Collection value by set, color and rarity, and the most valuable cards. */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenCard: (Long) -> Unit,
    /** Opens Browse of the whole collection; set [BrowseLaunch] first. */
    onBrowseAll: () -> Unit,
    vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val prices = AppSettings.showPrices
    var allSets by remember { mutableStateOf(false) }

    fun browse(filters: Filters) {
        BrowseLaunch.filters = filters
        onBrowseAll()
    }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Stats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        val s = stats ?: return@Column
        if (s.cards == 0) {
            EmptyState(icon = Icons.Filled.BarChart, title = "No cards yet", body = "Scan cards to see stats.")
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(
                if (prices) "${formatUsd(s.value)} · ${s.cards} cards" else "${s.cards} cards",
                style = MaterialTheme.typography.titleLarge, color = Palette.AccentText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            SectionHeader("By color")
            Breakdown(
                s.byColor.filter { it.cards > 0 }, prices,
                note = "Multicolor cards count in each color.",
                lead = { ColorPip(it.key[0]) },
                bar = { colorOf(it.key) },
                onRow = { browse(Filters(colors = setOf(it.key), sort = SortBy.PRICE)) },
            )

            SectionHeader("By rarity")
            Breakdown(
                s.byRarity, prices,
                lead = { RarityDot(it.key, 10.dp) },
                bar = { MtgColors.rarity(it.key) },
                onRow = { r -> if (r.key != "special") browse(Filters(rarities = setOf(r.key), sort = SortBy.PRICE)) },
            )

            SectionHeader("By set") {
                if (s.bySet.size > 10) TextButton(onClick = { allSets = !allSets }) { Text(if (allSets) "Less" else "All ${s.bySet.size}") }
            }
            Breakdown(
                if (allSets) s.bySet else s.bySet.take(10), prices,
                lead = null,
                bar = { Palette.Accent },
                onRow = { browse(Filters(set = it.key, sort = SortBy.PRICE)) },
            )

            if (prices && s.top.isNotEmpty()) {
                SectionHeader("Most valuable")
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        s.top.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenCard(c.id) }.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.width(24.dp))
                                CardImage(c.smallImageUrl, Modifier.width(32.dp), corner = 3.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${c.setCode.uppercase()}${if (c.foil) " · foil" else ""}${if (c.quantity > 1) " · ×${c.quantity}" else ""}",
                                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1,
                                    )
                                }
                                Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.titleSmall, color = Palette.AccentText)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Rows with a bar sized by value (or by cards when prices are hidden). */
@Composable
private fun Breakdown(
    rows: List<StatRow>,
    prices: Boolean,
    note: String? = null,
    lead: (@Composable (StatRow) -> Unit)?,
    bar: (StatRow) -> Color,
    onRow: (StatRow) -> Unit,
) {
    val max = rows.maxOfOrNull { if (prices) it.value else it.cards.toDouble() }?.takeIf { it > 0 } ?: 1.0
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column(Modifier.padding(vertical = 8.dp)) {
            rows.forEach { r ->
                Column(Modifier.fillMaxWidth().clickable { onRow(r) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lead != null) {
                            lead(r)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(r.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${r.cards}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        if (prices) {
                            Spacer(Modifier.width(10.dp))
                            Text(formatUsd(r.value), style = MaterialTheme.typography.labelLarge, color = Palette.AccentText)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val frac = ((if (prices) r.value else r.cards.toDouble()) / max).toFloat().coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.SurfaceHigh)) {
                        if (frac > 0f) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(bar(r)))
                    }
                }
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

private fun colorOf(k: String): Color = if (k == "C") MtgColors.C else MtgColors.forLetter(k[0])

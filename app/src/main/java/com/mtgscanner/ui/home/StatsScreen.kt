package com.mtgscanner.ui.home

import com.mtgscanner.ui.theme.Space
import com.mtgscanner.data.db.unitEur
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.LineChart
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.PriceStyle
import kotlinx.coroutines.flow.combine
import androidx.compose.foundation.layout.size
import com.mtgscanner.ui.detail.sharedCard
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.ui.AppViewModels
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
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.formatMoneyShort
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.primaryValue
import com.mtgscanner.util.PriceCurrency
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.PlaceholderBlock
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.ruleBelow

class StatsViewModel(repo: CollectionRepository) : ViewModel() {
    /** Null until the collection has loaded. */
    val stats: StateFlow<CollectionStats?> = repo.observeAll()
        .map { all -> val s: CollectionStats? = StatsMath.compute(all); s }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val boxes: StateFlow<List<CardBox>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The price history last read, with what it was read for; a card edit or move doesn't change it. */
    private var pointsCache: Pair<Any, List<com.mtgscanner.data.db.PricePoint>>? = null

    /** Each box's value by day over the last two months. A price refresh writes a snapshot, so it redraws then too. */
    val boxValues: StateFlow<BoxValues> = combine(repo.observeAll(), repo.observeSnapshots()) { all, snaps ->
        val today = java.time.LocalDate.now()
        // Read again only on a new day, a new snapshot (price refresh) or a printing added or gone, as Home does.
        val key = listOf(today.toString(), snaps.size, snaps.maxByOrNull { it.day }, all.mapTo(HashSet()) { it.scryfallId })
        val points = pointsCache?.takeIf { it.first == key }?.second
            ?: runCatching { repo.dao.pricePointsSince(today.minusDays(60).toString()) }.getOrNull()?.also { pointsCache = key to it }
            ?: emptyList()
        HomeMath.boxValues(all, points)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoxValues(emptyList(), emptyMap()))
}

/** Collection value by set, color, rarity and box, money paid and sold, the most valuable cards and bulk. */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenCard: (Long) -> Unit,
    /** Opens Browse of the whole collection; set [BrowseLaunch] first. */
    onBrowseAll: () -> Unit,
    vm: StatsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ctx = LocalContext.current
    remember { HomePrefs.load(ctx) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val boxValues by vm.boxValues.collectAsStateWithLifecycle()
    val prices = AppSettings.showPrices
    var allSets by remember { mutableStateOf(false) }

    fun browse(filters: Filters) {
        BrowseLaunch.filters = filters
        onBrowseAll()
    }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = Space.s1, end = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Stats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            stats?.takeIf { it.cards > 0 }?.let { st ->
                IconButton(onClick = {
                    val top = st.top.firstOrNull()
                    val text = StatsMath.shareText(
                        cards = st.cards,
                        value = if (prices) formatMoneyShort(st.value, st.valueEur) else null,
                        sets = st.bySet.size,
                        topName = top?.name,
                        topPrice = if (prices && top != null) formatMoneyShort(top.unitPrice(), top.unitEur()) else null,
                    )
                    com.mtgscanner.util.shareText(ctx, "Share stats", text)
                }) { Icon(Icons.Outlined.Share, "Share stats") }
            }
        }
        val s = stats
        if (s == null) {
            Column(Modifier.padding(Space.s4)) {
                PlaceholderBlock(140.dp)
                Spacer(Modifier.height(Space.s3))
                PlaceholderRows(4)
            }
            return@Column
        }
        if (s.cards == 0) {
            EmptyState("No cards yet")
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(
                if (prices) "${formatMoney(s.value, s.valueEur)} · ${s.cards} cards" else "${s.cards} cards",
                style = MaterialTheme.typography.titleLarge, color = Palette.Text,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
            )

            if (prices) MoneySection(s.paid)

            if (prices && boxValues.days.size >= 2 && boxes.isNotEmpty()) {
                SectionHeader("Value by box")
                Panel { BoxValueChart(boxValues, boxes) }
            }

            SectionHeader("By color")
            Breakdown(
                s.byColor.filter { it.cards > 0 }, prices,
                note = "Multicolor cards count in each color",
                lead = { ColorPip(it.key[0]) },
                bar = { colorOf(it.key) },
                onRow = { browse(Filters(colors = setOf(it.key), sort = SortBy.PRICE)) },
            )

            SectionHeader("By rarity")
            Breakdown(
                s.byRarity, prices,
                lead = { RarityDot(it.key, 10.dp) },
                bar = { MtgColors.rarity(it.key) },
                onRow = { r -> if (r.key != "special" && r.key != StatsMath.TOKENS) browse(Filters(rarities = setOf(r.key), sort = SortBy.PRICE)) },
            )

            SectionHeader("By set") {
                if (s.bySet.size > 10) TextButton(onClick = { allSets = !allSets }) { Text(if (allSets) "Less" else "All ${s.bySet.size}") }
            }
            val bySet = if (AppSettings.currency == PriceCurrency.EUR) s.bySet.sortedByDescending { it.eur } else s.bySet
            Breakdown(
                if (allSets) bySet else bySet.take(10), prices,
                lead = null,
                bar = { Palette.Muted },
                onRow = { browse(Filters(set = it.key, sort = SortBy.PRICE)) },
            )

            if (s.byYear.size > 1) {
                SectionHeader("By year added")
                Breakdown(
                    s.byYear.map { r ->
                        val ch = s.yearChange[r.key]
                        if (prices && ch != null && kotlin.math.abs(ch) >= 0.5) r.copy(label = "${r.label} · ${if (ch >= 0) "up" else "down"} ${formatUsd(kotlin.math.abs(ch))}") else r
                    },
                    prices,
                    note = if (prices && s.yearChange.isNotEmpty()) "Up or down from what you paid, or the price when added" else null,
                    lead = null,
                    bar = { Palette.Muted },
                    onRow = {},
                )
            }

            if (prices && s.top.isNotEmpty()) {
                SectionHeader("Most valuable")
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).clip(RoundedCornerShape(Corners.Small)).background(Palette.Surface).padding(horizontal = Space.s3, vertical = Space.s1)) {
                    Column {
                        s.top.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenCard(c.id) }.ruleBelow().padding(horizontal = Space.s1, vertical = Space.s2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.width(24.dp))
                                CardImage(c.smallImageUrl, Modifier.width(32.dp).sharedCard(c.id))
                                Spacer(Modifier.width(Space.s3))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${c.setCode.uppercase()}${if (c.foil) " · foil" else ""}${if (c.quantity > 1) " · ×${c.quantity}" else ""}",
                                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1,
                                    )
                                }
                                Text(cardPrice(c), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                            }
                        }
                    }
                }
            }
            if (prices && s.bulkCards > 0) {
                SectionHeader("Bulk estimate")
                Panel { BulkCard(s.bulkCards) }
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

/** A plain rounded panel like the other Stats blocks; [content] brings its own side padding. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = Space.s4), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(vertical = Space.s3)) { content() }
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
    // Values in the currency Settings › Prices picks (dollars with Both: rows are narrow).
    fun money(r: StatRow) = primaryValue(r.value, r.eur)
    val max = rows.maxOfOrNull { if (prices) money(it) else it.cards.toDouble() }?.takeIf { it > 0 } ?: 1.0
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).clip(RoundedCornerShape(Corners.Small)).background(Palette.Surface).padding(horizontal = Space.s3, vertical = Space.s1)) {
        Column {
            rows.forEach { r ->
                Column(Modifier.fillMaxWidth().clickable { onRow(r) }.ruleBelow().padding(horizontal = Space.s1, vertical = Space.s2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lead != null) {
                            lead(r)
                            Spacer(Modifier.width(Space.s2))
                        }
                        Text(r.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${r.cards}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        if (prices) {
                            Spacer(Modifier.width(Space.s3))
                            Text(formatMoneyShort(r.value, r.eur), style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                        }
                    }
                    Spacer(Modifier.height(Space.s1))
                    val frac = ((if (prices) money(r) else r.cards.toDouble()) / max).toFloat().coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh)) {
                        if (frac > 0f) {
                            val fill = bar(r)
                            Box(
                                Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(Corners.Small)).background(fill)
                                    // White would vanish on a light panel: give it an edge.
                                    .then(if (!Palette.dark && fill == MtgColors.WOnSurface) Modifier.border(1.dp, MtgColors.SliceEdge, RoundedCornerShape(Corners.Small)) else Modifier),
                            )
                        }
                    }
                }
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1))
            }
        }
    }
}

private fun colorOf(k: String): Color = if (k == "C") MtgColors.C else MtgColors.barFor(k[0])

/** One box's value over time; pick the box with the pills. Boxes with the most value come first. */
@Composable
internal fun BoxValueChart(data: BoxValues, boxes: List<CardBox>) {
    val ranked = boxes.filter { (data.values[it.id]?.lastOrNull() ?: 0.0) > 0.0 }
        .sortedByDescending { data.values[it.id]?.lastOrNull() ?: 0.0 }
    if (ranked.isEmpty()) return
    var selected by remember { mutableLongStateOf(ranked.first().id) }
    val box = ranked.firstOrNull { it.id == selected } ?: ranked.first()
    val values = data.values[box.id].orEmpty()
    Column(Modifier.fillMaxWidth().padding(bottom = Space.s1)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s4),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            ranked.forEach { b -> SelectPill(b.name, b.id == box.id, { selected = b.id }) }
        }
        Spacer(Modifier.height(Space.s3))
        // Drag along the chart to read the box's value on one day.
        var scrub by remember(values) { mutableStateOf<Int?>(null) }
        Row(Modifier.padding(horizontal = Space.s4), verticalAlignment = Alignment.CenterVertically) {
            val s = scrub?.takeIf { it in values.indices }
            Text(formatUsd(s?.let { values[it] } ?: values.lastOrNull()), style = PriceStyle.copy(fontSize = PriceStyle.fontSize * 1.3f), color = Palette.Text)
            Spacer(Modifier.width(Space.s3))
            if (s != null) {
                Text(data.days.getOrNull(s)?.let { shortDay(it) }.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            } else {
                val first = values.firstOrNull() ?: 0.0
                ChangeText((values.lastOrNull() ?: 0.0) - first, first, MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(Space.s2))
        LineChart(values, Modifier.fillMaxWidth().height(80.dp).padding(horizontal = Space.s4), onScrub = { scrub = it })
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4)) {
            Text(shortDay(data.days.first()), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            Text(shortDay(data.days.last()), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

/** Bulk commons and uncommons at a per-1000 rate the user can change. */
@Composable
internal fun BulkCard(cards: Int) {
    var editing by remember { mutableStateOf(false) }
    val rate = HomePrefs.bulkRate
    Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s2, bottom = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("≈ ${formatUsd(cards / 1000.0 * rate)}", style = PriceStyle.copy(fontSize = PriceStyle.fontSize * 1.3f), color = Palette.Text)
            Text(
                "$cards commons and uncommons under 50¢",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text("Estimate at ${formatUsd(rate)} per 1000", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        TextButton(onClick = { editing = true }) { Text("Rate") }
    }
    if (editing) {
        var text by remember { mutableStateOf(if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()) }
        val parsed = com.mtgscanner.util.PriceInput.parse(text)
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = Palette.Surface,
            title = { Text("Dollars per 1000") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = { parsed?.let { HomePrefs.updateBulkRate(it) }; editing = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

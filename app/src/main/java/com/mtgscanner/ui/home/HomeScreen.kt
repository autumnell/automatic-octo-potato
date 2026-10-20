package com.mtgscanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.GoalProgress
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.data.observeGoalProgress
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.BrowseLaunch
import com.mtgscanner.ui.collection.SortBy
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.StatTile
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    /** What the cards were worth when added (only cards with a known price then). */
    val valueAtAdd: Double = 0.0,
    /** Today's value of those same cards. */
    val valueNowTracked: Double = 0.0,
    val history: List<ValueSnapshot> = emptyList(),
    val gainers: List<CollectionCard> = emptyList(),
    /** Commons and uncommons under 50¢, for the bulk estimate. */
    val bulkCards: Int = 0,
    val boxCount: Int = 0,
)

/** Worked out from price history: this week's movers and each box's value by day. */
data class PriceData(
    val movers: List<PriceMover> = emptyList(),
    val boxValues: BoxValues = BoxValues(emptyList(), emptyMap()),
)

class HomeViewModel(private val repo: CollectionRepository) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(repo.observeAll(), repo.observeSnapshots(), repo.observeBoxes()) { all, history, boxes ->
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
            valuable = all.filter { !it.proxy && (it.unitPrice() ?: 0.0) > 0 }.sortedByDescending { it.unitPrice() }.take(12),
            colors = colorCounts.toList(),
            // Proxies are worth $0 today, so leave them out of "since added" on both sides.
            valueAtAdd = all.filter { !it.proxy && it.priceAtAdd != null }.sumOf { (it.priceWhenAdded() ?: 0.0) * it.quantity },
            valueNowTracked = all.filter { !it.proxy && it.priceAtAdd != null }.sumOf { it.totalPrice() },
            history = history,
            gainers = all.filter { !it.proxy && (it.changeSinceAdded() ?: 0.0) > 0.0 }
                .sortedByDescending { (it.changeSinceAdded() ?: 0.0) * it.quantity }.take(12),
            bulkCards = HomeMath.bulk(all, 0.0).cards,
            boxCount = boxes.size,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    val boxes: StateFlow<List<CardBox>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cards to move and unknown scans, for "To be sorted". */
    val toSort: StateFlow<ToSortUi> = combine(repo.observeAll(), repo.observeBoxes(), repo.observePending()) { cards, boxes, pending ->
        ToSortUi(
            items = runCatching { repo.cardsToSort(cards, boxes) }.getOrDefault(emptyList()),
            unknown = pending.filter { it.unknown },
            boxNames = boxes.associate { it.id to it.name },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ToSortUi())

    /** Price history, and what it was loaded for: the day, the latest snapshot and the printings owned. */
    private var pointsCache: Pair<Any, List<com.mtgscanner.data.db.PricePoint>>? = null

    /**
     * Recomputed when cards change or prices refresh (a refresh writes a new snapshot). The price
     * history table is only re-read when the day, the snapshots or the set of printings owned change,
     * not on every move or quantity edit.
     */
    val prices: StateFlow<PriceData> = combine(repo.observeAll(), repo.observeSnapshots()) { all, snaps -> all to snaps }
        .map { (all, snaps) ->
            val today = java.time.LocalDate.now()
            val key = listOf(today.toString(), snaps.size, snaps.maxByOrNull { it.day }, all.mapTo(HashSet()) { it.scryfallId })
            val points = pointsCache?.takeIf { it.first == key }?.second
                ?: runCatching { repo.dao.allPricePoints() }.getOrNull()?.also { pointsCache = key to it }
                ?: emptyList()
            val weekAgo = today.minusDays(7).toString()
            PriceData(HomeMath.movers(all, points, weekAgo), HomeMath.boxValues(all, points))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceData())

    val goals: StateFlow<List<GoalProgress>> = repo.observeGoalProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moves every card in "To be sorted" to its box, with Undo. */
    fun sortAll() = viewModelScope.launch {
        val items = toSort.value.items
        if (items.isEmpty()) return@launch
        // Moving merges into rows already in the target box, so undo restores a snapshot instead of moving back.
        val before = repo.snapshot(items.map { it.card.id })
        val n = repo.applyMoves(items.map { it.card.id to it.to }).size
        Undo.offer(if (n == 1) "Moved 1 card" else "Moved $n cards") { repo.restore(before) }
    }

    suspend fun searchCards(text: String): List<ScryfallCard> {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        val found = runCatching { repo.api.search(q) }.getOrDefault(emptyList()).take(20)
        if (found.isNotEmpty()) return found
        return listOfNotNull(runCatching { repo.api.named(q) }.getOrNull())
    }

    /** Adds the card picked for an unknown scan and clears the scan. */
    fun resolveUnknown(scan: PendingScan, card: ScryfallCard) = viewModelScope.launch {
        val box = if (scan.boxId != CardBox.AUTO_SORT && repo.box(scan.boxId) != null) scan.boxId
        else repo.pickBox(card, scan.foil) ?: repo.anythingBox()
        repo.add(card, scan.foil, scan.quantity.coerceAtLeast(1), box)
        repo.deletePending(scan.id)
        Undo.offer("Added ${card.name}")
    }

    fun deleteUnknown(scan: PendingScan) = viewModelScope.launch {
        repo.deletePending(scan.id)
        // Put the same scan back, quantity and all.
        Undo.offer("Scan deleted") { repo.dao.addPending(scan) }
    }
}

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onCollection: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onSettings: () -> Unit,
    /** Opens Browse of the whole collection. Without it, Home goes via the Collection tab. */
    onBrowseAll: (() -> Unit)? = null,
    /** Opens the goals screen. */
    onGoals: () -> Unit = {},
    vm: HomeViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ctx = LocalContext.current
    remember { HomePrefs.load(ctx) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val toSort by vm.toSort.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var unknown by remember { mutableStateOf<PendingScan?>(null) }

    fun browse(sort: SortBy? = null, color: String? = null) {
        BrowseLaunch.sort = sort
        BrowseLaunch.color = color
        if (onBrowseAll != null) onBrowseAll()
        else {
            BrowseLaunch.openAll = true
            onCollection()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        Header(onSettings, onEdit = { editing = true })
        HeroCard(ui, onScan)
        Spacer(Modifier.height(12.dp))

        if (ui.loaded && ui.totalCards == 0) {
            if (ui.boxCount == 0) {
                EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = "Start with a box",
                    body = "Then scan cards into it.",
                    action = { GradientButton("Make a box", onCollection, icon = Icons.Filled.Inventory2) },
                )
            } else {
                EmptyState(
                    icon = Icons.Filled.Style,
                    title = "No cards yet",
                    body = "Scan or search to add cards.",
                    action = {
                        TextButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Search by name")
                        }
                    },
                )
            }
            // Unknown scans can exist before any card was added.
            if (!toSort.isEmpty) {
                SectionHeader("To be sorted")
                ToSortSection(toSort, { vm.sortAll() }, onOpenCard, onUnknown = { unknown = it })
            }
        } else if (ui.loaded) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Unique", "${ui.uniqueCards}", Modifier.weight(1f))
                StatTile("Sets", "${ui.sets}", Modifier.weight(1f))
                StatTile("Foils", "${ui.foils}", Modifier.weight(1f))
            }
            val showPrices = AppSettings.showPrices
            HomePrefs.sections.forEach { section ->
                when (section) {
                    HomeSection.TO_SORT -> if (!toSort.isEmpty) {
                        SectionHeader("To be sorted")
                        ToSortSection(toSort, { vm.sortAll() }, onOpenCard, onUnknown = { unknown = it })
                    }
                    HomeSection.GOALS -> {
                        SectionHeader("Goals") { if (goals.isNotEmpty()) TextButton(onClick = onGoals) { Text("See all") } }
                        GoalsPreview(goals, onGoals)
                    }
                    HomeSection.RECENT -> if (ui.recent.isNotEmpty()) {
                        SectionHeader("Recently added") { TextButton(onClick = { browse(SortBy.RECENT) }) { Text("See all") } }
                        CardCarousel(ui.recent, onOpenCard)
                    }
                    HomeSection.MOVERS -> if (showPrices && prices.movers.isNotEmpty()) {
                        SectionHeader("Price moves this week")
                        MoversCarousel(prices.movers, onOpenCard)
                    }
                    HomeSection.VALUABLE -> if (showPrices && ui.valuable.isNotEmpty()) {
                        SectionHeader("Most valuable") { TextButton(onClick = { browse(SortBy.PRICE) }) { Text("See all") } }
                        CardCarousel(ui.valuable, onOpenCard)
                    }
                    HomeSection.GAINERS -> if (showPrices && ui.gainers.isNotEmpty()) {
                        SectionHeader("Biggest gains") { TextButton(onClick = { browse(SortBy.GAIN) }) { Text("See all") } }
                        CardCarousel(ui.gainers, onOpenCard, showGain = true)
                    }
                    HomeSection.BOX_VALUES -> if (showPrices && prices.boxValues.days.size >= 2 && boxes.isNotEmpty()) {
                        SectionHeader("Value by box")
                        BoxValueChart(prices.boxValues, boxes)
                    }
                    HomeSection.BULK -> if (showPrices && ui.bulkCards > 0) {
                        SectionHeader("Bulk estimate")
                        BulkCard(ui.bulkCards)
                    }
                    HomeSection.COLORS -> {
                        SectionHeader("Colors")
                        ColorBreakdown(ui.colors, onColor = { browse(color = it) })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editing) EditHomeDialog(onDismiss = { editing = false })
    unknown?.let { scan ->
        UnknownScanDialog(
            scan = scan,
            search = { vm.searchCards(it) },
            onPick = { card -> vm.resolveUnknown(scan, card); unknown = null },
            onDelete = { vm.deleteUnknown(scan); unknown = null },
            onDismiss = { unknown = null },
        )
    }
}

@Composable
private fun Header(onSettings: () -> Unit, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("MTG Scanner", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEdit) { Text("Edit", color = Palette.Muted) }
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
                val prices = AppSettings.showPrices
                Text(if (prices) "Collection value" else "Your collection", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                Text(
                    if (prices) formatUsd(ui.totalValue) else "${ui.totalCards} cards",
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prices) Text("${ui.totalCards} cards", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                    else Text("${ui.uniqueCards} different cards", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                    if (prices && ui.valueAtAdd > 0) {
                        Spacer(Modifier.width(10.dp))
                        ChangeText(ui.valueNowTracked - ui.valueAtAdd, ui.valueAtAdd)
                        Text(" since added", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
                if (prices && ui.history.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    ValueChart(ui.history, Modifier.fillMaxWidth().height(72.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(shortDay(ui.history.first().day), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                        Text(shortDay(ui.history.last().day), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                GradientButton("Scan cards", onScan, Modifier.fillMaxWidth(), icon = Icons.Filled.CameraAlt)
            }
        }
    }
}

@Composable
private fun CardCarousel(cards: List<CollectionCard>, onOpenCard: (Long) -> Unit, showGain: Boolean = false) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(cards, key = { it.id }) { c ->
            CardTile(
                imageUrl = c.imageUrl,
                name = c.name,
                price = if (showGain) "+" + formatUsd(c.changeSinceAdded()) else formatUsd(c.unitPrice()),
                quantity = c.quantity,
                foil = c.foil,
                modifier = Modifier.width(118.dp),
                onClick = { onOpenCard(c.id) },
            )
        }
    }
}

@Composable
private fun ColorBreakdown(colors: List<Pair<String, Int>>, onColor: (String) -> Unit) {
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
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    row.forEach { (k, n) ->
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = n > 0) { onColor(k) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (k in listOf("W", "U", "B", "R", "G", "C", "M")) ColorPip(k[0])
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

/** "+$1.20 (4.0%)" in green, or red when the value went down. */
@Composable
fun ChangeText(change: Double, base: Double, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge) {
    val pct = if (base > 0) change / base * 100 else 0.0
    val up = change >= 0.005
    val down = change <= -0.005
    val sign = if (up) "+" else if (down) "−" else ""
    Text(
        "$sign${formatUsd(kotlin.math.abs(change))} (${"%.1f".format(java.util.Locale.US, kotlin.math.abs(pct))}%)",
        style = style,
        color = when { up -> Palette.Success; down -> Palette.Danger; else -> Palette.Muted },
    )
}

/** Simple line chart of collection value over time. */
@Composable
private fun ValueChart(points: List<ValueSnapshot>, modifier: Modifier) =
    com.mtgscanner.ui.components.LineChart(points.map { it.value }, modifier)

internal fun shortDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}.getOrDefault(day)

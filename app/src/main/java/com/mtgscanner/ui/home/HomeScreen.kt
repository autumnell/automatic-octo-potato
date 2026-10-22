package com.mtgscanner.ui.home

import com.mtgscanner.ui.components.formatPrimary
import com.mtgscanner.ui.components.primaryValue
import com.mtgscanner.data.db.totalEur
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.mtgscanner.ui.collection.BrowseViewModel
import com.mtgscanner.ui.collection.Filters
import com.mtgscanner.ui.collection.GroupBy
import com.mtgscanner.ui.collection.SavedSearchStore
import com.mtgscanner.ui.collection.SortBy
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.cardPriceShort
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.work.CardAlertStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import com.mtgscanner.ui.components.RollingText
import com.mtgscanner.ui.theme.PriceStyle
import com.mtgscanner.ui.theme.ThemeSettings
import com.mtgscanner.ui.theme.AccentMath
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.ui.detail.sharedCard
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

data class HomeUi(
    val loaded: Boolean = false,
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    /** [totalValue] in euros. */
    val totalEur: Double = 0.0,
    val sets: Int = 0,
    val foils: Int = 0,
    val recent: List<CollectionCard> = emptyList(),
    val valuable: List<CollectionCard> = emptyList(),
    /** Copies per color (W, U, B, R, G) and "C" colorless. A multicolor card counts once for each of its colors. */
    val colors: List<Pair<String, Int>> = emptyList(),
    /** Multicolor copies per color pair ("WU"…), then "3+" for three or more colors. */
    val pairs: List<Pair<String, Int>> = emptyList(),
    /** What the cards were worth when added (only cards with a known price then). */
    val valueAtAdd: Double = 0.0,
    /** Today's value of those same cards. */
    val valueNowTracked: Double = 0.0,
    val history: List<ValueSnapshot> = emptyList(),
    val gainers: List<CollectionCard> = emptyList(),
    /** Commons and uncommons under 50¢, for the bulk estimate. */
    val bulkCards: Int = 0,
    val boxCount: Int = 0,
    /** Art crop of the most valuable card, shown faintly behind the value card. */
    val heroArt: String? = null,
    /** The color (W/U/B/R/G) with the most copies by color identity, for "Accent from my collection". */
    val colorLetter: Char? = null,
)

/** Worked out from price history: this week's movers and each box's value by day. */
data class PriceData(
    val movers: List<PriceMover> = emptyList(),
    val boxValues: BoxValues = BoxValues(emptyList(), emptyMap()),
)

class HomeViewModel(private val repo: CollectionRepository) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(repo.observeAll(), repo.observeSnapshots(), repo.observeBoxes()) { all, history, boxes ->
        val colorCounts = linkedMapOf("W" to 0, "U" to 0, "B" to 0, "R" to 0, "G" to 0, "C" to 0)
        all.forEach { c ->
            val keys = if (c.colors.isEmpty()) listOf("C") else c.colors.map { it.toString() }.filter { it in colorCounts }
            keys.forEach { key -> colorCounts[key] = (colorCounts[key] ?: 0) + c.quantity }
        }
        val valuable = all.filter { !it.proxy && (it.unitPrice() ?: 0.0) > 0 }.sortedByDescending { it.unitPrice() }.take(12)
        HomeUi(
            loaded = true,
            totalCards = all.sumOf { it.quantity },
            uniqueCards = all.map { it.oracleId ?: it.name }.distinct().size,
            totalValue = all.sumOf { it.totalPrice() },
            totalEur = all.sumOf { it.totalEur() },
            sets = all.map { it.setCode }.distinct().size,
            foils = all.filter { it.foil }.sumOf { it.quantity },
            recent = all.sortedByDescending { it.addedAt }.take(12),
            valuable = valuable,
            colors = colorCounts.toList(),
            pairs = ColorPairs.count(all.map { it.colors to it.quantity }),
            // Proxies are worth $0 today, so leave them out of "since added" on both sides.
            valueAtAdd = all.filter { !it.proxy && it.priceAtAdd != null }.sumOf { (it.priceWhenAdded() ?: 0.0) * it.quantity },
            valueNowTracked = all.filter { !it.proxy && it.priceAtAdd != null }.sumOf { it.totalPrice() },
            history = history,
            gainers = all.filter { !it.proxy && (it.changeSinceAdded() ?: 0.0) > 0.0 }
                .sortedByDescending { (it.changeSinceAdded() ?: 0.0) * it.quantity }.take(12),
            bulkCards = HomeMath.bulk(all, 0.0).cards,
            boxCount = boxes.size,
            heroArt = artCropUrl(valuable.firstOrNull()?.imageUrl),
            colorLetter = AccentMath.dominant(all.map { it.colorIdentity to it.quantity }),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    val boxes: StateFlow<List<CardBox>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One random card of yours, the same all day, with where it lives. */
    val cardOfDay: StateFlow<DayCard?> = combine(repo.observeAll(), repo.observeBoxes()) { all, boxes -> CardOfDay.of(repo.appContext, all, boxes) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
                ?: runCatching { repo.dao.pricePointsSince(today.minusDays(120).toString()) }.getOrNull()?.also { pointsCache = key to it }
                ?: emptyList()
            val weekAgo = today.minusDays(7).toString()
            PriceData(HomeMath.movers(all, points, weekAgo), HomeMath.boxValues(all, points))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceData())

    private val searches = SavedSearchStore(repo.appContext)

    /** Saved search names, for the "Saved list" tile. */
    val listNames: StateFlow<List<String>> = searches.saved.map { l -> l.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Numbers for the stat tiles. */
    val tiles: StateFlow<TileData> = combine(repo.observeAll(), repo.observeBoxes(), repo.observeWishlist(), searches.saved) { all, boxes, wish, saved ->
        TileData(
            values = TileMath.counts(all, boxes.size, wish.sumOf { it.wanted }, System.currentTimeMillis()),
            lists = saved.associate { s -> s.name to all.filter { (s.boxId == null || it.boxId == s.boxId) && BrowseViewModel.matches(it, s.filters) }.sumOf { it.quantity } },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TileData())

    /** Per-card price alerts, with the card each one is for. */
    val alerts: StateFlow<List<AlertRow>> = run {
        CardAlertStore.load(repo.appContext)
        combine(CardAlertStore.alerts, repo.observeAll()) { list, all -> alertRows(list, all) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** A saved search's filters, by name. */
    fun savedFilters(name: String): Filters? = searches.saved.value.firstOrNull { it.name == name }?.filters

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

    /** Cards matching [text]; null when Scryfall couldn't be reached (offline), so it isn't shown as "No match". */
    suspend fun searchCards(text: String): List<ScryfallCard>? {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        val search = runCatching { repo.api.search(q) }
        val found = search.getOrNull().orEmpty().take(20)
        if (found.isNotEmpty()) return found
        val named = runCatching { repo.api.named(q) }
        named.getOrNull()?.let { return listOf(it) }
        return if (search.isFailure && named.isFailure) null else emptyList()
    }

    /** Adds the card picked for an unknown scan and clears the scan. */
    fun resolveUnknown(scan: PendingScan, card: ScryfallCard) = viewModelScope.launch {
        val box = if (scan.boxId != CardBox.AUTO_SORT && repo.box(scan.boxId) != null) scan.boxId
        else repo.pickBox(card, scan.foil) ?: repo.anythingBox()
        repo.add(card, scan.foil, scan.quantity.coerceAtLeast(1), box, lang = if (card.lang != "en") card.lang else scan.lang ?: "en")
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
    /** Opens collection stats (value by set, color, rarity). */
    onStats: () -> Unit = {},
    vm: HomeViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ctx = LocalContext.current
    remember { HomePrefs.load(ctx) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val toSort by vm.toSort.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val listNames by vm.listNames.collectAsStateWithLifecycle()
    val cardOfDay by vm.cardOfDay.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var editingTiles by remember { mutableStateOf(false) }
    var unknown by remember { mutableStateOf<PendingScan?>(null) }
    // Which section's picture of a card grows into the card page: the first one showing it, so a card in two
    // carousels doesn't have two pictures claiming the same transition.
    val showPricesNow = AppSettings.showPrices
    val sharedIds: Map<HomeSection, Set<Long>> = remember(HomePrefs.sections, ui, toSort, prices.movers, showPricesNow, cardOfDay) {
        val seen = HashSet<Long>()
        HomePrefs.sections.associateWith { s ->
            val ids = when (s) {
                HomeSection.TO_SORT -> toSort.items.take(4).map { it.card.id }
                HomeSection.CARD_OF_DAY -> listOfNotNull(cardOfDay?.card?.id)
                HomeSection.RECENT -> ui.recent.map { it.id }
                HomeSection.MOVERS -> if (showPricesNow) prices.movers.map { it.card.id } else emptyList()
                HomeSection.VALUABLE -> if (showPricesNow) ui.valuable.map { it.id } else emptyList()
                HomeSection.GAINERS -> if (showPricesNow) ui.gainers.map { it.id } else emptyList()
                else -> emptyList()
            }
            ids.filter { seen.add(it) }.toSet()
        }
    }
    fun shared(section: HomeSection): Set<Long> = sharedIds[section].orEmpty()
    // Keeps "Accent from my collection" in step with what's owned (saved, so it's right at the next start).
    LaunchedEffect(ui.loaded, ui.colorLetter) { if (ui.loaded) ThemeSettings.noteCollectionColor(ui.colorLetter) }

    fun browse(sort: SortBy? = null, color: String? = null) {
        BrowseLaunch.sort = sort
        BrowseLaunch.color = color
        if (onBrowseAll != null) onBrowseAll()
        else {
            BrowseLaunch.openAll = true
            onCollection()
        }
    }

    fun browseWith(filters: Filters, title: String? = null) {
        BrowseLaunch.filters = filters
        BrowseLaunch.title = title
        if (onBrowseAll != null) onBrowseAll()
        else {
            BrowseLaunch.openAll = true
            onCollection()
        }
    }

    fun openTile(m: TileMetric) {
        when (m) {
            TileMetric.TOTAL, TileMetric.UNIQUE -> browse(SortBy.NAME)
            TileMetric.SETS -> browseWith(Filters(sort = SortBy.SET, group = GroupBy.SET))
            TileMetric.FOILS -> browseWith(Filters(foilOnly = true, sort = SortBy.PRICE))
            TileMetric.RARES -> browseWith(Filters(rarities = setOf("rare"), sort = SortBy.PRICE))
            TileMetric.MYTHICS -> browseWith(Filters(rarities = setOf("mythic"), sort = SortBy.PRICE))
            TileMetric.LANDS -> browseWith(Filters(types = setOf("Land"), sort = SortBy.NAME))
            TileMetric.WEEK -> browseWith(Filters(addedSince = System.currentTimeMillis() - TileMath.WEEK_MS, sort = SortBy.RECENT), m.label)
            TileMetric.VALUE, TileMetric.AVERAGE -> onStats()
            TileMetric.BOXES -> onCollection()
            TileMetric.CUSTOM -> HomePrefs.tileList?.let { n -> vm.savedFilters(n)?.let { browseWith(it, n) } }
            // The wishlist lives on the Collection tab.
            TileMetric.WISHLIST -> onCollection()
            TileMetric.COMMANDERS, TileMetric.BULK, TileMetric.FOR_SALE, TileMetric.PROXIES, TileMetric.SIGNED, TileMetric.GRADED ->
                TileFlags.of(m)?.let { flag -> browseWith(Filters(flags = setOf(flag), sort = SortBy.PRICE), m.label) }
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
        HeroCard(ui, onClick = if (ui.loaded && ui.totalCards > 0) onStats else null)
        Spacer(Modifier.height(12.dp))
        com.mtgscanner.ui.settings.BackupReminder(hasCards = ui.loaded && ui.totalCards > 0)

        if (ui.loaded && ui.totalCards == 0) {
            if (ui.boxCount == 0) EmptyState("Start with a box", action = "Make a box", onAction = onCollection)
            else EmptyState("No cards yet", action = "Search by name", onAction = onSearch)
            // Unknown scans can exist before any card was added.
            if (!toSort.isEmpty) {
                HomeHeading("To be sorted")
                ToSortSection(toSort, { vm.sortAll() }, onOpenCard, onUnknown = { unknown = it })
            }
        } else if (ui.loaded) {
            StatTileRow(tiles, onTap = { openTile(it) }, onEdit = { editingTiles = true })
            val showPrices = AppSettings.showPrices
            HomePrefs.sections.forEach { section ->
                when (section) {
                    HomeSection.TO_SORT -> if (!toSort.isEmpty) {
                        HomeHeading("To be sorted")
                        ToSortSection(toSort, { vm.sortAll() }, onOpenCard, onUnknown = { unknown = it }, shared = shared(section))
                    }
                    HomeSection.GOALS -> {
                        HomeHeading("Goals", if (goals.isNotEmpty()) "See all" else null, onGoals)
                        GoalsPreview(goals, onGoals)
                    }
                    HomeSection.CARD_OF_DAY -> cardOfDay?.let { day ->
                        HomeHeading("Card of the day")
                        CardOfDaySection(day, onOpenCard, shared = day.card.id in shared(section))
                    }
                    HomeSection.RECENT -> if (ui.recent.isNotEmpty()) {
                        HomeHeading("Recently added", "See all") { browse(SortBy.RECENT) }
                        CardCarousel(ui.recent, onOpenCard, shared = shared(section))
                    }
                    HomeSection.MOVERS -> if (showPrices && prices.movers.isNotEmpty()) {
                        HomeHeading("Moves this week")
                        MoversCarousel(prices.movers, onOpenCard, shared = shared(section))
                    }
                    HomeSection.VALUABLE -> if (showPrices && ui.valuable.isNotEmpty()) {
                        HomeHeading("Most valuable", "See all") { browse(SortBy.PRICE) }
                        CardCarousel(ui.valuable, onOpenCard, shared = shared(section))
                    }
                    HomeSection.GAINERS -> if (showPrices && ui.gainers.isNotEmpty()) {
                        HomeHeading("Biggest gains", "See all") { browse(SortBy.GAIN) }
                        CardCarousel(ui.gainers, onOpenCard, showGain = true, shared = shared(section))
                    }
                    HomeSection.BOX_VALUES -> if (showPrices && prices.boxValues.days.size >= 2 && boxes.isNotEmpty()) {
                        HomeHeading("Value by box")
                        BoxValueChart(prices.boxValues, boxes)
                    }
                    HomeSection.BULK -> if (showPrices && ui.bulkCards > 0) {
                        HomeHeading("Bulk estimate")
                        BulkCard(ui.bulkCards)
                    }
                    HomeSection.COLORS -> {
                        HomeHeading("Colors")
                        ColorBreakdown(ui.colors, onColor = { browse(color = it) })
                    }
                    HomeSection.PAIRS -> {
                        HomeHeading("Color pairs")
                        PairBreakdown(ui.pairs, onPair = { browse(color = it) })
                    }
                    HomeSection.ALERTS -> if (showPrices && alerts.isNotEmpty()) {
                        HomeHeading("Price alerts")
                        AlertsList(alerts, onOpenCard)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editing) EditHomeDialog(onDismiss = { editing = false }, onTiles = { editing = false; editingTiles = true })
    if (editingTiles) EditTilesDialog(listNames, onDismiss = { editingTiles = false })
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
        Text("Grimoire", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEdit) { Text("Edit", color = Palette.Muted) }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
    }
}

/**
 * A plain section start: a hairline, the heading in the serif, and an optional text action on the right.
 * Home's sections sit straight on the background under these, without cards around them.
 */
@Composable
internal fun HomeHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = Palette.Hairline)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction) { Text(action, color = Palette.AccentText) }
        }
    }
}

/** The one hero on Home: collection value over faint art from the most valuable card. */
@Composable
private fun HeroCard(ui: HomeUi, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(shape)
            .background(Palette.HeroGradient)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        val art = ui.heroArt?.takeIf { AppSettings.showPrices }
        if (art != null) {
            // Faint and softened (blur works on Android 12+), with a scrim on the text side so numbers stay crisp.
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (Palette.dark) 0.34f else 0.22f,
                modifier = Modifier.matchParentSize().blur(4.dp),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to Palette.BoxCard.copy(alpha = 0.9f),
                        0.55f to Palette.BoxCard.copy(alpha = 0.6f),
                        1f to Palette.BoxCard.copy(alpha = 0.15f),
                    ),
                ),
            )
        }
        Column(Modifier.padding(20.dp)) {
            val prices = AppSettings.showPrices
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (prices) "Collection value" else "Your collection", style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (onClick != null) {
                    Text("Stats", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Muted, modifier = Modifier.size(18.dp))
                }
            }
            val big = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = PriceStyle.fontFeatureSettings)
            // AccentText: the raw accent is too faint on the light panel. It shrinks to fit in wide fonts.
            if (prices) RollingText(primaryValue(ui.totalValue, ui.totalEur), { formatPrimary(it, ui.totalEur) }, big, Palette.AccentText)
            else RollingText(ui.totalCards.toDouble(), { "${it.toLong()} cards" }, big, Palette.AccentText)
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
        }
    }
}

@Composable
private fun CardCarousel(cards: List<CollectionCard>, onOpenCard: (Long) -> Unit, showGain: Boolean = false, shared: Set<Long> = emptySet()) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(cards, key = { it.id }) { c ->
            CardTile(
                imageUrl = c.imageUrl,
                name = c.name,
                price = if (showGain) "+" + formatUsd(c.changeSinceAdded()) else cardPriceShort(c),
                quantity = c.quantity,
                foil = c.foil,
                modifier = Modifier.width(118.dp),
                imageModifier = if (c.id in shared) Modifier.sharedCard(c.id) else Modifier,
                onClick = { onOpenCard(c.id) },
            )
        }
    }
}

/**
 * One bar of the six colors, then one line of pips with their share. Tapping a slice or a pip picks that color:
 * the others fade and a line under the bar gives its name, share and card count, with a link to Browse.
 */
@Composable
private fun ColorBreakdown(colors: List<Pair<String, Int>>, onColor: (String) -> Unit) {
    val total = colors.sumOf { it.second }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val toggle = { k: String -> picked = if (picked == k) null else k }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            colors.filter { it.second > 0 }.forEach { (k, n) ->
                val alpha by animateFloatAsState(if (picked == null || picked == k) 1f else 0.3f, label = "slice")
                val fill = colorFor(k)
                Box(
                    Modifier.weight(n.toFloat()).fillMaxHeight()
                        .graphicsLayer { this.alpha = alpha }
                        .background(fill)
                        // White would vanish on the light background: give it an edge.
                        .then(if (!Palette.dark && k == "W") Modifier.border(1.dp, MtgColors.SliceEdge, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable(onClickLabel = "Show ${label(k)}") { toggle(k) }
                        .semantics { contentDescription = "${label(k)}, ${sharePercent(n, total)}" },
                )
            }
            if (total == 0) Box(Modifier.weight(1f).fillMaxHeight().background(Palette.SurfaceHigh))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            colors.forEach { (k, n) ->
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = n > 0, onClickLabel = "Show ${label(k)}") { toggle(k) }
                        .semantics(mergeDescendants = true) { contentDescription = "${label(k)}, ${sharePercent(n, total)}" }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ColorPip(k[0])
                    Spacer(Modifier.width(4.dp))
                    Text(
                        sharePercent(n, total),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            n == 0 -> Palette.Muted
                            picked == null || picked == k -> Palette.Text
                            else -> Palette.Muted
                        },
                        maxLines = 1,
                    )
                }
            }
        }
        AnimatedVisibility(visible = picked != null) {
            val k = picked ?: return@AnimatedVisibility
            val n = colors.firstOrNull { it.first == k }?.second ?: 0
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(colorFor(k)))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${label(k)} · ${sharePercent(n, total)} · ${"%,d".format(n)} cards",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onColor(k) }) { Text("Browse") }
            }
        }
    }
}

/**
 * Multicolor cards by color pair: one bar where each slice is split into its two colors, then the pairs owned as
 * two pips and a share. Tap a slice or pair for its guild name and count, with a Browse link.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PairBreakdown(pairs: List<Pair<String, Int>>, onPair: (String) -> Unit) {
    val total = pairs.sumOf { it.second }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val toggle = { k: String -> picked = if (picked == k) null else k }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
        if (total == 0) {
            Text("No multicolor cards yet", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            return@Column
        }
        Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            pairs.filter { it.second > 0 }.forEach { (k, n) ->
                val alpha by animateFloatAsState(if (picked == null || picked == k) 1f else 0.3f, label = "pair")
                Box(
                    Modifier.weight(n.toFloat()).fillMaxHeight()
                        .graphicsLayer { this.alpha = alpha }
                        .background(pairBrush(k))
                        .clickable { toggle(k) }
                        .semantics { contentDescription = "${ColorPairs.name(k)}, ${sharePercent(n, total)}" },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pairs.filter { it.second > 0 }.forEach { (k, n) ->
                Row(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable { toggle(k) }
                        .semantics(mergeDescendants = true) { contentDescription = "${ColorPairs.name(k)}, ${sharePercent(n, total)}" }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (k == ColorPairs.THREE_PLUS) {
                        Text("3+", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    } else {
                        ColorPip(k[0]); Spacer(Modifier.width(1.dp)); ColorPip(k[1])
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        sharePercent(n, total),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (picked == null || picked == k) Palette.Text else Palette.Muted,
                        maxLines = 1,
                    )
                }
            }
        }
        AnimatedVisibility(visible = picked != null) {
            val k = picked ?: return@AnimatedVisibility
            val n = pairs.firstOrNull { it.first == k }?.second ?: 0
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(pairBrush(k)))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${ColorPairs.name(k)} · ${sharePercent(n, total)} · ${"%,d".format(n)} cards",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onPair(k) }) { Text("Browse") }
            }
        }
    }
}

/** A pair's slice: its two colors top and bottom, hard-edged. Three or more colors use the multicolor gold. */
private fun pairBrush(k: String): Brush =
    if (k.length == 2 && k != ColorPairs.THREE_PLUS) {
        val a = colorFor(k.substring(0, 1)); val b = colorFor(k.substring(1, 2))
        Brush.verticalGradient(0f to a, 0.5f to a, 0.5f to b, 1f to b)
    } else SolidColor(MtgColors.Multi)

/** A color's share of the total, rounded; tiny but non-zero shares read "<1%". */
internal fun sharePercent(n: Int, total: Int): String = when {
    total <= 0 || n <= 0 -> "0%"
    n * 100 < total -> "<1%"
    else -> "${Math.round(n * 100.0 / total)}%"
}

private fun colorFor(k: String): Color = when (k) {
    "C" -> MtgColors.C
    else -> MtgColors.barFor(k[0])
}

private fun label(k: String) = when (k) {
    "W" -> "White"; "U" -> "Blue"; "B" -> "Black"; "R" -> "Red"; "G" -> "Green"; else -> "Colorless"
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

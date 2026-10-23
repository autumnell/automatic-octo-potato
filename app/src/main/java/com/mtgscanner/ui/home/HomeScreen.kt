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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
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
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.cardPriceShort
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import com.mtgscanner.ui.components.RollingText
import com.mtgscanner.ui.theme.PriceStyle
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.theme.ThemeSettings
import com.mtgscanner.ui.theme.AccentMath
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.ui.detail.sharedCard

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
    /** What the cards were worth when added (only cards with a known price then). */
    val valueAtAdd: Double = 0.0,
    /** Today's value of those same cards. */
    val valueNowTracked: Double = 0.0,
    val history: List<ValueSnapshot> = emptyList(),
    /** Biggest gains since added, for the "Since added" side of Price moves. */
    val gainers: List<CollectionCard> = emptyList(),
    val boxCount: Int = 0,
    /** Art crop of the most valuable card, shown faintly behind the value card. */
    val heroArt: String? = null,
    /** The color (W/U/B/R/G) with the most copies by color identity, for "Accent from my collection". */
    val colorLetter: Char? = null,
)

/** Worked out from price history: this week's movers. */
data class PriceData(val movers: List<PriceMover> = emptyList())

class HomeViewModel(private val repo: CollectionRepository) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(repo.observeAll(), repo.observeSnapshots(), repo.observeBoxes()) { all, history, boxes ->
        val top = all.filter { !it.proxy && (it.unitPrice() ?: 0.0) > 0 }.maxByOrNull { it.unitPrice() ?: 0.0 }
        HomeUi(
            loaded = true,
            totalCards = all.sumOf { it.quantity },
            uniqueCards = all.map { it.oracleId ?: it.name }.distinct().size,
            totalValue = all.sumOf { it.totalPrice() },
            totalEur = all.sumOf { it.totalEur() },
            sets = all.map { it.setCode }.distinct().size,
            foils = all.filter { it.foil }.sumOf { it.quantity },
            recent = all.sortedByDescending { it.addedAt }.take(12),
            // Proxies are worth $0 today, so leave them out of "since added" on both sides.
            valueAtAdd = all.filter { !it.proxy && it.priceAtAdd != null }.sumOf { (it.priceWhenAdded() ?: 0.0) * it.quantity },
            valueNowTracked = all.filter { !it.proxy && it.priceAtAdd != null }.sumOf { it.totalPrice() },
            history = history,
            gainers = all.filter { !it.proxy && (it.changeSinceAdded() ?: 0.0) > 0.0 }
                .sortedByDescending { (it.changeSinceAdded() ?: 0.0) * it.quantity }.take(12),
            boxCount = boxes.size,
            heroArt = artCropUrl(top?.imageUrl),
            colorLetter = AccentMath.dominant(all.map { it.colorIdentity to it.quantity }),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

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
            PriceData(HomeMath.movers(all, points, weekAgo))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceData())

    private val searches = SavedSearchStore(repo.appContext)

    /** Saved search names, for the "Saved list" tile. */
    val listNames: StateFlow<List<String>> = searches.saved.map { l -> l.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The saved search the "Saved list" tile counts, or null when that tile isn't shown. */
    private val tileList = snapshotFlow { HomePrefs.tileList.takeIf { TileMetric.CUSTOM in HomePrefs.tiles } }

    /** Numbers for the stat tiles. */
    val tiles: StateFlow<TileData> = combine(repo.observeAll(), repo.observeBoxes(), repo.observeWishlist(), searches.saved, tileList) { all, boxes, wish, saved, listName ->
        TileData(
            values = TileMath.counts(all, boxes.size, wish.sumOf { it.wanted }, System.currentTimeMillis()),
            lists = run {
                // Only the one search a tile shows is counted: matching every saved search costs a pass each.
                val s = saved.firstOrNull { it.name == listName } ?: return@run emptyMap()
                // box: in a saved search needs the box names.
                val boxNames = boxes.associate { it.id to it.name }
                val env = com.mtgscanner.data.search.SearchEnv(boxName = { boxNames[it] })
                mapOf(s.name to all.filter { (s.boxId == null || it.boxId == s.boxId) && BrowseViewModel.matches(it, s.filters, null, env) }.sumOf { it.quantity })
            },
            eur = TileMath.eurCounts(all),
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
        val foil = scan.foil && card.hasFoil || !card.hasNonFoil
        val box = if (scan.boxId != CardBox.AUTO_SORT && repo.box(scan.boxId) != null) scan.boxId
        else repo.pickBox(card, foil) ?: repo.anythingBox()
        val qty = scan.quantity.coerceAtLeast(1)
        val row = repo.add(card, foil, qty, box, lang = if (card.lang != "en") card.lang else scan.lang ?: "en")
        repo.deletePending(scan.id)
        Undo.offer("Added ${card.name}") {
            repo.remove(row, qty)
            repo.dao.addPending(scan)
        }
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
    /** Opens a set's page (new set offer). */
    onOpenSet: (String) -> Unit = {},
    vm: HomeViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ctx = LocalContext.current
    remember { HomePrefs.load(ctx) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val toSort by vm.toSort.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val listNames by vm.listNames.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var editingTiles by remember { mutableStateOf(false) }
    var unknown by remember { mutableStateOf<PendingScan?>(null) }
    var finding by remember { mutableStateOf(false) }
    // Which section's picture of a card grows into the card page: the first one showing it, so a card in two
    // carousels doesn't have two pictures claiming the same transition.
    val showPricesNow = AppSettings.showPrices
    val sharedIds: Map<HomeSection, Set<Long>> = remember(HomePrefs.sections, ui, toSort, prices.movers, showPricesNow) {
        val seen = HashSet<Long>()
        HomePrefs.sections.associateWith { s ->
            val ids = when (s) {
                HomeSection.NEWEST -> ui.recent.map { it.id }
                HomeSection.TO_SORT -> toSort.items.take(4).map { it.card.id }
                HomeSection.RECENT -> ui.recent.map { it.id }
                HomeSection.MOVERS -> if (showPricesNow) prices.movers.map { it.card.id } + ui.gainers.map { it.id } else emptyList()
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
            // The wishlist is a page of the Collection tab.
            TileMetric.WISHLIST -> { WishlistLaunch.open(); onCollection() }
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
        Header(onSettings, onEdit = { editing = true }, onFind = if (ui.loaded && ui.totalCards > 0) ({ finding = true }) else null)
        HeroCard(ui, onClick = if (ui.loaded && ui.totalCards > 0) onStats else null)
        Spacer(Modifier.height(12.dp))
        com.mtgscanner.ui.settings.BackupReminder(hasCards = ui.loaded && ui.totalCards > 0)
        WhatsNewLine()
        if (ui.loaded && ui.totalCards > 0) {
            BoxCheckNudge(onCheck = { id -> com.mtgscanner.util.OpenRequests.boxCheck.value = id; onScan() })
            NewSetOffer(onOpenSet)
        }

        if (ui.loaded && ui.totalCards == 0) {
            if (ui.boxCount == 0) EmptyState("Start with a box", action = "Make a box", onAction = onCollection, fullScreen = true)
            else EmptyState("No cards yet", action = "Search by name", onAction = onSearch, fullScreen = true)
            // Unknown scans can exist before any card was added.
            if (!toSort.isEmpty) {
                HomeHeading("To be sorted")
                ToSortSection(toSort, { vm.sortAll() }, onOpenCard, onUnknown = { unknown = it })
            }
        } else if (ui.loaded) {
            val showPrices = AppSettings.showPrices
            HomePrefs.sections.forEach { section ->
                when (section) {
                    HomeSection.NEWEST -> if (ui.recent.isNotEmpty()) {
                        HomeHeading("Newest", "See all") { browse(SortBy.RECENT) }
                        NewestStrip(ui.recent, onOpenCard, shared = shared(section))
                    }
                    HomeSection.TILES -> {
                        Spacer(Modifier.height(8.dp))
                        StatTileRow(tiles, onTap = { openTile(it) }, onEdit = { editingTiles = true })
                    }
                    HomeSection.TO_SORT -> if (!toSort.isEmpty) {
                        HomeHeading("To be sorted")
                        ToSortSection(toSort, { vm.sortAll() }, onOpenCard, onUnknown = { unknown = it }, shared = shared(section))
                    }
                    HomeSection.GOALS -> {
                        HomeHeading("Goals", if (goals.isNotEmpty()) "See all" else null, onGoals)
                        GoalsPreview(goals, onGoals)
                    }
                    HomeSection.RECENT -> if (ui.recent.isNotEmpty()) {
                        HomeHeading("Recently added", "See all") { browse(SortBy.RECENT) }
                        CardCarousel(ui.recent, onOpenCard, shared = shared(section))
                    }
                    HomeSection.MOVERS -> if (showPrices && (prices.movers.isNotEmpty() || ui.gainers.isNotEmpty())) {
                        PriceMovesSection(prices.movers, ui.gainers, onOpenCard, onSeeGains = { browse(SortBy.GAIN) }, shared = shared(section))
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

    if (finding) com.mtgscanner.ui.collection.WhereIsSheet(onOpenCard = { finding = false; onOpenCard(it) }, onDismiss = { finding = false })
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
private fun Header(onSettings: () -> Unit, onEdit: () -> Unit, onFind: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Grimoire", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEdit) { Text("Edit", color = Palette.Muted) }
        if (onFind != null) IconButton(onClick = onFind) { Icon(Icons.Outlined.Search, "Where is it?", tint = Palette.Icon) }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = Palette.Icon) }
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


/** The one hero on Home: collection value, with a strip of art from the most valuable card on the right. */
@Composable
private fun HeroCard(ui: HomeUi, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(Corners.Medium)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(shape)
            .background(Palette.BoxCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        val art = ui.heroArt?.takeIf { AppSettings.showPrices }
        if (art != null) {
            // The top card's art behind the whole card, softened (blur works on Android 12+), with a scrim on the text side so numbers stay crisp.
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
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                }
            }
            val big = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = PriceStyle.fontFeatureSettings)
            // AccentText: the raw accent is too faint on the light panel. It shrinks to fit in wide fonts.
            if (prices) RollingText(primaryValue(ui.totalValue, ui.totalEur), { formatPrimary(it, ui.totalEur) }, big, Palette.Text)
            else RollingText(ui.totalCards.toDouble(), { "${it.toLong()} cards" }, big, Palette.Text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (prices) Text("${ui.totalCards} cards", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Text)
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
            val updated = rememberPricesUpdated()
            if (prices && updated.isNotEmpty()) {
                Spacer(Modifier.height(if (ui.history.size >= 2) 4.dp else 8.dp))
                Text(updated, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
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

/** This week's movers or the biggest gains since added, picked with two pills. */
@Composable
private fun PriceMovesSection(
    movers: List<PriceMover>,
    gainers: List<CollectionCard>,
    onOpenCard: (Long) -> Unit,
    onSeeGains: () -> Unit,
    shared: Set<Long>,
) {
    // Until a pill is tapped: this week when anything moved, else since added.
    var picked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val gains = picked ?: movers.isEmpty()
    HomeHeading("Price moves", if (gains && gainers.isNotEmpty()) "See all" else null, onSeeGains)
    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectPill("This week", !gains, { picked = false })
        SelectPill("Since added", gains, { picked = true })
    }
    val empty = when {
        gains && gainers.isEmpty() -> "No gains yet"
        !gains && movers.isEmpty() -> "No moves this week"
        else -> null
    }
    when {
        empty != null -> Text(empty, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(horizontal = 16.dp))
        gains -> CardCarousel(gainers, onOpenCard, showGain = true, shared = shared)
        else -> MoversCarousel(movers, onOpenCard, shared = shared)
    }
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
        style = style.merge(TabularNums),
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

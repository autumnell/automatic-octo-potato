package com.mtgscanner.ui.home

import com.mtgscanner.ui.theme.Space
import kotlin.math.roundToInt
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
    /** Opens a deck (the "is complete" note). */
    onOpenDeck: (Long) -> Unit = {},
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
    var bell by remember { mutableStateOf(false) }
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
        Header(onSettings, onBell = { bell = true }, onEdit = { editing = true }, onFind = if (ui.loaded && ui.totalCards > 0) ({ finding = true }) else null)
        HeroCard(ui, onClick = if (ui.loaded && ui.totalCards > 0) onStats else null)
        // Tap a color to browse those cards: one color exactly, multicolor, or colorless.
        if (ui.loaded && HomePrefs.colorBar && ui.colorSplit.size > 1) ColorBar(ui.colorSplit) { key ->
            val f = when (key) {
                "M" -> Filters(colors = setOf(com.mtgscanner.ui.collection.CardColors.LEGACY_MULTI), sort = SortBy.PRICE)
                else -> Filters(colors = setOf(key), colorMatch = com.mtgscanner.ui.collection.ColorMatch.EXACTLY, sort = SortBy.PRICE)
            }
            browseWith(f, ColorSplit.label(key))
        }
        Spacer(Modifier.height(Space.s3))
        com.mtgscanner.ui.settings.BackupReminder(hasCards = ui.loaded && ui.totalCards > 0)
        WhatsNewLine()
        val complete by vm.completeDecks.collectAsStateWithLifecycle()
        com.mtgscanner.ui.decks.DeckCompleteNotes(complete, onOpen = onOpenDeck)
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
                        Spacer(Modifier.height(Space.s2))
                        StatTileRow(tiles, onTap = { openTile(it) }, onEdit = { editingTiles = true })
                    }
                    HomeSection.TO_SORT -> if (!toSort.isEmpty) {
                        HomeHeading("To be sorted")
                        ToSortSection(toSort, { vm.sortAll() }, onOpenCard, onUnknown = { unknown = it }, shared = shared(section))
                    }
                    // The cards you opened last, to get back to them in one tap.
                    HomeSection.VIEWED -> com.mtgscanner.ui.collection.RecentCardsStrip(
                        ui.totalCards, onOpen = { onOpenCard(it.id) }, cardWidth = 88.dp,
                        heading = { HomeHeading("Recently viewed") },
                    )
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
        Spacer(Modifier.height(Space.s6))
    }

    if (finding) com.mtgscanner.ui.collection.WhereIsSheet(onOpenCard = { finding = false; onOpenCard(it) }, onDismiss = { finding = false })
    if (editing) EditHomeDialog(onDismiss = { editing = false }, onTiles = { editing = false; editingTiles = true })
    if (editingTiles) EditTilesDialog(listNames, onDismiss = { editingTiles = false })
    if (bell) NotificationsSheet(
        onOpen = { where ->
            when {
                where.startsWith("card/") -> where.removePrefix("card/").toLongOrNull()?.let(onOpenCard)
                where == "settings" -> onSettings()
                where == "collection" -> onCollection()
            }
        },
        onDismiss = { bell = false },
    )
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
private fun Header(onSettings: () -> Unit, onBell: () -> Unit, onEdit: () -> Unit, onFind: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s2, top = Space.s3, bottom = Space.s3), verticalAlignment = Alignment.CenterVertically) {
        Text("Grimoire", style = MaterialTheme.typography.titleLarge)
        NotificationBell(onBell)
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
    Column(Modifier.fillMaxWidth().padding(top = Space.s8)) {
        HorizontalDivider(Modifier.padding(horizontal = Space.s4), thickness = 1.dp, color = Palette.Hairline)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Space.s4, end = Space.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction) { Text(action, color = Palette.Muted) }
        }
    }
}

/** The one hero on Home: collection value, with a strip of art from the most valuable card on the right. */
@Composable
private fun HeroCard(ui: HomeUi, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(Corners.Medium)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = Space.s4)
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
        Column(Modifier.padding(Space.s6)) {
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
            // With large text the change moves to its own line whole, rather than squeezing "since added" into a column.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.Center) {
                if (prices) Text("${ui.totalCards} cards", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = Palette.Text, modifier = Modifier.padding(end = Space.s3))
                else Text("${ui.uniqueCards} different cards", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                if (prices && ui.valueAtAdd > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChangeText(ui.valueNowTracked - ui.valueAtAdd, ui.valueAtAdd)
                        Text(" since added", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, softWrap = false)
                    }
                }
            }
            if (prices && ui.history.size >= 2) {
                Spacer(Modifier.height(Space.s3))
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
    LazyRow(contentPadding = PaddingValues(horizontal = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
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
    Row(Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        SelectPill("This week", !gains, { picked = false })
        SelectPill("Since added", gains, { picked = true })
    }
    val empty = when {
        gains && gainers.isEmpty() -> "No gains yet"
        !gains && movers.isEmpty() -> "No moves this week"
        else -> null
    }
    when {
        empty != null -> Text(empty, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4))
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

/** The collection by color as one thin bar under the value card, with the biggest shares named below. Tap a color to browse it. */
@Composable
private fun ColorBar(split: List<Pair<String, Int>>, onPick: (String) -> Unit) {
    val total = split.sumOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s4, top = Space.s3)) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            split.forEachIndexed { i, (key, n) ->
                val color = if (key == "M") com.mtgscanner.ui.theme.MtgColors.Multi else com.mtgscanner.ui.theme.MtgColors.barFor(key[0])
                Box(
                    Modifier.weight(n.toFloat()).fillMaxHeight()
                        .clickable(onClickLabel = "Browse ${ColorSplit.label(key)}") { onPick(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(8.dp).padding(end = if (i < split.lastIndex) 2.dp else 0.dp)
                            .clip(RoundedCornerShape(2.dp)).background(color),
                    )
                }
            }
        }
        val top = split.sortedByDescending { it.second }.take(3)
        Text(
            top.joinToString(" · ") { (k, n) -> "${ColorSplit.label(k)} ${(n * 100f / total).roundToInt()}%" },
            style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
        )
    }
}

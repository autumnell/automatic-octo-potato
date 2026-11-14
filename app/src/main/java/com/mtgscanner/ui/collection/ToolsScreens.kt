package com.mtgscanner.ui.collection

import com.mtgscanner.util.plural
import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.BoxLayout
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.wantAllWithUndo
import com.mtgscanner.data.MoveLine
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.ExtraGroup
import com.mtgscanner.data.ExtraRow
import com.mtgscanner.data.ExtrasFinder
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.data.Goals
import com.mtgscanner.data.db.Goal
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import kotlinx.coroutines.flow.map
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.AddCardSheet
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.OwnedBadge
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.search.SearchViewModel
import com.mtgscanner.ui.search.of
import com.mtgscanner.ui.search.printingOf
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.PlaceholderCards
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.ruleBelow

/** How much of a set you own: different collector numbers, out of the main set (or every printing, with variants). */
data class SetProgress(
    val code: String,
    val name: String,
    val iconUrl: String?,
    val owned: Int,
    /** 0 when the set list couldn't be loaded. */
    val total: Int,
    val releasedAt: String?,
) {
    val fraction: Float get() = if (total > 0) (owned.toFloat() / total).coerceAtMost(1f) else 0f
}

/** Set tracker and extras finder. */
class ToolsViewModel(private val repo: CollectionRepository) : ViewModel() {
    val cards: StateFlow<List<CollectionCard>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val boxes: StateFlow<List<CardBox>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    // Sets

    private val _sets = MutableStateFlow<List<ScryfallSet>?>(null)
    val sets: StateFlow<List<ScryfallSet>?> = _sets
    private val _setsFailed = MutableStateFlow(false)
    val setsFailed: StateFlow<Boolean> = _setsFailed

    fun loadSets() {
        if (_sets.value != null) return
        viewModelScope.launch {
            runCatching { repo.sets() }.onSuccess { _sets.value = it; _setsFailed.value = false }.onFailure { _setsFailed.value = true }
        }
    }

    /** Count showcase, borderless, promo and other extras toward finishing a set. Off: main set only. */
    val includeVariants: StateFlow<Boolean> = SetPrefs.includeVariants(repo.appContext)
    fun setIncludeVariants(on: Boolean) = SetPrefs.setIncludeVariants(on)

    val setProgress: StateFlow<List<SetProgress>?> = combine(cards, _sets, _setsFailed, includeVariants) { all, sets, failed, variants ->
        if (sets == null && !failed) return@combine null
        val owned = all.groupBy { it.setCode.uppercase() }
        val known = sets.orEmpty().associateBy { it.code.uppercase() }
        owned.map { (code, cs) ->
            val s = known[code]
            val counted = if (variants) cs else cs.filter { MainSet.isMain(it.collectorNumber, MainSet.treatmentsOf(it.treatments), s?.printedSize) }
            SetProgress(
                code = code,
                name = s?.name ?: cs.first().setName,
                iconUrl = s?.iconSvgUri,
                owned = counted.map { it.collectorNumber.lowercase() }.distinct().size,
                total = s?.let { MainSet.total(it.cardCount, it.printedSize, variants) } ?: 0,
                releasedAt = s?.releasedAt ?: cs.first().releasedAt,
            )
        }.sortedWith(compareByDescending<SetProgress> { it.fraction }.thenByDescending { it.releasedAt.orEmpty() })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _setCards = MutableStateFlow<List<ScryfallCard>?>(null)
    val setCards: StateFlow<List<ScryfallCard>?> = _setCards
    private val _setFailed = MutableStateFlow(false)
    val setFailed: StateFlow<Boolean> = _setFailed

    fun loadSet(code: String) {
        viewModelScope.launch {
            _setFailed.value = false
            runCatching { repo.setCards(code) }.onSuccess { _setCards.value = it }.onFailure { _setFailed.value = true }
        }
        loadSets()
    }

    /** Codes of sets pinned to Home (kept as goals, so Home's Goals section shows their progress). */
    val pinnedSets: StateFlow<Map<String, Goal>> = repo.observeGoals()
        .map { gs -> gs.filter { it.kind == Goals.SET }.associateBy { it.target.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Pins a set to Home, or unpins it. Undo either way. */
    fun togglePin(code: String, name: String) = viewModelScope.launch {
        val existing = pinnedSets.value[code.lowercase()]
        if (existing != null) {
            repo.deleteGoal(existing.id)
            Undo.offer("Unpinned") { repo.addGoal(existing) }
        } else {
            val id = repo.addGoal(Goal(name = name, kind = Goals.SET, target = code.lowercase()))
            Undo.offer("Pinned to Home") { repo.deleteGoal(id) }
        }
    }

    fun setName(code: String): String =
        _sets.value?.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name
            ?: cards.value.firstOrNull { it.setCode.equals(code, ignoreCase = true) }?.setName ?: code.uppercase()

    /** Wishlists each missing card once, skipping cards owned in another printing. Tapping again adds nothing. */
    fun wishlistMissing(missing: List<ScryfallCard>) {
        viewModelScope.launch {
            val owned = repo.allCards().mapNotNull { it.oracleId }.toSet()
            val want = wishTargets(missing, owned) { it.oracleId }
            val batch = repo.wantAllWithUndo(want.map { it to 1 })
            val n = batch.count
            val msg = if (n == 1) "1 card on your wishlist" else "$n cards on your wishlist"
            if (batch.changed > 0) Undo.offer(msg, batch.undo) else _message.value = msg
        }
    }

    // Extras

    /** Copies to keep of each card: starts at your "Keep a playset" setting (4 when it's off). */
    private val _keep = MutableStateFlow(SortPrefs.playsetLimit.takeIf { it > 0 } ?: 4)
    val keep: StateFlow<Int> = _keep
    fun setKeep(n: Int) { _keep.value = n.coerceIn(1, 99) }

    val extras: StateFlow<List<ExtraGroup>> = combine(cards, boxes, _keep) { all, bs, k ->
        // Only main boxes count: not trade binders, deck boxes, or the extras and bulk boxes.
        val policy = SortPrefs.policy
        val skip = bs.filter { it.kind == "trade" || it.kind == "deck" || it.id == policy.extrasBoxId || it.id == policy.bulkBoxId }
            .map { it.id }.toSet()
        // Copies kept in a deck are in no box (see DeckSlots.kt): not counted or moved either.
        val inBoxes = bs.mapTo(HashSet()) { it.id }
        ExtrasFinder.find(
            all.filter { it.boxId in inBoxes }.map {
                ExtraRow(it.id, it.oracleId ?: it.name, it.name, it.quantity, it.boxId, it.addedAt, it.typeLine, it.unitPrice(), it.condition, it.lang)
            },
            k, skip,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveExtras(groups: List<ExtraGroup>, boxId: Long) {
        val moves = groups.flatMap { it.moves }
        if (moves.isEmpty()) return
        viewModelScope.launch {
            val before = repo.snapshot(moves.map { it.first })
            // The physical move list: which copies to pull from which box.
            val byBox = boxes.value.associateBy { it.id }
            val rows = cards.value.associateBy { it.id }
            val target = byBox[boxId]
            val layout = BoxLayout.of(target?.divider.orEmpty())
            val lines = moves.mapNotNull { (id, n) ->
                val c = rows[id] ?: return@mapNotNull null
                MoveLine(n, c.name, c.setCode, byBox[c.boxId]?.name ?: "?", target?.name ?: "?", Sorting.spot(layout, c.name, c.facts()))
            }
            val added = MoveList.add(lines, show = true)
            repo.moveCopiesMany(moves, boxId)
            val name = target?.name ?: "trade binder"
            Undo.offer("Moved ${moves.sumOf { it.second }} extra copies to $name") {
                repo.restore(before)
                MoveList.remove(added)
            }
        }
    }

    fun createTradeBinder() = viewModelScope.launch { repo.createBox("Trade binder", "trade") }
}

/** Missing cards worth wishlisting: one per card (any printing), none you already own. */
fun <T> wishTargets(missing: List<T>, ownedOracleIds: Set<String>, oracleId: (T) -> String?): List<T> =
    missing.filter { c -> oracleId(c).let { it != null && it !in ownedOracleIds } }.distinctBy { oracleId(it) }

@Composable
private fun toolsVm(): ToolsViewModel = viewModel(factory = AppViewModels.Factory)

@Composable
private fun TopBar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(end = Space.s2, top = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        actions()
    }
}

/** Every set you own cards from, with how close you are to the full set. */
@Composable
fun SetsScreen(onBack: () -> Unit, onOpenSet: (String) -> Unit) {
    val vm = toolsVm()
    val progress by vm.setProgress.collectAsStateWithLifecycle()
    val failed by vm.setsFailed.collectAsStateWithLifecycle()
    val variants by vm.includeVariants.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadSets() }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        TopBar("Set tracker", "How much of each set you own", onBack)
        var filter by remember { mutableStateOf("") }
        SearchField(filter, { filter = it }, Modifier.padding(horizontal = Space.s4, vertical = Space.s2), placeholder = "Filter sets")
        Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s1)) {
            SelectPill("Include variants", variants, { vm.setIncludeVariants(!variants) })
        }
        if (failed) Text(
            "Offline: set sizes missing",
            style = MaterialTheme.typography.bodySmall, color = Palette.Gold, modifier = Modifier.padding(horizontal = Space.s4),
        )
        val q = filter.trim().lowercase()
        val list = progress?.let { all -> if (q.isEmpty()) all else all.filter { it.name.lowercase().contains(q) || it.code.lowercase() == q } }
        when {
            list != null && list.isEmpty() && q.isNotEmpty() -> EmptyState("No sets found", Modifier.padding(top = Space.s6))
            list == null -> PlaceholderRows(8, Modifier.padding(Space.s4))
            list.isEmpty() -> EmptyState("No sets yet", Modifier.padding(top = Space.s6))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = Space.s6)) {
                items(list, key = { it.code }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenSet(s.code) }.padding(horizontal = Space.s4, vertical = Space.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            if (s.iconUrl != null) AsyncImage(
                                s.iconUrl, null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Palette.Text),
                            ) else Text(s.code.take(3), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        }
                        Spacer(Modifier.width(Space.s3))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(
                                    if (s.total > 0) "${s.owned} / ${s.total}" else "${s.owned}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (s.total > 0 && s.owned >= s.total) Palette.Success else Palette.Muted,
                                )
                            }
                            if (s.total > 0) {
                                Spacer(Modifier.height(Space.s2))
                                LinearProgressIndicator(
                                    progress = { s.fraction },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Corners.Small)),
                                    color = Palette.Text,
                                    trackColor = Palette.SurfaceHigh,
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One set: every card in it by rarity, with the ones you own marked and the missing ones dimmed. */
@Composable
fun SetDetailScreen(code: String, onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val vm = toolsVm()
    // Search's add flow, for cards you don't own yet.
    val search: SearchViewModel = viewModel(factory = AppViewModels.Factory)
    val sui by search.ui.collectAsStateWithLifecycle()
    val addBoxes by search.boxes.collectAsStateWithLifecycle()
    val ownedAny by search.owned.collectAsStateWithLifecycle()
    val cards by vm.setCards.collectAsStateWithLifecycle()
    val failed by vm.setFailed.collectAsStateWithLifecycle()
    val owned by vm.cards.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val variants by vm.includeVariants.collectAsStateWithLifecycle()
    val pinned by vm.pinnedSets.collectAsStateWithLifecycle()
    // Kept across rotation.
    var show by rememberSaveable { mutableStateOf(if (SetLaunch.takeMissing()) "missing" else "all") }
    var query by rememberSaveable { mutableStateOf("") }
    // 0: pictures, 1: poster (the whole set small), 2: list.
    var layout by rememberSaveable { mutableStateOf(0) }
    var confirmWish by remember { mutableStateOf(false) }
    var newBox by remember { mutableStateOf(false) }
    LaunchedEffect(code) { vm.loadSet(code) }
    LaunchedEffect(message) { message?.let { Undo.offer(it); vm.messageShown() } }
    LaunchedEffect(sui.message) { sui.message?.let { Undo.offer(it); search.messageShown() } }

    val info = sets?.firstOrNull { it.code.equals(code, ignoreCase = true) }
    val mine = remember(owned, code) {
        owned.filter { it.setCode.equals(code, ignoreCase = true) }.groupBy { it.collectorNumber.lowercase() }
    }
    // Main set only unless variants count: no showcase, borderless, extended art or promos.
    val all = remember(cards, variants, info) {
        cards.orEmpty().let { list -> if (variants) list else list.filter { MainSet.isMain(it.collectorNumber, it.treatments, info?.printedSize) } }
    }
    val missing = remember(all, mine) { all.filter { it.collectorNumber.lowercase() !in mine } }
    val missingCost = remember(missing) { missing.sumOf { it.prices.usd?.toDoubleOrNull() ?: 0.0 } }
    val have = all.size - missing.size
    // What "Wishlist missing" would add: once per card, none owned in another printing.
    val toWish = remember(missing, owned) { wishTargets(missing, owned.mapNotNull { it.oracleId }.toSet()) { it.oracleId } }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = Space.s1, top = Space.s1), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            val isPinned = code.lowercase() in pinned
            IconButton(onClick = { vm.togglePin(code, info?.name ?: vm.setName(code)) }) {
                Icon(
                    if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    if (isPinned) "Unpin from Home" else "Pin to Home",
                    tint = if (isPinned) Palette.Text else Palette.Icon,
                )
            }
            IconButton(onClick = { layout = (layout + 1) % 3 }) {
                Icon(
                    when (layout) { 0 -> Icons.Outlined.Apps; 1 -> Icons.AutoMirrored.Outlined.List; else -> Icons.Outlined.GridView },
                    when (layout) { 0 -> "Show as a poster"; 1 -> "Show as a list"; else -> "Show pictures" },
                    tint = Palette.Icon,
                )
            }
            if (toWish.isNotEmpty()) IconButton(onClick = { confirmWish = true }) {
                Icon(Icons.Outlined.FavoriteBorder, "Add missing cards to wishlist", tint = Palette.Icon)
            }
        }
        SetHeader(
            name = info?.name ?: vm.setName(code),
            code = code,
            iconUrl = info?.iconSvgUri,
            releasedAt = info?.releasedAt ?: all.firstOrNull()?.releasedAt,
            count = if (all.isNotEmpty()) all.size else info?.cardCount ?: 0,
            have = have,
            total = all.size,
            missingCost = if (missing.isEmpty()) null else missingCost,
        )
        when {
            failed && cards == null -> EmptyState("Couldn't load this set", Modifier.padding(top = Space.s6), action = "Try again", onAction = { vm.loadSet(code) })
            cards == null -> PlaceholderCards(9, Modifier.padding(Space.s4))
            else -> {
                SearchField(query, { query = it }, Modifier.padding(horizontal = Space.s4, vertical = Space.s2), placeholder = "Filter this set")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s4, vertical = Space.s1),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    SelectPill("All", show == "all", { show = "all" })
                    SelectPill("Owned", show == "owned", { show = "owned" })
                    SelectPill("Missing", show == "missing", { show = "missing" })
                    SelectPill("Include variants", variants, { vm.setIncludeVariants(!variants) })
                }
                val q = query.trim().lowercase()
                val groups = remember(all, mine, show, q) {
                    val shown = all.filter { c ->
                        val isMine = c.collectorNumber.lowercase() in mine
                        (show == "all" || (show == "owned") == isMine) &&
                            (q.isEmpty() || c.name.lowercase().contains(q) || c.collectorNumber.lowercase() == q || c.effectiveTypeLine.lowercase().contains(q))
                    }
                    RARITY_GROUPS.map { (key, label) -> Triple(key, label, shown.filter { rarityGroup(it.rarity) == key }) }
                        .filter { it.third.isNotEmpty() }
                }
                if (groups.isEmpty()) {
                    EmptyState(if (show == "missing" && q.isEmpty()) "You own the whole set" else "No cards", Modifier.padding(top = Space.s6))
                } else {
                    LazyVerticalGrid(
                        GridCells.Fixed(when (layout) { 0 -> 3; 1 -> 5; else -> 1 }),
                        contentPadding = PaddingValues(start = Space.s3, end = Space.s3, bottom = Space.s6),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups.forEach { (key, label, list) ->
                            item(key = "h_$key", span = { GridItemSpan(maxLineSpan) }) {
                                val got = list.count { it.collectorNumber.lowercase() in mine }
                                Row(Modifier.padding(start = Space.s2, top = Space.s4, bottom = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                                    RarityMark(key, Modifier.size(12.dp))
                                    Spacer(Modifier.width(Space.s2))
                                    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("$got / ${list.size}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                }
                            }
                            items(list, key = { it.id }) { c ->
                                val rows = mine[c.collectorNumber.lowercase()]
                                val qty = rows?.sumOf { it.quantity } ?: 0
                                val open: () -> Unit = {
                                    val first = rows?.firstOrNull()
                                    if (first != null) onOpenCard(first.id) else search.select(c)
                                }
                                // Hold a grey card to wishlist it.
                                val wish: (() -> Unit)? = if (qty == 0) { { vm.wishlistMissing(listOf(c)) } } else null
                                when (layout) {
                                    0 -> SetCardTile(c, qty, open, wish)
                                    1 -> SetPosterTile(c, qty, open, wish)
                                    else -> SetCardRow(c, qty, open)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val decks by search.decks.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf<com.mtgscanner.ui.components.AddPick?>(null) }
    LaunchedEffect(sui.selected?.id) { pick = null }
    sui.selected?.let { card ->
        if (!sui.showPrintings) AddCardSheet(
            card = card,
            boxes = addBoxes,
            defaultBoxId = search.lastBoxId,
            onAdd = search::add,
            onChangePrinting = search::openPrintings,
            onDismiss = { search.select(null) },
            onCreateBox = { newBox = true },
            onWishlist = search::wishlist,
            owned = ownedAny.printingOf(card).label,
            decks = decks,
            onAddToDeck = search::addToDeck,
            onCreateDeck = { name -> search.createDeck(name) { pick = com.mtgscanner.ui.components.AddPick(deckId = it) } },
            pick = pick,
        )
    }
    if (sui.showPrintings) PrintingsSheet(sui.printings, sui.selected?.id, search::pickPrinting, search::closePrintings)
    if (newBox) BoxEditorSheet(
        title = "New box",
        onSave = { d -> search.createBox(d) { pick = com.mtgscanner.ui.components.AddPick(boxId = it) }; newBox = false },
        onDismiss = { newBox = false },
    )

    if (confirmWish) AlertDialog(
        onDismissRequest = { confirmWish = false },
        containerColor = Palette.Surface,
        title = { Text("Wishlist ${toWish.size} missing cards?") },
        text = {
            val cost = toWish.sumOf { it.prices.usd?.toDoubleOrNull() ?: 0.0 }
            Text("Any printing counts" + if (AppSettings.showPrices) " · about ${formatUsd(cost)} total" else "")
        },
        confirmButton = { TextButton(onClick = { vm.wishlistMissing(toWish); confirmWish = false }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { confirmWish = false }) { Text("Cancel") } },
    )
}

/** Rarity group key to heading, in display order. */
private val RARITY_GROUPS = listOf("mythic" to "Mythics", "rare" to "Rares", "uncommon" to "Uncommons", "common" to "Commons", "special" to "Special")

private fun rarityGroup(rarity: String): String = when (val r = rarity.lowercase()) {
    "mythic", "rare", "uncommon", "common" -> r
    else -> "special"
}

private fun ScryfallCard.anyPrice(): String? = prices.usd ?: prices.usdFoil ?: prices.usdEtched

@Composable
private fun SetHeader(
    name: String,
    code: String,
    iconUrl: String?,
    releasedAt: String?,
    count: Int,
    have: Int,
    total: Int,
    missingCost: Double?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface), contentAlignment = Alignment.Center) {
                if (iconUrl != null) AsyncImage(iconUrl, null, Modifier.size(32.dp), colorFilter = ColorFilter.tint(Palette.Icon))
                else Text(code.uppercase().take(4), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            Spacer(Modifier.width(Space.s4))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(code.uppercase(), releasedAt, if (count > 0) "${plural(count, "card")}" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
            }
        }
        if (total > 0) {
            Spacer(Modifier.height(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "You own $have of $total",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (have >= total) Palette.Success else Palette.Text,
                    modifier = Modifier.weight(1f),
                )
                if (missingCost != null && AppSettings.showPrices) {
                    Text("Missing ${formatUsd(missingCost)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
            }
            Spacer(Modifier.height(Space.s2))
            LinearProgressIndicator(
                progress = { (have.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Corners.Small)),
                color = Palette.Text,
                trackColor = Palette.SurfaceHigh,
                drawStopIndicator = {},
            )
        }
    }
}

/** The whole set at a glance: small pictures only, owned in color, missing in grey. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SetPosterTile(c: ScryfallCard, qty: Int, onClick: () -> Unit, onWish: (() -> Unit)?) {
    Box(Modifier.padding(Space.s1).clip(RoundedCornerShape(Corners.Small)).combinedClickable(onClick = onClick, onLongClick = onWish)) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth().alpha(if (qty > 0) 1f else 0.55f), grey = qty == 0)
        if (qty > 1) Text(
            "$qty",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(Space.s1)
                .clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = Space.s1),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SetCardTile(c: ScryfallCard, qty: Int, onClick: () -> Unit, onWish: (() -> Unit)?) {
    Column(Modifier.padding(Space.s1).clip(RoundedCornerShape(Corners.Small)).combinedClickable(onClick = onClick, onLongClick = onWish)) {
        Box {
            // Missing cards in grey; hold one to wishlist it.
            CardImage(c.imageUrl ?: c.smallImageUrl, Modifier.fillMaxWidth().alpha(if (qty > 0) 1f else 0.55f), zoom = qty > 0, grey = qty == 0)
            if (qty > 0) OwnedBadge(qty, Modifier.align(Alignment.TopStart).padding(Space.s1))
            val price = c.anyPrice()
            if (AppSettings.showPrices && price != null) Text(
                formatUsd(price),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.s1)
                    .clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = Space.s1, vertical = 1.dp),
            )
        }
        Text(
            "#${c.collectorNumber} ${c.name}",
            style = MaterialTheme.typography.labelSmall,
            color = if (qty > 0) Palette.Text else Palette.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.s1),
        )
    }
}

@Composable
private fun SetCardRow(c: ScryfallCard, qty: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).ruleBelow().padding(horizontal = Space.s1, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl, Modifier.width(42.dp).alpha(if (qty > 0) 1f else 0.45f), zoom = true)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(
                c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (qty > 0) Palette.Text else Palette.Muted,
            )
            Text(
                "#${c.collectorNumber} · ${c.effectiveTypeLine}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (qty > 0) {
            Spacer(Modifier.width(Space.s2))
            OwnedBadge(qty)
        }
        if (AppSettings.showPrices) {
            Spacer(Modifier.width(Space.s2))
            Text(formatUsd(c.anyPrice()), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text)
        }
    }
}

/** Cards you have more of than a deck can use, ready to move to a trade binder. */
@Composable
fun ExtrasScreen(onBack: () -> Unit) {
    val vm = toolsVm()
    val groups by vm.extras.collectAsStateWithLifecycle()
    val keep by vm.keep.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val trade = boxes.filter { it.kind == "trade" }
    var target by remember { mutableStateOf<Long?>(null) }
    var skipped by remember { mutableStateOf(setOf<String>()) }
    val chosen = groups.filter { it.key !in skipped }
    val dest = target?.takeIf { id -> trade.any { it.id == id } } ?: trade.firstOrNull()?.id

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        TopBar("Extras", "Copies beyond what a deck can use", onBack)
        MoveListHost()
        Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Text("Keep", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            QuantityStepper(keep, onChange = vm::setKeep)
        }
        Text(
            "Not counted: basic lands, trade binders, decks",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4),
        )
        if (groups.isEmpty()) {
            EmptyState("No extras over $keep", Modifier.padding(top = Space.s6))
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.s2)) {
            items(groups, key = { it.key }) { g ->
                val on = g.key !in skipped
                Row(
                    Modifier.fillMaxWidth().clickable { skipped = if (on) skipped + g.key else skipped - g.key }.padding(horizontal = Space.s3, vertical = Space.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, { skipped = if (on) skipped + g.key else skipped - g.key }, colors = CheckboxDefaults.colors(checkedColor = Palette.Text))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        Text("You have ${g.owned} · keep ${g.keep} · ${g.extra} extra", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    if (AppSettings.showPrices) Text(formatUsd(g.extraValue), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text)
                }
            }
        }
        Surface(color = Palette.Surface) {
            Column(Modifier.fillMaxWidth().padding(Space.s4).navigationBarsPadding()) {
                if (trade.isEmpty()) {
                    Text("Make a trade binder to move extras into", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.height(Space.s2))
                    GradientButton("Create a trade binder", { vm.createTradeBinder() }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
                } else {
                    if (trade.size > 1) Row(Modifier.padding(bottom = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        trade.forEach { b ->
                            SelectPill(b.name, dest == b.id, { target = b.id }, leading = { Icon(boxIcon(b.kind), null, Modifier.size(18.dp), tint = boxColor(b.color)) })
                        }
                    }
                    val n = chosen.sumOf { it.extra }
                    val value = chosen.sumOf { it.extraValue }
                    GradientButton(
                        "Move $n extras to ${trade.firstOrNull { it.id == dest }?.name ?: "trade binder"}" + priceSuffix(value),
                        { dest?.let { vm.moveExtras(chosen, it) } },
                        Modifier.fillMaxWidth(),
                        enabled = n > 0 && dest != null,
                    )
                }
            }
        }
    }
}

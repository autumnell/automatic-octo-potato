package com.mtgscanner.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ExtraGroup
import com.mtgscanner.data.ExtraRow
import com.mtgscanner.data.ExtrasFinder
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PackOpening
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How much of a set you own: different collector numbers, out of every printing in the set. */
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

/** Set tracker, extras finder and pack history. */
class ToolsViewModel(private val repo: CollectionRepository) : ViewModel() {
    val cards: StateFlow<List<CollectionCard>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val boxes: StateFlow<List<CardBox>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    // Sets

    private val _sets = MutableStateFlow<List<ScryfallSet>?>(null)
    private val _setsFailed = MutableStateFlow(false)
    val setsFailed: StateFlow<Boolean> = _setsFailed

    fun loadSets() {
        if (_sets.value != null) return
        viewModelScope.launch {
            runCatching { repo.sets() }.onSuccess { _sets.value = it; _setsFailed.value = false }.onFailure { _setsFailed.value = true }
        }
    }

    val setProgress: StateFlow<List<SetProgress>?> = combine(cards, _sets, _setsFailed) { all, sets, failed ->
        if (sets == null && !failed) return@combine null
        val owned = all.groupBy { it.setCode.uppercase() }
        val known = sets.orEmpty().associateBy { it.code.uppercase() }
        owned.map { (code, cs) ->
            val s = known[code]
            SetProgress(
                code = code,
                name = s?.name ?: cs.first().setName,
                iconUrl = s?.iconSvgUri,
                owned = cs.map { it.collectorNumber.lowercase() }.distinct().size,
                total = s?.cardCount ?: 0,
                releasedAt = s?.releasedAt ?: cs.first().releasedAt,
            )
        }.sortedWith(compareByDescending<SetProgress> { it.fraction }.thenByDescending { it.releasedAt.orEmpty() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun setName(code: String): String =
        _sets.value?.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name
            ?: cards.value.firstOrNull { it.setCode.equals(code, ignoreCase = true) }?.setName ?: code.uppercase()

    fun wishlistMissing(missing: List<ScryfallCard>) {
        viewModelScope.launch {
            missing.forEach { runCatching { repo.addToWishlist(it) } }
            _message.value = "Added ${missing.size} cards to your wishlist"
        }
    }

    // Extras

    private val _keep = MutableStateFlow(4)
    val keep: StateFlow<Int> = _keep
    fun setKeep(n: Int) { _keep.value = n }

    val extras: StateFlow<List<ExtraGroup>> = combine(cards, boxes, _keep) { all, bs, k ->
        val trade = bs.filter { it.kind == "trade" }.map { it.id }.toSet()
        ExtrasFinder.find(
            all.map { ExtraRow(it.id, it.oracleId ?: it.name, it.name, it.quantity, it.boxId, it.addedAt, it.typeLine, it.unitPrice()) },
            k, trade,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveExtras(groups: List<ExtraGroup>, boxId: Long) {
        val moves = groups.flatMap { it.moves }
        if (moves.isEmpty()) return
        viewModelScope.launch {
            val before = repo.snapshot(moves.map { it.first })
            repo.moveCopiesMany(moves, boxId)
            val name = boxes.value.firstOrNull { it.id == boxId }?.name ?: "trade binder"
            Undo.offer("Moved ${moves.sumOf { it.second }} extra copies to $name") { repo.restore(before) }
        }
    }

    fun createTradeBinder() = viewModelScope.launch { repo.createBox("Trade binder", "trade") }

    // Packs

    val packs: StateFlow<List<PackOpening>> = repo.observePacks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun deletePack(id: Long) = viewModelScope.launch { repo.deletePack(id) }
}

@Composable
private fun toolsVm(): ToolsViewModel = viewModel(factory = AppViewModels.Factory)

@Composable
private fun TopBar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
    LaunchedEffect(Unit) { vm.loadSets() }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        TopBar("Set tracker", "How much of each set you own", onBack)
        if (failed) Text(
            "Couldn't reach Scryfall, so set sizes are missing. Your owned counts are still shown.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Gold, modifier = Modifier.padding(horizontal = 20.dp),
        )
        val list = progress
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
            list.isEmpty() -> EmptyState(Icons.Filled.Inventory2, "No sets yet", "Scan or add cards and their sets show up here.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.code }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenSet(s.code) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            if (s.iconUrl != null) AsyncImage(
                                s.iconUrl, null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Palette.Text),
                            ) else Text(s.code.take(3), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        }
                        Spacer(Modifier.width(12.dp))
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
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { s.fraction },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Palette.Accent,
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

/** One set: every card in it, with the ones you own lit up and the missing ones dimmed. */
@Composable
fun SetDetailScreen(code: String, onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val vm = toolsVm()
    val cards by vm.setCards.collectAsStateWithLifecycle()
    val failed by vm.setFailed.collectAsStateWithLifecycle()
    val owned by vm.cards.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf("all") }
    var confirmWish by remember { mutableStateOf(false) }
    LaunchedEffect(code) { vm.loadSet(code) }
    LaunchedEffect(message) { message?.let { Undo.offer(it); vm.messageShown() } }

    val mine = remember(owned, code) {
        owned.filter { it.setCode.equals(code, ignoreCase = true) }.groupBy { it.collectorNumber.lowercase() }
    }
    val all = cards.orEmpty()
    val missing = all.filter { it.collectorNumber.lowercase() !in mine }
    val missingCost = missing.sumOf { it.prices.usd?.toDoubleOrNull() ?: 0.0 }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        TopBar(
            vm.setName(code),
            if (all.isEmpty()) null else "${all.size - missing.size} of ${all.size} owned · ${missing.size} missing${priceSuffix(missingCost)}",
            onBack,
        )
        when {
            failed && cards == null -> EmptyState(Icons.Filled.CloudOff, "Couldn't load this set", "Check your connection and try again.",
                action = { TextButton(onClick = { vm.loadSet(code) }) { Text("Try again") } })
            cards == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
            else -> {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectPill("All", show == "all", { show = "all" })
                    SelectPill("Owned", show == "owned", { show = "owned" })
                    SelectPill("Missing", show == "missing", { show = "missing" })
                    Spacer(Modifier.weight(1f))
                    if (missing.isNotEmpty()) IconButton(onClick = { confirmWish = true }) {
                        Icon(Icons.Filled.FavoriteBorder, "Add missing cards to wishlist", tint = Palette.Accent)
                    }
                }
                val shown = when (show) {
                    "owned" -> all.filter { it.collectorNumber.lowercase() in mine }
                    "missing" -> missing
                    else -> all
                }
                LazyVerticalGrid(GridCells.Adaptive(100.dp), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp)) {
                    items(shown, key = { it.id }) { c ->
                        val rows = mine[c.collectorNumber.lowercase()]
                        Column(
                            Modifier.padding(4.dp).clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = rows != null) { rows?.firstOrNull()?.let { onOpenCard(it.id) } }
                                .alpha(if (rows != null) 1f else 0.38f),
                        ) {
                            CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth(), corner = 8.dp)
                            Text("#${c.collectorNumber}" + (rows?.let { " · ×${it.sumOf { r -> r.quantity }}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        }
                    }
                }
            }
        }
    }

    if (confirmWish) AlertDialog(
        onDismissRequest = { confirmWish = false },
        containerColor = Palette.Surface,
        title = { Text("Add ${missing.size} missing cards to your wishlist?") },
        text = { Text("Any printing you scan later counts toward them." + if (AppSettings.showPrices) " Together they cost about ${formatUsd(missingCost)}." else "") },
        confirmButton = { TextButton(onClick = { vm.wishlistMissing(missing); confirmWish = false }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { confirmWish = false }) { Text("Cancel") } },
    )
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectPill("Keep 4 (60-card)", keep == 4, { vm.setKeep(4) })
            SelectPill("Keep 1 (Commander)", keep == 1, { vm.setKeep(1) })
        }
        Text(
            "Basic lands and cards already in a trade binder aren't counted. The newest copies are the ones suggested to move.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (groups.isEmpty()) {
            EmptyState(Icons.Filled.ContentCopy, "No extras", "You don't have more than $keep of any card outside your trade binders.")
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(groups, key = { it.key }) { g ->
                val on = g.key !in skipped
                Row(
                    Modifier.fillMaxWidth().clickable { skipped = if (on) skipped + g.key else skipped - g.key }.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, { skipped = if (on) skipped + g.key else skipped - g.key }, colors = CheckboxDefaults.colors(checkedColor = Palette.Accent))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        Text("You have ${g.owned} · keep ${g.keep} · ${g.extra} extra", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    if (AppSettings.showPrices) Text(formatUsd(g.extraValue), style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
                }
            }
        }
        Surface(color = Palette.Surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                if (trade.isEmpty()) {
                    Text("Make a trade binder to move extras into.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.height(8.dp))
                    GradientButton("Create a trade binder", { vm.createTradeBinder() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
                } else {
                    if (trade.size > 1) Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        trade.forEach { b ->
                            SelectPill(b.name, dest == b.id, { target = b.id }, leading = { Icon(boxIcon(b.kind), null, Modifier.size(14.dp), tint = boxColor(b.color)) })
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

/** Packs opened in pack mode, with what each was worth. */
@Composable
fun PacksScreen(onBack: () -> Unit) {
    val vm = toolsVm()
    val packs by vm.packs.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Long?>(null) }
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        val spent = packs.sumOf { it.cost ?: 0.0 }
        val pulled = packs.sumOf { it.value }
        TopBar(
            "Pack history",
            if (packs.isEmpty()) null else "${packs.size} packs" + if (AppSettings.showPrices) " · paid ${formatUsd(spent)} · pulled ${formatUsd(pulled)}" else "",
            onBack,
        )
        if (packs.isEmpty()) {
            EmptyState(Icons.Filled.Inventory2, "No packs yet", "In the scanner, tap the modes button at the top and choose Open a pack. Scan what you pulled, then finish the pack to see if it was worth it.")
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(packs, key = { it.id }) { p ->
                Surface(
                    onClick = { open = if (open == p.id) null else p.id },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Palette.Surface,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("${fmt.format(Date(p.openedAt))} · ${p.cards} cards", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                            if (AppSettings.showPrices) Column(horizontalAlignment = Alignment.End) {
                                Text(formatUsd(p.value), style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
                                p.cost?.takeIf { it > 0 }?.let { ChangeText(p.value - it, it, MaterialTheme.typography.labelMedium) }
                            }
                        }
                        if (p.best.isNotBlank()) Text("Best: ${p.best}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        if (open == p.id) {
                            Spacer(Modifier.height(8.dp))
                            p.pulls.lines().filter { it.isNotBlank() }.forEach { line ->
                                val parts = line.split('|')
                                Row {
                                    Text("${parts.getOrNull(0)} (${parts.getOrNull(1)?.uppercase()} #${parts.getOrNull(2)})",
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    if (AppSettings.showPrices) Text(formatUsd(parts.getOrNull(3)?.toDoubleOrNull()), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                }
                            }
                            TextButton(onClick = { vm.deletePack(p.id) }) {
                                Icon(Icons.Filled.DeleteOutline, null, Modifier.size(16.dp), tint = Palette.Danger)
                                Spacer(Modifier.width(4.dp))
                                Text("Delete from history", color = Palette.Danger)
                            }
                        }
                    }
                }
            }
        }
    }
}

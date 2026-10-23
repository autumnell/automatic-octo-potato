package com.mtgscanner.ui.goals

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mtgscanner.data.GoalProgress
import com.mtgscanner.data.Goals
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Goal
import com.mtgscanner.data.observeGoalProgress
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.ruleBelow

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GoalsViewModel(private val repo: CollectionRepository) : ViewModel() {
    /** Bumped by Retry, to fetch goals that failed offline again. */
    private val retries = MutableStateFlow(0)

    val goals: StateFlow<List<GoalProgress>?> = retries
        .flatMapLatest { repo.observeGoalProgress() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun retry() {
        com.mtgscanner.data.GoalCache.clearFailures()
        retries.value++
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    suspend fun suggest(text: String): List<String> = runCatching { repo.api.autocomplete(text) }.getOrDefault(emptyList())

    /** Every printing of the card named [name]. */
    fun addCardGoal(name: String) = viewModelScope.launch {
        val card = runCatching { repo.api.named(name) }.getOrNull()
        val oracle = card?.oracleId
        if (card == null || oracle == null) { _message.value = "Card not found"; return@launch }
        repo.addGoal(Goal(name = "Every ${card.name}", kind = Goals.PRINTINGS, target = oracle))
    }

    /** Oracle ids on the wishlist, for the hearts on missing cards. */
    val wished: StateFlow<Set<String>> = repo.observeWishlist().map { l -> l.map { it.oracleId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Puts a missing card on the wishlist, or takes it off when it's already there. Undo either way. */
    fun toggleWish(card: ScryfallCard) = viewModelScope.launch {
        val oracle = card.oracleId ?: return@launch
        val existing = repo.wishlistItem(oracle)
        if (existing != null) {
            repo.removeWish(existing.id)
            Undo.offer("Off wishlist") { repo.restoreWish(existing) }
        } else {
            repo.addToWishlist(card)
            Undo.offer("Wishlisted ${card.name}") { repo.wishlistItem(oracle)?.let { repo.removeWish(it.id) } }
        }
    }

    /** Wishlists every missing card not already on it, with Undo. */
    fun wishlistAll(missing: List<ScryfallCard>) = viewModelScope.launch {
        val have = repo.observeWishlist().first().map { it.oracleId }.toSet()
        val add = Goals.toWishlist(missing, have)
        if (add.isEmpty()) { _message.value = "All on your wishlist"; return@launch }
        add.forEach { repo.addToWishlist(it) }
        Undo.offer(if (add.size == 1) "Wishlisted 1 card" else "Wishlisted ${add.size} cards") {
            add.forEach { c -> repo.wishlistItem(c.oracleId)?.let { repo.removeWish(it.id) } }
        }
    }

    fun delete(goal: Goal) = viewModelScope.launch {
        repo.deleteGoal(goal.id)
        Undo.offer("Goal removed") { repo.addGoal(goal) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { GoalsViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MtgApp).repository) }
        }
    }
}

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    /** Sets pinned from Set tracker open their set page. */
    onOpenSet: (String) -> Unit = {},
    vm: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory),
) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    // Home can ask for one goal to open (tapping it there).
    var openId by remember { mutableLongStateOf(com.mtgscanner.ui.home.GoalsLaunch.take()) }
    LaunchedEffect(message) { message?.let { Undo.offer(it); vm.messageShown() } }
    val found = goals?.firstOrNull { it.goal.id == openId }
    // A pinned set has its own page in Set tracker.
    LaunchedEffect(found) {
        val g = found?.goal
        if (g != null && g.kind == Goals.SET) { openId = -1L; onOpenSet(g.target) }
    }
    val open = found?.takeIf { it.goal.kind != Goals.SET }
    BackHandler(enabled = open != null) { openId = -1L }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (open != null) openId = -1L else onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(
                open?.goal?.name ?: "Goals", style = MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (open == null) IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, "Add goal") }
            else IconButton(onClick = { vm.delete(open.goal); openId = -1L }) { Icon(Icons.Outlined.DeleteOutline, "Delete goal") }
        }
        val list = goals
        when {
            list == null -> PlaceholderRows(3, Modifier.padding(16.dp), rowHeight = 72.dp)
            open != null -> MissingList(open, vm, onRetry = { vm.retry() })
            list.isEmpty() -> EmptyState("No goals yet", action = "Add goal", onAction = { adding = true })
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(list, key = { it.goal.id }) { p ->
                    GoalCard(p, onClick = { if (p.goal.kind == Goals.SET) onOpenSet(p.goal.target) else openId = p.goal.id })
                }
            }
        }
    }

    if (adding) {
        AddGoalDialog(
            vm = vm,
            onDismiss = { adding = false },
        )
    }
}

/** One goal with its progress bar. Used on Home too. */
@Composable
fun GoalCard(p: GoalProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A plain row on the background with a rule under it.
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).ruleBelow().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when {
                p.error -> Icon(Icons.Outlined.CloudOff, "Offline", tint = Palette.Icon, modifier = Modifier.size(16.dp))
                !p.loaded -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.Accent)
                else -> Text(
                    "${p.have}/${p.total}", style = MaterialTheme.typography.labelLarge,
                    color = if (p.done) Palette.Success else Palette.Muted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { p.fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Corners.Small)),
            color = if (p.done) Palette.Success else Palette.Accent,
            trackColor = Palette.SurfaceHigh,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun MissingList(p: GoalProgress, vm: GoalsViewModel, onRetry: () -> Unit) {
    if (p.loaded && p.missing.isEmpty()) {
        EmptyState("Complete")
        return
    }
    if (p.error) {
        EmptyState("Offline", action = "Retry", onAction = onRetry)
        return
    }
    if (!p.loaded) {
        PlaceholderRows(6, Modifier.padding(16.dp))
        return
    }
    val wished by vm.wished.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Missing ${p.missing.size}", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                    if (AppSettings.showPrices && p.cost > 0) {
                        Text("${formatUsd(p.cost)} to complete", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Text)
                    }
                }
                TextButton(onClick = { vm.wishlistAll(p.missing) }) {
                    Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Wishlist all")
                }
            }
        }
        items(p.missing, key = { it.id }) { c ->
            val isWished = c.oracleId != null && c.oracleId in wished
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (isWished) "Remove from wishlist" else "Add to wishlist") { vm.toggleWish(c) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardImage(c.smallImageUrl, Modifier.width(34.dp), zoom = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${c.set.uppercase()} #${c.collectorNumber} · ${c.rarity.replaceFirstChar(Char::uppercase)}",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1,
                    )
                }
                if (AppSettings.showPrices) Goals.cheapest(c)?.let {
                    Text(formatUsd(it), style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (isWished) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint = if (isWished) Palette.AccentText else Palette.Icon,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AddGoalDialog(vm: GoalsViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    /** The suggestion just picked, so picking it doesn't look it up again. */
    var picked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(text) {
        if (text.length < 2 || text == picked) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        suggestions = vm.suggest(text).take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Every printing of") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Card name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    suggestions.forEach { s ->
                        Text(
                            s, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { picked = s; text = s; suggestions = emptyList() }.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { vm.addCardGoal(text.trim()); onDismiss() }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

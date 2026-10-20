package com.mtgscanner.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.db.Goal
import com.mtgscanner.data.observeGoalProgress
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalsViewModel(private val repo: CollectionRepository) : ViewModel() {
    val goals: StateFlow<List<GoalProgress>?> = repo.observeGoalProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    suspend fun suggest(text: String): List<String> = runCatching { repo.api.autocomplete(text) }.getOrDefault(emptyList())
    suspend fun sets(): List<ScryfallSet> = runCatching { repo.sets() }.getOrDefault(emptyList())

    /** Every printing of the card named [name]. */
    fun addCardGoal(name: String) = viewModelScope.launch {
        val card = runCatching { repo.api.named(name) }.getOrNull()
        val oracle = card?.oracleId
        if (card == null || oracle == null) { _message.value = "Card not found"; return@launch }
        repo.addGoal(Goal(name = "Every ${card.name}", kind = Goals.PRINTINGS, target = oracle))
    }

    fun addSetGoal(set: ScryfallSet, rarities: Set<String>) = viewModelScope.launch {
        val label = if (rarities.isEmpty() || rarities.size == Goals.RARITIES.size) "" else
            " " + Goals.RARITIES.filter { it in rarities }.joinToString("/") { it.replaceFirstChar(Char::uppercase) }
        repo.addGoal(Goal(name = "${set.name}$label", kind = Goals.SET, target = set.code.lowercase(), rarities = rarities.joinToString(",")))
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
fun GoalsScreen(onBack: () -> Unit, vm: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory)) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var openId by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(message) { message?.let { Undo.offer(it); vm.messageShown() } }
    val open = goals?.firstOrNull { it.goal.id == openId }
    BackHandler(enabled = open != null) { openId = -1L }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (open != null) openId = -1L else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                open?.goal?.name ?: "Goals", style = MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (open == null) IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add goal") }
            else IconButton(onClick = { vm.delete(open.goal); openId = -1L }) { Icon(Icons.Filled.DeleteOutline, "Delete goal") }
        }
        val list = goals
        when {
            list == null -> Unit
            open != null -> MissingList(open)
            list.isEmpty() -> EmptyState(
                icon = Icons.Filled.EmojiEvents,
                title = "No goals yet",
                body = "Every printing of a card, or a set.",
                action = { GradientButton("Add goal", { adding = true }, icon = Icons.Filled.Add) },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(list, key = { it.goal.id }) { p -> GoalCard(p, onClick = { openId = p.goal.id }) }
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
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = Palette.Surface) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                when {
                    p.error -> Icon(Icons.Filled.CloudOff, "Offline", tint = Palette.Muted, modifier = Modifier.size(16.dp))
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
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (p.done) Palette.Success else Palette.Accent,
                trackColor = Palette.SurfaceHigh,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun MissingList(p: GoalProgress) {
    if (p.loaded && p.missing.isEmpty()) {
        EmptyState(Icons.Filled.EmojiEvents, "Complete", "You have them all.")
        return
    }
    if (!p.loaded) {
        EmptyState(Icons.Filled.CloudOff, if (p.error) "Offline" else "Loading…", "Needs Scryfall.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
        item {
            Text("Missing ${p.missing.size}", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(vertical = 8.dp))
        }
        items(p.missing, key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                CardImage(c.smallImageUrl, Modifier.width(34.dp), corner = 3.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${c.set.uppercase()} #${c.collectorNumber} · ${c.rarity.replaceFirstChar(Char::uppercase)}",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGoalDialog(vm: GoalsViewModel, onDismiss: () -> Unit) {
    var bySet by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var sets by remember { mutableStateOf<List<ScryfallSet>?>(null) }
    var chosenSet by remember { mutableStateOf<ScryfallSet?>(null) }
    var rarities by remember { mutableStateOf(setOf("rare", "mythic")) }

    LaunchedEffect(bySet) { if (bySet && sets == null) sets = vm.sets() }
    LaunchedEffect(text, bySet) {
        if (bySet || text.length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        suggestions = vm.suggest(text).take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("New goal") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectPill("Every printing", !bySet, { bySet = false; text = "" })
                    SelectPill("Set", bySet, { bySet = true; text = "" })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; if (bySet) chosenSet = null },
                    placeholder = { Text(if (bySet) "Set name" else "Card name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    if (!bySet) {
                        suggestions.forEach { s ->
                            Text(
                                s, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().clickable { text = s; suggestions = emptyList() }.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        val all = sets
                        if (all == null) {
                            CircularProgressIndicator(Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp, color = Palette.Accent)
                        } else {
                            val q = text.trim()
                            all.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.code.equals(q, ignoreCase = true) }
                                .take(30)
                                .forEach { s ->
                                    val picked = chosenSet?.code == s.code
                                    Text(
                                        "${s.name} (${s.code.uppercase()})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (picked) Palette.AccentText else Palette.Text,
                                        modifier = Modifier.fillMaxWidth().clickable { chosenSet = s }.padding(vertical = 8.dp),
                                    )
                                }
                        }
                    }
                }
                if (bySet) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Goals.RARITIES.forEach { r ->
                            SelectPill(r.replaceFirstChar(Char::uppercase), r in rarities, { rarities = if (r in rarities) rarities - r else rarities + r })
                        }
                    }
                }
            }
        },
        confirmButton = {
            val set = chosenSet
            val ok = if (bySet) set != null else text.isNotBlank()
            TextButton(enabled = ok, onClick = {
                if (bySet) { if (set != null) vm.addSetGoal(set, rarities) } else vm.addCardGoal(text.trim())
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

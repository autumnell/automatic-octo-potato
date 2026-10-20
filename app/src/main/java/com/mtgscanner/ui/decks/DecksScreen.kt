package com.mtgscanner.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.DeckCheck
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.data.db.Deck
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A legendary creature you own, and how many of your cards could go in its Commander deck. */
data class CommanderIdea(val name: String, val imageUrl: String?, val identity: String, val playable: Int, val cardId: Long)

/** A saved deck with how much of it you own. */
data class DeckRow(val deck: Deck, val cards: Int, val owned: Int)

class DecksViewModel(repo: CollectionRepository) : ViewModel() {
    val decks: StateFlow<List<DeckRow>?> = combine(repo.observeDecks(), repo.observeAll()) { decks, cards ->
        // Same name matching as the deck check, and each owned copy counts toward one line only.
        fun key(n: String) = com.mtgscanner.scan.CardTextParser.normalize(n.substringBefore(" // "))
        val owned = cards.groupBy { key(it.name) }.mapValues { (_, v) -> v.sumOf { it.quantity } }
        decks.map { d ->
            val lines = DecklistParser.parse(d.text)
            val left = owned.toMutableMap()
            val have = lines.sumOf { l ->
                val k = key(l.name)
                minOf(l.quantity, left[k] ?: 0).also { left[k] = (left[k] ?: 0) - it }
            }
            DeckRow(d, lines.sumOf { it.quantity }, have)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Commanders best supported by the cards you own. */
    val commanders: StateFlow<List<CommanderIdea>> = repo.observeAll().map { cards ->
        val legal = cards.filter { "legal:commander" in it.tags.split(',') }
        val unique = legal.distinctBy { it.oracleId ?: it.name }
        unique.filter { c ->
            (c.typeLine.contains("Legendary") && c.typeLine.contains("Creature")) ||
                c.oracleText.contains("can be your commander", ignoreCase = true)
        }.map { cmd ->
            val id = cmd.colorIdentity.toSet()
            val playable = unique.count { o -> o.name != cmd.name && o.colorIdentity.all { it in id } }
            CommanderIdea(cmd.name, cmd.imageUrl, cmd.colorIdentity, playable, cmd.id)
        }.sortedByDescending { it.playable }.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** The Decks tab on the Collection screen. */
@Composable
fun DecksTab(onOpenDeck: (Long) -> Unit, onOpenCard: (Long) -> Unit = {}, vm: DecksViewModel = viewModel(factory = AppViewModels.Factory)) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val commanders by vm.commanders.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionHeader("Decks") {
                TextButton(onClick = { onOpenDeck(0L) }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        if (decks?.isEmpty() == true) {
            item {
                EmptyState(
                    icon = Icons.Filled.Style,
                    title = "Check a deck against your cards",
                    body = "Paste a decklist from Moxfield, Archidekt, MTG Arena or anywhere else. You'll see which cards you own, which box they're in, and what's missing.",
                    action = { GradientButton("Paste a decklist", { onOpenDeck(0L) }, icon = Icons.Filled.Add) },
                )
            }
        }
        items(decks.orEmpty(), key = { it.deck.id }) { row ->
            Surface(
                onClick = { onOpenDeck(row.deck.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(22.dp),
                color = Palette.BoxCard,
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Style, null, tint = Palette.Accent)
                        Spacer(Modifier.width(10.dp))
                        Text(row.deck.name, style = MaterialTheme.typography.titleLarge, color = Palette.Accent, modifier = Modifier.weight(1f))
                        if (row.cards > 0 && row.owned >= row.cards) Icon(Icons.Filled.CheckCircle, "Complete", tint = Palette.Success)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (row.cards == 0) 0f else row.owned.toFloat() / row.cards },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Palette.Accent,
                        trackColor = Palette.SurfaceHigh,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("You own ${row.owned} of ${row.cards} cards", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
            }
        }
        if (commanders.isNotEmpty()) {
            item {
                SectionHeader("Commander ideas")
                Text(
                    "Legendary creatures you own, ranked by how many of your other cards fit their colors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(commanders, key = { "c" + it.name }) { c ->
                Surface(
                    onClick = { onOpenCard(c.cardId) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Palette.Surface,
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardImage(c.imageUrl, Modifier.width(44.dp), corner = 4.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (c.identity.isEmpty()) Text("Colorless", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                c.identity.forEach { ColorPip(it); Spacer(Modifier.width(3.dp)) }
                            }
                        }
                        Text("${c.playable} cards", style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

data class DeckUi(
    val loaded: Boolean = false,
    val id: Long = 0,
    val name: String = "",
    val text: String = "",
    val editing: Boolean = true,
    val checking: Boolean = false,
    val check: DeckCheck? = null,
    val message: String? = null,
)

class DeckViewModel(private val repo: CollectionRepository, deckId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(DeckUi(id = deckId))
    val ui: StateFlow<DeckUi> = _ui

    init {
        viewModelScope.launch {
            val deck = if (deckId != 0L) repo.observeDeck(deckId).first() else null
            _ui.update { it.copy(loaded = true, name = deck?.name ?: "", text = deck?.text ?: "", editing = deck == null) }
            if (deck != null) check()
        }
    }

    fun setName(v: String) = _ui.update { it.copy(name = v) }
    fun setText(v: String) = _ui.update { it.copy(text = v) }
    fun edit() = _ui.update { it.copy(editing = true) }

    fun save() {
        val s = _ui.value
        if (s.text.isBlank()) return
        viewModelScope.launch {
            val name = s.name.ifBlank { "Untitled deck" }
            val id = repo.saveDeck(s.id, name, s.text)
            _ui.update { it.copy(id = id, name = name, editing = false) }
            check()
        }
    }

    private fun check() {
        _ui.update { it.copy(checking = true) }
        viewModelScope.launch {
            val cards = repo.observeAll().first()
            val boxes = repo.observeBoxes().first()
            val result = repo.checkDeck(_ui.value.text, cards, boxes)
            _ui.update { it.copy(checking = false, check = result) }
        }
    }

    fun wishlistMissing() {
        val check = _ui.value.check ?: return
        viewModelScope.launch {
            val msg = runCatching { repo.wishlistMissing(check) }
                .fold({ "Added $it cards to your wishlist" }, { "Couldn't reach Scryfall" })
            _ui.update { it.copy(message = msg) }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repo.deleteDeck(_ui.value.id); onDone() }
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
}

/** One deck: paste or edit the list, then see what you own, where it is, and what's missing. */
@Composable
fun DeckScreen(deckId: Long, onBack: () -> Unit) {
    val vm: DeckViewModel = viewModel(key = "deck$deckId", factory = AppViewModels.deck(deckId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    if (ui.editing) (if (ui.id == 0L) "New deck" else "Edit deck") else ui.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!ui.editing) {
                    IconButton(onClick = vm::edit) { Icon(Icons.Filled.Edit, "Edit list") }
                    IconButton(onClick = { vm.delete(onBack) }) { Icon(Icons.Filled.DeleteOutline, "Delete deck") }
                }
            }
            if (!ui.loaded) return@Column
            if (ui.editing) Editor(ui, vm) else Results(ui, vm)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Editor(ui: DeckUi, vm: DeckViewModel) {
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent)
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = ui.name, onValueChange = vm::setName, label = { Text("Deck name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = colors,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ui.text, onValueChange = vm::setText,
            placeholder = { Text("4 Lightning Bolt\n1 Sol Ring (CMR) 263\n\nSideboard\n2 Duress") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 420.dp),
            shape = RoundedCornerShape(14.dp), colors = colors,
        )
        Spacer(Modifier.height(6.dp))
        Text("One card per line. Exports from Moxfield, Archidekt, Arena and MTGO all work.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Spacer(Modifier.height(14.dp))
        GradientButton("Check my collection", vm::save, Modifier.fillMaxWidth(), enabled = ui.text.isNotBlank())
    }
}

@Composable
private fun Results(ui: DeckUi, vm: DeckViewModel) {
    val check = ui.check
    if (ui.checking || check == null) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        return
    }
    val missing = check.lines.filter { it.missing > 0 }
    val owned = check.lines.filter { it.owned > 0 }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Owned", "${check.owned} / ${check.needed}", Modifier.weight(1f))
                if (AppSettings.showPrices) Stat("Missing cost", formatUsd(check.missingCost), Modifier.weight(1f))
                else Stat("Missing", "${check.needed - check.owned}", Modifier.weight(1f))
            }
            if (missing.isNotEmpty()) {
                OutlinedButton(
                    onClick = vm::wishlistMissing,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add missing cards to wishlist")
                }
            }
        }
        if (missing.isNotEmpty()) {
            item { SectionHeader("Missing (${missing.sumOf { it.missing }})") }
            items(missing, key = { "m" + it.section + it.name }) { DeckLineRow(it, missingView = true) }
        }
        if (owned.isNotEmpty()) {
            item { SectionHeader("In your collection") }
            items(owned, key = { "o" + it.section + it.name }) { DeckLineRow(it, missingView = false) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Palette.Surface) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun DeckLineRow(l: DeckCheckLine, missingView: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CardImage(l.imageUrl, Modifier.width(40.dp), corner = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (missingView) "${l.missing}× " else "${minOf(l.owned, l.needed)}/${l.needed} ") + l.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val where = if (missingView) {
                if (l.owned > 0) "Have ${l.owned} of ${l.needed}" else l.section
            } else l.boxes.joinToString(" · ") { (b, n) -> "$b ×$n" }
            Text(where, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (missingView) { if (AppSettings.showPrices) Text(formatUsd(l.price?.times(l.missing)), style = MaterialTheme.typography.titleSmall, color = Palette.Accent) }
        else Icon(Icons.Filled.CheckCircle, null, tint = if (l.owned >= l.needed) Palette.Success else Palette.Gold, modifier = Modifier.size(18.dp))
    }
}

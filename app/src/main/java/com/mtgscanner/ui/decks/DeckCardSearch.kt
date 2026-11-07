package com.mtgscanner.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copies of each card on [board] of the list being edited, by [DeckNames.key]. */
private fun boardCounts(text: String, board: String): Map<String, Int> =
    DecklistParser.parse(text).filter { it.section == board }.groupBy { DeckNames.key(it.name) }.mapValues { (_, l) -> l.sumOf { it.quantity } }

/**
 * The top of the Cards tab: type a name and tap a suggestion to add a copy to [board], or open the full search.
 * The box stays put after an add, so cards can be added one after another.
 */
@Composable
fun DeckQuickAdd(ui: DeckUi, vm: DeckViewModel, board: String) {
    var query by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var added by remember { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        delay(200)
        suggestions = vm.suggestNames(q).take(6)
    }
    val counts = remember(ui.text, board) { boardCounts(ui.text, board) }
    val where = Board.entries.firstOrNull { it.section == board }?.let { if (it == Board.MAIN) "the deck" else "the ${it.label.lowercase()}" } ?: "the deck"
    Row(verticalAlignment = Alignment.CenterVertically) {
        SearchField(query, { query = it; added = null }, Modifier.weight(1f), placeholder = "Add a card by name")
        Spacer(Modifier.width(Space.s2))
        TextButton(onClick = { searching = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Search, null, tint = Palette.Icon, modifier = Modifier.padding(end = Space.s1).size(18.dp))
            Text("Search")
        }
    }
    suggestions.forEach { name ->
        val have = counts[DeckNames.key(name)] ?: 0
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                com.mtgscanner.util.Haptics.tick()
                vm.addCard(name, board)
                added = name
                query = ""
                suggestions = emptyList()
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (have > 0) Text("×$have", style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = Palette.Muted, modifier = Modifier.padding(end = Space.s2))
            Icon(Icons.Outlined.Add, "Add $name", tint = Palette.Icon)
        }
        HorizontalDivider(color = Palette.Rule)
    }
    added?.let { name ->
        Text(
            "Added $name to $where", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            modifier = Modifier.padding(top = Space.s2),
        )
    }
    Spacer(Modifier.height(Space.s2))
    if (searching) DeckCardSearchSheet(ui, vm, board, onDismiss = { searching = false })
}

/** Card types to narrow a search by, as Scryfall's "t:" words. */
private val TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle")

/**
 * Searches all of Magic for cards to add to [board]: text (Scryfall syntax works too), a card type, and, when the
 * deck has them, its format's legality and its commander's colors, both on to begin with. Most played cards come
 * first. Tap a card, or its +, to add a copy; the sheet stays open to add more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckCardSearchSheet(ui: DeckUi, vm: DeckViewModel, board: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    val format = DeckFormats.of(ui.format)
    var legalOnly by rememberSaveable { mutableStateOf(format.id.isNotEmpty()) }
    val leads = if (format.commander) ui.commanders.commanders else emptyList()
    val identity = remember(leads, ui.leadInfo) {
        if (leads.isEmpty() || leads.any { ui.leadInfo[it.lowercase()] == null }) null
        else "WUBRG".filter { c -> leads.any { c in ui.leadInfo[it.lowercase()]?.identity.orEmpty().uppercase() } }.ifEmpty { "C" }
    }
    var inColors by rememberSaveable { mutableStateOf(true) }
    var results by remember { mutableStateOf(emptyList<ScryfallCard>()) }
    var hasMore by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val counts = remember(ui.text, board) { boardCounts(ui.text, board) }

    val query = listOfNotNull(
        text.trim().takeIf { it.isNotEmpty() },
        type?.let { "t:${it.lowercase()}" },
        format.id.takeIf { legalOnly && it.isNotEmpty() }?.let { "f:$it" },
        identity?.takeIf { inColors }?.let { "id<=$it" },
        "game:paper",
    ).joinToString(" ")
    // Only filters and no words or type would list half of Magic: wait for something to look for.
    val ready = text.trim().length >= 2 || type != null

    suspend fun load(p: Int) {
        loading = true
        failed = false
        try {
            val list = vm.searchCards(query, p)
            results = if (p == 1) list.data else (results + list.data).distinctBy { it.id }
            hasMore = list.hasMore
            page = p
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(query, ready) {
        if (!ready) { results = emptyList(); hasMore = false; return@LaunchedEffect }
        delay(400)
        load(1)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = Space.s4)) {
                Text("Add cards", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.s3))
                SearchField(text, { text = it }, placeholder = "Name or rules text, like draw a card", focusOnOpen = true)
                Spacer(Modifier.height(Space.s2))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s4, vertical = Space.s1),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                if (format.id.isNotEmpty()) SelectPill("Legal in ${format.label}", legalOnly, { legalOnly = !legalOnly })
                if (identity != null) SelectPill("Commander's colors", inColors, { inColors = !inColors })
                TYPES.forEach { t -> SelectPill(t, type == t, { type = if (type == t) null else t }) }
            }
            Spacer(Modifier.height(Space.s2))
            Box(Modifier.weight(1f)) {
                when {
                    !ready -> Text(
                        "Type at least two letters or pick a type. Scryfall search words work too, like o:flying or cmc<=2",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(Space.s4),
                    )
                    failed && results.isEmpty() -> Column(Modifier.padding(Space.s4)) {
                        Text("Couldn't reach Scryfall", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                        TextButton(onClick = { scope.launch { load(1) } }) { Text("Try again") }
                    }
                    loading && results.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Text)
                    results.isEmpty() -> Text(
                        "No cards match", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(Space.s4),
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth()) {
                        items(results, key = { it.id }) { c ->
                            SearchResultRow(c, counts[DeckNames.key(c.name)] ?: 0) {
                                com.mtgscanner.util.Haptics.tick()
                                vm.addCard(c.name, board)
                            }
                        }
                        if (hasMore) item(key = "more") {
                            TextButton(
                                onClick = { scope.launch { load(page + 1) } }, enabled = !loading,
                                modifier = Modifier.fillMaxWidth().padding(vertical = Space.s2),
                            ) { Text(if (loading) "Loading…" else "More cards") }
                        }
                    }
                }
            }
        }
    }
}

/** One card in the search: picture, name, type and cost, how many the list has, and +. */
@Composable
private fun SearchResultRow(c: ScryfallCard, have: Int, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl, Modifier.width(44.dp), zoom = true)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                c.manaCost?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.width(Space.s2)); ManaCost(it) }
            }
            Text(c.typeLine.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (have > 0) Text("×$have", style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = Palette.Text, modifier = Modifier.padding(start = Space.s2))
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Add ${c.name}", tint = Palette.Icon) }
    }
}

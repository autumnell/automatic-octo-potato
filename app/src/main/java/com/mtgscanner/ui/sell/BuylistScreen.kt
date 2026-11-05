package com.mtgscanner.ui.sell

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.data.CardKingdom
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.LoanMath
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Loan
import com.mtgscanner.data.observeLoans
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.db.placeName
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.Haptics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.panelRow

/** Everything a pull list is worked out from. */
data class BuylistInput(val cards: List<CollectionCard>, val boxes: List<Box>, val loans: List<Loan>)

class BuylistViewModel(private val repo: CollectionRepository) : ViewModel() {
    init { CardKingdom.init(repo.appContext) }

    /** Null until loaded. */
    // Decks' card rows too, so a copy kept in a deck says which deck.
    val input: StateFlow<BuylistInput?> = combine(repo.observeAll(), repo.observePlaces(), repo.observeLoans()) { cards, boxes, loans ->
        BuylistInput(cards, boxes, loans)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Moves the pulled copies to the Sold list at the buylist price, with one Undo for all. */
    fun markSold(items: List<BuyPull>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val (n, undo) = repo.sellCopies(items.map { Triple(it.card.id, it.copies, it.each) })
        if (n > 0) Undo.offer(if (n == 1) "Sold 1 card" else "Sold $n cards") { undo() }
    }
}

private val MINIMUMS = listOf(0.5, 1.0, 2.0, 5.0, 10.0)

private fun minLabel(v: Double) = if (v < 1) "$${"%.2f".format(java.util.Locale.US, v)}" else "$${v.toInt()}"

/** Card Kingdom's offers: pick a minimum; get every card worth selling, where it lives, and the total. */
@Composable
internal fun ColumnScope.BuylistContent(onOpenCard: (Long) -> Unit) {
    val vm: BuylistViewModel = viewModel(factory = AppViewModels.Factory)
    val input by vm.input.collectAsStateWithLifecycle()
    var min by remember { mutableDoubleStateOf(1.0) }
    var skipped by remember { mutableStateOf(setOf<Long>()) }

    run {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s4, vertical = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            MINIMUMS.forEach { m -> SelectPill("${minLabel(m)}+", min == m, { min = m; skipped = emptySet() }) }
        }
        val data = input ?: return@run
        // Reading CardKingdom.version redraws once the list has loaded.
        val listVersion = CardKingdom.version
        val boxNames = data.boxes.associate { it.id to it.placeName() }
        val pulls = remember(data, min, listVersion) {
            val out = LoanMath.outByRow(data.loans, data.cards)
            BuylistMath.pullList(
                data.cards, min, data.boxes.map { it.id },
                priceOf = { CardKingdom.buyPrice(it.scryfallId, it.foil) },
                outOf = { out[it.id] ?: 0 },
            )
        }
        if (pulls.isEmpty()) {
            EmptyState(
                if (listVersion == 0) "No buylist yet" else "Nothing at ${minLabel(min)}+",
                Modifier.padding(top = Space.s6),
                body = if (listVersion == 0) "It downloads once a day" else null,
            )
            return@run
        }
        val chosen = pulls.filter { it.card.id !in skipped }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.s2)) {
            items(pulls, key = { it.card.id }) { p ->
                val on = p.card.id !in skipped
                PullRow(p, boxNames[p.card.boxId], on, first = p === pulls.first(), last = p === pulls.last(), onToggle = { skipped = if (on) skipped + p.card.id else skipped - p.card.id }, onOpen = { onOpenCard(p.card.id) })
            }
        }
        Surface(color = Palette.Surface) {
            Column(Modifier.fillMaxWidth().padding(Space.s4).navigationBarsPadding()) {
                val n = chosen.sumOf { it.copies }
                GradientButton(
                    "Mark $n sold · ${formatUsd(chosen.sumOf { it.total })}",
                    { vm.markSold(chosen); skipped = emptySet() },
                    Modifier.fillMaxWidth(),
                    enabled = n > 0,
                )
            }
        }
    }
}

@Composable
private fun PullRow(p: BuyPull, box: String?, on: Boolean, first: Boolean, last: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = p.card
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.s4).panelRow(first, last).clickable(onClick = onOpen)
            .padding(start = 0.dp, end = Space.s3, top = Space.s1, bottom = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(on, { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Palette.Text))
        CardImage(c.smallImageUrl, Modifier.width(36.dp), zoom = true)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = listOfNotNull(box, BuylistMath.pageOf(c.binderSlot)).joinToString(" · ")
            Text(
                "${c.setCode.uppercase()}${if (c.foil) " foil" else ""} · ×${p.copies}" + if (where.isNotEmpty()) " · $where" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.s2))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatUsd(p.total), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text)
            if (p.copies > 1) Text("${formatUsd(p.each)} each", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

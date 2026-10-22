package com.mtgscanner.ui.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mtgscanner.MtgApp
import com.mtgscanner.data.CardKingdom
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.LoanMath
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Loan
import com.mtgscanner.data.observeLoans
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
import kotlinx.coroutines.launch

/** Everything a pull list is worked out from. */
data class BuylistInput(val cards: List<CollectionCard>, val boxes: List<Box>, val loans: List<Loan>)

class BuylistViewModel(private val repo: CollectionRepository) : ViewModel() {
    init { CardKingdom.init(repo.appContext) }

    /** Null until loaded. */
    val input: StateFlow<BuylistInput?> = combine(repo.observeAll(), repo.observeBoxes(), repo.observeLoans()) { cards, boxes, loans ->
        BuylistInput(cards, boxes, loans)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Moves the pulled copies to the Sold list at the buylist price, with one Undo for all. */
    fun markSold(items: List<BuyPull>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val (n, undo) = repo.sellCopies(items.map { Triple(it.card.id, it.copies, it.each) })
        if (n > 0) Undo.offer(if (n == 1) "Sold 1 card" else "Sold $n cards") { undo() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { BuylistViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MtgApp).repository) }
        }
    }
}

private val MINIMUMS = listOf(0.5, 1.0, 2.0, 5.0, 10.0)

private fun minLabel(v: Double) = if (v < 1) "$${"%.2f".format(java.util.Locale.US, v)}" else "$${v.toInt()}"

/** Pick a buylist and a minimum; get every card worth selling to it, where it lives, and the total. */
@Composable
fun BuylistScreen(onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val vm: BuylistViewModel = viewModel(factory = BuylistViewModel.Factory)
    val input by vm.input.collectAsStateWithLifecycle()
    var min by remember { mutableDoubleStateOf(1.0) }
    var skipped by remember { mutableStateOf(setOf<Long>()) }
    Haptics.Attach()

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Sell to a buylist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The only buylist the app reads today.
            SelectPill("Card Kingdom", true, {})
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MINIMUMS.forEach { m -> SelectPill("${minLabel(m)}+", min == m, { min = m; skipped = emptySet() }) }
        }
        val data = input ?: return@Column
        // Reading CardKingdom.version redraws once the list has loaded.
        val listVersion = CardKingdom.version
        val boxNames = data.boxes.associate { it.id to it.name }
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
                Modifier.padding(top = 24.dp),
                body = if (listVersion == 0) "It downloads once a day" else null,
            )
            return@Column
        }
        val chosen = pulls.filter { it.card.id !in skipped }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(pulls, key = { it.card.id }) { p ->
                val on = p.card.id !in skipped
                PullRow(p, boxNames[p.card.boxId], on, onToggle = { skipped = if (on) skipped + p.card.id else skipped - p.card.id }, onOpen = { onOpenCard(p.card.id) })
            }
        }
        Surface(color = Palette.Surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
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
private fun PullRow(p: BuyPull, box: String?, on: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = p.card
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Palette.Accent))
        CardImage(c.smallImageUrl, Modifier.width(36.dp), zoom = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = listOfNotNull(box, BuylistMath.pageOf(c.binderSlot)).joinToString(" · ")
            Text(
                "${c.setCode.uppercase()}${if (c.foil) " foil" else ""} · ×${p.copies}" + if (where.isNotEmpty()) " · $where" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatUsd(p.total), style = MaterialTheme.typography.titleSmall, color = Palette.AccentText)
            if (p.copies > 1) Text("${formatUsd(p.each)} each", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

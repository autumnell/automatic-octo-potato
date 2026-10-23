package com.mtgscanner.ui.loans

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.MtgApp
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.LoanMath
import com.mtgscanner.data.cancelLoan
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Loan
import com.mtgscanner.data.db.placeName
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.lend
import com.mtgscanner.data.observeLoans
import com.mtgscanner.data.putLoanBack
import com.mtgscanner.data.returned
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.Haptics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mtgscanner.ui.components.panelRow

/** One loan with the row its copies live in (null when the card has left the collection) and that row's box. */
data class LoanRow(val loan: Loan, val card: CollectionCard?, val boxName: String?)

class LoansViewModel(private val repo: CollectionRepository) : ViewModel() {
    /** Loans by person, oldest first. Null until loaded. */
    val people: StateFlow<List<Pair<String, List<LoanRow>>>?> =
        combine(repo.observeLoans(), repo.observeAll(), repo.observePlaces()) { loans, cards, places ->
            val names = places.associate { it.id to it.placeName() }
            LoanMath.byPerson(loans).map { (who, list) ->
                who to list.map { l -> LoanMath.rowFor(l, cards).let { c -> LoanRow(l, c, c?.let { names[it.boxId] }) } }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The copies came back, with Undo. */
    fun returned(loan: Loan) = viewModelScope.launch {
        repo.returned(loan)
        Undo.offer("${loan.name} back from ${loan.who}") { repo.putLoanBack(loan) }
    }
}

private val sinceFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** "since Mar 3". */
internal fun sinceText(millis: Long): String = runCatching {
    "since " + Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(sinceFormat)
}.getOrDefault("")

/** Who has what, since when, with Returned on each. */
@Composable
fun LoansScreen(onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val vm: LoansViewModel = viewModel(factory = AppViewModels.Factory)
    val people by vm.people.collectAsStateWithLifecycle()
    Haptics.Attach()

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Loans", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        val list = people ?: return@Column
        if (list.isEmpty()) {
            EmptyState("Nothing lent out", Modifier.padding(top = 24.dp), body = "Lend a card from its page")
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            list.forEach { (who, rows) ->
                item(key = "who-$who") {
                    val n = rows.sumOf { it.loan.quantity }
                    SectionHeader("$who · ${if (n == 1) "1 card" else "$n cards"}")
                }
                itemsIndexed(rows, key = { _, r -> r.loan.id }) { i, r ->
                    LoanItem(r, first = i == 0, last = i == rows.lastIndex, onOpen = { r.card?.let { onOpenCard(it.id) } }, onReturned = { vm.returned(r.loan) })
                }
            }
        }
    }
}

@Composable
private fun LoanItem(r: LoanRow, first: Boolean, last: Boolean, onOpen: () -> Unit, onReturned: () -> Unit) {
    val c = r.card
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).panelRow(first, last).clickable(enabled = c != null, onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c?.smallImageUrl, Modifier.width(40.dp), zoom = c != null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.loan.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = when {
                c == null -> "Not in collection"
                else -> listOfNotNull(c.setCode.uppercase(), r.boxName).joinToString(" · ")
            }
            Text(
                "×${r.loan.quantity}${if (r.loan.foil) " foil" else ""} · $where · ${sinceText(r.loan.at)}",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onReturned) { Text("Returned", color = Palette.AccentText) }
    }
}

/** The "Out · Kenji" label for a card's page, or null when no copies are lent. */
@Composable
fun loanLabel(card: CollectionCard): String? {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val loansFlow = remember(repo) { repo.observeLoans() }
    val cardsFlow = remember(repo) { repo.observeAll() }
    val loans by loansFlow.collectAsStateWithLifecycle(emptyList())
    val cards by cardsFlow.collectAsStateWithLifecycle(emptyList())
    return if (loans.isEmpty()) null else LoanMath.label(card, loans, cards.ifEmpty { listOf(card) })
}

/** Lend some copies: who, and how many. Saves on Lend, with Undo. */
@Composable
fun LendDialog(card: CollectionCard, onDismiss: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val loansFlow = remember(repo) { repo.observeLoans() }
    val loans by loansFlow.collectAsStateWithLifecycle(emptyList())
    val cardsFlow = remember(repo) { repo.observeAll() }
    val cards by cardsFlow.collectAsStateWithLifecycle(emptyList())
    // Copies already lent can't be lent again.
    val left = card.quantity - LoanMath.outCount(card, loans, cards.ifEmpty { listOf(card) })
    var who by remember { mutableStateOf("") }
    var qty by remember { mutableIntStateOf(card.quantity) }
    val lendQty = qty.coerceIn(1, left.coerceAtLeast(1))
    val name = LoanMath.cleanName(who)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Lend", maxLines = 1) },
        text = {
            Column {
                Text(card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = who,
                    onValueChange = { who = it.take(30) },
                    label = { Text("To") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                val recent = LoanMath.recentNames(loans)
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { n -> SelectPill(n, n.equals(name, ignoreCase = true), { who = n }) }
                    }
                }
                if (left <= 0) {
                    Spacer(Modifier.height(10.dp))
                    Text("All copies are out", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                } else if (left > 1) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Copies", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        QuantityStepper(lendQty, onChange = { qty = it.coerceIn(1, left) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // On the app's scope: the dialog closes right away.
                    repo.appScope.launch {
                        val loan = repo.lend(card.id, lendQty, name) ?: return@launch
                        Undo.offer("Lent ${card.name} to ${loan.who}") { repo.cancelLoan(loan) }
                    }
                    onDismiss()
                },
                enabled = name.isNotEmpty() && left > 0,
            ) { Text("Lend") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

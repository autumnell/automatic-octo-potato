package com.mtgscanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.sell.Sales
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums

class MoneyViewModel(private val repo: CollectionRepository) : ViewModel() {
    /** The last six months, oldest first. Empty until loaded. */
    val months: StateFlow<List<MonthMoney>> = combine(repo.observeAll(), repo.dao.observeSnapshots()) { cards, snaps ->
        val sales = runCatching { repo.dao.allEvents() }.getOrDefault(emptyList())
            .filter { it.kind == Sales.KIND }
            .mapNotNull { e -> Sales.amount(e.detail)?.let { e.at to it } }
        MoneyMath.months(
            added = cards.mapNotNull { c -> c.purchasePrice?.let { c.addedAt to it * c.quantity } },
            sales = sales,
            snapshots = snaps.map { it.day to it.value },
            now = YearMonth.now(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer { MoneyViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MtgApp).repository) }
        }
    }
}

private val monthName = DateTimeFormatter.ofPattern("MMM", Locale.US)

/**
 * One "Money" block: what was paid for the cards with a price paid against what they're worth, then spent
 * (prices paid for cards added) and sold (the Sold list) each month next to the collection's worth.
 */
@Composable
fun MoneySection(paid: PaidSummary, vm: MoneyViewModel = viewModel(factory = MoneyViewModel.Factory)) {
    val months by vm.months.collectAsStateWithLifecycle()
    val showPaid = paid.copies > 0
    val showMonths = MoneyMath.hasData(months)
    if (!showPaid && !showMonths) return
    SectionHeader("Money")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (showPaid) PaidRow(paid)
            if (showPaid && showMonths) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Palette.Hairline)
            if (showMonths) {
                MoneyRow("", "Spent", "Sold", "Worth", header = true)
                months.asReversed().forEach { m ->
                    MoneyRow(
                        m.month.format(monthName),
                        if (m.spent > 0) formatUsd(m.spent) else "–",
                        if (m.earned > 0) formatUsd(m.earned) else "–",
                        m.worth?.let { formatUsd(it) } ?: "–",
                    )
                }
            }
        }
    }
}

/** What was paid for the copies with a price paid, what they're worth now, and the difference. */
@Composable
private fun PaidRow(p: PaidSummary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Paid", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(formatUsd(p.paid), style = MaterialTheme.typography.titleMedium.merge(TabularNums))
        }
        Column(Modifier.weight(1f)) {
            Text("Worth", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(formatUsd(p.worth), style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = Palette.AccentText)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (p.copies == 1) "1 card" else "${p.copies} cards", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            ChangeText(p.change, p.paid)
        }
    }
}

@Composable
private fun MoneyRow(month: String, spent: String, sold: String, worth: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val muted = if (header) Palette.Muted else Palette.Text
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(month, style = style, color = Palette.Muted, modifier = Modifier.width(44.dp))
        Text(spent, style = style, color = muted, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
        Text(sold, style = style, color = if (header) Palette.Muted else Palette.Success, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
        Text(worth, style = style, color = if (header) Palette.Muted else Palette.AccentText, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1.2f))
    }
}

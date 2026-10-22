package com.mtgscanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Spent (prices paid for cards added) and sold (the Sold list) each month, next to what the collection was worth. */
@Composable
fun MoneySection(vm: MoneyViewModel = viewModel(factory = MoneyViewModel.Factory)) {
    val months by vm.months.collectAsStateWithLifecycle()
    if (!MoneyMath.hasData(months)) return
    SectionHeader("Money in and out")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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

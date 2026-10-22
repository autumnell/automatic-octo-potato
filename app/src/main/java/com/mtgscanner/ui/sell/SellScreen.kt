package com.mtgscanner.ui.sell

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.Haptics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums

/** Asking price per copy: your own, or the market price. */
fun CollectionCard.salePrice(): Double? = askPrice ?: unitPrice()

class SellViewModel(private val repo: CollectionRepository) : ViewModel() {
    init { CardKingdom.init(repo.appContext) }

    /** Cards marked for sale, by name. Null until loaded. */
    val cards: StateFlow<List<CollectionCard>?> = repo.observeAll()
        .map { all -> all.filter { it.forSale }.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Each change re-reads the row, so a stale copy from the list never overwrites newer edits.
    fun setAsk(c: CollectionCard, price: Double?) = viewModelScope.launch {
        val now = repo.cardOnce(c.id) ?: return@launch
        repo.update(now.copy(askPrice = price))
        Haptics.tick()
    }

    /** What sales have brought in, from the cards' history. Updates as cards are sold. */
    val earned: StateFlow<Double> = repo.observeAll()
        .map { _ -> runCatching { Sales.earned(repo.dao.allEvents().map { it.kind to it.detail }) }.getOrDefault(0.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** Takes [qty] sold copies out of the collection and notes the price in the card's history, with Undo. */
    fun sold(c: CollectionCard, qty: Int, each: Double) = viewModelScope.launch {
        val (n, undo) = repo.sellCopies(listOf(Triple(c.id, qty, each)))
        if (n > 0) Undo.offer("Sold ${n}× ${c.name}") { undo() }
    }

    fun unlist(c: CollectionCard) = viewModelScope.launch {
        val now = repo.cardOnce(c.id) ?: return@launch
        repo.update(now.copy(forSale = false))
        Undo.offer("Removed ${c.name} from sale") {
            repo.cardOnce(c.id)?.let { repo.update(it.copy(forSale = true)) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SellViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MtgApp).repository) }
        }
    }
}

/** Plain text of the sell list, for the share sheet. */
fun sellText(cards: List<CollectionCard>): String = buildString {
    appendLine("For sale")
    cards.forEach { c ->
        append("${c.quantity}× ${c.name} (${c.setCode.uppercase()})")
        if (c.foil) append(" foil")
        if (c.condition != "NM") append(" ${c.condition}")
        if (AppSettings.showPrices) c.salePrice()?.let { append(" — ${formatUsd(it)}") }
        appendLine()
    }
    if (AppSettings.showPrices) {
        appendLine()
        appendLine("Total ${formatUsd(cards.sumOf { (it.salePrice() ?: 0.0) * it.quantity })}")
    }
    appendLine()
    append("Shared from Grimoire")
}

/**
 * The Sell tool: "My sell list" (asking price, Sold, earned) and, with prices on, "Card Kingdom offers".
 * [startTab] 0 is the sell list, 1 the offers.
 */
@Composable
fun SellScreen(onBack: () -> Unit, onBrowse: () -> Unit, onOpenCard: (Long) -> Unit = {}, startTab: Int = 0) {
    val vm: SellViewModel = viewModel(factory = SellViewModel.Factory)
    val cards by vm.cards.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(if (AppSettings.showPrices) startTab else 0) }
    Haptics.Attach()

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Sell", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val shown = cards.orEmpty()
            if (tab == 0 && shown.isNotEmpty()) {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "For sale").putExtra(Intent.EXTRA_TEXT, sellText(shown))
                    ctx.startActivity(Intent.createChooser(send, "Share sell list"))
                }) { Icon(Icons.Outlined.Share, "Share", tint = Palette.Icon) }
            }
        }
        if (AppSettings.showPrices) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("My sell list", tab == 0, { tab = 0 })
                SelectPill("Card Kingdom offers", tab == 1, { tab = 1 })
            }
        }
        if (tab == 1) BuylistContent(onOpenCard) else SellListContent(vm, onBrowse)
    }
}

/** Cards marked for sale, with the total and what sales have earned. */
@Composable
private fun ColumnScope.SellListContent(vm: SellViewModel, onBrowse: () -> Unit) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val earned by vm.earned.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CollectionCard?>(null) }
    var selling by remember { mutableStateOf<CollectionCard?>(null) }
    run {
        val list = cards ?: return@run
        if (AppSettings.showPrices && (list.isNotEmpty() || earned > 0)) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
                Column(Modifier.padding(14.dp)) {
                    if (list.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${list.sumOf { it.quantity }} cards", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatUsd(list.sumOf { (it.salePrice() ?: 0.0) * it.quantity }), style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = Palette.Text)
                    }
                    if (earned > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Earned", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                        Text(formatUsd(earned), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Success)
                    }
                }
            }
        }
        if (list.isEmpty()) {
            EmptyState("Nothing for sale", Modifier.padding(top = 24.dp), action = "Browse cards", onAction = onBrowse)
            return@run
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list, key = { it.id }) { c -> SellRow(c) { editing = c } }
        }
    }

    editing?.let { c ->
        AskDialog(
            c,
            onSave = { vm.setAsk(c, it); editing = null },
            onUnlist = { vm.unlist(c); editing = null },
            onSold = { selling = c; editing = null },
            onDismiss = { editing = null },
        )
    }
    selling?.let { c ->
        SoldDialog(c, onSold = { qty, each -> vm.sold(c, qty, each); selling = null }, onDismiss = { selling = null })
    }
}

@Composable
private fun SellRow(c: CollectionCard, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CardImage(c.smallImageUrl, Modifier.width(40.dp), zoom = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ck = if (AppSettings.showPrices) CardKingdom.buyPrice(c.scryfallId, c.foil)?.let { " · CK buys ${formatUsd(it)}" }.orEmpty() else ""
            Text(
                "${c.setCode.uppercase()} · ×${c.quantity}${if (c.foil) " · foil" else ""}$ck",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (AppSettings.showPrices) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUsd(c.salePrice()), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text)
                Text(if (c.askPrice != null) "Ask" else "Market", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun AskDialog(c: CollectionCard, onSave: (Double?) -> Unit, onUnlist: () -> Unit, onSold: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(c.askPrice?.let { "%.2f".format(java.util.Locale.US, it) }.orEmpty()) }
    val parsed = text.replace(',', '.').trim().removePrefix("$").toDoubleOrNull()
    val valid = text.isBlank() || (parsed != null && parsed >= 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Asking price") },
                    placeholder = { Text("Market ${formatUsd(c.unitPrice())}") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = onSold) { Text("Sold", color = Palette.AccentText) }
                    TextButton(onClick = onUnlist) { Text("Not for sale", color = Palette.Danger) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (text.isBlank()) null else parsed) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Marks copies as sold: how many, and the price each. */
@Composable
private fun SoldDialog(c: CollectionCard, onSold: (qty: Int, each: Double) -> Unit, onDismiss: () -> Unit) {
    var qty by remember { mutableIntStateOf(c.quantity) }
    var text by remember { mutableStateOf(c.salePrice()?.let { "%.2f".format(java.util.Locale.US, it) }.orEmpty()) }
    val each = Sales.parsePrice(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Sold", maxLines = 1) },
        text = {
            Column {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                if (c.quantity > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Copies", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        QuantityStepper(qty, onChange = { qty = it.coerceIn(1, c.quantity) })
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Price each") },
                    singleLine = true,
                    isError = each == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { each?.let { onSold(qty, it) } }, enabled = each != null) { Text("Sold") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

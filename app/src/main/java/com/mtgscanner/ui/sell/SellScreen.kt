package com.mtgscanner.ui.sell

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mtgscanner.ui.components.GradientButton
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

/** Cards marked for sale, with a total and a Share button. */
@Composable
fun SellScreen(onBack: () -> Unit, onBrowse: () -> Unit) {
    val vm: SellViewModel = viewModel(factory = SellViewModel.Factory)
    val cards by vm.cards.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<CollectionCard?>(null) }
    Haptics.Attach()

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Sell list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val shown = cards.orEmpty()
            if (shown.isNotEmpty()) {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "For sale").putExtra(Intent.EXTRA_TEXT, sellText(shown))
                    ctx.startActivity(Intent.createChooser(send, "Share sell list"))
                }) { Icon(Icons.Filled.Share, "Share", tint = Palette.Text) }
            }
        }
        val list = cards ?: return@Column
        if (list.isEmpty()) {
            EmptyState(
                Icons.Filled.Sell, "Nothing for sale", "Mark cards for sale on their page.",
                Modifier.padding(top = 24.dp),
                action = { GradientButton("Browse cards", onBrowse) },
            )
            return@Column
        }
        if (AppSettings.showPrices) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp), color = Palette.Surface) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${list.sumOf { it.quantity }} cards", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatUsd(list.sumOf { (it.salePrice() ?: 0.0) * it.quantity }), style = MaterialTheme.typography.titleMedium, color = Palette.AccentText)
                }
            }
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
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SellRow(c: CollectionCard, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CardImage(c.smallImageUrl, Modifier.width(40.dp), corner = 4.dp, zoom = true)
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
                Text(formatUsd(c.salePrice()), style = MaterialTheme.typography.titleSmall, color = Palette.AccentText)
                Text(if (c.askPrice != null) "Ask" else "Market", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun AskDialog(c: CollectionCard, onSave: (Double?) -> Unit, onUnlist: () -> Unit, onDismiss: () -> Unit) {
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
                TextButton(onClick = onUnlist) { Text("Not for sale", color = Palette.Danger) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (text.isBlank()) null else parsed) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

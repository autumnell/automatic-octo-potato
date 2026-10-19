package com.mtgscanner.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.tagList
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.formatUsd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CardDetailViewModel(private val repo: CollectionRepository, id: Long) : ViewModel() {
    private val currentId = MutableStateFlow(id)
    val card: StateFlow<CollectionCard?> = currentId
        .flatMapLatest { repo.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuantity(q: Int) = viewModelScope.launch { repo.setQuantity(currentId.value, q) }
    fun setFoil(foil: Boolean) = viewModelScope.launch { currentId.value = repo.setFoil(currentId.value, foil) }
    fun setCondition(c: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(condition = c)) } }
    fun setNotes(n: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(notes = n)) } }
    fun delete(done: () -> Unit) = viewModelScope.launch { repo.delete(currentId.value); done() }
}

private val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(id: Long, onBack: () -> Unit) {
    val vm: CardDetailViewModel = viewModel(key = "card-$id", factory = AppViewModels.detail(id))
    val card by vm.card.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    card?.let { c ->
                        IconButton(onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.scryfallUri)))
                        }) { Icon(Icons.Filled.OpenInBrowser, "Open on Scryfall") }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Remove from collection") }
                },
            )
        },
    ) { padding ->
        val c = card ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            CardImage(c.imageUrl, Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                ManaCost(c.manaCost, size = 20.dp)
            }
            Text(c.typeLine, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RarityDot(c.rarity)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${c.setName} (${c.setCode}) #${c.collectorNumber} · ${c.rarity}" +
                        (c.releasedAt?.let { " · ${it.take(4)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            c.artist?.let { Text("Illustrated by $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
            if (c.oracleText.isNotBlank()) Text(c.oracleText, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            Text("In your collection", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.setQuantity((c.quantity - 1).coerceAtLeast(1)) }) { Icon(Icons.Filled.Remove, "One fewer") }
                Text("${c.quantity}", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { vm.setQuantity(c.quantity + 1) }) { Icon(Icons.Filled.Add, "One more") }
                Spacer(Modifier.weight(1f))
                Text("Foil")
                Spacer(Modifier.width(8.dp))
                Switch(checked = c.foil, onCheckedChange = { vm.setFoil(it) })
            }
            Text(
                "${formatUsd(c.unitPrice())} each · ${formatUsd(c.totalPrice())} total",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text("Condition", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CONDITIONS.forEach { cond ->
                    FilterChip(selected = c.condition == cond, onClick = { vm.setCondition(cond) }, label = { Text(cond) })
                }
            }
            Spacer(Modifier.height(8.dp))
            var notes by remember(c.id) { mutableStateOf(c.notes) }
            LaunchedEffect(notes) {
                if (notes != c.notes) {
                    kotlinx.coroutines.delay(500)
                    vm.setNotes(notes)
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (deck, binder page, trade…)") },
                modifier = Modifier.fillMaxWidth(),
            )

            val tags = c.tagList()
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Tags from Scryfall", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${card?.name ?: "card"}?") },
            text = { Text("This removes all ${card?.quantity ?: 0} copies from your collection.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

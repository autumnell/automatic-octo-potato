package com.mtgscanner.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.PrintingsSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUi(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val foil: Boolean = false,
    val pickingFor: String? = null,
    val printings: List<ScryfallCard>? = null,
    val message: String? = null,
)

/** Manual lookup for cards the camera can't read (foreign, damaged, oddly lit). */
class SearchViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui
    private var job: Job? = null

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
        job?.cancel()
        job = viewModelScope.launch {
            delay(250)
            val list = runCatching { repo.api.autocomplete(q.trim()) }.getOrDefault(emptyList())
            _ui.update { it.copy(suggestions = list) }
        }
    }

    fun pick(name: String) {
        _ui.update { it.copy(pickingFor = name, printings = null) }
        viewModelScope.launch {
            val list = runCatching {
                val card = repo.api.named(name) ?: return@runCatching emptyList<ScryfallCard>()
                repo.api.printings(card).ifEmpty { listOf(card) }
            }.getOrElse { e ->
                _ui.update { it.copy(pickingFor = null, message = "Couldn't reach Scryfall: ${e.message}") }
                return@launch
            }
            _ui.update { it.copy(printings = list) }
        }
    }

    fun add(card: ScryfallCard) {
        val foil = _ui.value.foil && card.hasFoil || !card.hasNonFoil
        _ui.update { it.copy(pickingFor = null, printings = null) }
        viewModelScope.launch {
            repo.add(card, foil)
            _ui.update { it.copy(message = "Added ${card.name} (${card.set.uppercase()})${if (foil) " foil" else ""}") }
        }
    }

    fun closePicker() = _ui.update { it.copy(pickingFor = null, printings = null) }
    fun setFoil(on: Boolean) = _ui.update { it.copy(foil = on) }
    fun messageShown() = _ui.update { it.copy(message = null) }
}

@Composable
fun SearchScreen(vm: SearchViewModel = viewModel(factory = AppViewModels.Factory)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).statusBarsPadding().fillMaxSize().padding(12.dp)) {
            Text("Add a card by name", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Card name") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, "Clear") }
                    },
                )
                FilterChip(selected = ui.foil, onClick = { vm.setFoil(!ui.foil) }, label = { Text("Foil") })
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(ui.suggestions) { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { vm.pick(name) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }

    if (ui.pickingFor != null) {
        PrintingsSheet(
            printings = ui.printings,
            selectedId = null,
            onPick = vm::add,
            onDismiss = vm::closePicker,
        )
    }
}

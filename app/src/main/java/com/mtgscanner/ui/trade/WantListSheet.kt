package com.mtgscanner.ui.trade

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.mtgscanner.ui.decks.FromPhotoButton
import com.mtgscanner.ui.decks.appendList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CardKingdom
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.DecklistParser
import com.mtgscanner.util.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.ruleBelow
import com.mtgscanner.ui.components.panelRow

/** Paste a friend's want list and see which binder cards they want. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WantListSheet(binder: List<CollectionCard>, onAddAll: (List<Pair<CollectionCard, Int>>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val matches = remember(text, binder) {
        matchWants(DecklistParser.parse(text), binder, { it.name }, { it.setCode }, { it.quantity })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = Space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Their want list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FromPhotoButton({ text = appendList(text, it) })
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Paste list") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
            when {
                text.isBlank() -> item { Text("One card per line", color = Palette.Muted, modifier = Modifier.padding(Space.s6)) }
                matches.isEmpty() -> item { com.mtgscanner.ui.components.EmptyState("No matches in this binder") }
                else -> items(matches, key = { it.first.id }) { m ->
                    val c = m.first
                    val sub = "${c.setCode} · ×${m.second}" + if (AppSettings.showPrices) " · ${cardPrice(c)}" else ""
                    PickRow(c.name, sub, c.smallImageUrl) { onAddAll(listOf(m)) }
                }
            }
        }
        GradientButton(
            if (matches.isEmpty()) "Add all to give" else "Add all ${matches.sumOf { it.second }} to give",
            { onAddAll(matches) },
            Modifier.fillMaxWidth().padding(Space.s4).navigationBarsPadding(),
            enabled = matches.isNotEmpty(),
        )
    }
}

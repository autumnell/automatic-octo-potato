package com.mtgscanner.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.userTagList
import com.mtgscanner.ui.collection.TagDialog
import com.mtgscanner.ui.components.LineChart
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.roleList
import com.mtgscanner.data.db.tagList
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardTextBlock
import com.mtgscanner.ui.components.textInfo
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CardDetailViewModel(private val repo: CollectionRepository, id: Long) : ViewModel() {
    private val currentId = MutableStateFlow(id)
    /** (loaded from the database yet, the card or null when it's gone). */
    private val state: StateFlow<Pair<Boolean, CollectionCard?>> = currentId
        .flatMapLatest { rowId -> repo.observe(rowId).map { Pair<Boolean, CollectionCard?>(true, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Pair<Boolean, CollectionCard?>(false, null))
    val card: StateFlow<CollectionCard?> = state
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val loaded: StateFlow<Boolean> = state
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val boxes: StateFlow<List<CardBox>> = repo.observeBoxes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The card's full text from Scryfall (flavor, faces, every format); null offline. */
    val live: StateFlow<com.mtgscanner.data.ScryfallCard?> = card
        .map { it?.scryfallId }
        .distinctUntilChanged()
        .mapLatest { id -> id?.let { runCatching { repo.api.byId(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuantity(q: Int) = viewModelScope.launch { repo.setQuantity(currentId.value, q) }
    fun setFoil(foil: Boolean) = viewModelScope.launch { currentId.value = repo.setFoil(currentId.value, foil) }
    fun moveTo(boxId: Long) = viewModelScope.launch {
        val from = currentId.value
        val before = repo.snapshot(listOf(from))
        currentId.value = repo.moveToBox(from, boxId)
        val name = boxes.value.firstOrNull { it.id == boxId }?.name ?: "box"
        Undo.offer("Moved to $name") { repo.restore(before); currentId.value = from }
    }

    /** Moves [qty] copies to another box; the rest stay here. Moving all of them is [moveTo]. */
    fun moveSome(boxId: Long, qty: Int) = viewModelScope.launch {
        val c = card.value ?: return@launch
        if (qty >= c.quantity) { moveTo(boxId); return@launch }
        if (qty < 1) return@launch
        val from = currentId.value
        val before = repo.snapshot(listOf(from))
        repo.moveCopiesMany(listOf(from to qty), boxId)
        val name = boxes.value.firstOrNull { it.id == boxId }?.name ?: "box"
        Undo.offer("Moved $qty to $name") { repo.restore(before); currentId.value = from }
    }
    fun setCondition(c: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(condition = c)) } }
    fun setNotes(n: String) = viewModelScope.launch { card.value?.let { repo.update(it.copy(notes = n)) } }
    fun delete(done: () -> Unit) = viewModelScope.launch {
        val before = repo.snapshot(listOf(currentId.value))
        val name = card.value?.name ?: "card"
        repo.delete(currentId.value)
        Undo.offer("Removed $name") { repo.restore(before) }
        done()
    }

    /** Daily prices recorded for this printing, oldest first. */
    val priceHistory: StateFlow<List<PricePoint>> = card
        .flatMapLatest { c -> if (c == null) flowOf(emptyList()) else repo.observePriceHistory(c.scryfallId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every tag used anywhere in the collection, for suggestions. */
    val allTags: StateFlow<List<String>> = repo.observeAll()
        .map { all -> all.flatMap { it.userTagList() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(tag: String) = viewModelScope.launch { repo.addTag(listOf(currentId.value), tag) }
    fun removeTag(tag: String) = viewModelScope.launch { repo.removeTag(currentId.value, tag) }
}

private val CONDITIONS = listOf("NM" to "Near mint", "LP" to "Lightly played", "MP" to "Moderately played", "HP" to "Heavily played", "DMG" to "Damaged")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(id: Long, onBack: () -> Unit) {
    val vm: CardDetailViewModel = viewModel(key = "card-$id", factory = AppViewModels.detail(id))
    val card by vm.card.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    val history by vm.priceHistory.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    var moveTarget by remember { mutableStateOf<CardBox?>(null) }
    var editingNotes by remember { mutableStateOf(false) }
    val c = card
    // A row briefly disappears while it merges into another box; only call it missing if it stays gone.
    var showMissing by remember { mutableStateOf(false) }
    LaunchedEffect(c == null, loaded) {
        showMissing = false
        if (c == null && loaded) {
            kotlinx.coroutines.delay(600)
            showMissing = true
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (c == null) {
            if (showMissing) {
                EmptyState(
                    Icons.Filled.SearchOff, "Card not found", "It may have been removed.",
                    Modifier.align(Alignment.Center),
                    action = { GradientButton("Back", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack) },
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Accent)
            }
        }
        if (c != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Hero(c)
                Column(Modifier.padding(horizontal = 16.dp).offset(y = (-12).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        ManaCost(c.manaCost, size = 20.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip { RarityDot(c.rarity, 8.dp); Spacer(Modifier.width(6.dp)); Text(c.rarity.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium) }
                        InfoChip { Text("${c.setName} · #${c.collectorNumber}", style = MaterialTheme.typography.labelMedium) }
                        c.releasedAt?.let { InfoChip { Text(it.take(4), style = MaterialTheme.typography.labelMedium) } }
                        if (AppSettings.showPrices) InfoChip { Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.labelMedium, color = Palette.AccentText) }
                    }

                    // Compact editing block: quantity, foil, condition, box, notes and tags.
                    Spacer(Modifier.height(10.dp))
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Palette.Surface) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuantityStepper(c.quantity, onChange = { vm.setQuantity(it.coerceAtLeast(1)) })
                                Spacer(Modifier.weight(1f))
                                SelectPill("Foil", c.foil, onClick = { vm.setFoil(!c.foil) })
                                ConditionPicker(c.condition) { vm.setCondition(it) }
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (boxes.size > 1) BoxPicker(boxes, c.boxId) { b ->
                                    if (c.quantity > 1) moveTarget = b else vm.moveTo(b.id)
                                }
                                DropChip(
                                    if (c.notes.isBlank()) "Notes" else c.notes.lineSequence().first().take(18),
                                    onClick = { editingNotes = true },
                                    leading = { Icon(Icons.Filled.Edit, null, Modifier.size(16.dp), tint = Palette.Muted) },
                                    trailing = false,
                                )
                                DropChip(
                                    "Tag",
                                    onClick = { addingTag = true },
                                    leading = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = Palette.AccentText) },
                                    trailing = false,
                                )
                            }
                            val userTags = c.userTagList()
                            if (userTags.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    userTags.forEach { t ->
                                        Row(
                                            Modifier.clip(RoundedCornerShape(50)).background(Palette.SurfaceHigh).padding(start = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(t, style = MaterialTheme.typography.labelMedium, color = Palette.AccentText)
                                            IconButton(onClick = { vm.removeTag(t) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Filled.Close, "Remove tag $t", Modifier.size(16.dp), tint = Palette.Muted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    CardTextBlock(live?.textInfo() ?: c.textInfo())

                    Spacer(Modifier.height(12.dp))
                    if (AppSettings.showPrices) Panel {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Price each", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.headlineMedium, color = Palette.AccentText)
                                if (c.condition != "NM" && c.nearMintPrice() != null) {
                                    Text("${c.condition} · near mint ${formatUsd(c.nearMintPrice())}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                }
                                val change = c.changeSinceAdded()
                                val was = c.priceWhenAdded()
                                if (change != null && was != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ChangeText(change, was, MaterialTheme.typography.labelMedium)
                                        Text(" since added", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("You own", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text("${c.quantity} · ${formatUsd(c.totalPrice())}", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    val points = history.mapNotNull { p -> (if (c.foil) p.usdFoil ?: p.usd else p.usd)?.let { p.day to it } }
                    if (AppSettings.showPrices && points.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Price history", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                                ChangeText(points.last().second - points.first().second, points.first().second, MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(10.dp))
                            LineChart(points.map { it.second }, Modifier.fillMaxWidth().height(80.dp))
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Text(shortDay(points.first().first), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                                Text(
                                    "low ${formatUsd(points.minOf { it.second })} · high ${formatUsd(points.maxOf { it.second })}",
                                    style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(2f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Text(shortDay(points.last().first), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    } else if (AppSettings.showPrices) {
                        Text(
                            "Price chart appears after a few days.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip {
                            Text("Identity ", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            ManaCost(identityCost(c.colorIdentity), size = 18.dp)
                        }
                    }

                    val roles = c.roleList()
                    if (roles.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What it does", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            roles.forEach { InfoChip { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.AccentText) } }
                        }
                    }

                    val tags = c.tagList()
                    if (tags.any { !it.startsWith("legal:") }) {
                        Spacer(Modifier.height(12.dp))
                        Text("Tags", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.filterNot { it.startsWith("legal:") }.forEach { InfoChip { Text(it, style = MaterialTheme.typography.labelMedium) } }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.scryfallUri))) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open on Scryfall")
                    }
                    Spacer(Modifier.navigationBarsPadding().height(16.dp))
                }
            }
        }

        // Floating top buttons over the artwork.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            val btn = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White)
            IconButton(onClick = onBack, colors = btn) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (c != null) IconButton(onClick = { confirmDelete = true }, colors = btn) { Icon(Icons.Filled.DeleteOutline, "Remove from collection") }
        }
    }

    moveTarget?.let { target ->
        val have = c?.quantity ?: 1
        var count by remember(target.id, have) { mutableIntStateOf(have) }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            containerColor = Palette.Surface,
            title = { Text("Move how many?") },
            text = {
                Column {
                    Text("To ${target.name}", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuantityStepper(count, onChange = { count = it.coerceIn(1, have) })
                        Spacer(Modifier.width(12.dp))
                        Text("of $have", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.moveSome(target.id, count); moveTarget = null }) { Text("Move") } },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } },
        )
    }

    if (editingNotes && c != null) NotesDialog(c.notes, onSave = { vm.setNotes(it); editingNotes = false }, onDismiss = { editingNotes = false })

    if (addingTag) TagDialog(allTags, onDone = { vm.addTag(it); addingTag = false }, onDismiss = { addingTag = false })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.Surface,
            title = { Text("Remove ${card?.name ?: "card"}?") },
            text = { Text("This removes all ${card?.quantity ?: 0} copies from your collection.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Remove", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Blurred artwork backdrop with the card itself floating on top. */
@Composable
private fun Hero(c: CollectionCard) {
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = artCropUrl(c.imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp) else Modifier),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Palette.Background.copy(alpha = 0.6f), Palette.Background))))
        CardImage(
            c.imageUrl,
            Modifier.align(Alignment.Center).statusBarsPadding().padding(top = 8.dp, bottom = 20.dp).height(170.dp),
            corner = 10.dp,
        )
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoChip(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun NotesDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Notes") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Deck, binder page, trades…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(notes) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A compact pill that opens a menu or dialog. */
@Composable
private fun DropChip(label: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, trailing: Boolean = true) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (trailing) Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = Palette.Muted)
    }
}

@Composable
private fun ConditionPicker(condition: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        DropChip(condition, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CONDITIONS.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text("$code · $label", color = if (code == condition) Palette.AccentText else Palette.Text) },
                    onClick = { open = false; onPick(code) },
                )
            }
        }
    }
}

@Composable
private fun BoxPicker(boxes: List<CardBox>, current: Long, onPick: (CardBox) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val here = boxes.firstOrNull { it.id == current }
    Box {
        DropChip(
            here?.name ?: "Box",
            onClick = { open = true },
            leading = { Icon(boxIcon(here?.kind ?: "box"), null, Modifier.size(16.dp), tint = Palette.AccentText) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            boxes.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name, color = if (b.id == current) Palette.AccentText else Palette.Text) },
                    leadingIcon = { Icon(boxIcon(b.kind), null, tint = if (b.id == current) Palette.AccentText else Palette.Muted) },
                    onClick = { open = false; if (b.id != current) onPick(b) },
                )
            }
        }
    }
}

/** Color identity as mana symbols, e.g. "WU" becomes "{W}{U}"; colorless is "{C}". */
internal fun identityCost(identity: String): String {
    val letters = identity.filter { it.uppercaseChar() in "WUBRG" }
    return if (letters.isEmpty()) "{C}" else letters.map { "{${it.uppercaseChar()}}" }.joinToString("")
}

private fun shortDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}.getOrDefault(day)

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.ColorPip
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
    val card: StateFlow<CollectionCard?> = currentId
        .flatMapLatest { repo.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
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
    val c = card

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (c != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Hero(c)
                Column(Modifier.padding(horizontal = 20.dp).offset(y = (-24).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        ManaCost(c.manaCost, size = 20.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip { RarityDot(c.rarity, 8.dp); Spacer(Modifier.width(6.dp)); Text(c.rarity.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium) }
                        InfoChip { Text("${c.setName} · #${c.collectorNumber}", style = MaterialTheme.typography.labelMedium) }
                        c.releasedAt?.let { InfoChip { Text(it.take(4), style = MaterialTheme.typography.labelMedium) } }
                        if (c.foil) FoilTag(Modifier.align(Alignment.CenterVertically))
                    }

                    Spacer(Modifier.height(18.dp))
                    if (AppSettings.showPrices) Panel {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Price each", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.headlineMedium, color = Palette.Accent)
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
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quantity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            QuantityStepper(c.quantity, onChange = { vm.setQuantity(it.coerceAtLeast(1)) })
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Foil", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Switch(checked = c.foil, onCheckedChange = { vm.setFoil(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Condition", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CONDITIONS.forEach { (code, _) -> SelectPill(code, c.condition == code, onClick = { vm.setCondition(code) }) }
                        }
                        Text(
                            CONDITIONS.firstOrNull { it.first == c.condition }?.second.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (boxes.size > 1) {
                            Spacer(Modifier.height(14.dp))
                            Text("Stored in", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                boxes.forEach { b ->
                                    SelectPill(b.name, c.boxId == b.id, onClick = { vm.moveTo(b.id) }, leading = {
                                        Icon(boxIcon(b.kind), null, modifier = Modifier.size(14.dp), tint = if (c.boxId == b.id) Palette.Accent else Palette.Muted)
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        NotesField(c, vm)
                        Spacer(Modifier.height(14.dp))
                        Text("Your tags", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.userTagList().forEach { t ->
                                InfoChip {
                                    Text(t, style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.Close, "Remove tag $t", Modifier.size(14.dp).clickable { vm.removeTag(t) }, tint = Palette.Muted)
                                }
                            }
                            InfoChip {
                                Row(Modifier.clickable { addingTag = true }, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Add, null, Modifier.size(14.dp), tint = Palette.Accent)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add tag", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    CardTextBlock(live?.textInfo() ?: c.textInfo())

                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip {
                            Text("Identity ", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            if (c.colorIdentity.isEmpty()) Text("Colorless", style = MaterialTheme.typography.labelMedium)
                            c.colorIdentity.forEach { ColorPip(it); Spacer(Modifier.width(3.dp)) }
                        }
                    }

                    val roles = c.roleList()
                    if (roles.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What it does", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            roles.forEach { InfoChip { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Accent) } }
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
            IconButton(onClick = { confirmDelete = true }, colors = btn) { Icon(Icons.Filled.DeleteOutline, "Remove from collection") }
        }
    }

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
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        AsyncImage(
            model = artCropUrl(c.imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp) else Modifier),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Palette.Background.copy(alpha = 0.6f), Palette.Background))))
        CardImage(
            c.imageUrl,
            Modifier.align(Alignment.Center).statusBarsPadding().padding(top = 8.dp).height(300.dp),
            corner = 14.dp,
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
private fun NotesField(c: CollectionCard, vm: CardDetailViewModel) {
    var notes by remember(c.id) { mutableStateOf(c.notes) }
    LaunchedEffect(notes) {
        if (notes != c.notes) {
            kotlinx.coroutines.delay(500)
            vm.setNotes(notes)
        }
    }
    Text("Notes", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh).padding(12.dp)) {
        if (notes.isEmpty()) Text("Deck, binder page, trade plans…", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
        BasicTextField(
            value = notes,
            onValueChange = { notes = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
            cursorBrush = SolidColor(Palette.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun shortDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}.getOrDefault(day)

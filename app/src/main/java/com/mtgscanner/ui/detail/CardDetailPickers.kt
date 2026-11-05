package com.mtgscanner.ui.detail

import com.mtgscanner.ui.theme.Space
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.CardPhotos
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.changePrinting
import com.mtgscanner.data.db.CardEvent
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.isDeckSlot
import com.mtgscanner.data.db.placeName
import com.mtgscanner.data.autoBoxFor
import com.mtgscanner.data.deckSlotId
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.userTagList
import com.mtgscanner.ui.collection.TagDialog
import com.mtgscanner.ui.components.LineChart
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.tagList
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardTextBlock
import com.mtgscanner.ui.components.textInfo
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.nearMintEur
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.foilShimmer
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.SetSymbol
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.cardTotal
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.PlaceholderPage

/** Purchase price, where it came from and a photo, behind one small menu. */
@Composable
internal fun MorePicker(
    c: CollectionCard,
    onPaid: () -> Unit,
    onFrom: () -> Unit,
    onCamera: (() -> Unit)?,
    onPick: () -> Unit,
    onRemovePhoto: () -> Unit,
    /** Change condition, finish or language for some copies; null with only one copy. */
    onSplit: (() -> Unit)? = null,
    onLend: (() -> Unit)? = null,
    /** Count copies from a photo of the stack's edge. */
    onCountStack: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        DropChip("More", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onSplit != null) DropdownMenuItem(
                text = { Text("Split copies…") },
                onClick = { open = false; onSplit() },
            )
            if (onLend != null) DropdownMenuItem(
                text = { Text("Lend…") },
                onClick = { open = false; onLend() },
            )
            if (onCountStack != null) DropdownMenuItem(
                text = { Text("Count a stack…") },
                onClick = { open = false; onCountStack() },
            )
            DropdownMenuItem(
                // With several copies, what was paid is their average (copies added later blend in).
                text = {
                    Text(
                        when {
                            c.purchasePrice == null -> "Paid…"
                            c.quantity > 1 -> "Paid: ${formatUsd(c.purchasePrice)} each, on average"
                            else -> "Paid: ${formatUsd(c.purchasePrice)}"
                        },
                        color = Palette.Text,
                    )
                },
                onClick = { open = false; onPaid() },
            )
            DropdownMenuItem(
                text = { Text(if (c.acquiredFrom.isBlank()) "From…" else "From: ${c.acquiredFrom}", color = Palette.Text) },
                onClick = { open = false; onFrom() },
            )
            if (onCamera != null) DropdownMenuItem(
                text = { Text(if (c.photo.isBlank()) "Take photo" else "Retake photo") },
                onClick = { open = false; onCamera() },
            )
            DropdownMenuItem(
                text = { Text(if (c.photo.isBlank()) "Pick photo" else "Pick new photo") },
                onClick = { open = false; onPick() },
            )
            if (c.photo.isNotBlank()) DropdownMenuItem(
                text = { Text("Remove photo", color = Palette.Danger) },
                onClick = { open = false; onRemovePhoto() },
            )
        }
    }
}

/** A compact pill that opens a menu or dialog. */
@Composable
internal fun DropChip(label: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, trailing: Boolean = true) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(Corners.Small))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.s2))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (trailing) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Palette.Icon)
    }
}

@Composable
internal fun LangPicker(lang: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = lang.ifBlank { "en" }.lowercase()
    Box {
        DropChip(current.uppercase(), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LANGUAGES.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text("${code.uppercase()} · $label", color = if (code == current) Palette.AccentText else Palette.Text) },
                    onClick = { open = false; if (code != current) onPick(code) },
                )
            }
        }
    }
}

/** Signed, altered and graded, in one small menu. */
@Composable
internal fun SpecialPicker(
    c: CollectionCard,
    onSigned: (Boolean) -> Unit,
    onAltered: (Boolean) -> Unit,
    onGraded: () -> Unit,
    onProxy: (Boolean) -> Unit,
    onForSale: (Boolean) -> Unit,
    onAsk: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val set = listOfNotNull(
        "Proxy".takeIf { c.proxy }, "Signed".takeIf { c.signed }, "Altered".takeIf { c.altered },
        c.graded.takeIf { it.isNotBlank() }, "Sale".takeIf { c.forSale },
    )
    Box {
        DropChip(if (set.isEmpty()) "Special" else set.joinToString(" · ").take(18), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Signed", color = if (c.signed) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.signed) Icon(Icons.Outlined.Check, null, tint = Palette.Text) },
                onClick = { open = false; onSigned(!c.signed) },
            )
            DropdownMenuItem(
                text = { Text("Altered", color = if (c.altered) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.altered) Icon(Icons.Outlined.Check, null, tint = Palette.Text) },
                onClick = { open = false; onAltered(!c.altered) },
            )
            DropdownMenuItem(
                text = { Text(if (c.graded.isBlank()) "Graded…" else "Graded: ${c.graded}", color = Palette.Text) },
                onClick = { open = false; onGraded() },
            )
            DropdownMenuItem(
                text = { Text("Proxy", color = if (c.proxy) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.proxy) Icon(Icons.Outlined.Check, null, tint = Palette.Text) },
                onClick = { open = false; onProxy(!c.proxy) },
            )
            DropdownMenuItem(
                text = { Text("For sale", color = if (c.forSale) Palette.AccentText else Palette.Text) },
                trailingIcon = { if (c.forSale) Icon(Icons.Outlined.Check, null, tint = Palette.Text) },
                onClick = { open = false; onForSale(!c.forSale) },
            )
            DropdownMenuItem(
                text = { Text(if (c.askPrice == null) "Ask price…" else "Ask: ${formatUsd(c.askPrice)}", color = Palette.Text) },
                onClick = { open = false; onAsk() },
            )
        }
    }
}

@Composable
internal fun ConditionPicker(condition: String, onPick: (String) -> Unit) {
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

/** Where to move a card: a box ([boxId]), a deck ([deckId]), or neither for the box Auto-sort picks. */
data class MoveDest(val name: String, val boxId: Long? = null, val deckId: Long? = null)

/** Where the card is, and where it can go: boxes, then decks in their own group. Out of a deck, Auto-sort comes first. */
@Composable
internal fun BoxPicker(boxes: List<CardBox>, decks: List<Deck>, places: List<CardBox>, current: Long, onPick: (MoveDest) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val here = places.firstOrNull { it.id == current }
    Box {
        DropChip(
            here?.placeName() ?: "Box",
            onClick = { open = true },
            leading = { Icon(boxIcon(here?.kind ?: "box"), null, Modifier.size(18.dp), tint = Palette.Icon) },
        )
        BoxPickMenu(open, { open = false }, boxes, decks, places, current, onPick)
    }
}

/** The boxes and decks a card can move to, as a dropdown under whatever holds it. */
@Composable
internal fun BoxPickMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    boxes: List<CardBox>,
    decks: List<Deck>,
    places: List<CardBox>,
    current: Long,
    onPick: (MoveDest) -> Unit,
) {
    val here = places.firstOrNull { it.id == current }
    val inDeck = here?.isDeckSlot == true
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (inDeck) {
            DropdownMenuItem(
                text = { Text("Auto-sort") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null, tint = Palette.Icon) },
                onClick = { onDismiss(); onPick(MoveDest("Auto-sort")) },
            )
        }
        boxes.forEach { b ->
            DropdownMenuItem(
                text = { Text(b.name, color = if (b.id == current) Palette.AccentText else Palette.Text) },
                leadingIcon = { Icon(boxIcon(b.kind), null, tint = if (b.id == current) Palette.AccentText else Palette.Icon) },
                onClick = { onDismiss(); if (b.id != current) onPick(MoveDest(b.name, boxId = b.id)) },
            )
        }
        if (decks.isNotEmpty()) {
            HorizontalDivider(color = Palette.Rule)
            Text(
                "Decks", style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                modifier = Modifier.padding(start = Space.s4, top = Space.s2, bottom = Space.s1),
            )
            decks.forEach { d ->
                val mine = inDeck && here?.deckId == d.id
                DropdownMenuItem(
                    text = { Text(d.name, color = if (mine) Palette.AccentText else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(boxIcon("deck"), null, tint = if (mine) Palette.AccentText else Palette.Icon) },
                    onClick = { onDismiss(); if (!mine) onPick(MoveDest(d.name, deckId = d.id)) },
                )
            }
        }
    }
}

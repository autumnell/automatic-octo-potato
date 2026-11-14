package com.mtgscanner.ui.detail

import androidx.compose.animation.core.tween
import com.mtgscanner.ui.theme.Motion
import com.mtgscanner.ui.theme.Space
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
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
import com.mtgscanner.ui.components.RuledPanel
import androidx.compose.material.icons.outlined.MoreVert
import com.mtgscanner.ui.components.textInfo
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

internal val LANGUAGES = com.mtgscanner.ui.collection.CARD_LANGUAGES

internal val CONDITIONS = com.mtgscanner.ui.collection.CARD_CONDITIONS

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(id: Long, onBack: () -> Unit, onOpenSet: ((String) -> Unit)? = null) {
    val vm: CardDetailViewModel = viewModel(key = "card-$id", factory = AppViewModels.detail(id))
    val card by vm.card.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var addingTag by remember { mutableStateOf(false) }
    val history by vm.priceHistory.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    var moveTarget by remember { mutableStateOf<MoveDest?>(null) }
    var editingNotes by remember { mutableStateOf(false) }
    var editingGraded by remember { mutableStateOf(false) }
    var editingPaid by remember { mutableStateOf(false) }
    var editingAsk by remember { mutableStateOf(false) }
    var editingFrom by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    var lending by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf(false) }
    var showAllEvents by remember { mutableStateOf(false) }
    val events by vm.events.collectAsStateWithLifecycle()
    val nb by vm.neighbors.collectAsStateWithLifecycle()
    val copyRows by vm.copyRows.collectAsStateWithLifecycle()
    val c = card
    // For the "Recently opened" strip on Browse.
    LaunchedEffect(c?.id) { c?.id?.let { com.mtgscanner.ui.collection.RecentCards.opened(ctx, it) } }

    // Photo of this copy: camera (Android 10+) or the photo picker; both are copied into app storage.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        cameraUri = null
        if (u != null) { if (ok) vm.attachPhoto(u, temp = true) else vm.discardTemp(u) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attachPhoto(uri, temp = false)
    }
    val startCamera: () -> Unit = {
        val u = vm.newCameraTarget()
        if (u != null) {
            cameraUri = u
            if (runCatching { takePhoto.launch(u) }.isFailure) { cameraUri = null; vm.discardTemp(u) }
        }
    }
    val startPicker: () -> Unit = {
        runCatching { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    // Count a stack: a photo of the stack's edge, from the camera or the gallery.
    var stackUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var askingStack by remember { mutableStateOf(false) }
    val stackPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = stackUri
        stackUri = null
        if (u != null) { if (ok) vm.countStack(u, temp = true) else vm.discardTemp(u) }
    }
    val stackPick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.countStack(uri, temp = false)
    }

    // Swipe left/right for the next/previous card in the same box (name order).
    val nbNow by rememberUpdatedState(nb)
    var dragX by remember { mutableFloatStateOf(0f) }
    val swipe = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val limit = 72.dp.toPx()
                val target = when {
                    dragX < -limit -> nbNow.next
                    dragX > limit -> nbNow.prev
                    else -> null
                }
                dragX = 0f
                if (target != null) vm.go(target)
            },
            onDragCancel = { dragX = 0f },
        ) { change, amount ->
            change.consume()
            dragX += amount
        }
    }
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
                EmptyState("Card not found", Modifier.align(Alignment.Center), action = "Back", onAction = onBack)
            } else {
                PlaceholderPage(Modifier.statusBarsPadding().padding(Space.s4))
            }
        }
        if (c != null) {
            val scroll = rememberScrollState()
            // Pull down at the top of the page to close it, one-handed.
            val closeAt = with(LocalDensity.current) { 110.dp.toPx() }
            val backNow by rememberUpdatedState(onBack)
            var pull by remember { mutableFloatStateOf(0f) }
            val pullToClose = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // Scrolling back up first undoes the pull.
                        if (pull > 0f && available.y < 0f) {
                            val used = maxOf(available.y, -pull)
                            pull += used
                            return Offset(0f, used)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput && available.y > 0f) {
                            pull += available.y * 0.5f
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        val was = pull
                        pull = 0f
                        if (was > closeAt) backNow()
                        return if (was > 0f) available else Velocity.Zero
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                // Back, where this card sits in its box, and every other action in one menu.
                CardTopBar(
                    counter = if (nb.total > 1) "${nb.index} / ${nb.total}" else null,
                    onBack = onBack,
                ) { open, close ->
                    CardPageMenu(
                        open, close, c,
                        onShare = { shareCard(ctx, c) },
                        onNotes = { editingNotes = true },
                        onTag = { addingTag = true },
                        onSplit = if (c.quantity > 1) ({ splitting = true }) else null,
                        onLend = { lending = true },
                        onCountStack = { askingStack = true },
                        onPaid = { editingPaid = true },
                        onFrom = { editingFrom = true },
                        onCamera = if (CardPhotos.canUseCamera) startCamera else null,
                        onPick = startPicker,
                        onRemovePhoto = { vm.removePhoto() },
                        onDelete = { vm.delete(onBack) },
                    )
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .then(swipe)
                        .graphicsLayer { translationX = dragX * 0.4f; translationY = pull }
                        .nestedScroll(pullToClose)
                        .verticalScroll(scroll)
                        .padding(horizontal = Space.gutter),
                ) {
                    // Only the card opened grows back into the picture it came from, not one swiped to.
                    CardPicture(c, sharedId = if (c.id == id) id else 0L, onPhoto = { viewingPhoto = true })
                    PrintingLine(c, onOpenSet)
                    // Special-copy marks, only when the copy has any.
                    val loan = com.mtgscanner.ui.loans.loanLabel(c)
                    val foreign = c.lang.isNotBlank() && !c.lang.equals("en", ignoreCase = true)
                    if (foreign || c.signed || c.altered || c.graded.isNotBlank() || c.proxy || loan != null || c.forSale) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(bottom = Space.s3),
                            horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(Space.s2),
                        ) {
                            if (foreign) Badge(c.lang.uppercase())
                            if (c.signed) Badge("Signed")
                            if (c.altered) Badge("Altered")
                            if (c.graded.isNotBlank()) Badge(c.graded)
                            if (c.proxy) InfoChip { Text("Proxy", style = MaterialTheme.typography.labelMedium, color = Palette.Danger) }
                            loan?.let { Badge(it) }
                            if (c.forSale) Badge(if (c.askPrice != null && AppSettings.showPrices) "For sale ${formatUsd(c.askPrice)}" else "For sale")
                        }
                    }

                    // The card's text right under its picture, as on Scryfall. Legality lives in the Rules tab below.
                    CardTextBlock(
                        (live?.textInfo() ?: c.textInfo()).copy(legalities = emptyMap()),
                        heading = true,
                        // Hold the name to copy it.
                        onHoldName = {
                            com.mtgscanner.util.Haptics.tick()
                            com.mtgscanner.util.CopyText.copy(ctx, c.name)
                        },
                    )

                    // Your copy: count, finish, condition, where it lives, language and special marks.
                    Spacer(Modifier.height(Space.s3))
                    RuledPanel {
                        Column(Modifier.padding(horizontal = Space.s4, vertical = Space.s3)) {
                            Text("Your copy", style = MaterialTheme.typography.titleMedium)
                            // Where every copy is, when there's more than this row: "3 copies: Rares ×2, Atraxa deck ×1".
                            val copiesLine = remember(copyRows, places, decks) {
                                val deckNames = decks.associate { it.id to it.name }
                                val spots = places.associate { b ->
                                    val spot = if (b.isDeckSlot) com.mtgscanner.data.CopiesLine.Spot(deckNames[b.deckId] ?: b.name, deck = true)
                                    else com.mtgscanner.data.CopiesLine.Spot(b.name)
                                    b.id to spot
                                }
                                com.mtgscanner.data.CopiesLine.text(copyRows, spots)
                            }
                            Text(
                                copiesLine ?: listOf("Added ${shortDate(c.addedAt)}", c.acquiredFrom).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider(color = Palette.Rule)
                        Column(Modifier.padding(horizontal = Space.s4, vertical = Space.s3)) {
                            // How many and where, then what kind of copy: two rows on most phones.
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                                QuantityStepper(c.quantity, onChange = { vm.changeQuantity(it - c.quantity) })
                                val inDeck = places.any { it.id == c.boxId && it.isDeckSlot }
                                if (boxes.size > 1 || decks.isNotEmpty() || inDeck) Box(Modifier.align(Alignment.CenterVertically)) {
                                    BoxPicker(boxes, decks, places, c.boxId) { d -> if (c.quantity > 1) moveTarget = d else vm.moveToDest(d) }
                                }
                            }
                            Spacer(Modifier.height(Space.s2))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                                SelectPill("Foil", c.foil, onClick = { vm.setFoil(!c.foil) })
                                ConditionPicker(c.condition) { vm.setCondition(it) }
                                LangPicker(c.lang) { vm.setLang(it) }
                                SpecialPicker(
                                    c,
                                    onSigned = { vm.setSigned(it) },
                                    onAltered = { vm.setAltered(it) },
                                    onGraded = { editingGraded = true },
                                    onProxy = { vm.setProxy(it) },
                                    onForSale = { vm.setForSale(it) },
                                    onAsk = { editingAsk = true },
                                )
                            }
                        }
                        // Notes and your own tags show only once there are some; the menu adds them.
                        if (c.notes.isNotBlank()) {
                            HorizontalDivider(color = Palette.Rule)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { editingNotes = true }
                                    .padding(horizontal = Space.s4, vertical = Space.s2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Edit, "Edit note", Modifier.size(18.dp), tint = Palette.Icon)
                                Spacer(Modifier.width(Space.s3))
                                Text(c.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val userTags = c.userTagList()
                        if (userTags.isNotEmpty()) {
                            HorizontalDivider(color = Palette.Rule)
                            FlowRow(
                                Modifier.padding(horizontal = Space.s4, vertical = Space.s3),
                                horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2),
                            ) {
                                userTags.forEach { t ->
                                    Row(
                                        Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh).padding(start = Space.s3),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(t, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
                                        IconButton(onClick = { vm.removeTag(t) }) {
                                            Icon(Icons.Outlined.Close, "Remove tag $t", Modifier.size(18.dp), tint = Palette.Icon)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Where its box sits on the shelf, when the shelf is laid out.
                    com.mtgscanner.ui.collection.ShelfSpot(boxes, setOf(c.boxId), Modifier.padding(top = Space.s3))
                    // Rules (legality and rulings) and versions come before the price, so the card's own text stays together.
                    Spacer(Modifier.height(Space.s4))
                    CardInfoTabs(c, live, vm)

                    Spacer(Modifier.height(Space.s3))
                    if (AppSettings.showPrices) Panel {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Price each", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text(cardPrice(c), style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                                val change = c.changeSinceAdded()
                                val was = c.priceWhenAdded()
                                if (change != null && was != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ChangeText(change, was, MaterialTheme.typography.labelMedium)
                                        Text(
                                            if (c.purchasePrice != null) " vs paid ${formatUsd(c.purchasePrice)}" else " since added",
                                            style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("You own", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text("${c.quantity} · ${cardTotal(c)}", style = MaterialTheme.typography.titleMedium.merge(TabularNums))
                            }
                        }
                        PriceAlertRow(c)
                    }
                    // EDHREC's salt score (how much a card annoys the table), for its saltiest cards.
                    LaunchedEffect(Unit) { runCatching { com.mtgscanner.data.SaltScores.load(ctx) } }
                    com.mtgscanner.data.SaltScores.of(c.name)?.let { salt ->
                        Text(
                            "Salt %.1f on EDHREC".format(salt), style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                            color = Palette.Muted, modifier = Modifier.padding(top = Space.s2, start = Space.s1),
                        )
                    }

                    val allPoints = history.mapNotNull { p -> (if (c.foil) p.usdFoil ?: p.usd else p.usd)?.let { p.day to it } }
                    // 7 days, 30 days or everything; a range with under 2 points shows everything.
                    var range by rememberSaveable { mutableIntStateOf(0) }
                    val points = remember(allPoints, range) {
                        val days = when (range) { 1 -> 7L; 2 -> 30L; else -> 0L }
                        if (days == 0L) allPoints
                        else {
                            val from = java.time.LocalDate.now().minusDays(days).toString()
                            allPoints.filter { it.first >= from }.takeIf { it.size >= 2 } ?: allPoints
                        }
                    }
                    if (AppSettings.showPrices && points.size >= 2) {
                        Spacer(Modifier.height(Space.s3))
                        Panel {
                            // Drag along the chart to read one day's price in place of the title.
                            var scrub by remember(points) { mutableStateOf<Int?>(null) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val s = scrub
                                if (s != null) {
                                    Text(shortDay(points[s].first), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                                    Text(formatUsd(points[s].second), style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Palette.Text)
                                } else {
                                    RangeSegments(range) { range = it }
                                    Spacer(Modifier.weight(1f))
                                    ChangeText(points.last().second - points.first().second, points.first().second, MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(Modifier.height(Space.s2))
                            LineChart(points.map { it.second }, Modifier.fillMaxWidth().height(56.dp), onScrub = { scrub = it })
                            Spacer(Modifier.height(Space.s2))
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
                            "Price chart appears after a few days",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = Space.s2),
                        )
                    }


                    Spacer(Modifier.height(Space.s3))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        InfoChip {
                            Text("Identity ", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            ManaCost(identityCost(c.colorIdentity), size = 18.dp)
                        }
                    }

                    val tags = c.tagList()
                    if (tags.any { !it.startsWith("legal:") }) {
                        Spacer(Modifier.height(Space.s3))
                        Text("Tags", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Spacer(Modifier.height(Space.s2))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            tags.filterNot { it.startsWith("legal:") }.forEach { InfoChip { Text(it, style = MaterialTheme.typography.labelMedium) } }
                        }
                    }

                    Spacer(Modifier.height(Space.s3))
                    Text("History", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Spacer(Modifier.height(Space.s2))
                    if (events.isEmpty()) {
                        Text("Nothing yet", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    } else {
                        val shown = if (showAllEvents) events else events.take(4)
                        shown.forEach { e -> EventRow(e) }
                        if (events.size > 4) {
                            TextButton(onClick = { showAllEvents = !showAllEvents }) {
                                Text(if (showAllEvents) "Less" else "All ${events.size}")
                            }
                        }
                    }

                    Spacer(Modifier.height(Space.s4))
                    // Scryfall, then EDHREC and the two stores (Scryfall's own links once it has answered).
                    val links = remember(c.name, c.scryfallUri, live) {
                        listOf(com.mtgscanner.data.CardLinks.Link("Scryfall", c.scryfallUri)).filter { it.url.isNotBlank() } +
                            com.mtgscanner.data.CardLinks.of(c.name, live?.relatedUris.orEmpty(), live?.purchaseUris.orEmpty())
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                        links.forEach { link ->
                            TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) } }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Space.s1))
                                Text(link.label)
                            }
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(Space.s6))
                }
            }
        }
    }

    moveTarget?.let { target ->
        val have = c?.quantity ?: 1
        var count by remember(target, have) { mutableIntStateOf(have) }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            containerColor = Palette.Surface,
            title = { Text("Move how many?") },
            text = {
                Column {
                    Text("To ${target.name}", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Space.s3))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuantityStepper(count, onChange = { count = it.coerceIn(1, have) })
                        Spacer(Modifier.width(Space.s3))
                        Text("of $have", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.moveToDest(target, count); moveTarget = null }) { Text("Move") } },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } },
        )
    }

    if (editingNotes && c != null) NotesDialog(c.notes, onSave = { vm.setNotes(it); editingNotes = false }, onDismiss = { editingNotes = false })

    if (editingGraded && c != null) GradedDialog(c.graded, onSave = { vm.setGraded(it); editingGraded = false }, onDismiss = { editingGraded = false })

    if (editingPaid && c != null) AmountDialog("Paid each", c.purchasePrice, onSave = { vm.setPaid(it); editingPaid = false }, onDismiss = { editingPaid = false })

    if (editingAsk && c != null) AmountDialog("Asking price", c.askPrice, onSave = { vm.setAsk(it); editingAsk = false }, onDismiss = { editingAsk = false })

    if (editingFrom && c != null) FromDialog(c.acquiredFrom, onSave = { vm.setFrom(it); editingFrom = false }, onDismiss = { editingFrom = false })

    if (viewingPhoto && c != null && c.photo.isNotBlank()) {
        PhotoViewer(
            c.photo,
            onRemove = { viewingPhoto = false; vm.removePhoto() },
            onReplace = { viewingPhoto = false; if (CardPhotos.canUseCamera) startCamera() else startPicker() },
            onDismiss = { viewingPhoto = false },
        )
    }

    // Count a stack: where the photo comes from, then the count to confirm.
    if (askingStack) {
        AlertDialog(
            onDismissRequest = { askingStack = false },
            containerColor = Palette.Surface,
            title = { Text("Count a stack") },
            text = { Text("Photograph the side of a stack of this card, close enough that the stack fills the picture", color = Palette.Muted) },
            confirmButton = {
                if (CardPhotos.canUseCamera) TextButton(onClick = {
                    askingStack = false
                    val u = vm.newCameraTarget()
                    if (u != null) {
                        stackUri = u
                        if (runCatching { stackPhoto.launch(u) }.isFailure) { stackUri = null; vm.discardTemp(u) }
                    }
                }) { Text("Take photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askingStack = false
                    runCatching { stackPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }) { Text("Pick photo") }
            },
        )
    }
    val stackCounting by vm.stackCounting.collectAsStateWithLifecycle()
    val stackCount by vm.stackCount.collectAsStateWithLifecycle()
    val stackFailed by vm.stackFailed.collectAsStateWithLifecycle()
    if (stackCounting) {
        AlertDialog(onDismissRequest = {}, containerColor = Palette.Surface, title = { Text("Counting…") }, confirmButton = {})
    }
    stackCount?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.stackCount.value = null },
            containerColor = Palette.Surface,
            title = { Text("About ${r.count} cards") },
            text = {
                Text(
                    if (r.sure) "Set this card to ${r.count} copies?"
                    else "The edges weren't clear, so this may be off. Set this card to ${r.count} copies?",
                    color = Palette.Muted,
                )
            },
            confirmButton = { TextButton(onClick = vm::useStackCount) { Text("Set to ${r.count}") } },
            dismissButton = { TextButton(onClick = { vm.stackCount.value = null }) { Text("Cancel") } },
        )
    }
    if (stackFailed) {
        AlertDialog(
            onDismissRequest = { vm.stackFailed.value = false },
            containerColor = Palette.Surface,
            title = { Text("Couldn't count the edges") },
            text = { Text("Try closer, with the stack filling the picture and light from the side", color = Palette.Muted) },
            confirmButton = { TextButton(onClick = { vm.stackFailed.value = false }) { Text("OK") } },
        )
    }
    if (splitting && c != null && c.quantity > 1) {
        SplitDialog(c, onSplit = { vm.split(it); splitting = false }, onDismiss = { splitting = false })
    }
    if (lending && c != null) {
        com.mtgscanner.ui.loans.LendDialog(c, onDismiss = { lending = false })
    }

    if (addingTag) {
        val allTags by vm.allTags.collectAsStateWithLifecycle()
        TagDialog(allTags, onDone = { vm.addTag(it); addingTag = false }, onDismiss = { addingTag = false })
    }
}

/**
 * The card itself, centered and small enough that its text shows below it. Tap to make it bigger (tap again to
 * shrink it); hold it for the big card over everything. Your own photo of the copy sits beside it.
 */
@Composable
private fun CardPicture(c: CollectionCard, sharedId: Long, onPhoto: () -> Unit) {
    var big by rememberSaveable(c.id) { mutableStateOf(false) }
    val cardH by animateDpAsState(if (big) 440.dp else 280.dp, tween(Motion.base, easing = Motion.ease), label = "cardPicture")
    Box(Modifier.fillMaxWidth().padding(top = Space.s2), contentAlignment = Alignment.Center) {
        CardImage(
            c.imageUrl,
            Modifier.height(cardH)
                .sharedCard(sharedId)
                .foilShimmer(c.foil)
                .semantics { onClick(if (big) "Make the card small" else "Make the card big") { big = !big; true } },
            zoom = true,
            onTap = { big = !big },
        )
        if (c.photo.isNotBlank() && !big) {
            AsyncImage(
                model = java.io.File(c.photo),
                contentDescription = "Your photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(Corners.Small))
                    .background(Palette.SurfaceHigh)
                    .clickable(onClick = onPhoto),
            )
        }
    }
}

/** "Magic 2011 · #149 · Common · 2010" under the picture; tap it to open the set. */
@Composable
private fun PrintingLine(c: CollectionCard, onOpenSet: ((String) -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onOpenSet != null) Modifier.clickable { onOpenSet(c.setCode.lowercase()) } else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetSymbol(c.setCode, c.rarity, modifier = Modifier.padding(end = Space.s2))
        Text(
            listOfNotNull(c.setName, "#${c.collectorNumber}", c.rarity.replaceFirstChar(Char::uppercase), c.releasedAt?.take(4))
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Back, the card's place in its box ("6 / 12") and one menu for everything else. [menu] draws the menu under ⋮. */
@Composable
private fun CardTopBar(counter: String?, onBack: () -> Unit, menu: @Composable (open: Boolean, close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Palette.Background).statusBarsPadding().padding(horizontal = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Palette.Icon) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            counter?.let { Text(it, style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Palette.Muted) }
        }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = Palette.Icon) }
            menu(open) { open = false }
        }
    }
}

@Composable
private fun EventRow(e: CardEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Text(
            e.kind.replaceFirstChar(Char::uppercase),
            style = MaterialTheme.typography.labelMedium, color = Palette.Text,
            modifier = Modifier.width(64.dp),
        )
        Text(e.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
        Text(shortDate(e.at), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/** A timestamp as "Mar 3, 2025". */
internal fun shortDate(millis: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))
}.getOrDefault("")

@Composable
private fun Panel(content: @Composable () -> Unit) {
    RuledPanel { Column(Modifier.padding(Space.s4)) { content() } }
}

@Composable
private fun InfoChip(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Badge(text: String) {
    InfoChip { Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.Text) }
}

/** Color identity as mana symbols, e.g. "WU" becomes "{W}{U}"; colorless is "{C}". */
internal fun identityCost(identity: String): String {
    val letters = identity.filter { it.uppercaseChar() in "WUBRG" }
    return if (letters.isEmpty()) "{C}" else letters.map { "{${it.uppercaseChar()}}" }.joinToString("")
}

/** "All · 7d · 30d" as one small segmented row for the price chart. */
@Composable
private fun RangeSegments(selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh).padding(Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("All", "7d", "30d").forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(Corners.Small))
                    .background(if (on) Palette.Surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(i) }
                    .padding(horizontal = Space.s3, vertical = Space.s1),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Palette.Text else Palette.Muted)
            }
        }
    }
}

private fun shortDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}.getOrDefault(day)

/** "Ragavan, Nimble Pilferer · MH2 #138 · $54.10" and the Scryfall link, to any app. */
private fun shareCard(ctx: android.content.Context, c: CollectionCard) {
    val line = listOfNotNull(
        c.name,
        "${c.setCode.uppercase()} #${c.collectorNumber}" + if (c.foil) " foil" else "",
        if (AppSettings.showPrices) cardPrice(c).takeIf { it.isNotBlank() } else null,
    ).joinToString(" · ")
    val text = listOf(line, c.scryfallUri).filter { it.isNotBlank() }.joinToString("\n")
    com.mtgscanner.util.shareText(ctx, c.name, text)
}

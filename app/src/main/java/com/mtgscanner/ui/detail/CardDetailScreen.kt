package com.mtgscanner.ui.detail

import com.mtgscanner.ui.components.ruleAbove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.BiasAlignment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.mtgscanner.ui.components.tintedBackground
import com.mtgscanner.ui.components.rememberArtTint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.ScrollState
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
                PlaceholderPage(Modifier.statusBarsPadding().padding(16.dp))
            }
        }
        if (c != null) {
            // Art first: the card's art fills the top of the screen and the details sit on a sheet scrolled up over it.
            val scroll = rememberScrollState()
            val screenH = LocalConfiguration.current.screenHeightDp.dp
            val artH = screenH * 0.52f
            val tint = rememberArtTint(artCropUrl(c.imageUrl))
            val sheetColor = tintedBackground(tint, 0.16f)
            ArtBackdrop(c, artH, scroll)
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
            Column(
                Modifier
                    .fillMaxSize()
                    .then(swipe)
                    .graphicsLayer { translationX = dragX * 0.4f; translationY = pull }
                    .nestedScroll(pullToClose)
                    .verticalScroll(scroll),
            ) {
                // Only the card opened grows back into the picture it came from, not one swiped to.
                Hero(c, sharedId = if (c.id == id) id else 0L, height = artH, onPhoto = { viewingPhoto = true })
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = screenH)
                        .clip(RoundedCornerShape(topStart = Corners.Medium, topEnd = Corners.Medium))
                        .background(Brush.verticalGradient(listOf(sheetColor, Palette.Background), endY = 900f))
                        .padding(horizontal = 16.dp),
                ) {
                    // The sheet's handle: scroll up for everything else.
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 10.dp)
                            .size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Outline),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Hold the name to copy it.
                        Text(
                            c.name, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).pointerInput(c.name) {
                                detectTapGestures(onLongPress = {
                                    com.mtgscanner.util.Haptics.tick()
                                    com.mtgscanner.util.CopyText.copy(ctx, c.name)
                                })
                            },
                        )
                        ManaCost(c.manaCost, size = 20.dp)
                    }
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
                    copiesLine?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Special-copy badges first, so they're seen next to the name.
                        if (c.lang.isNotBlank() && !c.lang.equals("en", ignoreCase = true)) Badge(c.lang.uppercase())
                        if (c.signed) Badge("Signed")
                        if (c.altered) Badge("Altered")
                        if (c.graded.isNotBlank()) Badge(c.graded)
                        if (c.proxy) InfoChip { Text("Proxy", style = MaterialTheme.typography.labelMedium, color = Palette.Danger) }
                        com.mtgscanner.ui.loans.loanLabel(c)?.let { Badge(it) }
                        if (c.forSale) Badge(if (c.askPrice != null && AppSettings.showPrices) "For sale ${formatUsd(c.askPrice)}" else "For sale")
                        InfoChip { RarityDot(c.rarity, 8.dp); Spacer(Modifier.width(6.dp)); Text(c.rarity.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium) }
                        // Tap the set to open it in the set tracker.
                        InfoChip(onClick = onOpenSet?.let { open -> { open(c.setCode.lowercase()) } }) {
                            SetSymbol(c.setCode, c.rarity, modifier = Modifier.padding(end = 6.dp))
                            Text(
                                "${c.setName} · #${c.collectorNumber}", style = MaterialTheme.typography.labelMedium,
                                color = if (onOpenSet != null) Palette.AccentText else Palette.Text,
                            )
                        }
                        c.releasedAt?.let { InfoChip { Text(it.take(4), style = MaterialTheme.typography.labelMedium) } }
                        if (AppSettings.showPrices) InfoChip { Text(cardPrice(c), style = MaterialTheme.typography.labelMedium, color = Palette.Text) }
                        InfoChip {
                            Text(
                                listOf("Added ${shortDate(c.addedAt)}", c.acquiredFrom).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                            )
                        }
                    }

                    // Compact editing block: quantity, foil, condition, box, notes and tags.
                    Spacer(Modifier.height(10.dp))
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuantityStepper(c.quantity, onChange = { vm.changeQuantity(it - c.quantity) })
                                Spacer(Modifier.weight(1f))
                                SelectPill("Foil", c.foil, onClick = { vm.setFoil(!c.foil) })
                                ConditionPicker(c.condition) { vm.setCondition(it) }
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val inDeck = places.any { it.id == c.boxId && it.isDeckSlot }
                                if (boxes.size > 1 || decks.isNotEmpty() || inDeck) BoxPicker(boxes, decks, places, c.boxId) { d ->
                                    if (c.quantity > 1) moveTarget = d else vm.moveToDest(d)
                                }
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
                                MorePicker(
                                    c,
                                    onPaid = { editingPaid = true },
                                    onFrom = { editingFrom = true },
                                    onCamera = if (CardPhotos.canUseCamera) startCamera else null,
                                    onPick = startPicker,
                                    onRemovePhoto = { vm.removePhoto() },
                                    onSplit = if (c.quantity > 1) ({ splitting = true }) else null,
                                    onLend = { lending = true },
                                    onCountStack = { askingStack = true },
                                )
                                DropChip(
                                    if (c.notes.isBlank()) "Notes" else c.notes.lineSequence().first().take(18),
                                    onClick = { editingNotes = true },
                                    leading = { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp), tint = Palette.Icon) },
                                    trailing = false,
                                )
                                DropChip(
                                    "Tag",
                                    onClick = { addingTag = true },
                                    leading = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), tint = Palette.Icon) },
                                    trailing = false,
                                )
                            }
                            val userTags = c.userTagList()
                            if (userTags.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    userTags.forEach { t ->
                                        Row(
                                            Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh).padding(start = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(t, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
                                            IconButton(onClick = { vm.removeTag(t) }) {
                                                Icon(Icons.Outlined.Close, "Remove tag $t", Modifier.size(16.dp), tint = Palette.Icon)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Where its box sits on the shelf, when the shelf is laid out.
                    com.mtgscanner.ui.collection.ShelfSpot(boxes, setOf(c.boxId), Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(10.dp))
                    // Legality lives in the Rules tab below, with every format.
                    CardTextBlock((live?.textInfo() ?: c.textInfo()).copy(legalities = emptyMap()))

                    // Rules (legality and rulings) and versions come before the price, so the card's own text stays together.
                    Spacer(Modifier.height(14.dp))
                    CardInfoTabs(c, live, vm)

                    Spacer(Modifier.height(12.dp))
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
                            color = Palette.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
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
                        Spacer(Modifier.height(12.dp))
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
                            Spacer(Modifier.height(8.dp))
                            LineChart(points.map { it.second }, Modifier.fillMaxWidth().height(56.dp), onScrub = { scrub = it })
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

                    val tags = c.tagList()
                    if (tags.any { !it.startsWith("legal:") }) {
                        Spacer(Modifier.height(12.dp))
                        Text("Tags", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.filterNot { it.startsWith("legal:") }.forEach { InfoChip { Text(it, style = MaterialTheme.typography.labelMedium) } }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("History", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Spacer(Modifier.height(6.dp))
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

                    Spacer(Modifier.height(16.dp))
                    // Scryfall, then EDHREC and the two stores (Scryfall's own links once it has answered).
                    val links = remember(c.name, c.scryfallUri, live) {
                        listOf(com.mtgscanner.data.CardLinks.Link("Scryfall", c.scryfallUri)).filter { it.url.isNotBlank() } +
                            com.mtgscanner.data.CardLinks.of(c.name, live?.relatedUris.orEmpty(), live?.purchaseUris.orEmpty())
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        links.forEach { link ->
                            TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) } }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(link.label)
                            }
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(16.dp + ACTION_BAR))
                }
            }
        }

        // Thumb-reach actions along the bottom.
        if (c != null) {
            val canMove = boxes.size > 1 || decks.isNotEmpty() || places.any { it.id == c.boxId && it.isDeckSlot }
            val moveMenu: (@Composable (Boolean, () -> Unit) -> Unit)? = if (!canMove) null else { open, close ->
                BoxPickMenu(open, close, boxes, decks, places, c.boxId) { d -> if (c.quantity > 1) moveTarget = d else vm.moveToDest(d) }
            }
            ActionBar(
                c,
                Modifier.align(Alignment.BottomCenter),
                move = moveMenu,
                onAdd = { vm.changeQuantity(1); com.mtgscanner.util.Haptics.tick() },
                onSell = { vm.setForSale(!c.forSale) },
                onShare = { shareCard(ctx, c) },
            )
        }

        // Floating top buttons over the artwork.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            val btn = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White)
            IconButton(onClick = onBack, colors = btn) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (c != null && nb.total > 1) {
                Text(
                    "${nb.index} / ${nb.total}",
                    style = MaterialTheme.typography.labelMedium, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .clip(RoundedCornerShape(Corners.Small))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
            }
            if (c != null) IconButton(onClick = { vm.delete(onBack) }, colors = btn) { Icon(Icons.Outlined.DeleteOutline, "Remove from collection") }
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
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuantityStepper(count, onChange = { count = it.coerceIn(1, have) })
                        Spacer(Modifier.width(12.dp))
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
            text = { Text("Photograph the side of a stack of this card, close enough that the stack fills the picture.", color = Palette.Muted) },
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
            text = { Text("Try closer, with the stack filling the picture and light from the side.", color = Palette.Muted) },
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

/** The card's art, full width and sharp, fixed behind the page; it drifts up slower than the sheet. */
@Composable
private fun ArtBackdrop(c: CollectionCard, height: Dp, scroll: ScrollState) {
    Box(
        Modifier.fillMaxWidth().height(height).background(Palette.Surface)
            .graphicsLayer { translationY = -scroll.value * 0.4f },
    ) {
        AsyncImage(
            model = artCropUrl(c.imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Dark at the top for the buttons, and into the page at the bottom.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Transparent, Palette.Background.copy(alpha = 0.7f)))))
    }
}

/**
 * The see-through top of the page over the art: the card itself in the corner, and your photo of it.
 * Tap the card to make it fill the top of the page (tap again to shrink it); hold it for the big card over everything.
 */
@Composable
private fun Hero(c: CollectionCard, sharedId: Long, height: Dp, onPhoto: () -> Unit) {
    var big by rememberSaveable(c.id) { mutableStateOf(false) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Room under the floating buttons, down to the sheet.
    val bigH = (height - 16.dp - top - 64.dp - 12.dp).coerceAtLeast(132.dp)
    val cardH by animateDpAsState(if (big) bigH else 132.dp, label = "heroCard")
    val bias by animateFloatAsState(if (big) 0f else -1f, label = "heroBias")
    val dim by animateFloatAsState(if (big) 0.55f else 0f, label = "heroDim")
    Box(Modifier.fillMaxWidth().height(height - 16.dp)) {
        // The art behind dims while the card is big, so the card stands out.
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        CardImage(
            c.imageUrl,
            Modifier.align(BiasAlignment(bias, 1f)).padding(start = 16.dp, end = 16.dp, bottom = 12.dp).height(cardH)
                .sharedCard(sharedId)
                .foilShimmer(c.foil)
                .semantics { onClick(if (big) "Make the card small" else "Make the card big") { big = !big; true } },
            zoom = true,
            onTap = { big = !big },
        )
        // Your own copy's photo, small in the corner; tap to view.
        if (c.photo.isNotBlank()) {
            AsyncImage(
                model = java.io.File(c.photo),
                contentDescription = "Your photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(Corners.Small))
                    .background(Palette.SurfaceHigh)
                    .clickable(onClick = onPhoto),
            )
        }
    }
}

@Composable
private fun EventRow(e: CardEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoChip(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
        Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("All", "7d", "30d").forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(Corners.Small))
                    .background(if (on) Palette.Surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(i) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Palette.Text else Palette.Muted)
            }
        }
    }
}

private fun shortDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}.getOrDefault(day)

private val ACTION_BAR = 64.dp

/** Move, +1, Sell and Share along the bottom of the card page. [move] draws the box menu, anchored to its button. */
@Composable
private fun ActionBar(
    c: CollectionCard,
    modifier: Modifier,
    move: (@Composable (open: Boolean, close: () -> Unit) -> Unit)?,
    onAdd: () -> Unit,
    onSell: () -> Unit,
    onShare: () -> Unit,
) {
    var moving by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), color = Palette.Surface) {
        Row(
            Modifier.fillMaxWidth().ruleAbove().navigationBarsPadding().height(ACTION_BAR).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (move != null) Box(Modifier.weight(1f)) {
                BarButton(Icons.Outlined.Inventory2, "Move", onClick = { moving = true })
                move(moving) { moving = false }
            }
            BarButton(Icons.Outlined.Add, "+1", Modifier.weight(1f), onAdd)
            BarButton(Icons.Outlined.Sell, if (c.forSale) "For sale" else "Sell", Modifier.weight(1f), onSell, on = c.forSale)
            BarButton(Icons.Outlined.Share, "Share", Modifier.weight(1f), onShare)
        }
    }
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit, on: Boolean = false) {
    val tint = if (on) Palette.AccentText else Palette.Text
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (on) Palette.AccentText else Palette.Icon, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

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

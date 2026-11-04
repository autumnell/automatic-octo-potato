package com.mtgscanner.ui.collection

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import com.mtgscanner.data.db.placeName
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.mtgscanner.ui.components.pinchToResize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.boxIcon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.mtgscanner.data.BoxRules
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.mtgscanner.ui.components.CollapsingHeader
import com.mtgscanner.ui.components.flashOnce
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import androidx.compose.material.icons.outlined.Style
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.SetMark
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.unitEur
import com.mtgscanner.util.PriceCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mtgscanner.ui.components.foilShimmer
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.ToolChip
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.cardPriceShort
import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.mtgscanner.data.Binder
import com.mtgscanner.ui.detail.CardDetailScreen
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.TabularNums

/** A few examples of Scryfall search syntax, which the collection search reads too. */
@Composable
internal fun SyntaxHelp(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        listOf(
            "t:elf  c:rg  id<=esper  mv>=4",
            "o:\"draw a card\"  r:mythic  s:mh2",
            "pow>tou  usd>5  is:foil  a:rush",
            "-t:land  (bolt or shock)  !\"Sol Ring\"",
            "box:binder  tag:trade  cond:lp  qty>1",
            "added>=7d  is:lent  is:forsale",
            "f:modern  otag:ramp  (asks Scryfall)",
        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Text) }
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search name, type, text, tag",
    /** Scryfall-syntax search: placeholder and text in Plex Mono, no search icon. */
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    /** Puts the cursor in the field (keyboard up) when it first shows. */
    focusOnOpen: Boolean = false,
) {
    val textStyle = if (mono) monoStyle(14.sp) else MaterialTheme.typography.bodyLarge
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    if (focusOnOpen) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(LookShape).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!mono) {
            Icon(Icons.Outlined.Search, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Palette.Muted, style = textStyle, maxLines = 1)
            // Results update as you type: the keyboard's Search key just puts the keyboard away.
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
                textStyle = textStyle.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Clear, "Clear", tint = Palette.Icon, modifier = Modifier.size(20.dp)) }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun CardGrid(
    groups: List<Pair<String, List<CollectionCard>>>,
    grid: Boolean,
    selected: Set<Long>,
    picking: Boolean,
    dense: Boolean,
    /** Change per copy over the last week, by card id; cards without a week of history are missing. */
    week: Map<Long, Double>,
    slotOf: (CollectionCard) -> String?,
    onOpen: (CollectionCard) -> Unit,
    onLongPress: (CollectionCard) -> Unit,
    onSwipeMove: (CollectionCard) -> Unit,
    onSwipeSell: (CollectionCard) -> Unit,
    tabs: Boolean = false,
    byName: Boolean = false,
    modifier: Modifier = Modifier,
    /** Hold a card, then drag over more to select them all. */
    onDragSelect: ((Set<Long>) -> Unit)? = null,
    /** Where the list was scrolled is kept under this key while the app runs, so coming back lands in the same spot. */
    scrollKey: String? = null,
    /** Cards new since the list was last looked at; each gets a small dot. */
    fresh: Set<Long> = emptySet(),
) {
    val saved = remember(scrollKey) { scrollKey?.let { ScrollMemory.get(it) } }
    val state = rememberLazyGridState(saved?.first ?: 0, saved?.second ?: 0)
    if (scrollKey != null) DisposableEffect(scrollKey, state) {
        onDispose { ScrollMemory.put(scrollKey, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
    }
    val scope = rememberCoroutineScope()
    val headers = remember(groups) { ListSections.headerIndices(groups) }
    /** Copies per section, counted once per list change instead of in every header. */
    val counts = remember(groups) { groups.map { g -> g.second.sumOf { it.quantity } } }
    val rail = remember(groups, headers) { ListSections.rail(groups, headers) }
    val jumpBar = remember(groups, rail, byName) { JumpBar.shows(byName, groups.sumOf { it.second.size }, rail) }
    /** The section pinned to the top, or -1 while its own header is in place. */
    val pinned by remember(groups, headers) {
        derivedStateOf {
            val g = ListSections.sectionAt(headers, state.firstVisibleItemIndex)
            if (g < 0 || (headers[g] == state.firstVisibleItemIndex && state.firstVisibleItemScrollOffset == 0)) -1 else g
        }
    }
    var railLetter by remember { mutableStateOf<String?>(null) }
    // Card ids in the order shown, for selecting a run by dragging.
    val order = remember(groups) { groups.flatMap { g -> g.second.map { it.id } } }
    // Cards that turn up while the list is open (just added or moved in) glow for a moment; a big change,
    // such as a new search, doesn't.
    val newIds = remember { com.mtgscanner.ui.components.NewIds() }
    val justAdded = remember(order) { newIds.update(order).takeIf { it.size <= 3 } ?: emptySet() }
    val selectedNow by rememberUpdatedState(selected)
    Column(modifier.fillMaxSize()) {
    if (!grid) ListHeader(end = (if (jumpBar) 28.dp else 10.dp) + 6.dp)
    Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyVerticalGrid(
            state = state,
            columns = if (grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = 10.dp, end = if (jumpBar) 28.dp else 10.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize().then(
                if (onDragSelect != null) Modifier.dragToSelect(state, order, { selectedNow }, onDragSelect, scope) else Modifier,
            ),
        ) {
            groups.forEachIndexed { gi, (title, cards) ->
                if (title.isNotEmpty()) {
                    item(key = "h-$gi-$title", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        if (tabs) DividerTabHead(title, counts[gi], gi, Modifier.padding(top = 14.dp, bottom = 6.dp))
                        else SectionHead(title, counts[gi], Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp))
                    }
                }
                items(cards, key = { it.id }, contentType = { if (grid) "tile" else if (dense) "dense" else "row" }) { c ->
                    Box(Modifier.animateItem().flashOnce(c.id in justAdded)) {
                    if (grid) {
                        CardTile(
                            c.smallImageUrl ?: c.imageUrl, c.name, cardPriceShort(c), quantity = c.quantity, foil = c.foil,
                            onLongClick = { onLongPress(c) }, selected = c.id in selected, onClick = { onOpen(c) },
                        )
                    } else if (picking) {
                        PriceRow(c, c.id in selected, slotOf(c), week[c.id], dense, onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                    } else {
                        SwipeRow(onMove = { onSwipeMove(c) }, onSell = { onSwipeSell(c) }) {
                            PriceRow(c, false, slotOf(c), week[c.id], dense, onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                        }
                    }
                    if (c.id in fresh) FreshDot(Modifier.align(if (grid) Alignment.TopStart else Alignment.CenterStart).padding(if (grid) 6.dp else 0.dp))
                    }
                }
            }
        }
        // The current section stays at the top while its cards scroll under it.
        val p = pinned
        if (p >= 0 && p < groups.size) {
            SectionHead(
                groups[p].first, counts[p],
                Modifier.fillMaxWidth().background(Palette.Background).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        if (jumpBar) {
            LetterRail(
                rail, Modifier.align(Alignment.CenterEnd),
                onLetter = { railLetter = it },
                onJump = { index -> scope.launch { state.scrollToItem(index) } },
            )
        }
        railLetter?.let { letter ->
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Text(letter, style = MaterialTheme.typography.headlineMedium, color = Palette.Text) }
        }
    }
    }
}

/** The "new since last time" mark: a small accent dot. */
@Composable
private fun FreshDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Palette.Accent)
            .semantics { contentDescription = "New since last time" },
    )
}

/** A section title with its card count. */
@Composable
private fun SectionHead(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    }
}

/** A-Z down the right edge: touch or drag to jump to a letter. Letters with no cards are dimmed and jump to the next one. */
@Composable
private fun LetterRail(entries: List<Pair<String, Int>>, modifier: Modifier, onLetter: (String?) -> Unit, onJump: (Int) -> Unit) {
    var heightPx by remember { mutableIntStateOf(1) }
    val present = remember(entries) { entries.map { it.first }.toSet() }
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .width(26.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(entries) {
                var last: String? = null
                fun pick(y: Float) {
                    val letter = JumpBar.letterAt(y, heightPx.toFloat())
                    if (letter != last) {
                        last = letter
                        onLetter(letter)
                        JumpBar.firstIndex(entries, letter)?.let(onJump)
                        com.mtgscanner.util.Haptics.tick()
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    last = null
                    onLetter(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JumpBar.LETTERS.forEach { letter ->
            Text(letter, fontSize = 10.sp, lineHeight = 11.sp, color = if (letter in present) Palette.Muted else Palette.Muted.copy(alpha = 0.35f))
        }
    }
}

/** Swipe right to move a card to another box, left to list it for sale (with Undo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(onMove: () -> Unit, onSell: () -> Unit, content: @Composable () -> Unit) {
    // One swipe can confirm more than once; act on the first only.
    val last = remember { longArrayOf(0L) }
    // The swipe state keeps its first callback, so read the row's latest actions (and card) through these.
    val move by rememberUpdatedState(onMove)
    val sell by rememberUpdatedState(onSell)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        val now = android.os.SystemClock.uptimeMillis()
        if (v != SwipeToDismissBoxValue.Settled && now - last[0] > 600) {
            last[0] = now
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> move()
                SwipeToDismissBoxValue.EndToStart -> sell()
                else -> Unit
            }
        }
        // Always spring back: the list updates itself, and a move asks for the box first.
        false
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(Corners.Medium))
                    .background(if (toEnd) Palette.Accent.copy(alpha = 0.25f) else Palette.Gold.copy(alpha = 0.22f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                if (toEnd) {
                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null, tint = Palette.AccentText)
                    Spacer(Modifier.width(8.dp))
                    Text("Move", color = Palette.AccentText)
                } else {
                    Text("Sell", color = Palette.Gold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Sell, null, tint = Palette.Gold)
                }
            }
        },
    ) { Box(Modifier.background(Palette.Background)) { content() } }
}

/** A card's thumbnail; foils get a moving sheen. */
@Composable
private fun Thumb(c: CollectionCard, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    // Holding the picture shows it big; holding the rest of the row still starts picking.
    Box(Modifier.width(width).clip(CardShape).foilShimmer(c.foil)) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth(), zoom = true, onTap = onClick)
    }
}

private val SET_COL = 48.dp
private val USD_COL = 64.dp

/** The price column, wider with a larger system font so prices don't clip. */
@Composable
private fun usdCol(): Dp = USD_COL * LocalDensity.current.fontScale.coerceAtLeast(1f)
private val WEEK_COL = 52.dp
private val COL_GAP = 8.dp

// Row text styles, made once rather than for every row.
private val Mono11 = monoStyle(11.sp)
private val Mono12 = monoStyle(12.sp)
private val Mono14 = monoStyle(14.sp)

/** Each card's change per copy since a week ago, for the cards that have that much history. */
internal suspend fun weekChanges(repo: CollectionRepository, cards: List<CollectionCard>): Map<Long, Double> {
    if (cards.isEmpty()) return emptyMap()
    val weekAgo = java.time.LocalDate.now().minusDays(WeekChange.DAYS)
    val oldest = weekAgo.minusDays(WeekChange.SLACK_DAYS)
    val history = PriceTrends.history(repo, cards.mapTo(HashSet()) { it.scryfallId })
    val out = HashMap<Long, Double>()
    for (c in cards) {
        val change = WeekChange.change(c.nearMintPrice(), historyPairs(history[c.scryfallId].orEmpty(), c.foil), weekAgo.toString(), oldest.toString())
        if (change != null) out[c.id] = change
    }
    return out
}

/** Price history is in dollars, so the 7d column only shows with dollar prices. */
private fun weekColumn() = AppSettings.currency != PriceCurrency.EUR

/** One copy's price as a plain number; the header says which currency. */
private fun rowPrice(c: CollectionCard): String =
    WeekChange.amount(if (AppSettings.currency == PriceCurrency.EUR) c.unitEur() else c.unitPrice())

/** Set / Card / USD / 7d over the list, with a rule under it. [end] lines the numbers up with the rows'. */
@Composable
private fun ListHeader(end: Dp) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = end, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(COL_GAP),
        ) {
            val style = MaterialTheme.typography.labelSmall
            Text("Set", style = style, color = Palette.Muted, maxLines = 1, modifier = Modifier.width(SET_COL))
            Text("Card", style = style, color = Palette.Muted, maxLines = 1, modifier = Modifier.weight(1f))
            if (AppSettings.showPrices) {
                Text(
                    if (AppSettings.currency == PriceCurrency.EUR) "EUR" else "USD",
                    style = style, color = Palette.Muted, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(usdCol()),
                )
                if (weekColumn()) Text("7d", style = style, color = Palette.Muted, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(WEEK_COL))
            }
        }
        RuleLine()
    }
}

/**
 * A card in the list's columns: set symbol and code, then the name with a color-identity dot (and the
 * thumbnail unless [dense]), its price and its change over the week. A hairline runs under each row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PriceRow(
    c: CollectionCard, selected: Boolean, slot: String?, week: Double?, dense: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.SurfaceHigh else Palette.Background)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(RuleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = if (dense) 44.dp else 52.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(COL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(SET_COL), verticalAlignment = Alignment.CenterVertically) {
            SetMark(c.setCode, c.rarity)
            Spacer(Modifier.width(4.dp))
            Text(c.setCode.uppercase(), style = Mono11, color = Palette.Muted, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
        if (!dense) Thumb(c, 30.dp, onClick)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(c.colorIdentity)
                Spacer(Modifier.width(8.dp))
                Text(
                    c.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (c.quantity > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("×${c.quantity}", style = Mono12, color = Palette.Muted, maxLines = 1)
                }
                if (c.foil) { Spacer(Modifier.width(6.dp)); FoilTag() }
            }
            if (!dense) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.pinned) Icon(Icons.Filled.PushPin, "Stays in this box", tint = Palette.Icon, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                    Text(
                        listOfNotNull("#${c.collectorNumber}", slot).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (slot != null) {
                Text(
                    slot, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        if (AppSettings.showPrices) {
            val price = rowPrice(c)
            Text(
                price, style = if (price.length > 7) Mono12 else Mono14, color = Palette.Text,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(usdCol()),
            )
            val change = week?.let { WeekChange.label(it) }.orEmpty()
            if (weekColumn()) Text(
                change, style = if (change.length > 6) Mono11 else Mono12, color = Palette.Muted,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(WEEK_COL),
            )
        }
    }
}

/** A binder as its pages: 9 pockets each, in slot order. Filters don't apply, so it matches the real binder. */
@Composable
internal fun BinderPages(cards: List<CollectionCard>, unslotted: Int, perCopy: Boolean, onNumber: () -> Unit, onOpen: (CollectionCard) -> Unit) {
    // With a pocket per copy, a card fills one pocket for each copy.
    val bySlot = remember(cards, perCopy) { Binder.pockets(cards, { it.binderSlot }, { Binder.span(it.quantity, perCopy) }) }
    val pages = bySlot.keys.maxOrNull()?.let { Binder.page(it) } ?: 0
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        if (unslotted > 0 && pages > 0) item(key = "number") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$unslotted to number", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                TextButton(onClick = onNumber) { Text("Number slots") }
            }
        }
        if (pages == 0) item(key = "empty") {
            EmptyState("No pages yet", action = "Number slots", onAction = onNumber)
        }
        items(pages, key = { "p$it" }) { i ->
            val page = i + 1
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Page $page", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 6.dp))
                for (row in 0 until 3) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 3) {
                            val slot = (page - 1) * Binder.POCKETS + row * 3 + col + 1
                            val c = bySlot[slot]
                            Box(Modifier.weight(1f)) {
                                if (c != null) {
                                    Box(Modifier.clip(CardShape).clickable { onOpen(c) }) {
                                        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth(), zoom = true)
                                    }
                                } else {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(CardShape)
                                            .border(1.dp, Palette.Outline, CardShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("${Binder.pocket(slot)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The card id under [pos] in the grid, or null over a header or a gap. */
private fun keyAt(state: LazyGridState, pos: Offset): Long? =
    state.layoutInfo.visibleItemsInfo.firstOrNull { i ->
        pos.x >= i.offset.x && pos.x < i.offset.x + i.size.width && pos.y >= i.offset.y && pos.y < i.offset.y + i.size.height
    }?.key as? Long

/**
 * Hold a card (its own long press selects it), then drag without lifting: every card between it and the finger
 * is selected, like a phone's photo gallery. Near the top or bottom edge the list scrolls along.
 */
private fun Modifier.dragToSelect(
    state: LazyGridState,
    order: List<Long>,
    selected: () -> Set<Long>,
    onSelect: (Set<Long>) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier = pointerInput(order) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        // Lifted or moved before the long press: an ordinary tap or scroll.
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + 60) {
            var ended = false
            while (!ended) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val ch = e.changes.firstOrNull { it.id == down.id }
                ended = ch == null || !ch.pressed || (ch.position - down.position).getDistance() > slop
            }
            true
        }
        if (early != null) return@awaitEachGesture
        val startKey = keyAt(state, down.position) ?: return@awaitEachGesture
        val start = order.indexOf(startKey)
        if (start < 0) return@awaitEachGesture
        var base: Set<Long>? = null
        var lastAt = start
        val edge = 56.dp.toPx()
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            ch.consume()
            val key = keyAt(state, ch.position)
            val at = key?.let { order.indexOf(it) } ?: -1
            if (at >= 0 && at != lastAt && (key != startKey || base != null)) {
                // The selection as it was when the drag began (the held card already in it).
                val b = base ?: selected().also { base = it }
                onSelect(b + order.subList(minOf(start, at), maxOf(start, at) + 1))
                com.mtgscanner.util.Haptics.step()
                lastAt = at
            }
            when {
                ch.position.y < edge -> scope.launch { state.scrollBy(-28f) }
                ch.position.y > size.height - edge -> scope.launch { state.scrollBy(28f) }
            }
        }
    }
}

/** Scroll positions of lists left and come back to, while the app runs. */
internal object ScrollMemory {
    private val spots = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()
    fun get(key: String): Pair<Int, Int>? = spots[key]
    fun put(key: String, index: Int, offset: Int) { spots[key] = index to offset }
}

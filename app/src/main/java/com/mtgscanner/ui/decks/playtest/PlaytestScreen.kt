package com.mtgscanner.ui.decks.playtest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Motion
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums

/** A card picked for its action sheet: which side it's on and its id (the zone is looked up fresh each time). */
internal data class Picked(val side: Int, val id: Int)

/**
 * Decks › ⋮ › Playtest: pick an opponent, then play the deck on a dark table. Tap a card on the battlefield to tap
 * or untap it; tap any other card, or press and hold one on the battlefield, for everything else it can do.
 */
@Composable
fun PlaytestScreen(
    deckId: Long,
    onBack: () -> Unit,
    /** A game already set up, for the screen pictures. */
    preview: Table? = null,
    /** With [preview]: show it as the opening hand, before the keep. */
    previewOpening: Boolean = false,
) {
    com.mtgscanner.ui.components.KeepUpright()
    val vm: PlaytestViewModel = viewModel(key = "playtest$deckId", factory = AppViewModels.playtest(deckId))
    if (preview != null) remember(preview) { vm.show(preview, previewOpening); true }
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.message) { ui.message?.let { Undo.offer(it); vm.messageShown() } }
    val table = ui.table
    when {
        ui.loading -> Box(Modifier.fillMaxSize().background(Palette.Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Palette.Text)
        }
        table == null -> PlaytestSetup(ui, vm, onBack)
        ui.opening -> {
            BackHandler { vm.restart() }
            OpeningHand(table, vm, onBack)
        }
        else -> {
            BackHandler { vm.restart() }
            PlayTable(table, ui, vm, onBack)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayTable(t: Table, ui: PlaytestUi, vm: PlaytestViewModel, onBack: () -> Unit) {
    // You play side 0; when you also play the opponent, the bottom half follows whoever's turn it is.
    val me = if (ui.pilot == HandPilot) t.active else 0
    val them = if (t.hasOpponent) 1 - me else null
    var picked by remember { mutableStateOf<Picked?>(null) }
    var zoneOpen by remember { mutableStateOf<Pair<Int, Zone>?>(null) }
    var tokens by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Stage).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.s1), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Leave playtest", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Turn ${t.turn}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (t.hasOpponent) Text(
                    if (t.active == 0) "Your turn" else "${t.sides[t.active].name}'s turn",
                    style = MaterialTheme.typography.labelMedium, color = Palette.OnDarkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = vm::undo, enabled = ui.history.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Outlined.Undo, "Undo", tint = if (ui.history.isNotEmpty()) Color.White else Palette.OnDarkMuted.copy(alpha = 0.4f))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Mulligan") }, onClick = { menu = false; vm.mulligan(me) })
                    DropdownMenuItem(text = { Text("Shuffle library") }, onClick = { menu = false; vm.shuffle(me) })
                    if (t.log.isNotEmpty()) DropdownMenuItem(text = { Text("What happened") }, onClick = { menu = false; log = true })
                    DropdownMenuItem(text = { Text("New game") }, onClick = { menu = false; vm.restart() })
                }
            }
        }

        if (them != null) {
            SideBar(t.sides[them], them, vm, onZone = { zoneOpen = them to it }, compact = true)
            Battlefield(t.sides[them], them, small = true, vm = vm, onPick = { picked = it }, modifier = Modifier.weight(1f))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        }
        Battlefield(t.sides[me], me, small = false, vm = vm, onPick = { picked = it }, modifier = Modifier.weight(if (them != null) 1.4f else 1f))
        t.log.lastOrNull()?.let {
            Text(
                it, style = MaterialTheme.typography.labelMedium, color = Palette.OnDarkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { log = true }.padding(horizontal = Space.s4, vertical = Space.s1),
            )
        }
        SideBar(t.sides[me], me, vm, onZone = { zoneOpen = me to it }, compact = false, onPick = { picked = it })
        HandRow(t.sides[me], me, onPick = { picked = it }, onPlay = { p ->
            // Permanents go onto the battlefield; instants and sorceries are cast, so they go to the graveyard.
            val to = if (p.card.isPermanent || p.card.typeLine.isBlank()) Zone.BATTLEFIELD else Zone.GRAVEYARD
            com.mtgscanner.util.Haptics.tick()
            vm.act { Playtest.move(it, me, p.id, to) }
        })
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            StageButton("Token", Modifier.weight(1f)) { tokens = true }
            StageButton("Draw", Modifier.weight(1f), enabled = t.sides[me][Zone.LIBRARY].isNotEmpty()) { vm.act { Playtest.draw(it, me) } }
            GradientButton("Next turn", vm::nextTurn, Modifier.weight(1.4f))
        }
    }

    picked?.let { p ->
        val found = t.sides.getOrNull(p.side)?.find(p.id)
        if (found == null) picked = null
        else CardActionsSheet(t.sides[p.side], p.side, found.first, found.second, vm, onDismiss = { picked = null })
    }
    zoneOpen?.let { (side, zone) ->
        ZoneSheet(t.sides[side], side, zone, vm, onPick = { picked = Picked(side, it) }, onDismiss = { zoneOpen = null })
    }
    if (tokens) TokenSheet(onMake = { card, n -> vm.act { Playtest.token(it, me, card, n) }; tokens = false }, onDismiss = { tokens = false })
    if (log) LogSheet(t.log, onDismiss = { log = false })
}

/** Life with − and +, then the zones as counts; tap a zone to look through it. */
@Composable
private fun SideBar(s: Side, index: Int, vm: PlaytestViewModel, onZone: (Zone) -> Unit, compact: Boolean, onPick: (Picked) -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) Text(
            s.name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Space.s2).width(96.dp),
        )
        LifeCounter(s.life, onChange = { d -> vm.act { Playtest.life(it, index, d) } })
        if (compact) ZoneChip("Hand", s[Zone.HAND].size) { onZone(Zone.HAND) }
        ZoneChip("Library", s[Zone.LIBRARY].size) { onZone(Zone.LIBRARY) }
        ZoneChip("Graveyard", s[Zone.GRAVEYARD].size) { onZone(Zone.GRAVEYARD) }
        if (s[Zone.EXILE].isNotEmpty()) ZoneChip("Exile", s[Zone.EXILE].size) { onZone(Zone.EXILE) }
        // The commander sits in the bar while it's in the command zone.
        s[Zone.COMMAND].forEach { p ->
            Box(Modifier.padding(horizontal = Space.s1)) {
                PieceTile(p, 36.dp, onTap = { onPick(Picked(index, p.id)) }, onHold = { onPick(Picked(index, p.id)) })
            }
        }
    }
}

@Composable
private fun LifeCounter(life: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(-1) }) { Text("−", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        Text("$life", style = MaterialTheme.typography.titleLarge.merge(TabularNums), color = Color.White)
        TextButton(onClick = { onChange(1) }) { Text("+", color = Color.White, style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ZoneChip(label: String, count: Int, onClick: () -> Unit) {
    Column(
        Modifier.padding(horizontal = Space.s1).clip(RoundedCornerShape(Corners.Medium)).background(Palette.StagePanel)
            .clickable(onClick = onClick).padding(horizontal = Space.s3, vertical = Space.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.OnDarkMuted)
    }
}

/**
 * A side's battlefield in three rows: creatures nearest the opponent, then artifacts, enchantments and other
 * permanents, then lands nearest its player ([Lane]). The opponent's side is upside down, so their creatures face
 * yours. Each row fits its cards without scrolling. Tap a card to tap or untap it, hold it for more.
 */
@Composable
private fun Battlefield(s: Side, index: Int, small: Boolean, vm: PlaytestViewModel, onPick: (Picked) -> Unit, modifier: Modifier) {
    val all = s[Zone.BATTLEFIELD]
    val byLane = all.groupBy(Lane::of)
    val lanes = if (small) Lane.entries.reversed() else Lane.entries
    val most = if (small) 44.dp else 56.dp
    Column(modifier.fillMaxWidth().padding(horizontal = Space.s2)) {
        lanes.forEachIndexed { i, lane ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(vertical = Space.s1), contentAlignment = Alignment.CenterStart) {
                val cards = byLane[lane].orEmpty()
                // As big as the row lets it be, up to the usual size.
                val w = minOf(most, maxHeight / 1.4f).coerceAtLeast(24.dp)
                if (cards.isEmpty()) {
                    if (!small) Text(
                        if (all.isEmpty() && lane == Lane.OTHERS) "Drag a card up from your hand to play it" else lane.label,
                        style = MaterialTheme.typography.labelSmall, color = Palette.OnDarkMuted.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                } else FitRow(Stacks.of(cards), w, id = { it.first().id }) { pile ->
                    val p = pile.first()
                    Box {
                        PieceTile(
                            p, w,
                            // A pile taps or untaps one of its lands at a time.
                            onTap = { com.mtgscanner.util.Haptics.tick(); vm.act { Playtest.toggleTap(it, index, p.id) } },
                            onHold = { onPick(Picked(index, p.id)) },
                        )
                        if (pile.size > 1) Text(
                            "×${pile.size}", style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(Space.s1).clip(RoundedCornerShape(Corners.Small))
                                .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = Space.s1),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Your hand, all of it on screen: the cards overlap when there are many. Tap a card for what it can do, or drag it
 * up onto the battlefield to play it.
 */
@Composable
private fun HandRow(s: Side, index: Int, onPick: (Picked) -> Unit, onPlay: (Piece) -> Unit) {
    val hand = s[Zone.HAND]
    var dragging by remember { mutableStateOf<Int?>(null) }
    val playAt = with(LocalDensity.current) { 64.dp.toPx() }
    Box(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(horizontal = Space.s2, vertical = Space.s1), contentAlignment = Alignment.CenterStart) {
        if (hand.isEmpty()) Text("No cards in hand", style = MaterialTheme.typography.bodySmall, color = Palette.OnDarkMuted, modifier = Modifier.padding(Space.s4))
        FitRow(hand, 76.dp, id = { it.id }, z = { if (it.id == dragging) 1f else 0f }) { p ->
            var drag by remember(p.id) { mutableStateOf(Offset.Zero) }
            PieceTile(
                p, 76.dp, onTap = { onPick(Picked(index, p.id)) }, onHold = { onPick(Picked(index, p.id)) },
                modifier = Modifier
                    .graphicsLayer { translationX = drag.x; translationY = drag.y }
                    .pointerInput(p.id) {
                        detectDragGestures(
                            onDragStart = { dragging = p.id },
                            onDragEnd = {
                                if (drag.y < -playAt) onPlay(p)
                                drag = Offset.Zero; dragging = null
                            },
                            onDragCancel = { drag = Offset.Zero; dragging = null },
                        ) { change, d -> change.consume(); drag += d }
                    },
            )
        }
    }
}

/**
 * One card: its picture (or a plain face for a token or a face-down card), dimmed and marked when tapped, with its
 * counters and changed power and toughness written on it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PieceTile(p: Piece, width: Dp, onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val height = width * 1.4f
    Box(
        modifier.size(width, height).combinedClickable(onClick = onTap, onLongClick = onHold, onClickLabel = p.card.name),
        contentAlignment = Alignment.Center,
    ) {
        // A tapped card stays upright so its name stays readable; it dims and says "Tapped" instead of turning sideways.
        val turn by animateFloatAsState(if (p.tapped) 1f else 0f, tween(Motion.base, easing = Motion.ease), label = "tap")
        Box(Modifier.size(width, height).graphicsLayer { alpha = 1f - 0.45f * turn }) {
            when {
                p.faceDown -> PlainFace("Face down", null, Modifier.fillMaxSize())
                p.card.imageUrl == null -> PlainFace(p.card.name, p.card.typeLine.substringAfter("— ", "").ifBlank { null }, Modifier.fillMaxSize())
                else -> CardImage(p.card.imageUrl, Modifier.fillMaxSize())
            }
        }
        val badges = buildList {
            val net = (p.counters[Piece.PLUS] ?: 0) - (p.counters[Piece.MINUS] ?: 0)
            if (p.card.isCreature && (net != 0 || p.card.imageUrl == null)) {
                val pw = p.power; val tg = p.toughness
                if (pw != null && tg != null) add("$pw/$tg")
            }
            p.counters.filterKeys { it != Piece.PLUS && it != Piece.MINUS }.forEach { (k, v) -> add("${k.take(5)} $v") }
        }
        if (badges.isNotEmpty()) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.s1), horizontalAlignment = Alignment.CenterHorizontally) {
            badges.forEach { b ->
                Text(
                    b, style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Color.White, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = Space.s1),
                )
            }
        }
        val tags = listOfNotNull("Token".takeIf { p.card.token }, "Tapped".takeIf { p.tapped })
        if (tags.isNotEmpty()) Text(
            tags.joinToString(" · "), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Palette.OnDarkMuted,
            modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = Space.s1),
        )
    }
}

/** A card face with just words on it, for tokens, face-down cards and cards without a picture. */
@Composable
internal fun PlainFace(title: String, sub: String?, modifier: Modifier) {
    Column(
        modifier.clip(CardShape).background(Palette.StagePanel).padding(Space.s1),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A long word that won't fit a small card breaks with a hyphen, "Llano-war", not mid-word with none.
        Text(title, style = MaterialTheme.typography.labelSmall.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph), color = Color.White, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (sub != null && !sub.equals(title, true)) Text(sub, style = MaterialTheme.typography.labelSmall, color = Palette.OnDarkMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A flat button for the dark table: panel fill and white text. */
@Composable
internal fun StageButton(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.StagePanel)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Palette.OnDarkMuted)
    }
}

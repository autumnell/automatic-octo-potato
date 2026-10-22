package com.mtgscanner.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.game.Game
import com.mtgscanner.game.GameState
import com.mtgscanner.game.Player
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.mtgscanner.ui.theme.Corners

/** The table color during the day: a warm light cream. */
private val DAY_TABLE = Color(0xFFEFE8D8)

/** Turns a panel a quarter turn, swapping its width and height so it fills its slot. */
fun Modifier.rotateLayout(degrees: Int): Modifier = when ((degrees % 360 + 360) % 360) {
    90, 270 -> this.layout { measurable, c ->
        val p = measurable.measure(Constraints(c.minHeight, c.maxHeight, c.minWidth, c.maxWidth))
        layout(p.height, p.width) {
            p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = degrees.toFloat() }
        }
    }
    180 -> this.rotate(180f)
    else -> this
}

/**
 * The table: one panel per player, turned to face them. Tap the left half to lose life and the
 * right half to gain it; hold for ten, or swipe up and down. The middle button opens the game menu.
 */
@Composable
fun GamePlayScreen(onExit: () -> Unit) {
    val vm = gameViewModel()
    val game by vm.game.collectAsStateWithLifecycle()
    val g = game
    val style by vm.style.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var playerSheet by remember { mutableStateOf<Int?>(null) }

    // Keep the screen on during a game.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; vm.flush() }
    }

    // Full screen at the table: hide the status and navigation bars (swipe from an edge to see them).
    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(activity) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // Back opens the game menu instead of leaving the table by accident.
    BackHandler(enabled = !menu && playerSheet == null && !ending) { menu = true }

    // Day lights the table up; night (or not tracked) keeps it dark.
    val tableColor by animateColorAsState(
        if (g?.dayNight == "day") DAY_TABLE else Color.Black, animationSpec = tween(700), label = "table",
    )

    if (g == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    // Taps near the screen edges are life changes, not the system back swipe.
    Box(Modifier.fillMaxSize().background(tableColor).systemBarsPadding().systemGestureExclusion()) {
        val panel: @Composable (Int, Int, Modifier) -> Unit = { i, rot, mod ->
            PlayerPanel(
                g, i, rot, style, mod,
                onLife = { d -> vm.life(i, d) },
                onCommander = { from, d -> vm.commanderDamage(i, from, d, partner = false) },
                onMore = { playerSheet = i },
            )
        }
        val gap = 4.dp
        when (g.players.size) {
            1 -> panel(0, 0, Modifier.fillMaxSize().padding(gap))
            2 -> Column(Modifier.fillMaxSize()) {
                panel(0, 180, Modifier.weight(1f).fillMaxWidth().padding(gap))
                panel(1, 0, Modifier.weight(1f).fillMaxWidth().padding(gap))
            }
            3 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1.4f)) {
                    panel(0, 90, Modifier.weight(1f).fillMaxHeight().padding(gap))
                    panel(1, 270, Modifier.weight(1f).fillMaxHeight().padding(gap))
                }
                panel(2, 0, Modifier.weight(1f).fillMaxWidth().padding(gap))
            }
            4 -> Row(Modifier.fillMaxSize()) {
                // Clockwise from the top left: 1 and 4 on the left side, 2 and 3 on the right.
                Column(Modifier.weight(1f)) {
                    panel(0, 90, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    panel(3, 90, Modifier.weight(1f).fillMaxWidth().padding(gap))
                }
                Column(Modifier.weight(1f)) {
                    panel(1, 270, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    panel(2, 270, Modifier.weight(1f).fillMaxWidth().padding(gap))
                }
            }
            5 -> Column(Modifier.fillMaxSize()) {
                // Clockwise: 1 top left, 2 and 3 down the right, 4 across the bottom, 5 bottom left.
                Row(Modifier.weight(2.4f)) {
                    Column(Modifier.weight(1f)) {
                        panel(0, 90, Modifier.weight(1f).fillMaxWidth().padding(gap))
                        panel(4, 90, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    }
                    Column(Modifier.weight(1f)) {
                        panel(1, 270, Modifier.weight(1f).fillMaxWidth().padding(gap))
                        panel(2, 270, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    }
                }
                panel(3, 0, Modifier.weight(1f).fillMaxWidth().padding(gap))
            }
            else -> Row(Modifier.fillMaxSize()) {
                // Clockwise: 1, 2, 3 is the top left, top right, middle right... 6 is middle left.
                Column(Modifier.weight(1f)) {
                    panel(0, 90, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    panel(5, 90, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    panel(4, 90, Modifier.weight(1f).fillMaxWidth().padding(gap))
                }
                Column(Modifier.weight(1f)) {
                    panel(1, 270, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    panel(2, 270, Modifier.weight(1f).fillMaxWidth().padding(gap))
                    panel(3, 270, Modifier.weight(1f).fillMaxWidth().padding(gap))
                }
            }
        }

        // The middle: the game menu and undo. Day or night shows as a small chip once it's in play.
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(2.dp, Palette.Accent, CircleShape)
                        .clickable(onClickLabel = "Open game menu") { menu = true }
                        // Read out as what it is, not just "T3".
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (g.active != null) "Game menu, turn ${g.turn}" else "Game menu"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (g.active != null) Text("T${g.turn}", color = Palette.Accent, fontWeight = FontWeight.Bold)
                    else Icon(Icons.Outlined.MoreHoriz, "Game menu", tint = Palette.Accent)
                }
                QuickButton(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = g.undoStack.isNotEmpty()) { vm.undo() }
            }
            if (g.dayNight != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, Palette.Gold, RoundedCornerShape(Corners.Small))
                        .clickable { vm.update { Game.dayNight(it, nextDayNight(it.dayNight)) } }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(dayNightIcon(g.dayNight), null, tint = Palette.Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(dayNightLabel(g.dayNight), color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        g.winner?.let { w ->
            WinnerBanner(g.players[w].name, Modifier.align(Alignment.Center).padding(top = 140.dp), onRestart = vm::restart,
                onDone = { vm.end(save = true, winner = w); onExit() })
        }
    }

    if (menu) MenuOverlay(
        g, vm, onDismiss = { menu = false },
        onEnd = { menu = false; ending = true },
        onLeave = { menu = false; onExit() },
    )
    playerSheet?.let { i -> PlayerOverlay(g, i, vm, onDismiss = { playerSheet = null }) }
    if (ending) EndGameDialog(g, onDismiss = { ending = false }) { save, winner ->
        ending = false
        vm.end(save, winner)
        onExit()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerPanel(
    g: GameState, i: Int, rotation: Int, style: TableStyle, modifier: Modifier,
    onLife: (Int) -> Unit, onCommander: (from: Int, delta: Int) -> Unit, onMore: () -> Unit,
) {
    val p = g.players[i]
    val out = g.outReason(i)
    val haptics = LocalHapticFeedback.current
    val color = PLAYER_COLORS[p.color % PLAYER_COLORS.size]
    val lifeNow by rememberUpdatedState(onLife)

    // The running change, like "−7": it adds up while life keeps changing and fades after a pause.
    var runBase by remember { mutableStateOf<Int?>(null) }
    var prevLife by remember { mutableIntStateOf(p.life) }
    var shownRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(p.life) {
        if (p.life == prevLife) return@LaunchedEffect
        val ev = g.history.lastOrNull()
        // Only this player's own life changes count, not an undo or a restart.
        val mine = ev != null && ev.player == i && (ev.what == "Life" || ev.what.startsWith("Commander damage")) &&
            System.currentTimeMillis() - ev.at < 1500
        if (mine) {
            val base = runBase ?: prevLife
            runBase = base
            shownRun = p.life - base
        } else {
            runBase = null
        }
        prevLife = p.life
        if (mine) {
            delay(RUN_PAUSE_MS)
            runBase = null
        }
    }
    val runVisible = runBase != null && shownRun != 0
    val runAlpha by animateFloatAsState(
        if (runVisible) 1f else 0f, animationSpec = tween(if (runVisible) 120 else 600), label = "run",
    )

    val shape = RoundedCornerShape(Corners.Medium)
    val back = when (style) {
        TableStyle.BOLD -> Modifier.background(color)
        TableStyle.GRADIENT -> Modifier.background(Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.65f))))
        TableStyle.DARK -> Modifier.background(Color(0xFF15161A)).border(3.dp, color, shape)
    }
    // One life point per step of drag.
    val stepPx = with(LocalDensity.current) { SWIPE_STEP.toPx() }
    // Set by a hold, so sliding the finger afterwards doesn't count the swipe too. Cleared on each new touch.
    var held by remember { mutableStateOf(false) }
    Box(modifier.clip(shape).then(back)) {
        if (p.art != null) AsyncImage(
            p.art, null, Modifier.fillMaxSize().rotateLayout(rotation), contentScale = ContentScale.Crop, alpha = 0.55f,
        )
        if (out != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        BoxWithConstraints(Modifier.fillMaxSize().rotateLayout(rotation)) {
            // Smaller panels (5 or 6 players) get a smaller number.
            val lifeSize = (minOf(maxHeight.value * 0.36f, maxWidth.value * 0.3f)).coerceIn(34f, 72f).sp
            val active = g.active == i
            if (active) Box(Modifier.fillMaxSize().border(4.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(Corners.Medium)))
            // Left half loses, right half gains. Hold for ten. Swipe up or down to count one by one;
            // the panel is turned to face its player, so "up" is up for them.
            Row(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        held = false
                    }
                }.pointerInput(stepPx) {
                    var acc = 0f
                    var skip = false
                    detectVerticalDragGestures(onDragStart = { acc = 0f; skip = held }) { change, dy ->
                        change.consume()
                        if (skip) return@detectVerticalDragGestures
                        acc -= dy
                        while (acc >= stepPx) {
                            acc -= stepPx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lifeNow(1)
                        }
                        while (acc <= -stepPx) {
                            acc += stepPx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lifeNow(-1)
                        }
                    }
                },
            ) {
                listOf(-1, 1).forEach { sign ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().combinedClickable(
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onLife(sign) },
                            onLongClick = { held = true; haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLife(sign * HOLD_STEP) },
                        ),
                        contentAlignment = if (sign < 0) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Text(if (sign < 0) "−" else "+", color = Color.White.copy(alpha = 0.45f), fontSize = (lifeSize.value * 0.45f).sp,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                // The running change sits above the life total without pushing it down.
                Box(
                    Modifier
                        .layout { m, c ->
                            val pl = m.measure(c.copy(minHeight = 0))
                            layout(pl.width, 0) { pl.place(0, -pl.height) }
                        }
                        .graphicsLayer { alpha = runAlpha }
                        .clip(RoundedCornerShape(Corners.Medium))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        if (shownRun > 0) "+$shownRun" else "−${-shownRun}",
                        color = if (shownRun > 0) Color(0xFFB9F6CA) else Color(0xFFFFCDD2),
                        fontSize = (lifeSize.value * 0.55f).sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text("${p.life}", color = Color.White, fontSize = lifeSize, fontWeight = FontWeight.Bold)
                Text(p.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (out != null) Text(out, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            // Other counters along the top, shown only when they matter. Not scrollable, so taps and
            // swipes on them still change life.
            FlowRow(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (g.monarch == i) Badge("Monarch", com.mtgscanner.ui.components.ManaIcons.Monarch, gold = true)
                if (g.initiative == i) Badge("Initiative", com.mtgscanner.ui.components.ManaIcons.Initiative, gold = true)
                if (p.poison > 0) Badge("${p.poison}", com.mtgscanner.ui.components.ManaIcons.Poison)
                // Commanders show as dots at the bottom; with partners the highest still shows here.
                val maxCmd = p.commanderDamage.maxOrNull() ?: 0
                if (g.format.commanderDamage && g.partners && maxCmd > 0) Badge("$maxCmd", com.mtgscanner.ui.components.ManaIcons.Commander)
                if (p.commanderCasts > 0) Badge("Tax +${p.commanderTax}")
                if (p.partnerCasts > 0) Badge("Partner +${p.partnerTax}")
                if (p.energy > 0) Badge("${p.energy}", com.mtgscanner.ui.components.ManaIcons.Energy)
                if (p.experience > 0) Badge("XP ${p.experience}")
                if (p.rad > 0) Badge("${p.rad}", com.mtgscanner.ui.components.ManaIcons.Rad)
            }
            // Commander damage: one dot per opponent, in their color. Tap +1, hold −1.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (g.format.commanderDamage && g.players.size > 1) {
                    g.players.forEachIndexed { from, o ->
                        if (from != i) CommanderDot(
                            color = PLAYER_COLORS[o.color % PLAYER_COLORS.size],
                            damage = p.damageFrom(from),
                            name = o.name,
                            onAdd = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCommander(from, 1) },
                            onRemove = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onCommander(from, -1) },
                        )
                    }
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Outlined.MoreHoriz, "${p.name}: counters and commander damage", tint = Color.White)
                }
            }
        }
    }
}

/** How much a held + or − changes life or a counter, on the panel and in the counter overlay. */
internal const val HOLD_STEP = 10

/** How long the running change stays after the last life change. */
private const val RUN_PAUSE_MS = 2000L

/** Drag distance for one life point. */
private val SWIPE_STEP = 24.dp

/** Commander damage from one opponent, in their color. A red ring at 21. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommanderDot(color: Color, damage: Int, name: String, onAdd: () -> Unit, onRemove: () -> Unit) {
    val lethal = damage >= GameState.COMMANDER_LIMIT
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (lethal) Color.Black else color)
            .border(if (lethal) 3.dp else 1.5.dp, if (lethal) Color(0xFFFF5252) else Color.White.copy(alpha = 0.8f), CircleShape)
            .semantics { contentDescription = "Commander damage from $name: $damage" }
            .combinedClickable(
                onClickLabel = "+1 from $name",
                onLongClickLabel = "−1 from $name",
                onLongClick = onRemove,
                onClick = onAdd,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (damage > 0) Text(
            "$damage",
            color = if (lethal) Color(0xFFFF5252) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A small round button beside the middle button. */
@Composable
private fun QuickButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.85f))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Badge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, gold: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Small)).background(if (gold) Palette.Gold.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun WinnerBanner(name: String, modifier: Modifier, onRestart: () -> Unit, onDone: () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Color.Black.copy(alpha = 0.85f)) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name wins!", color = Palette.Gold, style = MaterialTheme.typography.headlineSmall)
            Text("Tap Done or Play again to save.",
                color = Palette.OnDarkMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Row {
                TextButton(onClick = onRestart) { Text("Play again") }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}
/** Background art for a player's panel, picked from cards in your collection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtPicker(current: String?, vm: GameViewModel, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val choices by vm.art.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Background", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (current != null) TextButton(onClick = { onPick(null) }) { Text("Remove") }
        TextButton(onClick = { open = !open; if (open) vm.searchArt(query, refresh = true) }) { Text(if (open) "Close" else "Pick card art") }
    }
    if (open) {
        OutlinedTextField(
            query, { query = it; vm.searchArt(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your collection, like your commander") },
        )
        Spacer(Modifier.height(8.dp))
        if (choices.isEmpty()) Text("No cards with art found. Scan some cards first.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.take(24).forEach { c ->
                Column(Modifier.width(96.dp).clip(RoundedCornerShape(Corners.Small)).clickable { onPick(c.art); open = false }) {
                    AsyncImage(c.art, c.name, Modifier.fillMaxWidth().height(70.dp)
                        .border(if (c.art == current) 2.dp else 0.dp, Palette.Accent, RoundedCornerShape(Corners.Small)), contentScale = ContentScale.Crop)
                    Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Palette.Muted)
                }
            }
        }
    }
}

/** Ends a game and asks who won, for the stats. */
@Composable
fun EndGameDialog(g: GameState, onDismiss: () -> Unit, onEnd: (save: Boolean, winner: Int?) -> Unit) {
    var winner by remember { mutableStateOf(g.winner ?: g.players.indices.filter { g.outReason(it) == null }.singleOrNull()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(if (g.recorded) "End this game?" else "Who won?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (g.recorded) Text("It's already in your game stats.")
                else {
                    Text("Pick the winner to save the game to your stats.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    g.players.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { winner = i }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(winner == i, { winner = i })
                            Text(p.name)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { winner = null }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(winner == null, { winner = null })
                        Text("No winner (draw)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEnd(!g.recorded, winner) }) { Text(if (g.recorded) "End game" else "Save and end") }
        },
        dismissButton = {
            Row {
                if (!g.recorded) TextButton(onClick = { onEnd(false, null) }) { Text("Don't save") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

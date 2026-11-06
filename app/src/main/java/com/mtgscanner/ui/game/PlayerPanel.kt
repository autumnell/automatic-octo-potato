package com.mtgscanner.ui.game

import com.mtgscanner.ui.theme.Space
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
import com.mtgscanner.ui.theme.Motion

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlayerPanel(
    g: GameState, i: Int, rotation: Int, style: TableStyle, modifier: Modifier,
    onLife: (Int) -> Unit,
    onCommander: (from: Int, partner: Boolean, delta: Int) -> Unit,
    onPoison: (Int) -> Unit,
    onCounter: (kind: String, delta: Int) -> Unit,
    onPlayer: () -> Unit,
) {
    // ⋯ flips the panel over to its back: commander damage and counters. ✓ flips it back.
    var flipped by remember(g.startedAt) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (flipped) 180f else 0f, animationSpec = tween(Motion.slow, easing = Motion.ease), label = "flip")
    val density = LocalDensity.current.density
    Box(modifier.graphicsLayer { rotationY = turn; cameraDistance = 12f * density }) {
        if (turn > 90f) {
            PlayerBack(
                g, i, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(RoundedCornerShape(Corners.Medium)).rotateLayout(rotation),
                onCommander = onCommander, onPoison = onPoison, onCounter = onCounter,
                onPlayer = onPlayer, onDone = { flipped = false },
            )
        } else PanelFront(g, i, rotation, style, Modifier.fillMaxSize(), onLife, onFlip = { flipped = true })
    }
}

/** The front of a player's panel: life, name, the counters that matter, and ⋯ to flip it over. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PanelFront(
    g: GameState, i: Int, rotation: Int, style: TableStyle, modifier: Modifier,
    onLife: (Int) -> Unit, onFlip: () -> Unit,
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
        if (runVisible) 1f else 0f, animationSpec = tween(if (runVisible) Motion.fast else 600, easing = Motion.ease), label = "run",
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
                            modifier = Modifier.padding(horizontal = Space.s4))
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
                        .padding(horizontal = Space.s3),
                ) {
                    Text(
                        if (shownRun > 0) "+$shownRun" else "−${-shownRun}",
                        color = if (shownRun > 0) Color(0xFFB9F6CA) else Color(0xFFFFCDD2),
                        fontSize = (lifeSize.value * 0.55f).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text("${p.life}", color = Color.White, fontSize = lifeSize, fontWeight = FontWeight.SemiBold)
                Text(p.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (out != null) Text(out, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            // Other counters along the top, shown only when they matter. Not scrollable, so taps and
            // swipes on them still change life.
            FlowRow(
                Modifier.align(Alignment.TopCenter).padding(top = Space.s3, start = Space.s2, end = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Space.s1),
            ) {
                if (g.monarch == i) Badge("Monarch", com.mtgscanner.ui.components.ManaIcons.Monarch, gold = true)
                if (g.initiative == i) Badge("Initiative", com.mtgscanner.ui.components.ManaIcons.Initiative, gold = true)
                if (p.poison > 0) Badge("${p.poison}", com.mtgscanner.ui.components.ManaIcons.Poison)
                // The most commander damage from any one commander; the back of the panel has each.
                val maxCmd = p.commanderDamage.maxOrNull() ?: 0
                if (g.format.commanderDamage && maxCmd > 0) Badge("$maxCmd", com.mtgscanner.ui.components.ManaIcons.Commander)
                if (p.commanderCasts > 0) Badge("Tax +${p.commanderTax}")
                if (p.partnerCasts > 0) Badge("Partner +${p.partnerTax}")
                if (p.rad > 0) Badge("${p.rad}", com.mtgscanner.ui.components.ManaIcons.Rad)
            }
            IconButton(onClick = onFlip, modifier = Modifier.align(Alignment.BottomCenter)) {
                Icon(Icons.Outlined.MoreHoriz, "${p.name}: commander damage and counters", tint = Color.White)
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

@Composable
private fun Badge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, gold: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Small)).background(if (gold) Palette.Gold.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = Space.s2, vertical = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Space.s1))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

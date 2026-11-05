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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
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
                Spacer(Modifier.height(Space.s2))
                Row(
                    Modifier.clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, Palette.Gold, RoundedCornerShape(Corners.Small))
                        .clickable { vm.update { Game.dayNight(it, nextDayNight(it.dayNight)) } }
                        .padding(horizontal = Space.s3, vertical = Space.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(dayNightIcon(g.dayNight), null, tint = Palette.Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Space.s1))
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
private fun WinnerBanner(name: String, modifier: Modifier, onRestart: () -> Unit, onDone: () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Color.Black.copy(alpha = 0.85f)) {
        Column(Modifier.padding(horizontal = Space.s6, vertical = Space.s4), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name wins!", color = Palette.Gold, style = MaterialTheme.typography.headlineSmall)
            Text("Tap Done or Rematch to save.",
                color = Palette.OnDarkMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Row {
                TextButton(onClick = onRestart) { Text("Rematch") }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

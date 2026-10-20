package com.mtgscanner.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
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
 * right half to gain it; hold for ten. The middle button has turns, dice, history and undo.
 */
@Composable
fun GamePlayScreen(onExit: () -> Unit) {
    val vm = gameViewModel()
    val game by vm.game.collectAsStateWithLifecycle()
    val g = game
    val style by vm.style.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var playerSheet by remember { mutableStateOf<Int?>(null) }

    // Keep the screen on during a game.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    if (g == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        val panel: @Composable (Int, Int, Modifier) -> Unit = { i, rot, mod ->
            PlayerPanel(g, i, rot, style, mod, onLife = { d -> vm.life(i, d) }, onMore = { playerSheet = i })
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

        // The middle button.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(2.dp, Palette.Accent, CircleShape)
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (g.active != null) Text("T${g.turn}", color = Palette.Accent, fontWeight = FontWeight.Bold)
            else Icon(Icons.Filled.MoreHoriz, "Game menu", tint = Palette.Accent)
        }

        g.winner?.let { w ->
            WinnerBanner(g.players[w].name, Modifier.align(Alignment.Center).padding(top = 140.dp), onRestart = vm::restart,
                onDone = { vm.end(); onExit() })
        }
    }

    if (menu) GameMenu(g, vm, onDismiss = { menu = false }, onExit = { menu = false; onExit() })
    playerSheet?.let { i -> PlayerSheet(g, i, vm, onDismiss = { playerSheet = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerPanel(g: GameState, i: Int, rotation: Int, style: TableStyle, modifier: Modifier, onLife: (Int) -> Unit, onMore: () -> Unit) {
    val p = g.players[i]
    val out = g.outReason(i)
    val haptics = LocalHapticFeedback.current
    val color = PLAYER_COLORS[p.color % PLAYER_COLORS.size]
    // The running change shown above the life total, like "-3", fades after a pause.
    val last = g.history.lastOrNull()?.takeIf { it.player == i && it.what == "Life" }
    var showDelta by remember { mutableStateOf(false) }
    LaunchedEffect(last?.at, last?.delta) {
        if (last != null && last.delta != 0) {
            showDelta = true
            delay(GameState.MERGE_MS)
            showDelta = false
        }
    }

    val shape = RoundedCornerShape(22.dp)
    val back = when (style) {
        TableStyle.BOLD -> Modifier.background(color)
        TableStyle.GRADIENT -> Modifier.background(Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.65f))))
        TableStyle.DARK -> Modifier.background(Color(0xFF15161A)).border(3.dp, color, shape)
    }
    Box(modifier.clip(shape).then(back)) {
        if (p.art != null) AsyncImage(
            p.art, null, Modifier.fillMaxSize().rotateLayout(rotation), contentScale = ContentScale.Crop, alpha = 0.55f,
        )
        if (out != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        BoxWithConstraints(Modifier.fillMaxSize().rotateLayout(rotation)) {
            // Smaller panels (5 or 6 players) get a smaller number.
            val lifeSize = (minOf(maxHeight.value * 0.36f, maxWidth.value * 0.3f)).coerceIn(34f, 72f).sp
            val active = g.active == i
            if (active) Box(Modifier.fillMaxSize().border(4.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(22.dp)))
            // Left half loses, right half gains. Hold for ten.
            Row(Modifier.fillMaxSize()) {
                listOf(-1, 1).forEach { sign ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().combinedClickable(
                            onClick = { onLife(sign) },
                            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLife(sign * 10) },
                        ),
                        contentAlignment = if (sign < 0) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Text(if (sign < 0) "−" else "+", color = Color.White.copy(alpha = 0.45f), fontSize = (lifeSize.value * 0.45f).sp,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(showDelta && last != null, enter = fadeIn(), exit = fadeOut()) {
                    val d = last?.delta ?: 0
                    Text(if (d > 0) "+$d" else "$d", color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("${p.life}", color = Color.White, fontSize = lifeSize, fontWeight = FontWeight.Bold)
                Text(p.name, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
                if (out != null) Text(out, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            // Other counters along the top, shown only when they matter.
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (g.monarch == i) Badge("Monarch", Icons.Filled.WorkspacePremium)
                if (g.initiative == i) Badge("Initiative", Icons.Filled.Shield)
                if (p.poison > 0) Badge("☠ ${p.poison}")
                val maxCmd = p.commanderDamage.maxOrNull() ?: 0
                if (g.format.commanderDamage && maxCmd > 0) Badge("⚔ $maxCmd")
                if (p.commanderCasts > 0) Badge("Tax +${p.commanderTax}")
                if (p.partnerCasts > 0) Badge("Partner +${p.partnerTax}")
                if (p.energy > 0) Badge("⚡ ${p.energy}")
                if (p.experience > 0) Badge("XP ${p.experience}")
                if (p.rad > 0) Badge("☢ ${p.rad}")
            }
            IconButton(onClick = onMore, modifier = Modifier.align(Alignment.BottomCenter)) {
                Icon(Icons.Filled.MoreHoriz, "${p.name}: counters and commander damage", tint = Color.White)
            }
        }
    }
}

@Composable
private fun Badge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 8.dp, vertical = 3.dp),
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
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = 0.85f)) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name wins!", color = Palette.Gold, style = MaterialTheme.typography.headlineSmall)
            Text("Saved to your game stats", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onRestart) { Text("Play again") }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

/** One player's details: life, poison, commander damage from each opponent, tax and other counters. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerSheet(g: GameState, i: Int, vm: GameViewModel, onDismiss: () -> Unit) {
    val p = g.players[i]
    var renaming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(PLAYER_COLORS[p.color % PLAYER_COLORS.size]))
                Spacer(Modifier.width(10.dp))
                Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).clickable { renaming = true })
                TextButton(onClick = { renaming = true }) { Text("Rename") }
            }
            if (renaming) {
                var name by remember { mutableStateOf(p.name) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.update { Game.rename(it, i, name) }; renaming = false }) { Text("Save") }
                }
            }
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PLAYER_COLORS.forEachIndexed { k, c ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c)
                            .border(if (p.color == k) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { vm.update { Game.recolor(it, i, k) } },
                    )
                }
            }
            ArtPicker(p.art, vm, onPick = { art -> vm.update { Game.setArt(it, i, art) } })

            Section("Life")
            StepRow("Life", p.life, steps = listOf(5, 1), onChange = { vm.life(i, it) })
            Section("Poison")
            StepRow("Poison counters", p.poison, note = "Out at ${GameState.POISON_LIMIT}", onChange = { vm.poison(i, it) })

            if (g.format.commanderDamage && g.players.size > 1) {
                Section("Commander damage taken")
                Text("Each commander's damage counts separately. It also comes off your life.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                g.players.indices.filter { it != i }.forEach { from ->
                    StepRow("From ${g.players[from].name}", p.damageFrom(from), note = "Out at ${GameState.COMMANDER_LIMIT}",
                        onChange = { vm.commanderDamage(i, from, it, false) })
                    if (g.partners) StepRow("From ${g.players[from].name}'s partner", p.damageFrom(from, true),
                        onChange = { vm.commanderDamage(i, from, it, true) })
                }
            }

            if (g.format.commanderDamage || g.format == com.mtgscanner.game.GameFormat.OATHBREAKER) {
                Section("Commander tax")
                StepRow("Times cast from the command zone", p.commanderCasts, note = "Costs {${p.commanderTax}} more",
                    onChange = { vm.counter(i, "Commander cast", it) })
                if (g.partners) StepRow("Partner casts", p.partnerCasts, note = "Costs {${p.partnerTax}} more",
                    onChange = { vm.counter(i, "Partner cast", it) })
            }

            Section("Other counters")
            StepRow("Energy", p.energy, onChange = { vm.counter(i, "Energy", it) })
            StepRow("Experience", p.experience, onChange = { vm.counter(i, "Experience", it) })
            StepRow("Rad", p.rad, onChange = { vm.counter(i, "Rad", it) })

            if (g.players.size > 1) {
                Section("Table")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectPill("Monarch", g.monarch == i, { vm.update { Game.monarch(it, if (it.monarch == i) null else i) } },
                        leading = { Icon(Icons.Filled.WorkspacePremium, null, Modifier.size(16.dp)) })
                    SelectPill("Initiative", g.initiative == i, { vm.update { Game.initiative(it, if (it.initiative == i) null else i) } },
                        leading = { Icon(Icons.Filled.Shield, null, Modifier.size(16.dp)) })
                    SelectPill("Their turn", g.active == i, { vm.update { Game.setActive(it, i) } })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.update { Game.concede(it, i, !p.conceded) } }, shape = RoundedCornerShape(14.dp)) {
                    Text(if (p.conceded) "Back in the game" else "Concede")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Accent, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun StepRow(label: String, value: Int, onChange: (Int) -> Unit, steps: List<Int> = listOf(1), note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        steps.forEach { s -> StepButton("−$s") { onChange(-s) } }
        Text("$value", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        steps.reversed().forEach { s -> StepButton("+$s") { onChange(s) } }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(width = 44.dp, height = 38.dp).clip(RoundedCornerShape(10.dp))
            .background(Palette.SurfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** Turns, dice, day and night, history, undo, restart. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GameMenu(g: GameState, vm: GameViewModel, onDismiss: () -> Unit, onExit: () -> Unit) {
    var roll by remember { mutableStateOf<String?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    if (ending) EndGameDialog(g, onDismiss = { ending = false }) { save, winner ->
        ending = false
        vm.end(save, winner)
        onExit()
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.format.label, style = MaterialTheme.typography.titleLarge)
                    Text(elapsed(now - g.startedAt), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                IconButton(onClick = vm::undo, enabled = g.undoStack.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo last change") }
            }

            Section("Turns")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.active?.let { "Turn ${g.turn} · ${g.players[it].name}" } ?: "Not tracking turns",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::nextTurn) { Text(if (g.active == null) "Start" else "Next turn") }
            }
            if (g.players.size > 1) TextButton(onClick = {
                val first = Random.nextInt(g.players.size)
                vm.update { Game.setActive(it, first) }
                roll = "${g.players[first].name} goes first"
            }) { Text("Pick who goes first") }

            Section("Dice")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Coin", false, { roll = if (Random.nextBoolean()) "Heads" else "Tails" })
                SelectPill("d6", false, { roll = "d6: ${Random.nextInt(1, 7)}" }, leading = { Icon(Icons.Filled.Casino, null, Modifier.size(16.dp)) })
                SelectPill("d20", false, { roll = "d20: ${Random.nextInt(1, 21)}" }, leading = { Icon(Icons.Filled.Casino, null, Modifier.size(16.dp)) })
                SelectPill("Planar die", false, {
                    roll = when (Random.nextInt(6)) { 0 -> "Planeswalk"; 1 -> "Chaos"; else -> "Blank" }
                })
            }
            roll?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = Palette.Gold, modifier = Modifier.padding(top = 10.dp)) }

            Section("Day and night")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Off", g.dayNight == null, { vm.update { Game.dayNight(it, null) } })
                SelectPill("☀ Day", g.dayNight == "day", { vm.update { Game.dayNight(it, "day") } })
                SelectPill("☾ Night", g.dayNight == "night", { vm.update { Game.dayNight(it, "night") } })
            }

            Section("History")
            if (g.history.isEmpty()) Text("Changes show up here.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            g.history.takeLast(40).reversed().forEach { e ->
                val who = g.players.getOrNull(e.player)?.name ?: "Nobody"
                val change = when {
                    e.delta > 0 -> " +${e.delta}"
                    e.delta < 0 -> " ${e.delta}"
                    else -> ""
                }
                Text("$who · ${e.what}$change", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Palette.Outline)
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmRestart = true }, shape = RoundedCornerShape(14.dp)) { Text("Restart") }
                OutlinedButton(onClick = { ending = true }, shape = RoundedCornerShape(14.dp)) { Text("End game") }
                OutlinedButton(onClick = onExit, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Leave table")
                }
            }
            if (confirmRestart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reset everyone to ${g.startingLife}?", modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.restart(); confirmRestart = false; onDismiss() }) { Text("Restart", color = Palette.Danger) }
                }
            }
            Text(
                "Leaving keeps the game. Resume it from the Game tab.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun elapsed(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m < 60) "Playing for $m min" else "Playing for ${m / 60} h ${m % 60} min"
}

/** Background art for a player's panel, picked from cards in your collection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtPicker(current: String?, vm: GameViewModel, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val choices by vm.art.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Background", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (current != null) TextButton(onClick = { onPick(null) }) { Text("Remove") }
        TextButton(onClick = { open = !open; if (open) vm.searchArt(query) }) { Text(if (open) "Close" else "Pick card art") }
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
                Column(Modifier.width(96.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(c.art); open = false }) {
                    AsyncImage(c.art, c.name, Modifier.fillMaxWidth().height(70.dp)
                        .border(if (c.art == current) 2.dp else 0.dp, Palette.Accent, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
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
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { winner = i }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(winner == i, { winner = i })
                            Text(p.name)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { winner = null }, verticalAlignment = Alignment.CenterVertically) {
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

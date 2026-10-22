package com.mtgscanner.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.game.GameFormat
import com.mtgscanner.game.Player
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** Player colors for the life counter panels. */
val PLAYER_COLORS = listOf(
    Color(0xFF8E2C2C), Color(0xFF1F4E8C), Color(0xFF2E6B3A), Color(0xFF6A3D8F),
    Color(0xFF9A6A12), Color(0xFF1E6F78), Color(0xFF8A2F63), Color(0xFF4A4F57),
)

/** The game view model lives with the activity, so setup and the table share one game. */
@Composable
fun gameViewModel(): GameViewModel =
    viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity, factory = AppViewModels.Factory)

/** The Game tab: pick a format and players, or carry on with the game in progress. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSetupScreen(onPlay: () -> Unit) {
    val vm = gameViewModel()
    val current by vm.game.collectAsStateWithLifecycle()
    // Saveable, so switching tabs and coming back keeps the format, players and names picked.
    var format by rememberSaveable { mutableStateOf(GameFormat.COMMANDER) }
    var players by rememberSaveable { mutableIntStateOf(format.defaultPlayers) }
    var life by rememberSaveable { mutableIntStateOf(format.life) }
    var partners by rememberSaveable { mutableStateOf(false) }
    val names = rememberSaveable(saver = NamesSaver) {
        mutableStateListOf<String>().apply { addAll(vm.lastNames + List(Player.MAX_PLAYERS) { "" }); while (size > Player.MAX_PLAYERS) removeAt(size - 1) }
    }
    val style by vm.style.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    var ending by remember { mutableStateOf(false) }
    // Starting over while a game is running asks about that game first, so a stray tap can't wipe it.
    var startingNew by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text("Game", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))

            current?.let { g ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.BoxCard, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text("${g.format.label} · ${g.players.size} players · turn ${g.turn}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            g.players.joinToString("  ·  ") { "${it.name} ${it.life}" }, style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GradientButton("Resume", onPlay, Modifier.weight(1f), icon = Icons.Outlined.PlayArrow)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { ending = true }) { Text("End game", color = Palette.Muted) }
                        }
                    }
                }
            }

            Label("Mode")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameFormat.entries.forEach { f ->
                    Chip(f.label, format == f) {
                        format = f; life = f.life; players = f.defaultPlayers
                        if (!f.commanderDamage) partners = false
                    }
                }
            }

            Label("Players")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..Player.MAX_PLAYERS).forEach { n -> Chip("$n", players == n, Modifier.weight(1f)) { players = n } }
            }

            Label("Life")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(20, 25, 30, 40).forEach { v -> Chip("$v", life == v, Modifier.weight(1f)) { life = v } }
                QuantityStepper(life, onChange = { life = it.coerceIn(1, 999) })
            }

            if (format.commanderDamage) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Partners", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(partners, { partners = it }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
                }
            }

            Label("Style")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TableStyle.entries.forEach { t -> Chip(t.label, style == t, Modifier.weight(1f)) { vm.setStyle(t) } }
            }

            Label("Names")
            (0 until players).chunked(2).forEach { pair ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { i -> NameField(i, names[i], { names[i] = it }, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(8.dp))
            StatsCard(records, onOpen = { showStats = true })
            Spacer(Modifier.height(8.dp))
        }
        // Pinned under the scroll so it's always in reach.
        GradientButton(
            if (current != null) "Start a new game" else "Start game",
            {
                if (current != null) startingNew = true
                else { vm.start(format, players, life, names.take(players), partners); onPlay() }
            },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            icon = Icons.Outlined.PlayArrow,
        )
    }

    current?.let { g -> if (ending) EndGameDialog(g, onDismiss = { ending = false }) { save, winner -> ending = false; vm.end(save, winner) } }
    current?.let { g ->
        if (startingNew) EndGameDialog(g, onDismiss = { startingNew = false }) { save, winner ->
            startingNew = false
            vm.end(save, winner)
            vm.start(format, players, life, names.take(players), partners)
            onPlay()
        }
    }
    if (showStats) StatsSheet(records, vm, onDismiss = { showStats = false })
}

/** Keeps the typed player names across tab switches. */
private val NamesSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } },
)

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.AccentText, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

/** A small selectable chip for the setup rows. */
@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Corners.Small)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) Palette.Accent.copy(alpha = 0.18f) else Palette.SurfaceHigh)
            .border(1.dp, if (selected) Palette.Accent else Color.Transparent, shape)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.AccentText else Palette.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A short one-line name box with the player's color dot. */
@Composable
private fun NameField(i: Int, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(Corners.Small)
    Row(
        modifier.height(48.dp).clip(shape).background(Palette.SurfaceHigh).border(1.dp, Palette.Outline, shape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(PLAYER_COLORS[i]))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Player ${i + 1}", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

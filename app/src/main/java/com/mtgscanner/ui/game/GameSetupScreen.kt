package com.mtgscanner.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.game.GameFormat
import com.mtgscanner.game.Player
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette

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
    var format by remember { mutableStateOf(GameFormat.COMMANDER) }
    var players by remember { mutableIntStateOf(format.defaultPlayers) }
    var life by remember { mutableIntStateOf(format.life) }
    var partners by remember { mutableStateOf(false) }
    val names = remember {
        mutableStateListOf<String>().apply { addAll(vm.lastNames + List(Player.MAX_PLAYERS) { "" }); while (size > Player.MAX_PLAYERS) removeAt(size - 1) }
    }
    val style by vm.style.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    var ending by remember { mutableStateOf(false) }
    // Starting over while a game is running asks about that game first, so a stray tap can't wipe it.
    var startingNew by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Text("Game", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Text("A life counter for up to ${Player.MAX_PLAYERS} players.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)

        current?.let { g ->
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = Palette.BoxCard, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Game in progress", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                    Text("${g.format.label} · ${g.players.size} players · turn ${g.turn}", style = MaterialTheme.typography.titleMedium)
                    Text(g.players.joinToString("  ·  ") { "${it.name} ${it.life}" }, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientButton("Resume", onPlay, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { ending = true }) { Text("End game", color = Palette.Muted) }
                    }
                }
            }
        }

        Label("Game mode")
        GameFormat.entries.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    format = f; life = f.life; players = f.defaultPlayers
                    if (!f.commanderDamage) partners = false
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = format == f, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                Column {
                    Text(f.label, style = MaterialTheme.typography.titleSmall)
                    Text(f.detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
        }

        Label("Players")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..Player.MAX_PLAYERS).forEach { n -> SelectPill("$n", players == n, { players = n }) }
        }

        Label("Starting life")
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 25, 30, 40).forEach { v -> SelectPill("$v", life == v, { life = v }) }
            }
            QuantityStepper(life, onChange = { life = it.coerceIn(1, 999) })
        }

        if (format.commanderDamage) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Partner commanders", style = MaterialTheme.typography.titleSmall)
                    Text("Track damage and tax for two commanders each.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                Switch(partners, { partners = it }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
            }
        }

        Label("Table style")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TableStyle.entries.forEach { t -> SelectPill(t.label, style == t, { vm.setStyle(t) }) }
        }

        Label("Names")
        (0 until players).forEach { i ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(PLAYER_COLORS[i]).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = names[i],
                    onValueChange = { names[i] = it },
                    placeholder = { Text("Player ${i + 1}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        GradientButton(
            if (current != null) "Start a new game" else "Start game",
            {
                if (current != null) startingNew = true
                else { vm.start(format, players, life, names.take(players), partners); onPlay() }
            },
            Modifier.fillMaxWidth(),
            icon = Icons.Filled.Favorite,
        )

        Spacer(Modifier.height(18.dp))
        StatsCard(records, onOpen = { showStats = true })
        Spacer(Modifier.height(32.dp))
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

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Accent, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

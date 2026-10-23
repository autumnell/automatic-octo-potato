package com.mtgscanner.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
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
import com.mtgscanner.ui.theme.PlexMonoText

/** Player colors for the life counter panels. */
val PLAYER_COLORS = listOf(
    Color(0xFF8E2C2C), Color(0xFF1F4E8C), Color(0xFF2E6B3A), Color(0xFF6A3D8F),
    Color(0xFF9A6A12), Color(0xFF1E6F78), Color(0xFF8A2F63), Color(0xFF4A4F57),
)

/** The game view model lives with the activity, so setup and the table share one game. */
@Composable
fun gameViewModel(): GameViewModel =
    viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity, factory = AppViewModels.Factory)

/** The Game tab: a small picture of the table to set up, or the game in progress to carry on. */
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
    val last = vm.lastSetup
    var ending by remember { mutableStateOf(false) }
    // Starting over while a game is running asks about that game first, so a stray tap can't wipe it.
    var startingNew by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var customLife by remember { mutableStateOf(false) }

    fun begin(f: GameFormat, n: Int, l: Int, p: Boolean) {
        val go = { vm.start(f, n, l, names.take(n), p); onPlay() }
        if (current != null) startingNew = go else go()
    }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Game", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StyleMenu(style, vm::setStyle)
            }

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
                            GradientButton("Resume", onPlay, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { ending = true }) { Text("End game", color = Palette.Muted) }
                        }
                    }
                }
            }

            // The setup as one line: tap a word to change it.
            Spacer(Modifier.height(12.dp))
            val words = buildList {
                add(format.label); add(if (players == 1) "1 player" else "$players players"); add("$life life")
                if (format.commanderDamage) add(if (partners) "partners" else "no partners")
            }
            // Shrink the words to keep the setup on one line, whatever the screen width.
            val base = MaterialTheme.typography.headlineSmall
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val avail = constraints.maxWidth - with(LocalDensity.current) { (4.dp * words.size).roundToPx() }
                val full = remember(words, base) { measurer.measure(words.joinToString(" · "), base, maxLines = 1, softWrap = false).size.width }
                val style = if (full <= avail || full == 0) base else base.copy(fontSize = base.fontSize * (avail.toFloat() / full) * 0.98f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pick(style, format.label, GameFormat.entries.map { it.label }) { i ->
                    val f = GameFormat.entries[i]
                    format = f; life = f.life; players = f.defaultPlayers
                    if (!f.commanderDamage) partners = false
                }
                Dot(style)
                Pick(style, if (players == 1) "1 player" else "$players players", (1..Player.MAX_PLAYERS).map { "$it" }) { players = it + 1 }
                Dot(style)
                Pick(style, "$life life", listOf("20", "25", "30", "40", "Other…")) { i ->
                    if (i == 4) customLife = true else life = listOf(20, 25, 30, 40)[i]
                }
                if (format.commanderDamage) {
                    Dot(style)
                    Pick(style, if (partners) "partners" else "no partners", listOf("No partners", "Partners")) { partners = it == 1 }
                }
            }
            }

            Spacer(Modifier.height(10.dp))
            TablePreview(players, life, names, onRename = { i, v -> names[i] = v })
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { players-- }, enabled = players > 1) { Text("− Seat") }
                TextButton(onClick = { players++ }, enabled = players < Player.MAX_PLAYERS) { Text("+ Seat") }
            }

            if (last != null) {
                SameAsLast(last, names) { begin(last.format, last.players, last.life, last.partners) }
                Spacer(Modifier.height(8.dp))
            }
            StatsCard(records, onOpen = { showStats = true })
            Spacer(Modifier.height(8.dp))
        }
        // Pinned under the scroll so it's always in reach.
        GradientButton(
            if (current != null) "Start a new game" else "Start",
            { begin(format, players, life, partners) },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    current?.let { g -> if (ending) EndGameDialog(g, onDismiss = { ending = false }) { save, winner -> ending = false; vm.end(save, winner) } }
    current?.let { g ->
        startingNew?.let { go ->
            EndGameDialog(g, onDismiss = { startingNew = null }) { save, winner ->
                startingNew = null
                vm.end(save, winner)
                go()
            }
        }
    }
    if (customLife) LifeDialog(life, onDismiss = { customLife = false }) { life = it; customLife = false }
    if (showStats) StatsSheet(records, vm, onDismiss = { showStats = false })
}

/** A word in the setup line, underlined, that opens a short list to pick from. */
@Composable
private fun Pick(style: TextStyle, text: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            text,
            style = style.copy(textDecoration = TextDecoration.Underline),
            maxLines = 1, softWrap = false,
            color = Palette.Text,
            modifier = Modifier.clip(RoundedCornerShape(Corners.Small)).clickable { open = true }.padding(horizontal = 2.dp, vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

@Composable
private fun Dot(style: TextStyle) {
    Text(" · ", style = style, maxLines = 1, color = Palette.Muted, modifier = Modifier.padding(vertical = 4.dp))
}

/** The table seats laid out the way the life counter will show them, with names to type on each seat. */
@Composable
private fun TablePreview(players: Int, life: Int, names: List<String>, onRename: (Int, String) -> Unit) {
    val gap = 4.dp
    @Composable
    fun seat(i: Int, modifier: Modifier) = Seat(i, names[i], life, { onRename(i, it) }, modifier.padding(gap))
    Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface, modifier = Modifier.fillMaxWidth().height(if (players >= 5) 320.dp else 250.dp)) {
        Box(Modifier.padding(gap)) {
            when (players) {
                1 -> seat(0, Modifier.fillMaxSize())
                2 -> Column(Modifier.fillMaxSize()) {
                    seat(0, Modifier.weight(1f).fillMaxWidth())
                    seat(1, Modifier.weight(1f).fillMaxWidth())
                }
                3 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1.4f)) {
                        seat(0, Modifier.weight(1f).fillMaxHeight())
                        seat(1, Modifier.weight(1f).fillMaxHeight())
                    }
                    seat(2, Modifier.weight(1f).fillMaxWidth())
                }
                4 -> Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        seat(0, Modifier.weight(1f).fillMaxWidth())
                        seat(3, Modifier.weight(1f).fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        seat(1, Modifier.weight(1f).fillMaxWidth())
                        seat(2, Modifier.weight(1f).fillMaxWidth())
                    }
                }
                5 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(2.4f)) {
                        Column(Modifier.weight(1f)) {
                            seat(0, Modifier.weight(1f).fillMaxWidth())
                            seat(4, Modifier.weight(1f).fillMaxWidth())
                        }
                        Column(Modifier.weight(1f)) {
                            seat(1, Modifier.weight(1f).fillMaxWidth())
                            seat(2, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                    seat(3, Modifier.weight(1f).fillMaxWidth())
                }
                else -> Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        seat(0, Modifier.weight(1f).fillMaxWidth())
                        seat(5, Modifier.weight(1f).fillMaxWidth())
                        seat(4, Modifier.weight(1f).fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        seat(1, Modifier.weight(1f).fillMaxWidth())
                        seat(2, Modifier.weight(1f).fillMaxWidth())
                        seat(3, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** One seat: the player's color, their life, and their name to type in. */
@Composable
private fun Seat(i: Int, value: String, life: Int, onChange: (String) -> Unit, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(Corners.Small)).background(PLAYER_COLORS[i]).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(
            "$life", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = PlexMonoText),
            color = Color.White, modifier = Modifier.align(Alignment.Center),
        )
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            if (value.isEmpty()) Text("Player ${i + 1}", color = Palette.OnDarkMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Starts the last game again with the same format, seats and life. */
@Composable
private fun SameAsLast(last: LastSetup, names: List<String>, onStart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface)
            .clickable(onClick = onStart).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Same as last time", style = MaterialTheme.typography.titleSmall)
            Text(
                "${last.format.label} · ${last.players} players · ${last.life} life  ·  " +
                    names.take(last.players).mapIndexed { i, n -> n.ifBlank { "Player ${i + 1}" } }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("Start", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
    }
}

/** The table look, tucked in a menu since it's rarely changed. */
@Composable
private fun StyleMenu(style: TableStyle, onPick: (TableStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = Palette.Icon) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("Table style", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            TableStyle.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.label) },
                    onClick = { open = false; onPick(t) },
                    trailingIcon = { if (t == style) Icon(Icons.Outlined.Check, contentDescription = null, tint = Palette.Accent) },
                )
            }
        }
    }
}

/** Any starting life, for house rules. */
@Composable
private fun LifeDialog(initial: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var v by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Starting life") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QuantityStepper(v, onChange = { v = it.coerceIn(1, 999) }) } },
        confirmButton = { TextButton(onClick = { onDone(v) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Keeps the typed player names across tab switches. */
private val NamesSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } },
)

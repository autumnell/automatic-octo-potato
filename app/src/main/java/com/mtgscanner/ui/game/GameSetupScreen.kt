package com.mtgscanner.ui.game

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.game.GameFormat
import com.mtgscanner.game.Player
import com.mtgscanner.game.TableLayouts
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.QuantityStepper
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space

/** Player colors for the life counter panels. */
val PLAYER_COLORS = listOf(
    Color(0xFF8E2C2C), Color(0xFF1F4E8C), Color(0xFF2E6B3A), Color(0xFF6A3D8F),
    Color(0xFF9A6A12), Color(0xFF1E6F78), Color(0xFF8A2F63), Color(0xFF4A4F57),
)

/** The game view model lives with the activity, so setup and the table share one game. */
@Composable
fun gameViewModel(): GameViewModel =
    viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity, factory = AppViewModels.Factory)

/** The setup screens, one question each: what format, how many players, where they sit, and their names. */
private enum class SetupStep(val title: String) { FORMAT("Game"), PLAYERS("Players"), SEATS("Seats"), NAMES("Names") }

/**
 * The Game tab. It opens on the format tiles, with the game in progress and "Same as last time" above them; a new
 * game then asks one thing per screen with big tiles to tap, and ends on the names and Start.
 */
@Composable
fun GameSetupScreen(
    onPlay: () -> Unit,
    /** Opens on this setup screen (0 the formats, 3 the names), for the screen pictures. */
    startAt: Int = 0,
) {
    com.mtgscanner.ui.components.KeepUpright()
    val vm = gameViewModel()
    val current by vm.game.collectAsStateWithLifecycle()
    // Saveable, so switching tabs and coming back keeps the screen and the choices made.
    var step by rememberSaveable { mutableStateOf(SetupStep.entries[startAt.coerceIn(0, SetupStep.entries.lastIndex)]) }
    var format by rememberSaveable { mutableStateOf(GameFormat.COMMANDER) }
    var players by rememberSaveable { mutableIntStateOf(format.defaultPlayers) }
    var life by rememberSaveable { mutableIntStateOf(format.life) }
    var layout by rememberSaveable { mutableIntStateOf(0) }
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

    fun begin(f: GameFormat, n: Int, l: Int, p: Boolean, seats: Int) {
        val go = { vm.start(f, n, l, names.take(n), p, seats); step = SetupStep.FORMAT; onPlay() }
        if (current != null) startingNew = go else go()
    }
    fun back() {
        step = when (step) {
            SetupStep.NAMES -> if (TableLayouts.count(players) > 1) SetupStep.SEATS else SetupStep.PLAYERS
            SetupStep.SEATS -> SetupStep.PLAYERS
            SetupStep.PLAYERS -> SetupStep.FORMAT
            else -> SetupStep.FORMAT
        }
    }
    BackHandler(enabled = step != SetupStep.FORMAT) { back() }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.s1), verticalAlignment = Alignment.CenterVertically) {
            if (step != SetupStep.FORMAT) IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(
                step.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)
                    .padding(start = if (step == SetupStep.FORMAT) Space.s3 else Space.s1),
            )
            if (step == SetupStep.FORMAT) StyleMenu(style, vm::setStyle, onStats = { showStats = true })
            else {
                // Where you are: the Seats step only comes up when the player count has more than one way to sit.
                val total = if (TableLayouts.count(players) > 1) 4 else 3
                val at = if (step == SetupStep.NAMES) total else step.ordinal + 1
                Text("$at of $total", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(end = Space.s3))
            }
        }
        // The picking screens sit in the middle of the space; Names starts at the top, under the keyboard.
        val centered = step == SetupStep.FORMAT || step == SetupStep.PLAYERS || step == SetupStep.SEATS
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val room = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = room).padding(horizontal = Space.s4),
                verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
            ) {
                when (step) {
                    SetupStep.FORMAT -> {
                        current?.let { g ->
                            Spacer(Modifier.height(Space.s3))
                            Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.BoxCard, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(Space.s3)) {
                                    Text("${g.format.label} · ${g.players.size} players · turn ${g.turn}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        g.players.joinToString("  ·  ") { "${it.name} ${it.life}" }, style = MaterialTheme.typography.bodySmall,
                                        color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(Space.s2))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        com.mtgscanner.ui.components.QuietButton(onClick = onPlay, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Resume") }
                                        Spacer(Modifier.width(Space.s2))
                                        TextButton(onClick = { ending = true }) { Text("End game", color = Palette.Muted) }
                                    }
                                }
                            }
                        }
                        if (last != null) {
                            Spacer(Modifier.height(Space.s3))
                            SameAsLast(last, names) { begin(last.format, last.players, last.life, last.partners, last.layout) }
                        }
                        Question("What are you playing?")
                        val formats = GameFormat.entries.filter { it != GameFormat.CUSTOM }
                        TileGrid(formats.size, columns = 2) { i, m ->
                            val f = formats[i]
                            Tile(PLAYER_COLORS[i % PLAYER_COLORS.size], selected = f == format, modifier = m.heightIn(min = 104.dp), onClick = {
                                format = f; life = f.life; players = f.defaultPlayers; layout = 0
                                if (!f.commanderDamage) partners = false
                                step = SetupStep.PLAYERS
                            }) {
                                Text(f.label, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${f.life} life", style = MaterialTheme.typography.bodySmall, color = Palette.OnDarkMuted)
                            }
                        }
                        Spacer(Modifier.height(Space.s3))
                        OtherRow("Other starting life", if (format == GameFormat.CUSTOM) "$life life" else "House rules") { customLife = true }
                        if (records.isNotEmpty()) {
                            Spacer(Modifier.height(Space.s3))
                            StatsCard(records, onOpen = { showStats = true })
                        }
                    }
                    SetupStep.PLAYERS -> {
                        Question("How many players?")
                        TileGrid(Player.MAX_PLAYERS, columns = 3) { i, m ->
                            val n = i + 1
                            Tile(PLAYER_COLORS[i], selected = n == players, modifier = m.aspectRatio(1f), center = true, onClick = {
                                players = n; layout = TableLayouts.valid(n, layout)
                                step = if (TableLayouts.count(n) > 1) SetupStep.SEATS else SetupStep.NAMES
                            }) {
                                Text("$n", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    SetupStep.SEATS -> {
                        Question("Where does everyone sit?")
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                            repeat(TableLayouts.count(players)) { k ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    MiniTable(players, k, selected = k == layout) { layout = k; step = SetupStep.NAMES }
                                    Spacer(Modifier.height(Space.s2))
                                    Text(TableLayouts.label(players, k), style = MaterialTheme.typography.bodyMedium, color = if (k == layout) Palette.Text else Palette.Muted)
                                }
                            }
                        }
                    }
                    SetupStep.NAMES -> {
                        Question("Who's playing?")
                        for (i in 0 until players) {
                            Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(Space.s6).clip(RoundedCornerShape(Corners.Small)).background(PLAYER_COLORS[i]))
                                Spacer(Modifier.width(Space.s3))
                                OutlinedTextField(
                                    value = names[i], onValueChange = { names[i] = it }, singleLine = true,
                                    placeholder = { Text("Player ${i + 1}") }, modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (format.commanderDamage) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Partner commanders", style = MaterialTheme.typography.bodyLarge)
                                    Text("Count damage from each partner on its own", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                }
                                Switch(checked = partners, onCheckedChange = { partners = it })
                            }
                        }
                        Spacer(Modifier.height(Space.s3))
                        Text(
                            listOf(format.label, "$players ${if (players == 1) "player" else "players"}", "$life life", TableLayouts.label(players, layout).takeIf { TableLayouts.count(players) > 1 })
                                .filterNotNull().joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                        )
                        Text("Everyone gets random card art. Change it from a player's menu at the table", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
                Spacer(Modifier.height(Space.s4))
            }
        }
        // One main action per screen, pinned under the scroll so it's always in reach.
        when (step) {
            SetupStep.NAMES -> GradientButton(
                "Start", { begin(format, players, life, partners, layout) },
                Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2),
            )
            else -> Unit
        }
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
    if (customLife) LifeDialog(life, onDismiss = { customLife = false }) {
        format = GameFormat.CUSTOM; life = it; players = GameFormat.CUSTOM.defaultPlayers; partners = false; layout = 0
        customLife = false
        step = SetupStep.PLAYERS
    }
    if (showStats) StatsSheet(records, vm, onDismiss = { showStats = false })
}

/** The one question a setup screen asks. */
@Composable
private fun Question(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Space.s4, bottom = Space.s4))
}

/** [count] tiles in rows of [columns]; a short last row keeps its tiles the same width. */
@Composable
private fun TileGrid(count: Int, columns: Int, tile: @Composable (Int, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        (0 until count).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                row.forEach { i -> tile(i, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A big colored tile for a setup choice; the choice made before has a white edge. */
@Composable
private fun Tile(color: Color, selected: Boolean, modifier: Modifier, center: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(Corners.Medium)).background(color)
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(Corners.Medium)) else Modifier)
            .clickable(onClick = onClick).padding(Space.s4),
        contentAlignment = if (center) Alignment.Center else Alignment.BottomStart,
    ) {
        Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) { content() }
    }
}

/** A quiet row under the tiles for the less common choice. */
@Composable
private fun OtherRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface)
            .clickable(onClick = onClick).padding(horizontal = Space.s4, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

/** A small picture of the table in one arrangement: each seat in its color, its number turned to face its player. */
@Composable
private fun MiniTable(players: Int, layout: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.56f).clip(RoundedCornerShape(Corners.Medium)).background(Palette.Stage)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Text else Palette.Rule, RoundedCornerShape(Corners.Medium))
            .clickable(onClick = onClick).padding(Space.s1)
            .semantics(mergeDescendants = true) { contentDescription = TableLayouts.label(players, layout) },
    ) {
        TableSeats(players, layout, Space.s1) { i, rot, m ->
            Box(m.clip(RoundedCornerShape(Corners.Small)).background(PLAYER_COLORS[i]), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.rotate(rot.toFloat()))
            }
        }
    }
}

/** Starts the last game again with the same format, seats and life. */
@Composable
private fun SameAsLast(last: LastSetup, names: List<String>, onStart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface)
            .clickable(onClick = onStart).padding(horizontal = Space.s3, vertical = Space.s3),
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
        Text("Start", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

/** The table look, tucked in a menu since it's rarely changed. */
@Composable
private fun StyleMenu(style: TableStyle, onPick: (TableStyle) -> Unit, onStats: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = Palette.Icon) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Game stats") }, onClick = { open = false; onStats() })
            HorizontalDivider()
            Text("Table style", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s3, vertical = Space.s2))
            TableStyle.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.label) },
                    onClick = { open = false; onPick(t) },
                    trailingIcon = { if (t == style) Icon(Icons.Outlined.Check, contentDescription = null, tint = Palette.Text) },
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


package com.mtgscanner.ui.game

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtgscanner.game.Game
import com.mtgscanner.game.GameFormat
import com.mtgscanner.game.GameState
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Which way each seat faces, matching the table layout. */
fun seatRotation(players: Int, i: Int): Int = when (players) {
    1 -> 0
    2 -> if (i == 0) 180 else 0
    3 -> listOf(90, 270, 0)[i]
    4 -> listOf(90, 270, 270, 90)[i]
    5 -> listOf(90, 270, 270, 0, 90)[i]
    else -> listOf(90, 270, 270, 270, 90, 90)[i]
}

/** Who holds the monarch or the initiative. */
enum class TableMarker(val label: String, val icon: ImageVector) {
    MONARCH("Monarch", Icons.Filled.WorkspacePremium),
    INITIATIVE("Initiative", Icons.Filled.Shield),
}

/**
 * A full-screen panel over the table, turned to face whoever opened it. Content is laid out
 * side to side in big tiles so it reads at a glance without picking the phone up or scrolling.
 */
@Composable
private fun TableOverlay(
    rotation: Int,
    title: String,
    tabs: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var turn by remember { mutableIntStateOf(rotation) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF20E0F12)).systemBarsPadding()) {
            Column(Modifier.fillMaxSize().rotateLayout(turn).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { turn = (turn + 90) % 360 }) {
                        Icon(Icons.Filled.ScreenRotation, "Turn this panel", tint = Color.White.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(30.dp)) }
                }
                if (tabs.size > 1) Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { tabs.forEachIndexed { k, t -> TabPill(t, k == tab) { onTab(k) } } }
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.Black else Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Palette.Accent else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Lays tiles out in rows that fill the width: more side by side when there's room. */
@Composable
private fun TileGrid(count: Int, minTile: Int = 170, modifier: Modifier = Modifier, tile: @Composable (Int, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = (maxWidth.value / minTile).toInt().coerceIn(2, 6)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k -> tile(k, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A counter with big minus and plus buttons. Hold a button to change it by 5. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterTile(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier, note: String? = null, warn: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.08f)) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(-1, 1).forEach { sign ->
                    if (sign > 0) Text(
                        "$value", color = if (warn) Palette.Danger else Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.width(56.dp),
                    )
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                            .combinedClickable(onClick = { onChange(sign) }, onLongClick = { onChange(sign * 5) }),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (sign < 0) "−" else "+", color = Color.White, fontSize = 28.sp) }
                }
            }
            if (note != null) Text(note, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A big button with an icon, a label and an optional line under it. */
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    detail: String? = null,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.height(104.dp).alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.08f),
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Big buttons, one per player, to hand something to someone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerPicker(g: GameState, question: String, current: Int?, onPick: (Int?) -> Unit, onBack: () -> Unit, allowNobody: Boolean = true) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back", fontSize = 16.sp) }
        }
        Spacer(Modifier.height(8.dp))
        TileGrid(g.players.size + if (allowNobody) 1 else 0, minTile = 150) { k, mod ->
            if (k < g.players.size) {
                val p = g.players[k]
                Box(
                    mod.height(84.dp).clip(RoundedCornerShape(18.dp)).background(PLAYER_COLORS[p.color % PLAYER_COLORS.size])
                        .border(if (current == k) 4.dp else 0.dp, Color.White, RoundedCornerShape(18.dp))
                        .clickable { onPick(k) },
                    contentAlignment = Alignment.Center,
                ) { Text(p.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else Box(
                mod.height(84.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.1f))
                    .border(if (current == null) 4.dp else 0.dp, Color.White, RoundedCornerShape(18.dp))
                    .clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Text("Nobody", color = Color.White, fontSize = 20.sp) }
        }
    }
}

/** One player's counters, commander damage and settings, facing that player. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerOverlay(g: GameState, i: Int, vm: GameViewModel, onDismiss: () -> Unit) {
    val p = g.players[i]
    val showCommander = g.format.commanderDamage && g.players.size > 1
    val tabs = listOfNotNull("Counters", if (showCommander) "Commander damage" else null, "Player")
    var tab by remember { mutableIntStateOf(0) }
    val name = tabs[tab.coerceIn(0, tabs.lastIndex)]
    TableOverlay(seatRotation(g.players.size, i), p.name, tabs, tab, { tab = it }, onDismiss) {
        when (name) {
            "Counters" -> {
                val tiles = buildList<@Composable (Modifier) -> Unit> {
                    add { m -> CounterTile("Life", p.life, { vm.life(i, it) }, m, note = "Hold for 5", warn = p.life <= 5) }
                    add { m -> CounterTile("Poison", p.poison, { vm.poison(i, it) }, m, note = "Out at ${g.format.poisonLimit}", warn = p.poison >= g.format.poisonLimit - 3) }
                    if (g.format.usesCommander) {
                        add { m -> CounterTile("Commander casts", p.commanderCasts, { vm.counter(i, "Commander cast", it) }, m, note = "Tax {${p.commanderTax}}") }
                        if (g.partners) add { m -> CounterTile("Partner casts", p.partnerCasts, { vm.counter(i, "Partner cast", it) }, m, note = "Tax {${p.partnerTax}}") }
                    }
                    add { m -> CounterTile("Energy", p.energy, { vm.counter(i, "Energy", it) }, m) }
                    add { m -> CounterTile("Experience", p.experience, { vm.counter(i, "Experience", it) }, m) }
                    add { m -> CounterTile("Rad", p.rad, { vm.counter(i, "Rad", it) }, m) }
                }
                TileGrid(tiles.size) { k, m -> tiles[k](m) }
            }
            "Commander damage" -> {
                Text("Damage from each commander, which also comes off life. 21 from one commander and you're out.",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                val sources = g.players.indices.filter { it != i }.flatMap { from -> listOf(from to false) + if (g.partners) listOf(from to true) else emptyList() }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TileGrid(sources.size) { k, m ->
                        val (from, partner) = sources[k]
                        val dmg = p.damageFrom(from, partner)
                        CounterTile(
                            g.players[from].name + if (partner) " (partner)" else "", dmg,
                            { vm.commanderDamage(i, from, it, partner) }, m,
                            note = "${(GameState.COMMANDER_LIMIT - dmg).coerceAtLeast(0)} to go", warn = dmg >= 15,
                        )
                    }
                }
            }
            else -> PlayerSettings(g, i, vm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSettings(g: GameState, i: Int, vm: GameViewModel) {
    val p = g.players[i]
    var name by remember(p.name) { mutableStateOf(p.name) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.update { Game.rename(it, i, name) } }, enabled = name.isNotBlank() && name != p.name) { Text("Save") }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PLAYER_COLORS.forEachIndexed { k, c ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c)
                            .border(if (p.color == k) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { vm.update { Game.recolor(it, i, k) } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ArtPicker(p.art, vm, onPick = { art -> vm.update { Game.setArt(it, i, art) } })
        }
        if (g.players.size > 1) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(TableMarker.MONARCH.icon, "Monarch", Modifier.weight(1f), if (g.monarch == i) "Yours" else "Take it",
                    tint = if (g.monarch == i) Palette.Gold else Color.White) {
                    vm.update { Game.monarch(it, if (it.monarch == i) null else i) }
                }
                ActionTile(TableMarker.INITIATIVE.icon, "Initiative", Modifier.weight(1f), if (g.initiative == i) "Yours" else "Take it",
                    tint = if (g.initiative == i) Palette.Gold else Color.White) {
                    vm.update { Game.initiative(it, if (it.initiative == i) null else i) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(Icons.Filled.SkipNext, "Their turn", Modifier.weight(1f), if (g.active == i) "Now" else null,
                    tint = if (g.active == i) Palette.Accent else Color.White) { vm.update { Game.setActive(it, i) } }
                ActionTile(Icons.Filled.Flag, if (p.conceded) "Back in" else "Concede", Modifier.weight(1f)) {
                    vm.update { Game.concede(it, i, !p.conceded) }
                }
            }
        }
    }
}

/** The middle button's panel: table actions, dice and history, in big tiles. */
@Composable
fun MenuOverlay(
    g: GameState,
    vm: GameViewModel,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
    onLeave: () -> Unit,
    startPick: TableMarker? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(startPick) }
    var choosingFirst by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var roll by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    TableOverlay(0, g.format.label, listOf("Table", "Dice", "History"), tab, { tab = it; picking = null }, onDismiss) {
        val marker = picking
        when {
            marker != null -> PlayerPicker(
                g, "Who has the ${marker.label.lowercase()}?",
                if (marker == TableMarker.MONARCH) g.monarch else g.initiative,
                onPick = { who ->
                    vm.update { if (marker == TableMarker.MONARCH) Game.monarch(it, who) else Game.initiative(it, who) }
                    picking = null
                    if (startPick != null) onDismiss()
                },
                onBack = { picking = null; if (startPick != null) onDismiss() },
            )
            choosingFirst -> PlayerPicker(
                g, "Who goes first?", g.active,
                onPick = { who -> if (who != null) vm.update { Game.setFirst(it, who) }; choosingFirst = false },
                onBack = { choosingFirst = false }, allowNobody = false,
            )
            tab == 0 -> {
                val tiles = buildList<@Composable (Modifier) -> Unit> {
                    add { m -> ActionTile(Icons.AutoMirrored.Filled.Undo, "Undo", m, g.history.lastOrNull()?.let { describe(g, it) }, enabled = g.undoStack.isNotEmpty()) { vm.undo() } }
                    add { m ->
                        ActionTile(Icons.Filled.SkipNext, if (g.active == null) "Start turns" else "Next turn", m,
                            g.active?.let { "Turn ${g.turn} · ${g.players[it].name}" }, tint = Palette.Accent) { vm.nextTurn() }
                    }
                    if (g.players.size > 1) {
                        add { m ->
                            ActionTile(Icons.Filled.Shuffle, "Random first", m, roll?.takeIf { it.endsWith("goes first") }) {
                                val first = Random.nextInt(g.players.size)
                                vm.update { Game.setFirst(it, first) }
                                roll = "${g.players[first].name} goes first"
                            }
                        }
                        add { m -> ActionTile(Icons.Filled.SkipNext, "Pick first", m, "Choose who starts") { choosingFirst = true } }
                        add { m ->
                            ActionTile(TableMarker.MONARCH.icon, "Monarch", m, g.monarch?.let { g.players[it].name } ?: "Nobody",
                                tint = if (g.monarch != null) Palette.Gold else Color.White) { picking = TableMarker.MONARCH }
                        }
                        add { m ->
                            ActionTile(TableMarker.INITIATIVE.icon, "Initiative", m, g.initiative?.let { g.players[it].name } ?: "Nobody",
                                tint = if (g.initiative != null) Palette.Gold else Color.White) { picking = TableMarker.INITIATIVE }
                        }
                    }
                    add { m ->
                        ActionTile(dayNightIcon(g.dayNight), dayNightLabel(g.dayNight), m, "Tap to change",
                            tint = if (g.dayNight != null) Palette.Gold else Color.White) { vm.update { Game.dayNight(it, nextDayNight(it.dayNight)) } }
                    }
                    add { m -> ActionTile(Icons.Filled.Timer, elapsed(now - g.startedAt), m, "Game time", enabled = true) {} }
                    add { m ->
                        ActionTile(Icons.Filled.RestartAlt, if (confirmRestart) "Tap again" else "Restart", m,
                            if (confirmRestart) "Resets to ${g.startingLife}" else "Same players",
                            tint = if (confirmRestart) Palette.Danger else Color.White) {
                            if (confirmRestart) { vm.restart(); confirmRestart = false; onDismiss() } else confirmRestart = true
                        }
                    }
                    add { m -> ActionTile(Icons.Filled.EmojiEvents, "End game", m, "Save who won") { onEnd() } }
                    add { m -> ActionTile(Icons.AutoMirrored.Filled.ExitToApp, "Leave table", m, "Game is kept") { onLeave() } }
                }
                TileGrid(tiles.size, minTile = 150) { k, m -> tiles[k](m) }
            }
            tab == 1 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    val dice = listOf<Pair<String, () -> String>>(
                        "Coin" to { if (Random.nextBoolean()) "Heads" else "Tails" },
                        "d6" to { "${Random.nextInt(1, 7)}" },
                        "d20" to { "${Random.nextInt(1, 21)}" },
                        "Planar die" to { when (Random.nextInt(6)) { 0 -> "Planeswalk"; 1 -> "Chaos"; else -> "Blank" } },
                    )
                    TileGrid(dice.size, minTile = 120) { k, m ->
                        val (label, rollIt) = dice[k]
                        ActionTile(Icons.Filled.Shuffle, label, m) { roll = "$label: ${rollIt()}" }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(roll ?: "Tap a die", color = if (roll != null) Palette.Gold else Color.White.copy(alpha = 0.5f),
                        fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            else -> {
                if (g.history.isEmpty()) Text("Changes show up here.", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(g.history.takeLast(150).reversed()) { e ->
                        Text(describe(g, e), color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

private fun describe(g: GameState, e: com.mtgscanner.game.GameEvent): String {
    val who = g.players.getOrNull(e.player)?.name ?: "Nobody"
    val change = when {
        e.delta > 0 -> " +${e.delta}"
        e.delta < 0 -> " ${e.delta}"
        else -> ""
    }
    return "$who · ${e.what}$change"
}

fun nextDayNight(v: String?): String? = when (v) {
    null -> "day"
    "day" -> "night"
    else -> "day"
}

fun dayNightIcon(v: String?): ImageVector = when (v) {
    "day" -> Icons.Filled.LightMode
    "night" -> Icons.Filled.DarkMode
    else -> Icons.Filled.Brightness4
}

fun dayNightLabel(v: String?): String = when (v) {
    "day" -> "Day"
    "night" -> "Night"
    else -> "Day / night"
}

fun elapsed(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

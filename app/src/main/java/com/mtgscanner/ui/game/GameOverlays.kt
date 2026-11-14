package com.mtgscanner.ui.game

import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.mtgscanner.ui.theme.Corners

/** Which way each seat faces, matching the table layout ([TableSeats]). */
fun seatRotation(players: Int, i: Int, layout: Int = 0): Int = when {
    players == 2 && layout == 1 -> 0
    players == 3 && layout == 1 -> listOf(180, 270, 90)[i]
    players == 4 && layout == 1 -> listOf(180, 270, 0, 90)[i]
    else -> when (players) {
    1 -> 0
    2 -> if (i == 0) 180 else 0
    3 -> listOf(90, 270, 0)[i]
    4 -> listOf(90, 270, 270, 90)[i]
    5 -> listOf(90, 270, 270, 0, 90)[i]
    else -> listOf(90, 270, 270, 270, 90, 90)[i]
    }
}

/** Who holds the monarch or the initiative. */
enum class TableMarker(val label: String, val icon: ImageVector) {
    MONARCH("Monarch", com.mtgscanner.ui.components.ManaIcons.Monarch),
    INITIATIVE("Initiative", com.mtgscanner.ui.components.ManaIcons.Initiative),
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
        Box(Modifier.fillMaxSize().background(Palette.Stage).systemBarsPadding()) {
            Column(Modifier.fillMaxSize().rotateLayout(turn).padding(horizontal = Space.s4, vertical = Space.s3)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { turn = (turn + 90) % 360 }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.ScreenRotation, "Turn this panel", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                if (tabs.size > 1) Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                ) { tabs.forEachIndexed { k, t -> TabPill(t, k == tab, Modifier.weight(1f)) { onTab(k) } } }
                Spacer(Modifier.height(Space.s2))
                content()
            }
        }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(Corners.Medium))
            .background(if (selected) Palette.Accent else Color.White.copy(alpha = 0.16f))
            .border(2.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f), RoundedCornerShape(Corners.Medium))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) Palette.OnAccent else Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = Space.s2),
        )
    }
}

/**
 * A page that scrolls, with big up and down buttons beside it so it can be paged through while the
 * phone lies flat on the table.
 */
@Composable
private fun ScrollPage(state: ScrollableState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 260.dp.toPx() }
    Row(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        if (state.canScrollBackward || state.canScrollForward) {
            Column(Modifier.fillMaxHeight().padding(start = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                PageButton(Icons.Outlined.KeyboardArrowUp, "Scroll up", state.canScrollBackward, Modifier.weight(1f)) {
                    scope.launch { state.animateScrollBy(-step) }
                }
                PageButton(Icons.Outlined.KeyboardArrowDown, "Scroll down", state.canScrollForward, Modifier.weight(1f)) {
                    scope.launch { state.animateScrollBy(step) }
                }
            }
        }
    }
}

@Composable
private fun PageButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.width(64.dp).clip(RoundedCornerShape(Corners.Medium))
            .background(Color.White.copy(alpha = if (enabled) 0.2f else 0.06f))
            .border(2.dp, Color.White.copy(alpha = if (enabled) 0.5f else 0.12f), RoundedCornerShape(Corners.Medium))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(48.dp))
    }
}

/** Lays tiles out in rows that fill the width: more side by side when there's room. */
@Composable
private fun TileGrid(count: Int, minTile: Int = 170, modifier: Modifier = Modifier, tile: @Composable (Int, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = (maxWidth.value / minTile).toInt().coerceIn(2, 6)
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    row.forEach { k -> tile(k, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
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
        onClick = onClick, enabled = enabled, modifier = modifier.height(124.dp).alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(Corners.Medium), color = Palette.StagePanel,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.padding(Space.s3), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(Space.s2))
            Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, color = Palette.OnDarkMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Big buttons, one per player, to hand something to someone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerPicker(
    g: GameState, question: String, current: Int?, onPick: (Int?) -> Unit, onBack: () -> Unit, allowNobody: Boolean = true,
    onRandom: (() -> Unit)? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onRandom != null) TextButton(onClick = onRandom) { Text("Random", fontSize = 19.sp) }
            TextButton(onClick = onBack) { Text("Back", fontSize = 19.sp) }
        }
        Spacer(Modifier.height(Space.s2))
        TileGrid(g.players.size + if (allowNobody) 1 else 0, minTile = 150) { k, mod ->
            if (k < g.players.size) {
                val p = g.players[k]
                Box(
                    mod.height(84.dp).clip(RoundedCornerShape(Corners.Medium)).background(PLAYER_COLORS[p.color % PLAYER_COLORS.size])
                        .border(if (current == k) 4.dp else 0.dp, Color.White, RoundedCornerShape(Corners.Medium))
                        .selectable(selected = current == k, role = Role.RadioButton) { onPick(k) },
                    contentAlignment = Alignment.Center,
                ) { Text(p.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else Box(
                mod.height(84.dp).clip(RoundedCornerShape(Corners.Medium)).background(Color.White.copy(alpha = 0.1f))
                    .border(if (current == null) 4.dp else 0.dp, Color.White, RoundedCornerShape(Corners.Medium))
                    .selectable(selected = current == null, role = Role.RadioButton) { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Text("Nobody", color = Color.White, fontSize = 20.sp) }
        }
    }
}

/**
 * One player's name, color, art, and the table things they can take (monarch, initiative, their turn, conceding),
 * facing that player. Commander damage and counters are on the back of their panel.
 */
@Composable
fun PlayerOverlay(g: GameState, i: Int, vm: GameViewModel, onDismiss: () -> Unit) {
    TableOverlay(seatRotation(g.players.size, i, g.layout), g.players[i].name, emptyList(), 0, {}, onDismiss) {
        PlayerSettings(g, i, vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSettings(g: GameState, i: Int, vm: GameViewModel) {
    val p = g.players[i]
    var name by remember(p.name) { mutableStateOf(p.name) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.update { Game.rename(it, i, name) } }, enabled = name.isNotBlank() && name != p.name) { Text("Save") }
            }
            Spacer(Modifier.height(Space.s3))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                PLAYER_COLORS.forEachIndexed { k, c ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c)
                            .border(if (p.color == k) 3.dp else 0.dp, Color.White, CircleShape)
                            .semantics { contentDescription = "Color ${k + 1}" }
                            .selectable(selected = p.color == k, role = Role.RadioButton) { vm.update { Game.recolor(it, i, k) } },
                    )
                }
            }
            Spacer(Modifier.height(Space.s3))
            ArtPicker(p.art, vm, onPick = { art -> vm.update { Game.setArt(it, i, art) } })
        }
        if (g.players.size > 1) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                ActionTile(TableMarker.MONARCH.icon, "Monarch", Modifier.weight(1f), if (g.monarch == i) "Yours" else "Take it",
                    tint = if (g.monarch == i) Palette.Gold else Color.White) {
                    vm.update { Game.monarch(it, if (it.monarch == i) null else i) }
                }
                ActionTile(TableMarker.INITIATIVE.icon, "Initiative", Modifier.weight(1f), if (g.initiative == i) "Yours" else "Take it",
                    tint = if (g.initiative == i) Palette.Gold else Color.White) {
                    vm.update { Game.initiative(it, if (it.initiative == i) null else i) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                ActionTile(Icons.Outlined.SkipNext, "Their turn", Modifier.weight(1f), if (g.active == i) "Now" else null,
                    tint = if (g.active == i) Palette.Accent else Color.White) { vm.update { Game.setActive(it, i) } }
                ActionTile(Icons.Outlined.Flag, if (p.conceded) "Back in" else "Concede", Modifier.weight(1f)) {
                    vm.update { Game.concede(it, i, !p.conceded) }
                }
            }
        }
    }
}

/**
 * The middle button's panel, on one page with no tabs: the turn first (next turn and undo, big), then the
 * table (monarch, initiative, day and night, who goes first), the dice, and the game itself at the bottom.
 * The game time sits in the title.
 */
@Composable
fun MenuOverlay(
    g: GameState,
    vm: GameViewModel,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
    onLeave: () -> Unit,
    startPick: TableMarker? = null,
    onTutorial: () -> Unit = {},
) {
    var picking by remember { mutableStateOf(startPick) }
    var choosingFirst by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var rollKind by remember { mutableStateOf<DieKind?>(null) }
    var rollResult by remember { mutableStateOf<String?>(null) }
    var rollKey by remember { mutableIntStateOf(0) }
    // The clock shows minutes, so the menu only redraws when the minute turns, not every second.
    var clock by remember(g.startedAt) { mutableStateOf(elapsed(System.currentTimeMillis() - g.startedAt)) }
    LaunchedEffect(g.startedAt) { while (true) { clock = elapsed(System.currentTimeMillis() - g.startedAt); delay(1000) } }

    TableOverlay(0, "${g.format.label} · $clock", emptyList(), 0, {}, onDismiss) {
        val marker = picking
        val pageScroll = remember(marker, choosingFirst) { ScrollState(0) }
        when {
            marker != null -> ScrollPage(pageScroll) {
                Column(Modifier.verticalScroll(pageScroll)) {
                    PlayerPicker(
                        g, "Who has the ${marker.label.lowercase()}?",
                        if (marker == TableMarker.MONARCH) g.monarch else g.initiative,
                        onPick = { who ->
                            vm.update { if (marker == TableMarker.MONARCH) Game.monarch(it, who) else Game.initiative(it, who) }
                            picking = null
                            if (startPick != null) onDismiss()
                        },
                        onBack = { picking = null; if (startPick != null) onDismiss() },
                    )
                }
            }
            choosingFirst -> ScrollPage(pageScroll) {
                Column(Modifier.verticalScroll(pageScroll)) {
                    PlayerPicker(
                        g, "Who goes first?", g.firstSeat,
                        onPick = { who -> if (who != null) vm.update { Game.setFirst(it, who) }; choosingFirst = false },
                        onBack = { choosingFirst = false }, allowNobody = false,
                        // Random picks among the players still in.
                        onRandom = {
                            vm.update { s -> Game.setFirst(s, s.players.indices.filter { s.outReason(it) == null }.randomOrNull() ?: Random.nextInt(s.players.size)) }
                            choosingFirst = false
                        },
                    )
                }
            }
            else -> ScrollPage(pageScroll) {
                Column(Modifier.verticalScroll(pageScroll), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    // The turn: the two things used every few minutes, big.
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        ActionTile(
                            Icons.Outlined.SkipNext, if (g.active == null) "Start turns" else "Next turn", Modifier.weight(1f),
                            g.active?.let { "Turn ${g.turn} · ${g.players[it].name}" }, tint = Palette.Accent,
                        ) { vm.nextTurn() }
                        ActionTile(
                            Icons.AutoMirrored.Outlined.Undo, "Undo", Modifier.weight(1f),
                            (if (with(Game) { g.lastStepHasNoLine() }) "Turn order" else g.history.lastOrNull()?.let { describe(g, it) }), enabled = g.undoStack.isNotEmpty(),
                        ) { vm.undo() }
                    }
                    MenuSection("Table")
                    val table = buildList<@Composable (Modifier) -> Unit> {
                        if (g.players.size > 1) {
                            add { m ->
                                SmallTile(TableMarker.MONARCH.icon, "Monarch", m, g.monarch?.let { g.players[it].name } ?: "Nobody",
                                    tint = if (g.monarch != null) Palette.Gold else Color.White) { picking = TableMarker.MONARCH }
                            }
                            add { m ->
                                SmallTile(TableMarker.INITIATIVE.icon, "Initiative", m, g.initiative?.let { g.players[it].name } ?: "Nobody",
                                    tint = if (g.initiative != null) Palette.Gold else Color.White) { picking = TableMarker.INITIATIVE }
                            }
                        }
                        add { m ->
                            SmallTile(dayNightIcon(g.dayNight), dayNightLabel(g.dayNight), m, "Tap to change",
                                tint = if (g.dayNight != null) Palette.Gold else Color.White) { vm.update { Game.dayNight(it, nextDayNight(it.dayNight, allowOff = true)) } }
                        }
                        if (g.players.size > 1) add { m ->
                            SmallTile(Icons.Outlined.SkipNext, "First player", m, g.firstSeat?.let { g.players.getOrNull(it)?.name } ?: "Pick or random") { choosingFirst = true }
                        }
                    }
                    TileGrid(table.size, minTile = 150) { k, m -> table[k](m) }
                    MenuSection("Dice")
                    if (rollKind != null) RollStage(rollKind, rollResult, rollKey)
                    TileGrid(DieKind.entries.size, minTile = 120) { k, m ->
                        val kind = DieKind.entries[k]
                        SmallTile(com.mtgscanner.ui.components.ManaIcons.Dice, kind.label, m) {
                            rollKind = kind; rollResult = kind.roll(); rollKey++
                        }
                    }
                    MenuSection("Game")
                    val game = buildList<@Composable (Modifier) -> Unit> {
                        add { m ->
                            SmallTile(Icons.Outlined.RestartAlt, if (confirmRestart) "Tap again" else "Restart", m,
                                if (confirmRestart) "Resets to ${g.startingLife}" else "Same players",
                                tint = if (confirmRestart) Palette.Danger else Color.White) {
                                if (confirmRestart) { vm.restart(); confirmRestart = false; onDismiss() } else confirmRestart = true
                            }
                        }
                        add { m -> SmallTile(Icons.Outlined.EmojiEvents, "End game", m, "Save who won") { onEnd() } }
                        add { m -> SmallTile(Icons.AutoMirrored.Outlined.ExitToApp, "Leave table", m, "Game is kept") { onLeave() } }
                        add { m -> SmallTile(Icons.AutoMirrored.Outlined.HelpOutline, "How it works", m, "The short tour") { onTutorial() } }
                    }
                    TileGrid(game.size, minTile = 150) { k, m -> game[k](m) }
                }
            }
        }
    }
}

/** A section name on the game menu, quiet so the tiles lead. */
@Composable
private fun MenuSection(title: String) {
    Text(title, color = Palette.OnDarkMuted, fontSize = 16.sp, modifier = Modifier.padding(top = Space.s3))
}

/** A smaller tile for the less used parts of the game menu: icon beside the label, the detail under it. */
@Composable
private fun SmallTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    detail: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(Corners.Medium), color = Palette.StagePanel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Row(Modifier.padding(horizontal = Space.s3, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Space.s3))
            Column {
                Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, color = Palette.OnDarkMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun describe(g: GameState, e: com.mtgscanner.game.GameEvent): String {
    val change = when {
        e.delta > 0 -> " +${e.delta}"
        e.delta < 0 -> " ${e.delta}"
        else -> ""
    }
    // Table-wide lines (day/night, a cleared monarch) have no player.
    val who = g.players.getOrNull(e.player)?.name ?: return "${e.what}$change"
    return "$who · ${e.what}$change"
}

/** Day, then night. From the menu ([allowOff]) the round goes on to off; the chip on the table only flips day and night. */
fun nextDayNight(v: String?, allowOff: Boolean = false): String? = when (v) {
    null -> "day"
    "day" -> "night"
    else -> if (allowOff) null else "day"
}

fun dayNightIcon(v: String?): ImageVector = when (v) {
    "day" -> com.mtgscanner.ui.components.ManaIcons.Day
    "night" -> com.mtgscanner.ui.components.ManaIcons.Night
    else -> com.mtgscanner.ui.components.ManaIcons.DayNight
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

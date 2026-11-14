package com.mtgscanner.ui.game

import com.mtgscanner.util.plural
import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgscanner.game.GameRecord
import com.mtgscanner.game.GameStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.ruleBelow
import com.mtgscanner.ui.components.ruleAbove
import androidx.compose.ui.graphics.Color

/** A summary of saved games on the Game tab. */
@Composable
fun StatsCard(records: List<GameRecord>, onOpen: () -> Unit) {
    val top = remember(records) { GameStats.byPlayer(records).firstOrNull() }
    Surface(onClick = onOpen, shape = RoundedCornerShape(Corners.Small), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EmojiEvents, null, tint = Palette.Icon)
            Spacer(Modifier.width(Space.s3))
            Column(Modifier.weight(1f)) {
                Text("Game stats", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        records.isEmpty() -> "No games yet"
                        top != null && top.wins > 0 -> "${plural(records.size, "game")} · ${top.name} leads with ${plural(top.wins, "win")}"
                        else -> "${plural(records.size, "game")}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
            }
        }
    }
}

/** Wins per player and the list of past games. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsSheet(records: List<GameRecord>, vm: GameViewModel, onDismiss: () -> Unit) {
    val players = remember(records) { GameStats.byPlayer(records) }
    val avg = remember(records) { GameStats.averageMinutes(records) }
    val fmt = remember { SimpleDateFormat("MMM d", Locale.US) }
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        // A lazy list: years of saved games open as fast as a handful, since only the rows on screen are built.
        LazyColumn(Modifier.padding(horizontal = Space.s4).navigationBarsPadding()) {
            item {
                Column {
                    Text("Game stats", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${records.size} ${if (records.size == 1) "game" else "games"}" + (avg?.let { " · about $it min each" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                }
            }
            if (records.isEmpty()) {
                item { com.mtgscanner.ui.components.EmptyState("No games yet", body = "Finished games show up here with their winner") }
            } else {
                item { Header("Players") }
                items(players) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${p.wins} of ${p.games} won · ${p.winRate}%", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
                item { Header("Past games") }
                items(records) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.winner?.let { "$it won" } ?: "Draw", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${fmt.format(Date(r.endedAt))} · ${r.format.label} · ${r.players.joinToString(", ")}" +
                                    (if (r.turns > 0) " · ${plural(r.turns, "turn")}" else "") + (if (r.minutes > 0) " · ${r.minutes} min" else ""),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            )
                        }
                        IconButton(onClick = { vm.deleteRecord(r) }) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete this game", tint = Palette.Icon, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                item {
                    Column {
                        Spacer(Modifier.height(Space.s2))
                        if (confirmClear) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Delete all ${plural(records.size, "game")}?", modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.clearRecords(); confirmClear = false }) { Text("Delete all", color = Palette.Danger) }
                            TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                        } else TextButton(onClick = { confirmClear = true }) { Text("Clear stats", color = Palette.Muted) }
                    }
                }
            }
            item { Spacer(Modifier.height(Space.s6)) }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.padding(top = Space.s4, bottom = Space.s1))
}

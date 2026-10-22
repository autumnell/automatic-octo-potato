package com.mtgscanner.ui.game

import androidx.compose.foundation.layout.Arrangement
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

/** A summary of saved games on the Game tab. */
@Composable
fun StatsCard(records: List<GameRecord>, onOpen: () -> Unit) {
    val top = remember(records) { GameStats.byPlayer(records).firstOrNull() }
    Surface(onClick = onOpen, shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EmojiEvents, null, tint = Palette.Gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Game stats", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        records.isEmpty() -> "No games yet"
                        top != null && top.wins > 0 -> "${records.size} games · ${top.name} leads with ${top.wins} wins"
                        else -> "${records.size} games"
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
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Game stats", style = MaterialTheme.typography.titleLarge)
            Text(
                "${records.size} games" + (avg?.let { " · about $it min each" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            if (records.isEmpty()) {
                com.mtgscanner.ui.components.EmptyState("No games yet")
            } else {
                Header("Players")
                players.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${p.wins} of ${p.games} won · ${p.winRate}%", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
                Header("Past games")
                records.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.winner?.let { "$it won" } ?: "Draw", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${fmt.format(Date(r.endedAt))} · ${r.format.label} · ${r.players.joinToString(", ")}" +
                                    (if (r.turns > 0) " · ${r.turns} turns" else "") + (if (r.minutes > 0) " · ${r.minutes} min" else ""),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            )
                        }
                        IconButton(onClick = { vm.deleteRecord(r) }) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete this game", tint = Palette.Icon, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (confirmClear) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Delete all ${records.size} games?", modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.clearRecords(); confirmClear = false }) { Text("Delete all", color = Palette.Danger) }
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                } else TextButton(onClick = { confirmClear = true }) { Text("Clear stats", color = Palette.Muted) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.AccentText, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

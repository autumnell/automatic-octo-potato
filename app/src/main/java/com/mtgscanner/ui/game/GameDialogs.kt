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

/** Background art for a player's panel, picked from cards in your collection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtPicker(current: String?, vm: GameViewModel, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val choices by vm.art.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Background", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (current != null) TextButton(onClick = { onPick(null) }) { Text("Remove") }
        TextButton(onClick = { open = !open; if (open) vm.searchArt(query, refresh = true) }) { Text(if (open) "Close" else "Pick card art") }
    }
    if (open) {
        OutlinedTextField(
            query, { query = it; vm.searchArt(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your collection, like your commander") },
        )
        Spacer(Modifier.height(Space.s2))
        if (choices.isEmpty()) Text("No cards with art found. Scan some cards first.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            choices.take(24).forEach { c ->
                Column(Modifier.width(96.dp).clip(RoundedCornerShape(Corners.Small)).clickable { onPick(c.art); open = false }) {
                    AsyncImage(c.art, c.name, Modifier.fillMaxWidth().height(70.dp)
                        .border(if (c.art == current) 2.dp else 0.dp, Palette.Accent, RoundedCornerShape(Corners.Small)), contentScale = ContentScale.Crop)
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
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { winner = i }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(winner == i, { winner = i })
                            Text(p.name)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { winner = null }, verticalAlignment = Alignment.CenterVertically) {
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

package com.mtgscanner.ui.decks.playtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums

/**
 * Before the first turn: your opening 7, big, with Mulligan (a new 7) and Keep. After a mulligan, keeping asks
 * which cards go on the bottom (the London mulligan; the first one is free in Commander), then play starts.
 */
@Composable
internal fun OpeningHand(t: Table, vm: PlaytestViewModel, onBack: () -> Unit) {
    val me = t.sides[0]
    val hand = me[Zone.HAND]
    val n = vm.toBottom()
    var choosing by remember(me.mulligans) { mutableStateOf(false) }
    var bottom by remember(me.mulligans) { mutableStateOf(emptyList<Int>()) }
    val lands = hand.count { it.card.isLand }

    Column(Modifier.fillMaxSize().background(Palette.Stage).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.s1), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Leave playtest", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Opening hand", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    when {
                        choosing -> "Tap ${if (n == 1) "a card" else "$n cards"} to put on the bottom"
                        me.mulligans == 0 -> "Keep these 7, or mulligan for a new 7"
                        n == 0 -> "Mulligan 1, the first one is free"
                        else -> "Mulligan ${me.mulligans}, you'll put $n on the bottom"
                    },
                    style = MaterialTheme.typography.labelMedium, color = Palette.OnDarkMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Space.s4)) {
            val w: Dp = (maxWidth - Space.s2 * 3) / 4
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                hand.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        row.forEach { p ->
                            val down = p.id in bottom
                            val toggle = { bottom = if (down) bottom - p.id else if (bottom.size < n) bottom + p.id else bottom }
                            Box(Modifier.size(w, w * 1.4f)) {
                                Box(Modifier.fillMaxSize().alpha(if (down) 0.35f else 1f)) {
                                    if (p.card.imageUrl == null) PlainFace(p.card.name, null, Modifier.fillMaxSize().clickable(enabled = choosing, onClick = toggle))
                                    else CardImage(p.card.imageUrl, Modifier.fillMaxSize(), zoom = true, onTap = if (choosing) toggle else null)
                                }
                                if (down) Text(
                                    "Bottom", style = MaterialTheme.typography.labelSmall, color = Color.White,
                                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(Corners.Small))
                                        .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = Space.s1),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "${if (lands == 1) "1 land" else "$lands lands"} · ${hand.size - lands} other · ${me[Zone.LIBRARY].size} in library",
            style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.OnDarkMuted,
            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s3),
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            if (!choosing) {
                StageButton("Mulligan", Modifier.weight(1f), enabled = me.mulligans < Playtest.HAND) { vm.mulliganOpening() }
                GradientButton("Keep", { if (n == 0) vm.keep(emptyList()) else choosing = true }, Modifier.weight(1.4f))
            } else {
                StageButton("Back", Modifier.weight(1f)) { choosing = false; bottom = emptyList() }
                GradientButton(
                    if (bottom.size < n) "Pick ${n - bottom.size} more" else "Put on the bottom",
                    { vm.keep(bottom) }, Modifier.weight(1.4f), enabled = bottom.size == n,
                )
            }
        }
    }
}

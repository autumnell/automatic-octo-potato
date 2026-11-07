package com.mtgscanner.ui.home

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.FirstSetup
import com.mtgscanner.scan.ArtModel
import com.mtgscanner.scan.CardPrints
import com.mtgscanner.scan.ForeignReading
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.delay

/** One download on the setup screen: done, failed, or how far along (null while it hasn't reported yet). */
private data class SetupStep(val label: String, val done: Boolean, val failed: Boolean = false, val progress: Float? = null, val note: String? = null)

/**
 * The first open: downloads everything the scanner uses (see [FirstSetup]) with a progress bar for each, then opens
 * the app. Each download carries on if you leave this screen.
 */
@Composable
fun FirstSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { (ctx.applicationContext as MtgApp).repository }
    LaunchedEffect(Unit) { FirstSetup.start(ctx) }
    var steps by remember { mutableStateOf(emptyList<SetupStep>()) }
    // The parts report in different ways; reading them a few times a second is plenty for progress bars.
    LaunchedEffect(Unit) {
        while (true) {
            val list = repo.localCards.status.value
            val prints = CardPrints.state.value
            val finder = CardPrints.finderDownload.value
            val reading = ForeignReading.state.value
            val art = repo.artEmbedder
            val artDl = art.download.value
            steps = listOf(
                SetupStep(
                    "Card list", done = list.cards > 0 && list.progress == null, failed = list.failed && list.cards == 0,
                    progress = list.progress, note = list.reason?.takeIf { list.failed },
                ),
                when (prints) {
                    CardPrints.State.Ready -> SetupStep("Card pictures", done = true)
                    is CardPrints.State.Downloading -> SetupStep("Card pictures", false, progress = prints.percent / 100f)
                    CardPrints.State.Preparing -> SetupStep("Card pictures", false, note = "Getting it ready")
                    is CardPrints.State.Failed -> SetupStep("Card pictures", false, failed = true, note = prints.reason)
                    CardPrints.State.Off -> SetupStep("Card pictures", false)
                },
                SetupStep(
                    // -1 means this build has no finder model: nothing to wait for, so it counts as done.
                    "Card finder (YOLO)", done = prints == CardPrints.State.Ready && (finder == null || finder == -1),
                    note = if (finder == -1) "Not in this build" else null,
                ),
                SetupStep(
                    "Art model", done = art.model == ArtModel.ADVANCED && art.ready, failed = art.failed.value,
                    progress = artDl?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total },
                ),
                when (reading) {
                    ForeignReading.State.Ready -> SetupStep("Japanese, Chinese, Korean names", done = true)
                    is ForeignReading.State.Downloading -> SetupStep("Japanese, Chinese, Korean names", false, progress = reading.percent?.div(100f))
                    ForeignReading.State.Failed -> SetupStep("Japanese, Chinese, Korean names", false, failed = true)
                    ForeignReading.State.Off -> SetupStep("Japanese, Chinese, Korean names", false)
                },
            )
            if (steps.all { it.done }) {
                FirstSetup.markDone(ctx)
                onDone()
                return@LaunchedEffect
            }
            delay(400)
        }
    }
    val failed = steps.any { it.failed }
    val finished = steps.count { it.done }
    Column(
        Modifier.fillMaxSize().background(Palette.Background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.s6),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Getting the scanner ready", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Space.s2))
        Text(
            "Every card, its pictures and the scanning models, so cards are found fast and offline. A few hundred MB, once. " +
                "Best on Wi-Fi",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
        )
        Spacer(Modifier.height(Space.s6))
        LinearProgressIndicator(
            progress = { if (steps.isEmpty()) 0f else finished.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Corners.Small)),
            color = Palette.Text,
        )
        Spacer(Modifier.height(Space.s4))
        steps.forEach { s -> StepRow(s) }
        Spacer(Modifier.height(Space.s6))
        if (failed) {
            GradientButton("Try again", onClick = { FirstSetup.start(ctx) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.s2))
        }
        TextButton(onClick = { FirstSetup.markDone(ctx); onDone() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Use the app now, finish in the background")
        }
    }
}

@Composable
private fun StepRow(s: SetupStep) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                s.done -> Icon(Icons.Outlined.CheckCircle, null, tint = Palette.Success, modifier = Modifier.size(18.dp))
                s.failed -> Icon(Icons.Outlined.ErrorOutline, null, tint = Palette.Danger, modifier = Modifier.size(18.dp))
                else -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Text)
            }
            Spacer(Modifier.width(Space.s3))
            Text(s.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val right = when {
                s.done -> "Done"
                s.failed -> "Failed"
                s.progress != null -> "${(s.progress * 100).toInt()}%"
                else -> s.note ?: "Waiting"
            }
            Text(right, style = MaterialTheme.typography.bodySmall, color = if (s.failed) Palette.Danger else Palette.Muted)
        }
        if (s.failed && s.note != null) {
            Text(s.note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = Space.s8, top = Space.s1))
        }
        if (!s.done && !s.failed && s.progress != null) {
            LinearProgressIndicator(
                progress = { s.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(start = Space.s8, top = Space.s2).height(4.dp).clip(RoundedCornerShape(Corners.Small)),
                color = Palette.Text,
            )
        }
    }
}

package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** One coach mark: the control it points at ([id]), a title and one short sentence. */
data class ScanHint(val id: String, val title: String, val body: String)

/** In order. A hint whose control isn't on screen is left out. */
val SCAN_HINTS = listOf(
    ScanHint("list", "Scan list", "Every card you've scanned"),
    ScanHint("flash", "Flashlight", "Light for dim rooms"),
    ScanHint("settings", "Settings", "Foil, set, language, copies and sounds"),
    ScanHint("last", "Last card", "Tap to open it"),
)

/** Where each hinted control sits on screen, in window coordinates. */
val LocalHintTargets = staticCompositionLocalOf<SnapshotStateMap<String, Rect>?> { null }

/** Marks a control a scan hint can point at. */
@Composable
fun Modifier.hintTarget(id: String): Modifier {
    val map = LocalHintTargets.current ?: return this
    // Forget the spot when the control leaves the screen.
    DisposableEffect(map, id) { onDispose { map.remove(id) } }
    return this.onGloballyPositioned { c ->
        val r = c.boundsInWindow()
        if (map[id] != r) map[id] = r
    }
}

/** Dims the screen, rings one control at a time and explains it, with Next and Skip. */
@Composable
fun ScanHintsOverlay(targets: Map<String, Rect>, onDone: () -> Unit) {
    val steps = SCAN_HINTS.filter { targets[it.id] != null }
    if (steps.isEmpty()) return
    var step by remember { mutableStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var boxHeight by remember { mutableStateOf(0) }
    BackHandler(onBack = onDone)

    val i = step.coerceIn(0, steps.lastIndex)
    val hint = steps[i]
    val target = targets[hint.id]?.translate(-origin.x, -origin.y) ?: return
    val accent = Palette.Accent
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow(); boxHeight = it.size.height }
            // Catch taps so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 6.dp.toPx()
            val corner = CornerRadius(14.dp.toPx())
            val hole = Rect(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad)
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(hole, corner))
            }
            drawPath(scrim, Color.Black.copy(alpha = 0.72f))
            drawRoundRect(accent, topLeft = hole.topLeft, size = hole.size, cornerRadius = corner, style = Stroke(2.dp.toPx()))
        }

        val density = LocalDensity.current
        val gap = with(density) { 18.dp.toPx() }
        // Card below the control when it's in the top half, above it otherwise.
        val below = target.center.y < boxHeight / 2f
        val cardModifier = if (below) {
            Modifier.align(Alignment.TopCenter).padding(top = with(density) { (target.bottom + gap).coerceAtLeast(0f).toDp() })
        } else {
            Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { (boxHeight - target.top + gap).coerceAtLeast(0f).toDp() })
        }
        Surface(
            cardModifier.padding(horizontal = Space.s6).widthIn(max = 360.dp).fillMaxWidth(),
            shape = RoundedCornerShape(Corners.Medium),
            color = Palette.Surface,
        ) {
            Column(Modifier.padding(Space.s4)) {
                Text("${i + 1} of ${steps.size}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text(hint.title, style = MaterialTheme.typography.titleMedium)
                Text(hint.body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                Spacer(Modifier.height(Space.s3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDone) { Text("Skip", color = Palette.Muted) }
                    Spacer(Modifier.weight(1f))
                    val last = i == steps.lastIndex
                    GradientButton(if (last) "Done" else "Next", { if (last) onDone() else step = i + 1 })
                }
            }
        }
    }
}

package com.mtgscanner.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgscanner.ui.components.Mana
import com.mtgscanner.ui.components.ManaIcons
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.mtgscanner.ui.theme.Corners

/** What can be rolled or flipped. */
enum class DieKind(val label: String) {
    COIN("Coin"), D6("d6"), D20("d20"), PLANAR("Planar die");

    fun roll(): String = when (this) {
        COIN -> if (Random.nextBoolean()) "Heads" else "Tails"
        D6 -> "${Random.nextInt(1, 7)}"
        D20 -> "${Random.nextInt(1, 21)}"
        PLANAR -> when (Random.nextInt(6)) { 0 -> "Planeswalk"; 1 -> "Chaos"; else -> "Blank" }
    }
}

/**
 * A big coin or die that flips and tumbles in 3D, then lands on [result].
 * A new [rollKey] starts a new throw.
 */
@Composable
fun RollStage(kind: DieKind?, result: String?, rollKey: Int, modifier: Modifier = Modifier) {
    val spinX = remember { Animatable(0f) }
    val spinY = remember { Animatable(0f) }
    val lift = remember { Animatable(1f) }
    var face by remember { mutableStateOf(result) }
    var settled by remember { mutableStateOf(true) }

    LaunchedEffect(rollKey) {
        if (kind == null || result == null) return@LaunchedEffect
        settled = false
        spinX.snapTo(0f); spinY.snapTo(0f)
        if (kind == DieKind.COIN) {
            face = result
            // Several half turns, ending on the side that came up; it rises and falls like a real toss.
            val target = 180f * (12 + if (result == "Tails") 1 else 0)
            launch {
                lift.animateTo(1.45f, tween(500, easing = FastOutSlowInEasing))
                lift.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
            }
            spinX.animateTo(target, tween(1000, easing = FastOutSlowInEasing))
        } else {
            // Tumble with the faces flicking past, then land.
            val flicker = launch { while (true) { face = kind.roll(); delay(70) } }
            launch { spinY.animateTo(720f, tween(850, easing = FastOutSlowInEasing)) }
            launch {
                lift.animateTo(1.25f, tween(300))
                lift.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            }
            spinX.animateTo(1080f, tween(850, easing = FastOutSlowInEasing))
            flicker.cancel()
            face = result
            lift.animateTo(1.12f, tween(80))
            lift.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        settled = true
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            if (kind == null) {
                Text("Tap a die", color = Color.White.copy(alpha = 0.7f), fontSize = 30.sp)
            } else {
                Box(
                    Modifier.size(120.dp).graphicsLayer {
                        rotationX = spinX.value
                        rotationY = spinY.value
                        scaleX = lift.value
                        scaleY = lift.value
                        cameraDistance = 14f * density
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    when (kind) {
                        DieKind.COIN -> {
                            // The back of the coin shows once it has turned past edge-on.
                            val angle = ((spinX.value % 360f) + 360f) % 360f
                            Coin(if (angle in 90f..270f) "Tails" else "Heads", flipped = angle in 90f..270f)
                        }
                        DieKind.D6 -> DieFace(face.orEmpty(), RoundedCornerShape(Corners.Medium), Color(0xFFF4F1EA), Color(0xFF15161A))
                        DieKind.D20 -> D20Face(face.orEmpty())
                        DieKind.PLANAR -> PlanarFace(face.orEmpty())
                    }
                }
            }
        }
        Text(
            if (kind != null && settled && result != null) "${kind.label}: $result" else " ",
            color = Palette.Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
        )
    }
}

@Composable
private fun Coin(side: String, flipped: Boolean) {
    val heads = side == "Heads"
    val colors = if (heads) listOf(Color(0xFFF7DC7A), Color(0xFFB8860B)) else listOf(Color(0xFFE3E6EA), Color(0xFF8A9099))
    Box(
        Modifier.fillMaxSize().graphicsLayer { if (flipped) rotationX = 180f }
            .clip(CircleShape).background(Brush.radialGradient(colors))
            .border(5.dp, colors[1], CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (heads) Icon(ManaIcons.Monarch, "Heads", tint = Color(0xFF5A4300), modifier = Modifier.size(64.dp))
        else Text("T", color = Color(0xFF3A3F47), fontSize = 58.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DieFace(text: String, shape: androidx.compose.ui.graphics.Shape, bg: Color, ink: Color) {
    Box(
        Modifier.fillMaxSize().clip(shape).background(bg).border(3.dp, Color.Black.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink, fontSize = 60.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun D20Face(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(ManaIcons.Dice, null, tint = Palette.Gold, modifier = Modifier.fillMaxSize())
        Text(text, color = Color(0xFF15161A), fontSize = 38.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlanarFace(face: String) {
    val shape = RoundedCornerShape(Corners.Medium)
    Box(
        Modifier.fillMaxSize().clip(shape).background(Color(0xFF2B2F3A)).border(3.dp, Color.White.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (face) {
            "Planeswalk" -> Icon(ManaIcons.Planeswalker, "Planeswalk", tint = Color.White, modifier = Modifier.size(72.dp))
            "Chaos" -> Mana.Icon("chaos", Color.White, 72.dp)
            else -> {}
        }
    }
}

package com.mtgscanner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.mtgscanner.ui.theme.Motion

/**
 * Shrinks a tile a little while a finger is on it and eases it back, so tapping a card, deck or box feels
 * physical. No bounce, and none at all with animations turned off. Pass the same [source] the tile's click uses.
 */
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val down by source.collectIsPressedAsState()
    val ctx = LocalContext.current
    val still = remember(ctx) { animationsOff(ctx) }
    val scale by animateFloatAsState(
        if (down && !still) pressed else 1f,
        tween(Motion.fast, easing = Motion.ease),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

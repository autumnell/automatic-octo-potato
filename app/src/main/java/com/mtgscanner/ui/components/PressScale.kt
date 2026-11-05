package com.mtgscanner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.mtgscanner.ui.theme.Motion

/**
 * Shrinks a tile a little while a finger is on it and eases it back, so tapping a card, deck or box feels
 * physical. No bounce. Pass the same [source] the tile's click uses.
 */
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (down) pressed else 1f,
        tween(Motion.fast, easing = Motion.ease),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

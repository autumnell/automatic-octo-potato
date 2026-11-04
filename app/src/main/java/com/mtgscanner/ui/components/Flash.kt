package com.mtgscanner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import com.mtgscanner.ui.theme.Palette

/** A soft accent glow over a row that fades over a second, when [on]: marks a card that was just added. */
fun Modifier.flashOnce(on: Boolean): Modifier = if (!on) this else composed {
    val glow = remember { Animatable(0.22f) }
    LaunchedEffect(Unit) { glow.animateTo(0f, tween(1200)) }
    drawWithContent {
        drawContent()
        val a = glow.value
        if (a > 0f) drawRoundRect(Palette.Accent.copy(alpha = a), cornerRadius = CornerRadius(6f, 6f))
    }
}

/**
 * Remembers which ids a list has already shown, so [isNew] is true only for ones that turn up later
 * (an add while the list is open), not for the first load.
 */
class NewIds {
    private val seen = HashSet<Long>()
    private var primed = false

    /** Call once per frame with every id in the list. */
    fun update(ids: Collection<Long>): Set<Long> {
        val fresh = if (primed) ids.filterTo(HashSet()) { it !in seen } else emptySet()
        seen.addAll(ids)
        if (ids.isNotEmpty()) primed = true
        return fresh
    }
}

package com.mtgscanner.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mtgscanner.ui.components.animationsOff

/** How long a card picture takes to grow into the card page. */
const val CARD_GROW_MS = 280

/** The app's shared-element scope, set by MainActivity around the screens. Null where there's none. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The screen's own enter/exit animation, set per screen by MainActivity. */
val LocalScreenAnimation = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Gives [content] its screen's animation scope, so card pictures in it can grow into the card page. */
@Composable
fun ScreenAnimation(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScreenAnimation provides scope, content = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val CardBounds = BoundsTransform { _, _ -> tween(CARD_GROW_MS, easing = FastOutSlowInEasing) }

/**
 * Marks a card picture for collection row [rowId]. Opening that card's page grows this picture into
 * the big one there, and going back shrinks it home. Does nothing outside the app's screens or
 * with animations off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCard(rowId: Long): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    val screen = LocalScreenAnimation.current ?: return this
    val ctx = LocalContext.current
    val still = remember(ctx) { animationsOff(ctx) }
    if (still || rowId == 0L) return this
    return with(shared) {
        this@sharedCard.sharedElement(
            rememberSharedContentState(key = "card-$rowId"),
            animatedVisibilityScope = screen,
            boundsTransform = CardBounds,
        )
    }
}

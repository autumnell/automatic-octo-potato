package com.mtgscanner.ui.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch

/** Remembers whether the first-run tour has been seen. */
object Tour {
    fun seen(context: Context) = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("tourSeen", false)
    fun setSeen(context: Context, seen: Boolean) =
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("tourSeen", seen).apply()
}

private data class TourPage(val icon: ImageVector, val title: String, val body: String)

private val PAGES = listOf(
    TourPage(Icons.Filled.Inventory2, "Make a box", "One for each real box or binder. Add rules, or let it take anything."),
    TourPage(Icons.Filled.CameraAlt, "Scan", "Hold a card up. The app says which box it goes in."),
    TourPage(Icons.Filled.Search, "Find a card", "Search your collection to see which box it's in."),
)

/** Three swipeable pages shown on first launch. Can be shown again from Settings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FirstRunTour(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onDone)
    Box(
        Modifier.fillMaxSize().background(Palette.Background)
            // Catch taps so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding(),
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val page = PAGES[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(110.dp).clip(CircleShape).background(Palette.Surface), contentAlignment = Alignment.Center) {
                    Icon(page.icon, null, tint = Palette.Accent, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAGES.indices.forEach { i ->
                    Box(Modifier.size(if (pager.currentPage == i) 10.dp else 8.dp).clip(CircleShape)
                        .background(if (pager.currentPage == i) Palette.Accent else Palette.Outline))
                }
            }
            Spacer(Modifier.height(20.dp))
            val last = pager.currentPage == PAGES.lastIndex
            GradientButton(
                if (last) "Get started" else "Next",
                { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.width(1.dp))
        }
        // Drawn last so it sits above the pager and gets the tap.
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("Skip", color = Palette.Muted) }
    }
}

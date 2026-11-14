package com.mtgscanner.ui.components

import com.mtgscanner.util.plural
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mtgscanner.MtgApp
import com.mtgscanner.ui.theme.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pull down to refresh prices. With prices hidden in Settings, it's a plain box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceRefreshBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (!AppSettings.showPrices) {
        Box(modifier, content = content)
        return
    }
    // The spinner only goes away once it has seen [refreshing] turn on and off again. A refresh that ends within
    // one frame (no connection, an empty box) never shows as on and would leave it hanging, so hold it on briefly.
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(held) {
        if (held) {
            delay(REFRESH_HOLD_MS)
            held = false
        }
    }
    PullToRefreshBox(isRefreshing = refreshing || held, onRefresh = { held = true; onRefresh() }, modifier = modifier, content = content)
}

private const val REFRESH_HOLD_MS = 500L

/**
 * [PriceRefreshBox] for screens without their own refresh, saying how it went in the app's snackbar. With [cards]
 * (a box's or a deck's printings) only those prices are refreshed; without, the whole collection's, as on Collection.
 */
@Composable
fun PriceRefreshBox(
    modifier: Modifier = Modifier,
    cards: (suspend () -> Collection<String>)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PriceRefreshBox(
        refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshing = true
                scope.launch {
                    val msg = com.mtgscanner.util.suspendRunCatching { if (cards != null) repo.refreshPricesOf(cards()) else repo.refreshPrices() }
                        .fold({ "Updated prices for ${plural(it, "card")}" }, { "Prices not refreshed. Check your connection and pull down to try again" })
                    refreshing = false
                    Undo.offer(msg)
                }
            }
        },
        modifier = modifier,
        content = content,
    )
}

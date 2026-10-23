package com.mtgscanner.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mtgscanner.widget.WidgetText
import kotlinx.coroutines.delay

/**
 * "Prices updated 3h ago", from the time the last price refresh finished (CollectionRepository saves it as
 * "pricesAt"); empty when prices were never fetched. Follows a refresh at once and ticks every minute.
 */
@Composable
fun rememberPricesUpdated(): String {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { ctx.getSharedPreferences("collection", Context.MODE_PRIVATE) }
    var at by remember { mutableLongStateOf(prefs.getLong("pricesAt", 0L)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "pricesAt") {
                at = p.getLong("pricesAt", 0L)
                now = System.currentTimeMillis()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    return WidgetText.pricesUpdated(at, now)
}

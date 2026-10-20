package com.mtgscanner.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App-wide options from Settings that aren't about looks. Changes show up immediately. */
object AppSettings {
    private var prefs: SharedPreferences? = null

    /** When off, the app shows no prices, values, gains or losses anywhere, and sends no price alerts. */
    var showPrices by mutableStateOf(true)
        private set

    /** Daily notification when a card you own or want changes price by more than these amounts. */
    var priceAlerts by mutableStateOf(false)
        private set
    var alertPercent by mutableDoubleStateOf(20.0)
        private set
    var alertDollars by mutableDoubleStateOf(1.0)
        private set

    /** Larger text everywhere, mainly card names and prices. */
    var bigText by mutableStateOf(false)
        private set

    /** Developer-ish: the hidden scan log is switched on. */
    var scanLogUnlocked by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        prefs = p
        showPrices = p.getBoolean("showPrices", true)
        priceAlerts = p.getBoolean("priceAlerts", false)
        alertPercent = p.getFloat("alertPercent", 20f).toDouble()
        alertDollars = p.getFloat("alertDollars", 1f).toDouble()
        scanLogUnlocked = p.getBoolean("scanLog", false)
        bigText = p.getBoolean("bigText", false)
    }

    fun updateShowPrices(v: Boolean) { showPrices = v; prefs?.edit()?.putBoolean("showPrices", v)?.apply() }
    fun updatePriceAlerts(v: Boolean) { priceAlerts = v; prefs?.edit()?.putBoolean("priceAlerts", v)?.apply() }
    fun updateAlertPercent(v: Double) { alertPercent = v; prefs?.edit()?.putFloat("alertPercent", v.toFloat())?.apply() }
    fun updateAlertDollars(v: Double) { alertDollars = v; prefs?.edit()?.putFloat("alertDollars", v.toFloat())?.apply() }
    fun updateBigText(v: Boolean) { bigText = v; prefs?.edit()?.putBoolean("bigText", v)?.apply() }
    fun unlockScanLog(v: Boolean) { scanLogUnlocked = v; prefs?.edit()?.putBoolean("scanLog", v)?.apply() }

    /** Read outside Compose (workers). */
    fun alertsWanted(context: Context): Boolean {
        val p = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        return p.getBoolean("priceAlerts", false) && p.getBoolean("showPrices", true)
    }
}

package com.mtgscanner.work

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * "Tell me if it goes over [above] / under [below]" for one printing and finish.
 * [firedAbove] / [firedBelow] are set once a crossing was notified, and cleared when the price goes back.
 */
@Serializable
data class CardPriceAlert(
    val scryfallId: String,
    val foil: Boolean,
    val name: String,
    val setCode: String = "",
    val above: Double? = null,
    val below: Double? = null,
    val firedAbove: Boolean = false,
    val firedBelow: Boolean = false,
    val createdAt: Long = 0,
) {
    val key: String get() = key(scryfallId, foil)

    companion object {
        fun key(scryfallId: String, foil: Boolean) = "$scryfallId:$foil"
    }
}

/** One crossing to notify: [up] is "went over", else "went under". */
data class AlertHit(val alert: CardPriceAlert, val price: Double, val up: Boolean)

/** Pure crossing logic, kept free of Android so it can be tested. */
object CardAlertMath {
    /** A new alert, armed against today's [price]: a line already crossed only fires after the price comes back and crosses again. */
    fun create(base: CardPriceAlert, price: Double?): CardPriceAlert = base.copy(
        firedAbove = price != null && base.above != null && price > base.above,
        firedBelow = price != null && base.below != null && price < base.below,
    )

    /**
     * Checks [alert] against [price]. Returns the alert with updated flags and the crossings to notify.
     * A crossing fires once; going back past the line re-arms it.
     */
    fun check(alert: CardPriceAlert, price: Double?): Pair<CardPriceAlert, List<AlertHit>> {
        if (price == null) return alert to emptyList()
        val hits = mutableListOf<AlertHit>()
        var a = alert
        if (a.above != null) {
            if (price > a.above!!) {
                if (!a.firedAbove) { hits += AlertHit(a, price, up = true); a = a.copy(firedAbove = true) }
            } else if (a.firedAbove) a = a.copy(firedAbove = false)
        }
        if (a.below != null) {
            if (price < a.below!!) {
                if (!a.firedBelow) { hits += AlertHit(a, price, up = false); a = a.copy(firedBelow = true) }
            } else if (a.firedBelow) a = a.copy(firedBelow = false)
        }
        return a to hits.map { it.copy(alert = a) }
    }

    /** Checks every alert against [prices] (by [CardPriceAlert.key]); alerts with no price are kept as they are. */
    fun checkAll(alerts: List<CardPriceAlert>, prices: Map<String, Double?>): Pair<List<CardPriceAlert>, List<AlertHit>> {
        val hits = mutableListOf<AlertHit>()
        val next = alerts.map { a ->
            val (n, h) = check(a, prices[a.key])
            hits += h
            n
        }
        return next to hits
    }
}

/** Per-card price alerts, saved on the phone as JSON. Every caller shares one list. */
object CardAlertStore {
    private const val KEY = "cardPriceAlerts"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(CardPriceAlert.serializer())
    private var prefs: SharedPreferences? = null
    private val _alerts = MutableStateFlow<List<CardPriceAlert>>(emptyList())
    val alerts: StateFlow<List<CardPriceAlert>> = _alerts

    @Synchronized
    fun load(context: Context): List<CardPriceAlert> {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)
            prefs = p
            _alerts.value = p.getString(KEY, null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()
        }
        return _alerts.value
    }

    fun hasAny(context: Context): Boolean = load(context).isNotEmpty()

    fun find(context: Context, scryfallId: String, foil: Boolean): CardPriceAlert? =
        load(context).firstOrNull { it.key == CardPriceAlert.key(scryfallId, foil) }

    @Synchronized
    private fun write(list: List<CardPriceAlert>) {
        _alerts.value = list
        prefs?.edit()?.putString(KEY, json.encodeToString(ser, list))?.apply()
    }

    /** Saves [alert] (replacing one for the same printing and finish), or removes it when it has no line set. */
    @Synchronized
    fun put(context: Context, alert: CardPriceAlert) {
        val rest = load(context).filterNot { it.key == alert.key }
        write(if (alert.above == null && alert.below == null) rest else listOf(alert) + rest)
        PriceAlerts.schedule(context)
    }

    @Synchronized
    fun remove(context: Context, key: String) {
        write(load(context).filterNot { it.key == key })
        PriceAlerts.schedule(context)
    }

    /** Checks all alerts against [prices], saves the new flags and returns crossings to notify. */
    @Synchronized
    fun check(context: Context, prices: Map<String, Double?>): List<AlertHit> {
        val (next, hits) = CardAlertMath.checkAll(load(context), prices)
        if (next != _alerts.value) write(next)
        return hits
    }
}

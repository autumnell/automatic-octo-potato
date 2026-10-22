package com.mtgscanner.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** Which prices the app shows: Scryfall's dollar (TCGplayer) prices, euro (Cardmarket) prices, or both. */
enum class PriceCurrency(val label: String) {
    USD("USD"), EUR("EUR"), BOTH("Both");

    companion object {
        fun of(name: String?): PriceCurrency = entries.firstOrNull { it.name == name } ?: USD
    }
}

/** Money as text. Pure Kotlin, so it can be tested without a phone. */
object Money {
    private val usdFormat = NumberFormat.getCurrencyInstance(Locale.US)

    fun usd(v: Double?): String = if (v == null) "--" else synchronized(usdFormat) { usdFormat.format(v) }

    /** Like "€1,234.50", written the same way as dollar prices. */
    fun eur(v: Double?): String =
        if (v == null) "--" else (if (v < 0) "-" else "") + "€" + String.format(Locale.US, "%,.2f", abs(v))

    /**
     * A price in [currency]. Both shows "$2.10 · €1.90", or just the dollar price when there's no euro price.
     * Euro alone shows "--" when there's no euro price, so a missing price never reads as a dollar one.
     */
    fun text(usd: Double?, eur: Double?, currency: PriceCurrency): String = when (currency) {
        PriceCurrency.USD -> usd(usd)
        PriceCurrency.EUR -> eur(eur)
        PriceCurrency.BOTH -> when {
            eur == null -> usd(usd)
            usd == null -> eur(eur)
            else -> "${usd(usd)} · ${eur(eur)}"
        }
    }
}

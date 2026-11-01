package com.mtgscanner.data

import android.content.Context
import com.mtgscanner.MtgApp
import com.mtgscanner.scan.ArtModel
import com.mtgscanner.scan.CardPrints
import com.mtgscanner.scan.ForeignReading

/**
 * Everything the scanner uses, downloaded on the first open (and once more on phones that had it turned off before):
 * the card list, the card picture model with every printing's picture, the YOLO card finder, the Advanced art model
 * and the Japanese, Chinese and Korean readers. The best of each, since they're no longer picked in Settings.
 */
object FirstSetup {
    private const val PREFS = "first_setup"
    /** Raised when what's downloaded changes, so phones set up before get the new parts once. */
    private const val VERSION = 1

    fun done(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("done", 0) >= VERSION

    fun markDone(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("done", VERSION).apply()
    }

    /** Starts every download (each skips what's already here). Safe to call again to retry. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val repo = (app as MtgApp).repository
        repo.localCards.enabled = true
        repo.localCards.artAuto = true
        com.mtgscanner.work.CardListWorker.start(app, force = true)
        CardPrints.setFinder(app, CardPrints.Finder.YOLO)
        if (CardPrints.state.value !is CardPrints.State.Ready) CardPrints.turnOn(app)
        if (!ForeignReading.ready) ForeignReading.turnOn(app)
        if (repo.artEmbedder.model != ArtModel.ADVANCED) repo.artEmbedder.select(ArtModel.ADVANCED) else repo.artEmbedder.ensure(force = true)
    }
}

package com.mtgscanner

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallApi
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.ThemeSettings
import com.mtgscanner.ui.theme.FontSettings

class MtgApp : Application(), ImageLoaderFactory {
    lateinit var repository: CollectionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        ThemeSettings.init(this)
        FontSettings.init(this)
        AppSettings.init(this)
        com.mtgscanner.data.JustTcg.init(this)
        com.mtgscanner.data.SortPrefs.init(this)
        // Card Kingdom's buy list is big: it's fetched only when Trade or Sell opens (CardKingdom.init there).
        com.mtgscanner.scan.ScanPrefs.init(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "collection.db")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        repository = CollectionRepository(this, db, ScryfallApi())
        repository.startBackgroundWork()
        com.mtgscanner.work.PriceAlerts.schedule(this)
    }

    /** Card images fade in, and set symbols (SVG) can be drawn. */
    // Scryfall's image servers expect an app name, like its API does.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            com.mtgscanner.data.sharedHttpClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "MTGScanner/0.1 (personal collection app)")
                            .header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                            .build(),
                    )
                }
                .build()
        }
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()
}

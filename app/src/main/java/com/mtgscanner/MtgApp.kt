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

class MtgApp : Application(), ImageLoaderFactory {
    lateinit var repository: CollectionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        ThemeSettings.init(this)
        AppSettings.init(this)
        com.mtgscanner.data.JustTcg.init(this)
        com.mtgscanner.data.SortPrefs.init(this)
        com.mtgscanner.scan.ScanPrefs.init(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "collection.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()
        repository = CollectionRepository(this, db, ScryfallApi())
        repository.startBackgroundWork()
        com.mtgscanner.work.PriceAlerts.schedule(this)
    }

    /** Card images fade in, and set symbols (SVG) can be drawn. */
    // Scryfall's image servers expect an app name, like its API does.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            okhttp3.OkHttpClient.Builder()
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

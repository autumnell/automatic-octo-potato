package com.mtgscanner

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
        // Price refresh, lookups and the name list only when a screen opens: a background job or the
        // widget waking the app shouldn't spend battery, data and JustTCG requests on them.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!backgroundStarted) {
                    backgroundStarted = true
                    repository.startBackgroundWork()
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        com.mtgscanner.work.PriceAlerts.schedule(this)
        com.mtgscanner.work.Notifications.scheduleDigest(this)
    }

    private var backgroundStarted = false

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

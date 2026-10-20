package com.mtgscanner

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallApi
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.ui.theme.ThemeSettings

class MtgApp : Application(), ImageLoaderFactory {
    lateinit var repository: CollectionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        ThemeSettings.init(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "collection.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        repository = CollectionRepository(this, db, ScryfallApi())
        repository.startBackgroundWork()
    }

    /** Card images fade in, and set symbols (SVG) can be drawn. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()
}

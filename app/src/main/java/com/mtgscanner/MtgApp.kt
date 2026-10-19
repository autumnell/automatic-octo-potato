package com.mtgscanner

import android.app.Application
import androidx.room.Room
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallApi
import com.mtgscanner.data.db.AppDatabase

class MtgApp : Application() {
    lateinit var repository: CollectionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "collection.db").build()
        repository = CollectionRepository(db.cardDao(), ScryfallApi())
    }
}

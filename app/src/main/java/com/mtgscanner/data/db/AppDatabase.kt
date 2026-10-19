package com.mtgscanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CollectionCard::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}

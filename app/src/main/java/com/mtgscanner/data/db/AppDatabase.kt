package com.mtgscanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CollectionCard::class, Box::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        /** Version 2 adds boxes. Existing cards go into "Bulk". */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `boxes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `boxId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("DROP INDEX IF EXISTS `index_cards_scryfallId_foil`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cards_scryfallId_foil_boxId` ON `cards` (`scryfallId`, `foil`, `boxId`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_boxId` ON `cards` (`boxId`)")
                seedBulk(db)
            }
        }

        fun seedBulk(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO `boxes` (`id`, `name`, `kind`, `createdAt`) VALUES (1, 'Bulk', 'box', " +
                    System.currentTimeMillis() + ")",
            )
        }

        val SEED = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = seedBulk(db)
        }
    }
}

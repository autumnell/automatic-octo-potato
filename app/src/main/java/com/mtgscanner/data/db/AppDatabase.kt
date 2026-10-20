package com.mtgscanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CollectionCard::class, Box::class, ValueSnapshot::class, Deck::class, WishlistItem::class, PendingScan::class],
    version = 4,
    exportSchema = true,
)
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

        /** Version 3 adds price-at-add, card roles, printing treatments, box rules, value history, decks, the wishlist and offline scans. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `priceAtAdd` REAL")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `roles` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `rolesCheckedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `treatments` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `boxes` ADD COLUMN `rules` TEXT NOT NULL DEFAULT ''")
                // Start tracking from today's price for cards already in the collection.
                db.execSQL("UPDATE `cards` SET `priceAtAdd` = CASE WHEN `foil` = 1 THEN COALESCE(`priceUsdFoil`, `priceUsd`) ELSE `priceUsd` END")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `value_snapshots` (`day` TEXT NOT NULL, `value` REAL NOT NULL, " +
                        "`cards` INTEGER NOT NULL, PRIMARY KEY(`day`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wishlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `oracleId` TEXT NOT NULL, " +
                        "`scryfallId` TEXT NOT NULL, `name` TEXT NOT NULL, `imageUrl` TEXT, `priceUsd` REAL, `wanted` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, `addedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wishlist_oracleId` ON `wishlist` (`oracleId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_scans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`setCode` TEXT, `collectorNumber` TEXT, `foil` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, " +
                        "`boxId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)",
                )
            }
        }

        /** Version 4 adds box colors, the Auto-sort order and a fallback box. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `boxes` ADD COLUMN `color` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `boxes` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `boxes` ADD COLUMN `fallback` INTEGER NOT NULL DEFAULT 0")
                // Keep the order boxes were created in.
                db.execSQL(
                    "UPDATE `boxes` SET `sortOrder` = (SELECT COUNT(*) FROM `boxes` AS b2 WHERE b2.`createdAt` < `boxes`.`createdAt` " +
                        "OR (b2.`createdAt` = `boxes`.`createdAt` AND b2.`id` < `boxes`.`id`))",
                )
            }
        }

        fun seedBulk(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO `boxes` (`id`, `name`, `kind`, `createdAt`) VALUES (1, 'Bulk', 'box', " +
                    System.currentTimeMillis() + ")",
            )
        }
    }
}

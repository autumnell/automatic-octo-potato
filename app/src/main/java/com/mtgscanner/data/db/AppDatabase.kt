package com.mtgscanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CollectionCard::class, Box::class, ValueSnapshot::class, Deck::class, WishlistItem::class, PendingScan::class, PricePoint::class, PackOpening::class, CardEvent::class, Goal::class],
    version = 10,
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

        /** Version 5 adds your own card tags, price history and opened packs. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `userTags` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `price_history` (`scryfallId` TEXT NOT NULL, `day` TEXT NOT NULL, `usd` REAL, " +
                        "`usdFoil` REAL, PRIMARY KEY(`scryfallId`, `day`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `packs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`cost` REAL, `value` REAL NOT NULL, `cards` INTEGER NOT NULL, `best` TEXT NOT NULL, `pulls` TEXT NOT NULL, " +
                        "`openedAt` INTEGER NOT NULL)",
                )
            }
        }

        /** Version 6 adds per-condition prices from JustTCG. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `conditionPrice` REAL")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `conditionPriceAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Version 7 adds box capacity and dividers, and signed/altered/graded flags. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `boxes` ADD COLUMN `capacity` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `boxes` ADD COLUMN `divider` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `signed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `altered` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `graded` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 8 adds purchase price, source, photo, proxy, for-sale and binder slot; card history; goals; unknown scans. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `purchasePrice` REAL")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `acquiredFrom` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `photo` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `proxy` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `forSale` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `askPrice` REAL")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `binderSlot` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `power` TEXT")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `toughness` TEXT")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `reserved` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pending_scans` ADD COLUMN `unknown` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scryfallId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `detail` TEXT NOT NULL, `at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_events_scryfallId` ON `card_events` (`scryfallId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `target` TEXT NOT NULL, `rarities` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * Version 9 keeps copies that differ apart: rows are unique by printing, finish and box plus
         * language, condition and the signed/altered/graded/proxy flags (see [COPY_KEY_SQL]).
         * Old rows were unique by fewer columns, so they all fit the new index.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_cards_scryfallId_foil_boxId`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cards_scryfallId_foil_boxId_lang_condition_signed_altered_graded_proxy` " +
                        "ON `cards` ($COPY_KEY_SQL)",
                )
            }
        }

        /** Version 10 lets offline scans keep their language, exact printing (from an art match) and skip rules. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_scans` ADD COLUMN `lang` TEXT")
                db.execSQL("ALTER TABLE `pending_scans` ADD COLUMN `scryfallId` TEXT")
                db.execSQL("ALTER TABLE `pending_scans` ADD COLUMN `skip` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Every migration, oldest first, for the database builder. */
        val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

        fun seedBulk(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO `boxes` (`id`, `name`, `kind`, `createdAt`) VALUES (1, 'Bulk', 'box', " +
                    System.currentTimeMillis() + ")",
            )
        }
    }
}

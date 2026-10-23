package com.mtgscanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<CollectionCard>>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observe(id: Long): Flow<CollectionCard?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: Long): CollectionCard?

    /** The row copies like these merge into: every column of [COPY_KEY_SQL] matches. */
    @Query(
        "SELECT * FROM cards WHERE scryfallId = :scryfallId AND foil = :foil AND boxId = :boxId AND lang = :lang " +
            "AND `condition` = :condition AND signed = :signed AND altered = :altered AND graded = :graded AND proxy = :proxy LIMIT 1"
    )
    suspend fun find(
        scryfallId: String, foil: Boolean, boxId: Long, lang: String, condition: String,
        signed: Boolean, altered: Boolean, graded: String, proxy: Boolean,
    ): CollectionCard?

    @Query("SELECT * FROM cards")
    suspend fun all(): List<CollectionCard>

    @Query("SELECT * FROM cards WHERE boxId = :boxId")
    suspend fun inBox(boxId: Long): List<CollectionCard>

    @Insert
    suspend fun insert(card: CollectionCard): Long

    @Update
    suspend fun update(card: CollectionCard)

    @Insert
    suspend fun addEvent(e: CardEvent): Long

    @Query("SELECT * FROM card_events WHERE scryfallId = :scryfallId ORDER BY at DESC")
    fun observeEvents(scryfallId: String): Flow<List<CardEvent>>

    @Query("SELECT * FROM card_events")
    suspend fun allEvents(): List<CardEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEvents(l: List<CardEvent>)

    @Query("DELETE FROM card_events")
    suspend fun deleteEvents()

    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun observeGoals(): Flow<List<Goal>>

    @Insert
    suspend fun addGoal(g: Goal): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM goals")
    suspend fun allGoals(): List<Goal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putGoals(l: List<Goal>)

    @Query("DELETE FROM goals")
    suspend fun deleteGoals()

    /** Copies of a card (by oracle id) outside the given boxes. */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM cards WHERE oracleId = :oracleId AND boxId NOT IN (:excluded)")
    suspend fun ownedOutside(oracleId: String, excluded: List<Long>): Int

    /** Like [ownedOutside], but copies kept in decks ([Box.deckId]) don't count either: for the sorting rules. */
    @Query(
        "SELECT COALESCE(SUM(quantity), 0) FROM cards WHERE oracleId = :oracleId AND boxId NOT IN (:excluded) " +
            "AND boxId NOT IN (SELECT id FROM boxes WHERE deckId != 0)"
    )
    suspend fun ownedInBoxesOutside(oracleId: String, excluded: List<Long>): Int

    @Query("SELECT boxId, COALESCE(SUM(quantity), 0) AS n FROM cards GROUP BY boxId")
    suspend fun boxCounts(): List<BoxCount>

    /**
     * Copies and value per box, worked out in SQL so the box list doesn't read every card. Value matches
     * [totalPrice]: proxies count 0, else the condition price or the near-mint price for the finish.
     * valueEur matches [totalEur]: the euro price, scaled like the condition price when there is one.
     */
    @Query(
        "SELECT boxId, COALESCE(SUM(quantity), 0) AS n, COALESCE(SUM(CASE WHEN proxy = 1 THEN 0 ELSE " +
            "COALESCE(conditionPrice, CASE WHEN foil = 1 THEN COALESCE(priceUsdFoil, priceUsd) ELSE priceUsd END, 0) * quantity END), 0) AS value, " +
            "COALESCE(SUM(CASE WHEN proxy = 1 THEN 0 ELSE COALESCE(CASE WHEN foil = 1 THEN COALESCE(priceEurFoil, priceEur) ELSE priceEur END * " +
            "CASE WHEN conditionPrice IS NOT NULL AND COALESCE(CASE WHEN foil = 1 THEN COALESCE(priceUsdFoil, priceUsd) ELSE priceUsd END, 0) > 0 " +
            "THEN conditionPrice / CASE WHEN foil = 1 THEN COALESCE(priceUsdFoil, priceUsd) ELSE priceUsd END ELSE 1 END, 0) * quantity END), 0) AS valueEur " +
            "FROM cards GROUP BY boxId"
    )
    fun observeBoxTotals(): Flow<List<BoxTotal>>

    /** Sets or clears "Stay in this box". */
    @Query("UPDATE cards SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE cards SET conditionPrice = NULL, conditionPriceAt = 0")
    suspend fun clearConditionPrices()

    /** Updates only a printing's prices, so edits made while prices download are kept. */
    @Query(
        "UPDATE cards SET priceUsd = :usd, priceUsdFoil = :foil, treatments = :treatments, " +
            "priceAtAdd = COALESCE(priceAtAdd, CASE WHEN foil = 1 THEN COALESCE(:foil, :usd) ELSE :usd END) " +
            "WHERE scryfallId = :scryfallId"
    )
    suspend fun setPrices(scryfallId: String, usd: Double?, foil: Double?, treatments: String): Int

    /** Updates a printing's euro prices (DB v12), next to [setPrices]. */
    @Query("UPDATE cards SET priceEur = :eur, priceEurFoil = :eurFoil WHERE scryfallId = :scryfallId")
    suspend fun setEurPrices(scryfallId: String, eur: Double?, eurFoil: Double?)

    /** Keeps rarity and tags (keywords, types, legal formats) up to date, so bans reach box rules. */
    @Query("UPDATE cards SET rarity = :rarity, tags = :tags WHERE scryfallId = :scryfallId")
    suspend fun setPrintingInfo(scryfallId: String, rarity: String, tags: String)

    /** Fills in stats added in DB v8 for cards saved before it. */
    @Query("UPDATE cards SET power = :power, toughness = :toughness, reserved = :reserved WHERE scryfallId = :scryfallId")
    suspend fun setStats(scryfallId: String, power: String?, toughness: String?, reserved: Boolean)

    @Delete
    suspend fun delete(card: CollectionCard)

    @Query("SELECT * FROM cards WHERE scryfallId IN (:ids)")
    suspend fun byScryfallIds(ids: List<String>): List<CollectionCard>

    @Query("DELETE FROM cards WHERE scryfallId IN (:ids)")
    suspend fun deleteByScryfallIds(ids: List<String>)

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<CollectionCard>

    @Query("DELETE FROM cards")
    suspend fun deleteAllCards()

    // Boxes. Rows holding a deck's cards (deckId != 0) aren't boxes: only the "places" queries and box(id) return them.

    @Query("SELECT * FROM boxes WHERE deckId = 0 ORDER BY sortOrder, createdAt, id")
    fun observeBoxes(): Flow<List<Box>>

    @Query("SELECT * FROM boxes WHERE deckId = 0 ORDER BY sortOrder, createdAt, id")
    suspend fun allBoxes(): List<Box>

    /** Boxes, then the rows holding decks' cards: everywhere a card can be. */
    @Query("SELECT * FROM boxes ORDER BY (deckId != 0), sortOrder, createdAt, id")
    fun observePlaces(): Flow<List<Box>>

    @Query("SELECT * FROM boxes ORDER BY (deckId != 0), sortOrder, createdAt, id")
    suspend fun allPlaces(): List<Box>

    /** The row holding the cards kept in a deck, if it has one. */
    @Query("SELECT * FROM boxes WHERE deckId = :deckId ORDER BY id LIMIT 1")
    suspend fun deckSlot(deckId: Long): Box?

    @Query("UPDATE boxes SET sortOrder = :order WHERE id = :id")
    suspend fun setBoxOrder(id: Long, order: Int)

    @Query("UPDATE boxes SET fallback = (id = :id)")
    suspend fun setFallback(id: Long)

    @Query("SELECT * FROM boxes WHERE id = :id")
    suspend fun box(id: Long): Box?

    @Insert
    suspend fun insertBox(box: Box): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBox(box: Box): Long

    @Update
    suspend fun updateBox(box: Box)

    @Delete
    suspend fun deleteBox(box: Box)

    @Query("DELETE FROM boxes")
    suspend fun deleteAllBoxes()

    @Query("DELETE FROM cards WHERE boxId = :boxId")
    suspend fun deleteCardsInBox(boxId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCards(cards: List<CollectionCard>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBoxes(boxes: List<Box>)

    // Value history

    @Upsert
    suspend fun putSnapshot(s: ValueSnapshot)

    @Query("SELECT * FROM value_snapshots ORDER BY day")
    fun observeSnapshots(): Flow<List<ValueSnapshot>>

    @Query("SELECT * FROM value_snapshots ORDER BY day")
    suspend fun allSnapshots(): List<ValueSnapshot>

    @Upsert
    suspend fun putSnapshots(s: List<ValueSnapshot>)

    @Query("DELETE FROM value_snapshots")
    suspend fun deleteSnapshots()

    // Decks

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun observeDecks(): Flow<List<Deck>>

    @Query("SELECT * FROM decks")
    suspend fun allDecks(): List<Deck>

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeDeck(id: Long): Flow<Deck?>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun deck(id: Long): Deck?

    @Upsert
    suspend fun putDeck(deck: Deck): Long

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)

    @Query("DELETE FROM decks")
    suspend fun deleteDecks()

    @Upsert
    suspend fun putDecks(decks: List<Deck>)

    // Wishlist

    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    fun observeWishlist(): Flow<List<WishlistItem>>

    @Query("SELECT * FROM wishlist")
    suspend fun allWishlist(): List<WishlistItem>

    @Query("SELECT * FROM wishlist WHERE oracleId = :oracleId LIMIT 1")
    suspend fun wishlistItem(oracleId: String): WishlistItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addWish(item: WishlistItem): Long

    @Query("DELETE FROM wishlist WHERE id = :id")
    suspend fun deleteWish(id: Long)

    @Update
    suspend fun updateWish(item: WishlistItem)

    @Query("DELETE FROM wishlist")
    suspend fun deleteWishlist()

    @Upsert
    suspend fun putWishlist(items: List<WishlistItem>)

    // Price history

    @Upsert
    suspend fun putPricePoints(points: List<PricePoint>)

    @Query("SELECT * FROM price_history WHERE scryfallId = :scryfallId ORDER BY day")
    fun observePriceHistory(scryfallId: String): Flow<List<PricePoint>>

    /** Price points from [day] (yyyy-MM-dd) on. */
    @Query("SELECT * FROM price_history WHERE day >= :day")
    suspend fun pricePointsSince(day: String): List<PricePoint>

    /** Keeps one point a week (Mondays) from before [before] (yyyy-MM-dd), so history doesn't grow forever. */
    @Query("DELETE FROM price_history WHERE day < :before AND strftime('%w', day) != '1'")
    suspend fun thinPriceHistory(before: String): Int

    @Query("DELETE FROM price_history")
    suspend fun deletePriceHistory()

    // Packs

    @Query("SELECT * FROM packs")
    suspend fun allPacks(): List<PackOpening>

    @Query("DELETE FROM packs")
    suspend fun deletePacks()

    @Upsert
    suspend fun putPacks(items: List<PackOpening>)

    // Loans (DB v12)

    @Query("SELECT * FROM loans ORDER BY at DESC")
    fun observeLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans")
    suspend fun allLoans(): List<Loan>

    @Insert
    suspend fun addLoan(l: Loan): Long

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)

    @Query("DELETE FROM loans")
    suspend fun deleteLoans()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLoans(l: List<Loan>)

    // Offline scans

    @Query("SELECT * FROM pending_scans ORDER BY addedAt")
    fun observePending(): Flow<List<PendingScan>>

    @Query("SELECT * FROM pending_scans ORDER BY addedAt")
    suspend fun allPending(): List<PendingScan>

    @Insert
    suspend fun addPending(p: PendingScan): Long

    @Query("DELETE FROM pending_scans WHERE id = :id")
    suspend fun deletePending(id: Long): Int

    @Query("DELETE FROM pending_scans")
    suspend fun deleteAllPending()

    @Upsert
    suspend fun putPending(items: List<PendingScan>)
}

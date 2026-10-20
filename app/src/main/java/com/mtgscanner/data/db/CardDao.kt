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

    @Query("SELECT * FROM cards WHERE scryfallId = :scryfallId AND foil = :foil AND boxId = :boxId LIMIT 1")
    suspend fun find(scryfallId: String, foil: Boolean, boxId: Long): CollectionCard?

    @Query("SELECT * FROM cards")
    suspend fun all(): List<CollectionCard>

    @Query("SELECT * FROM cards WHERE boxId = :boxId")
    suspend fun inBox(boxId: Long): List<CollectionCard>

    @Query("SELECT * FROM cards WHERE rolesCheckedAt = 0 AND oracleId IS NOT NULL")
    suspend fun needingRoles(): List<CollectionCard>

    @Query("UPDATE cards SET roles = :roles, rolesCheckedAt = :at WHERE oracleId = :oracleId")
    suspend fun setRoles(oracleId: String, roles: String, at: Long)

    @Query("UPDATE cards SET rolesCheckedAt = 0")
    suspend fun resetRoles()

    @Insert
    suspend fun insert(card: CollectionCard): Long

    @Update
    suspend fun update(card: CollectionCard)

    @Update
    suspend fun updateAll(cards: List<CollectionCard>)

    @Delete
    suspend fun delete(card: CollectionCard)

    @Query("DELETE FROM cards")
    suspend fun deleteAllCards()

    // Boxes

    @Query("SELECT * FROM boxes ORDER BY createdAt, id")
    fun observeBoxes(): Flow<List<Box>>

    @Query("SELECT * FROM boxes ORDER BY createdAt, id")
    suspend fun allBoxes(): List<Box>

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

    // Offline scans

    @Query("SELECT * FROM pending_scans ORDER BY addedAt")
    fun observePending(): Flow<List<PendingScan>>

    @Query("SELECT * FROM pending_scans ORDER BY addedAt")
    suspend fun allPending(): List<PendingScan>

    @Insert
    suspend fun addPending(p: PendingScan): Long

    @Query("DELETE FROM pending_scans WHERE id = :id")
    suspend fun deletePending(id: Long)

    @Query("DELETE FROM pending_scans")
    suspend fun deleteAllPending()

    @Upsert
    suspend fun putPending(items: List<PendingScan>)
}

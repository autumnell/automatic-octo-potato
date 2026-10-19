package com.mtgscanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<CollectionCard>>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observe(id: Long): Flow<CollectionCard?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: Long): CollectionCard?

    @Query("SELECT * FROM cards WHERE scryfallId = :scryfallId AND foil = :foil LIMIT 1")
    suspend fun find(scryfallId: String, foil: Boolean): CollectionCard?

    @Query("SELECT * FROM cards")
    suspend fun all(): List<CollectionCard>

    @Insert
    suspend fun insert(card: CollectionCard): Long

    @Update
    suspend fun update(card: CollectionCard)

    @Update
    suspend fun updateAll(cards: List<CollectionCard>)

    @Delete
    suspend fun delete(card: CollectionCard)
}

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

    @Query("SELECT * FROM cards WHERE scryfallId = :scryfallId AND foil = :foil AND boxId = :boxId LIMIT 1")
    suspend fun find(scryfallId: String, foil: Boolean, boxId: Long): CollectionCard?

    @Query("SELECT * FROM cards")
    suspend fun all(): List<CollectionCard>

    @Query("SELECT * FROM cards WHERE boxId = :boxId")
    suspend fun inBox(boxId: Long): List<CollectionCard>

    @Insert
    suspend fun insert(card: CollectionCard): Long

    @Update
    suspend fun update(card: CollectionCard)

    @Update
    suspend fun updateAll(cards: List<CollectionCard>)

    @Delete
    suspend fun delete(card: CollectionCard)

    // Boxes

    @Query("SELECT * FROM boxes ORDER BY CASE WHEN id = 1 THEN 0 ELSE 1 END, name COLLATE NOCASE")
    fun observeBoxes(): Flow<List<Box>>

    @Query("SELECT * FROM boxes WHERE id = :id")
    suspend fun box(id: Long): Box?

    @Insert
    suspend fun insertBox(box: Box): Long

    @Update
    suspend fun updateBox(box: Box)

    @Delete
    suspend fun deleteBox(box: Box)
}

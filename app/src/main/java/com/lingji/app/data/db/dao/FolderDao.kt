package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY orderIndex DESC, createdAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE folders SET orderIndex = :orderIndex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrderIndex(id: String, orderIndex: Int, updatedAt: Long)

    // ── 云同步 ──

    @Query("SELECT * FROM folders")
    suspend fun getAllOnce(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE updatedAt > :since")
    suspend fun getUpdatedSince(since: Long): List<FolderEntity>
}

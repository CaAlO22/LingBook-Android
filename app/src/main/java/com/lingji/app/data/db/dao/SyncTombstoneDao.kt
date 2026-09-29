package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.SyncTombstoneEntity

@Dao
interface SyncTombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tombstones: List<SyncTombstoneEntity>)

    @Query("SELECT * FROM sync_tombstones WHERE deletedAt > :since ORDER BY deletedAt ASC")
    suspend fun getSince(since: Long): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

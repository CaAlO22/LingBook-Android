package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 'singleton' LIMIT 1")
    fun getSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 'singleton' LIMIT 1")
    suspend fun getSettingsOnce(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)

    /** 云同步：远端删除了某个模型配置时，清空本机对它的引用。 */
    @Query(
        "UPDATE settings SET " +
            "primaryModelId = CASE WHEN primaryModelId = :id THEN '' ELSE primaryModelId END, " +
            "assistantModelId = CASE WHEN assistantModelId = :id THEN '' ELSE assistantModelId END"
    )
    suspend fun clearModelReference(id: String)
}

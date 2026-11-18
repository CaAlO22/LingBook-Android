package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.ModelConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_configs ORDER BY createdAt ASC")
    fun getModelConfigs(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs ORDER BY createdAt ASC")
    suspend fun getModelConfigsOnce(): List<ModelConfigEntity>

    /** 云同步：取本轮水位线之后有变更的模型配置。 */
    @Query("SELECT * FROM model_configs WHERE updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun getUpdatedSince(since: Long): List<ModelConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ModelConfigEntity)

    @Query("DELETE FROM model_configs WHERE id = :id")
    suspend fun deleteById(id: String)
}

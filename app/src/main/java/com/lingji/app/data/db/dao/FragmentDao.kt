package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.FragmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FragmentDao {
    @Query("SELECT * FROM fragments WHERE subjectId = :subjectId ORDER BY timestamp ASC")
    fun getFragmentsBySubject(subjectId: String): Flow<List<FragmentEntity>>

    @Query("SELECT * FROM fragments WHERE subjectId = :subjectId ORDER BY timestamp ASC")
    suspend fun getFragmentsBySubjectOnce(subjectId: String): List<FragmentEntity>

    @Query("SELECT * FROM fragments ORDER BY timestamp ASC")
    fun getAllFragments(): Flow<List<FragmentEntity>>

    @Query("SELECT * FROM fragments WHERE subjectId = :subjectId AND isUnmerged = 1 ORDER BY timestamp ASC")
    suspend fun getUnmergedFragments(subjectId: String): List<FragmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fragment: FragmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fragments: List<FragmentEntity>)

    @Delete
    suspend fun delete(fragment: FragmentEntity)

    @Query("DELETE FROM fragments WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: String)

    @Query("DELETE FROM fragments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE fragments SET isUnmerged = 0, updatedAt = :updatedAt WHERE subjectId = :subjectId AND isUnmerged = 1 AND id IN (:ids)")
    suspend fun markUnmergedMergedByIds(subjectId: String, ids: List<String>, updatedAt: Long)

    @Query("UPDATE fragments SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, updatedAt: Long)

    // ── 云同步 ──

    @Query("SELECT * FROM fragments")
    suspend fun getAllOnce(): List<FragmentEntity>

    @Query("SELECT * FROM fragments WHERE updatedAt > :since")
    suspend fun getUpdatedSince(since: Long): List<FragmentEntity>
}

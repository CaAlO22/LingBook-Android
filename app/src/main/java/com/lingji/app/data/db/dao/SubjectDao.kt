package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lingji.app.data.db.entities.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY orderIndex DESC, createdAt DESC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)

    @Query("UPDATE subjects SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE subjects SET aggregatedNote = :content, prevAggregatedNote = :prev WHERE id = :id")
    suspend fun updateAggregatedNote(id: String, content: String, prev: String?)

    @Query("UPDATE subjects SET studyPlan = :content WHERE id = :id")
    suspend fun updateStudyPlan(id: String, content: String)

    @Query("UPDATE subjects SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: String, orderIndex: Int)

    @Query("UPDATE subjects SET pageIndexJson = :json WHERE id = :id")
    suspend fun updatePageIndexJson(id: String, json: String)

    @Query("UPDATE subjects SET lastOpenedPageId = :pageId WHERE id = :id")
    suspend fun updateLastOpenedPageId(id: String, pageId: String?)

    @Query("UPDATE subjects SET folderId = :folderId WHERE id = :id")
    suspend fun updateFolderId(id: String, folderId: String?)

    @Transaction
    suspend fun upsert(subject: SubjectEntity) = insert(subject)
}

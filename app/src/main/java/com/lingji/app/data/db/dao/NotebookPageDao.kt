package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.NotebookPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookPageDao {
    @Query("SELECT * FROM notebook_pages WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    fun getPagesBySubject(subjectId: String): Flow<List<NotebookPageEntity>>

    @Query("SELECT * FROM notebook_pages WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    suspend fun getPagesBySubjectOnce(subjectId: String): List<NotebookPageEntity>

    @Query("SELECT * FROM notebook_pages ORDER BY orderIndex ASC")
    fun getAllPages(): Flow<List<NotebookPageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: NotebookPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<NotebookPageEntity>)

    @Delete
    suspend fun delete(page: NotebookPageEntity)

    @Query("DELETE FROM notebook_pages WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: String)

    @Query("UPDATE notebook_pages SET title = :title, content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: String, title: String, content: String, updatedAt: Long)

    @Query("UPDATE notebook_pages SET indexedAt = :indexedAt WHERE id = :id")
    suspend fun updateIndexedAt(id: String, indexedAt: Long)
}

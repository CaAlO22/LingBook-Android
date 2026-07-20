package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.NoteRevisionEntity

@Dao
interface NoteRevisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(revision: NoteRevisionEntity)

    @Query("SELECT * FROM note_revisions WHERE subjectId = :subjectId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForSubject(subjectId: String): NoteRevisionEntity?

    @Query("SELECT * FROM note_revisions WHERE subjectId = :subjectId AND batchId = :batchId ORDER BY createdAt ASC")
    suspend fun getByBatchForSubject(subjectId: String, batchId: String): List<NoteRevisionEntity>

    @Query("DELETE FROM note_revisions WHERE subjectId = :subjectId AND batchId = :batchId")
    suspend fun deleteByBatchForSubject(subjectId: String, batchId: String)

    @Query("DELETE FROM note_revisions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM note_revisions WHERE subjectId = :subjectId AND id NOT IN (SELECT id FROM note_revisions WHERE subjectId = :subjectId ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimForSubject(subjectId: String, keep: Int)
}

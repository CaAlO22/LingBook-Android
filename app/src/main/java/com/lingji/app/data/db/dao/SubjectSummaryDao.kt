package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.SubjectSummaryEntity

@Dao
interface SubjectSummaryDao {
    @Query("SELECT * FROM subject_summaries")
    suspend fun getAll(): List<SubjectSummaryEntity>

    @Query("SELECT * FROM subject_summaries WHERE subjectId = :subjectId")
    suspend fun getBySubjectId(subjectId: String): SubjectSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubjectSummaryEntity)

    @Query("DELETE FROM subject_summaries WHERE subjectId = :subjectId")
    suspend fun deleteBySubjectId(subjectId: String)
}

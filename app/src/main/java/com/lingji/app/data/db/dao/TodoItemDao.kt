package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.TodoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoItemDao {
    @Query("SELECT * FROM todo_items ORDER BY createdAt ASC")
    fun getAllTodoItems(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE subjectId = :subjectId ORDER BY createdAt ASC")
    suspend fun getTodoItemsBySubjectOnce(subjectId: String): List<TodoItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TodoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TodoItemEntity>)

    @Query("DELETE FROM todo_items WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: String)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE todo_items SET isDone = :isDone, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDone(id: String, isDone: Boolean, updatedAt: Long)

    @Query("UPDATE todo_items SET isDone = 0, updatedAt = :updatedAt WHERE subjectId = :subjectId AND isDone = 1")
    suspend fun resetDoneBySubject(subjectId: String, updatedAt: Long)

    // ── 云同步 ──

    @Query("SELECT * FROM todo_items WHERE updatedAt > :since")
    suspend fun getUpdatedSince(since: Long): List<TodoItemEntity>
}

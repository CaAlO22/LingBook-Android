package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.TodoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoItemDao {
    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllTodoItems(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE subjectId = :subjectId ORDER BY sortOrder ASC, createdAt ASC")
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

    @Query("UPDATE todo_items SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, updatedAt: Long)

    /** 当前学科下的最大排序位次；没有条目时返回 -1。 */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM todo_items WHERE subjectId = :subjectId")
    suspend fun maxSortOrder(subjectId: String): Int

    @Query("UPDATE todo_items SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE todo_items SET isDone = 0, updatedAt = :updatedAt WHERE subjectId = :subjectId AND isDone = 1")
    suspend fun resetDoneBySubject(subjectId: String, updatedAt: Long)

    // ── 云同步 ──

    @Query("SELECT * FROM todo_items WHERE updatedAt > :since")
    suspend fun getUpdatedSince(since: Long): List<TodoItemEntity>
}

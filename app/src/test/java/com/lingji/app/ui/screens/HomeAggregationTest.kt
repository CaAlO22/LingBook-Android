package com.lingji.app.ui.screens

import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.ui.viewmodel.HomeFragmentData
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAggregationTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun todoSubject(
        id: String,
        title: String,
        items: List<TodoItem>,
        repeat: TodoRepeat = TodoRepeat(),
        lastResetAt: Long = now
    ) = Subject(
        id = id, title = title, type = SubjectType.TODO,
        todoItems = items, todoRepeat = repeat, todoLastResetAt = lastResetAt
    )

    @Test
    fun mergesUndoneTodosAcrossLists_newestFirst() {
        val subjects = listOf(
            todoSubject("s1", "每日委托", listOf(
                TodoItem(id = "a", content = "旧任务", createdAt = now - 3000),
                TodoItem(id = "b", content = "已完成", isDone = true, createdAt = now)
            )),
            todoSubject("s2", "购物清单", listOf(
                TodoItem(id = "c", content = "新任务", createdAt = now - 1000)
            )),
            Subject(id = "s3", title = "碎片笔记", type = SubjectType.FRAGMENT, fragments = emptyList())
        )
        val result = buildAggregationTodos(subjects, now)
        assertEquals(listOf("c", "a"), result.map { it.item.id })
        assertEquals("购物清单", result[0].subjectTitle)
        assertEquals("s2", result[0].subjectId)
    }

    @Test
    fun repeatPeriodExpired_treatsDoneAsUndone() {
        val subject = todoSubject(
            "s1", "每日委托",
            items = listOf(TodoItem(id = "a", content = "昨日已勾选", isDone = true, createdAt = now - dayMs)),
            repeat = TodoRepeat(type = TodoRepeatType.DAILY),
            lastResetAt = now - 2 * dayMs
        )
        val result = buildAggregationTodos(listOf(subject), now)
        assertEquals(listOf("a"), result.map { it.item.id })
    }

    @Test
    fun repeatAlreadyReset_keepsDoneExcluded() {
        val subject = todoSubject(
            "s1", "每日委托",
            items = listOf(TodoItem(id = "a", content = "本周期已勾选", isDone = true, createdAt = now - 1000)),
            repeat = TodoRepeat(type = TodoRepeatType.DAILY),
            lastResetAt = now
        )
        assertEquals(emptyList<AggregationTodo>(), buildAggregationTodos(listOf(subject), now))
    }

    @Test
    fun onceRepeat_doneAlwaysExcluded() {
        val subject = todoSubject(
            "s1", "一次性清单",
            items = listOf(TodoItem(id = "a", content = "已完成", isDone = true, createdAt = now)),
            repeat = TodoRepeat(type = TodoRepeatType.ONCE),
            lastResetAt = 0
        )
        assertEquals(emptyList<AggregationTodo>(), buildAggregationTodos(listOf(subject), now))
    }

    @Test
    fun fragments_sortedNewestFirst_nullTimestampLast() {
        val fragments = listOf(
            HomeFragmentData(content = "无时间戳", timestamp = null),
            HomeFragmentData(content = "较早", timestamp = now - 5000),
            HomeFragmentData(content = "最新", timestamp = now)
        )
        val result = sortFragmentsNewestFirst(fragments)
        assertEquals(listOf("最新", "较早", "无时间戳"), result.map { it.content })
    }
}
package com.lingji.app.ui.screens

import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.ui.viewmodel.HomeFragmentData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

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
    fun mergesUndoneTodosAcrossLists_inputOrderWhenNoReminders() {
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
        // 都没有提醒：按录入时间升序
        assertEquals(listOf("a", "c"), result.map { it.item.id })
        assertEquals("每日委托", result[0].subjectTitle)
        assertEquals("s1", result[0].subjectId)
    }

    @Test
    fun todos_sortedByReminderTime_nearestFirst_noReminderLast() {
        val nowMinutes = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            .toLocalTime().let { it.hour * 60 + it.minute }
        val soon = (nowMinutes + 60) % 1440
        val later = (nowMinutes + 120) % 1440
        val subject = todoSubject(
            "s1", "每日委托",
            items = listOf(
                TodoItem(
                    id = "later", content = "晚点", createdAt = now - 1000,
                    reminderEnabled = true, reminderMinutes = later
                ),
                TodoItem(
                    id = "none", content = "没提醒", createdAt = now - 5000
                ),
                TodoItem(
                    id = "soon", content = "最近的", createdAt = now - 2000,
                    reminderEnabled = true, reminderMinutes = soon
                )
            ),
            repeat = TodoRepeat(type = TodoRepeatType.DAILY),
            lastResetAt = now
        )
        val result = buildAggregationTodos(listOf(subject), now)
        assertEquals(listOf("soon", "later", "none"), result.map { it.item.id })
    }

    @Test
    fun todos_sameReminderTime_tieBrokenByInputTime() {
        val nowMinutes = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            .toLocalTime().let { it.hour * 60 + it.minute }
        val same = (nowMinutes + 60) % 1440
        val subject = todoSubject(
            "s1", "每日委托",
            items = listOf(
                TodoItem(
                    id = "newer", content = "后录入", createdAt = now - 1000,
                    reminderEnabled = true, reminderMinutes = same
                ),
                TodoItem(
                    id = "older", content = "先录入", createdAt = now - 4000,
                    reminderEnabled = true, reminderMinutes = same
                )
            ),
            repeat = TodoRepeat(type = TodoRepeatType.DAILY),
            lastResetAt = now
        )
        val result = buildAggregationTodos(listOf(subject), now)
        assertEquals(listOf("older", "newer"), result.map { it.item.id })
    }

    @Test
    fun onceTodos_sortedByAbsoluteReminderAt() {
        val subject = todoSubject(
            "s1", "一次性清单",
            items = listOf(
                TodoItem(
                    id = "hour2", content = "两小时后", createdAt = now - 3000,
                    reminderEnabled = true, reminderAt = now + 2 * 60 * 60 * 1000
                ),
                TodoItem(
                    id = "hour1", content = "一小时后", createdAt = now - 2000,
                    reminderEnabled = true, reminderAt = now + 60 * 60 * 1000
                ),
                TodoItem(id = "none", content = "没提醒", createdAt = now - 5000)
            ),
            repeat = TodoRepeat(type = TodoRepeatType.ONCE),
            lastResetAt = 0
        )
        val result = buildAggregationTodos(listOf(subject), now)
        assertEquals(listOf("hour1", "hour2", "none"), result.map { it.item.id })
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
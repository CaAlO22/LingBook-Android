package com.lingji.app.domain.tool.todo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = TodoTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    private val todoSubject = Subject(
        id = "s1", title = "每日委托", type = SubjectType.TODO,
        todoItems = listOf(
            TodoItem(id = "t1", content = "买牛奶", isDone = false, sortOrder = 0),
            TodoItem(id = "t2", content = "买面包", isDone = true, sortOrder = 1),
            TodoItem(id = "t3", content = "买面包和牛奶", isDone = false, sortOrder = 2)
        )
    )
    private val dailyTodoSubject = todoSubject.copy(
        todoRepeat = TodoRepeat(type = TodoRepeatType.DAILY)
    )
    private val fragmentSubject = Subject(id = "s2", title = "碎片", type = SubjectType.FRAGMENT)
    private val notebookSubject = Subject(id = "s3", title = "笔记本", type = SubjectType.NOTEBOOK, pages = emptyList())

    private fun params(vararg pairs: Pair<String, Any>) = JsonObject().apply {
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> addProperty(k, v)
                is Number -> addProperty(k, v)
                is Boolean -> addProperty(k, v)
                is List<*> -> add(k, JsonArray().apply {
                    v.filterIsInstance<Number>().forEach { add(it.toInt()) }
                })
            }
        }
    }

    @Test
    fun create_returnsFiveTools() {
        assertEquals(5, tools.size)
        assertTrue(toolMap.containsKey("list_todos"))
        assertTrue(toolMap.containsKey("add_todo"))
        assertTrue(toolMap.containsKey("update_todo"))
        assertTrue(toolMap.containsKey("delete_todo"))
        assertTrue(toolMap.containsKey("set_todo_list_reminder"))
    }

    @Test
    fun list_todos_returnsIndexedItemsAndRepeat() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        val result = toolMap["list_todos"]!!.execute(params("subject_id" to "s1"))
        assertTrue(result.contains("\"index\":0"))
        assertTrue(result.contains("\"id\":\"t1\""))
        assertTrue(result.contains("\"content\":\"买牛奶\""))
        assertTrue(result.contains("\"is_done\":true"))
        // repeat 是整篇待办列表的设置，只出现在顶层一份，不复制到每条 item
        assertTrue(result.contains("\"repeat\""))
        assertTrue(result.contains("\"type\":\"ONCE\""))
        assertTrue(result.contains("\"scope\":\"subject\""))
        assertTrue(result.contains("\"auto_reset\":false"))
        assertTrue(result.contains("\"count\":3"))
    }

    @Test
    fun list_todos_showsReminderFields() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns dailyTodoSubject.copy(
            todoItems = listOf(
                TodoItem(
                    id = "t1", content = "吃药", sortOrder = 0,
                    reminderEnabled = true, reminderMinutes = 480
                ),
                TodoItem(id = "t2", content = "没提醒", sortOrder = 1)
            )
        )
        val result = toolMap["list_todos"]!!.execute(params("subject_id" to "s1"))
        assertTrue(result, result.contains("\"reminder_time\":\"08:00\""))
        // 未设提醒的条目不输出提醒字段
        val t2Part = result.substringAfter("\"id\":\"t2\"")
        assertTrue(t2Part, !t2Part.contains("reminder_"))
    }

    @Test
    fun list_todos_dailyWithStaleChecks_reportsResetPending() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject.copy(
            todoRepeat = TodoRepeat(type = TodoRepeatType.DAILY, anchorAt = System.currentTimeMillis()),
            todoLastResetAt = 0
        )
        val result = toolMap["list_todos"]!!.execute(params("subject_id" to "s1"))
        assertTrue(result.contains("\"type\":\"DAILY\""))
        assertTrue(result.contains("\"label\":\"每日委托\""))
        assertTrue(result.contains("\"auto_reset\":true"))
        assertTrue(result.contains("\"current_period_start\""))
        // t2 已完成且 lastResetAt=0，早于今天 0 点 → 上一周期遗留的勾选尚未清空
        assertTrue(result, result.contains("\"reset_pending\":true"))
    }

    /** 六档重复都要有可判别的输出：auto_reset、week_days、interval_days 只在对应档位出现。 */
    @Test
    fun list_todos_reportsAllSixRepeatTiers() = runTest {
        val cases = listOf(
            TodoRepeat(type = TodoRepeatType.ONCE) to "\"auto_reset\":false",
            TodoRepeat(type = TodoRepeatType.DAILY) to "\"label\":\"每日委托\"",
            TodoRepeat(type = TodoRepeatType.WEEKDAYS) to "\"label\":\"仅工作日重复\"",
            TodoRepeat(type = TodoRepeatType.WEEKENDS) to "\"label\":\"仅休息日重复\"",
            TodoRepeat(type = TodoRepeatType.INTERVAL, intervalDays = 4) to "\"interval_days\":4",
            TodoRepeat(type = TodoRepeatType.CUSTOM, weekDays = setOf(1, 3)) to "\"week_days\":[1,3]"
        )
        for ((repeat, expected) in cases) {
            coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject.copy(
                todoRepeat = repeat,
                todoLastResetAt = 0
            )
            val result = toolMap["list_todos"]!!.execute(params("subject_id" to "s1"))
            assertTrue("${repeat.type} 的输出缺少 $expected：$result", result.contains(expected))
            assertTrue("${repeat.type} 必须标明作用域：$result", result.contains("\"scope\":\"subject\""))
        }
    }

    @Test
    fun list_todos_rejectsNonTodoWithStructuredError() = runTest {
        coEvery { repo.getSubjectByIdOnce("s2") } returns fragmentSubject
        val result = toolMap["list_todos"]!!.execute(params("subject_id" to "s2"))
        assertTrue(result.startsWith("Error:"))
        assertTrue(result.contains("\"code\":\"UNSUPPORTED_FOR_SUBJECT_TYPE\""))
        assertTrue(result.contains("\"subject_type\":\"FRAGMENT\""))
        assertTrue(result.contains("add_fragment"))
    }

    @Test
    fun add_todo_callsRepoAndReturnsNewId() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        coEvery {
            repo.addTodoItem("s1", "交作业", false, -1, 0)
        } returns TodoItem(id = "t4", content = "交作业", sortOrder = 3)
        val result = toolMap["add_todo"]!!.execute(params("subject_id" to "s1", "content" to "交作业"))
        assertTrue(result.contains("\"id\":\"t4\""))
        assertTrue(result.contains("\"index\":3"))
        assertTrue(result.contains("\"is_done\":false"))
        coVerify(exactly = 1) { repo.addTodoItem("s1", "交作业", false, -1, 0) }
        coVerify(exactly = 0) { repo.setTodoItemDone(any(), any()) }
    }

    @Test
    fun add_todo_withDoneTrue_checksIt() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        coEvery {
            repo.addTodoItem("s1", "已完成项", false, -1, 0)
        } returns TodoItem(id = "t5", content = "已完成项", sortOrder = 3)
        coEvery { repo.setTodoItemDone("t5", true) } returns Unit
        val result = toolMap["add_todo"]!!.execute(params("subject_id" to "s1", "content" to "已完成项", "done" to true))
        assertTrue(result.contains("\"is_done\":true"))
        coVerify(exactly = 1) { repo.setTodoItemDone("t5", true) }
    }

    @Test
    fun add_todo_toNotebook_isRejectedAndNothingWritten() = runTest {
        coEvery { repo.getSubjectByIdOnce("s3") } returns notebookSubject
        val result = toolMap["add_todo"]!!.execute(params("subject_id" to "s3", "content" to "不该写入"))
        assertTrue(result.contains("\"code\":\"UNSUPPORTED_FOR_SUBJECT_TYPE\""))
        coVerify(exactly = 0) { repo.addTodoItem(any(), any(), any(), any(), any()) }
    }

    @Test
    fun update_todo_byIndex_changesContentAndDone() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        coEvery { repo.updateTodoItem("t1", "买酸奶", false, -1, 0) } returns Unit
        coEvery { repo.setTodoItemDone("t1", true) } returns Unit
        val result = toolMap["update_todo"]!!.execute(
            params("subject_id" to "s1", "todo_index" to 0, "content" to "买酸奶", "done" to true)
        )
        assertTrue(result.contains("\"success\":true"))
        assertTrue(result.contains("\"index\":0"))
        coVerify(exactly = 1) { repo.updateTodoItem("t1", "买酸奶", false, -1, 0) }
        coVerify(exactly = 1) { repo.setTodoItemDone("t1", true) }
    }

    @Test
    fun update_todo_byText_ambiguousReturnsCandidates() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        val result = toolMap["update_todo"]!!.execute(
            params("subject_id" to "s1", "todo_text" to "买", "done" to true)
        )
        assertTrue(result.contains("\"code\":\"AMBIGUOUS_MATCH\""))
        assertTrue(result.contains("\"candidates\""))
        coVerify(exactly = 0) { repo.setTodoItemDone(any(), any()) }
    }

    @Test
    fun update_todo_exactTextMatch_winsOverContains() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        coEvery { repo.setTodoItemDone("t2", false) } returns Unit
        val result = toolMap["update_todo"]!!.execute(
            params("subject_id" to "s1", "todo_text" to "买面包", "done" to false, "todo_id" to "t2")
        )
        assertTrue(result.contains("\"success\":true"))
        coVerify(exactly = 1) { repo.setTodoItemDone("t2", false) }
    }

    @Test
    fun update_todo_rejectsForeignTodoId() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        val result = toolMap["update_todo"]!!.execute(
            params("subject_id" to "s1", "todo_id" to "foreign-id", "done" to true)
        )
        assertTrue(result.contains("\"code\":\"OBJECT_NOT_FOUND\""))
        coVerify(exactly = 0) { repo.setTodoItemDone(any(), any()) }
    }

    @Test
    fun update_todo_withoutAnyField_isRejected() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        val result = toolMap["update_todo"]!!.execute(params("subject_id" to "s1"))
        assertTrue(result.startsWith("Error:"))
        assertTrue(result.contains("\"code\":\"MISSING_PARAMETER\""))
        coVerify(exactly = 0) { repo.setTodoItemDone(any(), any()) }
    }

    @Test
    fun update_todo_clearReminder_callsRepo() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns dailyTodoSubject.copy(
            todoItems = listOf(
                TodoItem(
                    id = "t1", content = "吃药", sortOrder = 0,
                    reminderEnabled = true, reminderMinutes = 480
                )
            )
        )
        coEvery { repo.updateTodoItem("t1", "吃药", false, -1, 0) } returns Unit
        val result = toolMap["update_todo"]!!.execute(
            params("subject_id" to "s1", "todo_index" to 0, "clear_reminder" to true)
        )
        assertTrue(result.contains("\"reminder\":\"none\""))
        coVerify(exactly = 1) { repo.updateTodoItem("t1", "吃药", false, -1, 0) }
    }

    @Test
    fun delete_todo_byId_callsRepo() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        coEvery { repo.deleteTodoItem("t1") } returns Unit
        val result = toolMap["delete_todo"]!!.execute(params("subject_id" to "s1", "todo_id" to "t1"))
        assertTrue(result.contains("\"success\":true"))
        assertTrue(result.contains("\"deleted_content\":\"买牛奶\""))
        coVerify(exactly = 1) { repo.deleteTodoItem("t1") }
    }

    @Test
    fun delete_todo_byText_callsRepo() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        coEvery { repo.deleteTodoItem("t1") } returns Unit
        val result = toolMap["delete_todo"]!!.execute(params("subject_id" to "s1", "todo_text" to "买牛奶"))
        assertTrue(result.contains("\"success\":true"))
        coVerify(exactly = 1) { repo.deleteTodoItem("t1") }
    }

    @Test
    fun add_todo_withReminderOnDailyList_setsMinutes() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns dailyTodoSubject
        coEvery {
            repo.addTodoItem("s1", "吃药", true, 480, 0)
        } returns TodoItem(id = "t6", content = "吃药", reminderEnabled = true, reminderMinutes = 480)
        val result = toolMap["add_todo"]!!.execute(
            params("subject_id" to "s1", "content" to "吃药", "reminder_time" to "08:00")
        )
        assertTrue(result.contains("\"reminder\":\"08:00\""))
        coVerify(exactly = 1) { repo.addTodoItem("s1", "吃药", true, 480, 0) }
    }

    @Test
    fun add_todo_oneShotWithoutDate_returnsInvalid() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns todoSubject
        val result = toolMap["add_todo"]!!.execute(
            params("subject_id" to "s1", "content" to "x", "reminder_time" to "08:00")
        )
        assertTrue(result.contains("\"code\":\"INVALID_PARAMETER\""))
        coVerify(exactly = 0) { repo.addTodoItem(any(), any(), any(), any(), any()) }
    }

    @Test
    fun set_list_reminder_onDailyList_callsRepo() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns dailyTodoSubject
        coEvery { repo.updateTodoReminder("s1", true, 1260, 0) } returns Unit
        val result = toolMap["set_todo_list_reminder"]!!.execute(
            params("subject_id" to "s1", "reminder_time" to "21:00")
        )
        assertTrue(result.contains("\"reminder\":\"21:00\""))
        coVerify(exactly = 1) { repo.updateTodoReminder("s1", true, 1260, 0) }
    }
}

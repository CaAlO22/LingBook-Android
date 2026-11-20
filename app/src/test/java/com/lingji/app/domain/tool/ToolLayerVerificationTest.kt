package com.lingji.app.domain.tool

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.tool.fragment.FragmentTools
import com.lingji.app.domain.tool.image.ConversationImageStore
import com.lingji.app.domain.tool.note.NoteTools
import com.lingji.app.domain.tool.page.PageTools
import com.lingji.app.domain.tool.subject.SubjectTools
import com.lingji.app.domain.tool.todo.TodoTools
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可复现的验收记录：按问题报告里的调用序列执行工具，并把「实际返回值」逐字打印。
 * Gradle 会把 stdout 收进 test-results 的 XML <system-out>，
 * 复现命令与逐字输出见 docs/compose/specs/2026-11-21-agent-tool-layer-fix.md。
 */
class ToolLayerVerificationTest {

    private fun p(label: String, result: String) {
        println("CALL $label")
        println("RETURN $result")
    }

    private fun obj(vararg pairs: Pair<String, Any>) = JsonObject().apply {
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> addProperty(k, v)
                is Number -> addProperty(k, v)
                is Boolean -> addProperty(k, v)
            }
        }
    }

    @Test
    fun printActualToolReturnsForEveryAcceptanceCriterion() = runTest {
        val repo = mockk<SubjectRepository>()
        val todoId = "cb720714-f26d-4f51-b4ed-1ba94e143cec"
        val shortId = "7b7fe4f2"
        var currentTodo = Subject(
            id = todoId, title = "每日委托", type = SubjectType.TODO,
            todoItems = listOf(TodoItem(id = "t1", content = "森空岛签到", isDone = false))
        )
        val notebook = Subject(id = shortId, title = "英语单词", type = SubjectType.NOTEBOOK, pages = emptyList())

        coEvery { repo.getSubjectByIdOnce(todoId) } answers { currentTodo }
        coEvery { repo.getSubjectByIdOnce(shortId) } returns notebook
        coEvery { repo.getSubjectByIdOnce("other") } returns Subject(id = "other", title = "每周委托", type = SubjectType.TODO)
        coEvery { repo.getAllSubjects() } returns flowOf(
            listOf(notebook, currentTodo, Subject(id = "other", title = "每周委托", type = SubjectType.TODO))
        )
        coEvery { repo.addTodoItem(todoId, any()) } answers {
            val content = secondArg<String>()
            val item = TodoItem(id = "t-new", content = content, sortOrder = currentTodo.todoItems.size)
            currentTodo = currentTodo.copy(todoItems = currentTodo.todoItems + item)
            item
        }
        coEvery { repo.setTodoItemDone(any(), any()) } answers {
            val id = firstArg<String>()
            val done = secondArg<Boolean>()
            currentTodo = currentTodo.copy(
                todoItems = currentTodo.todoItems.map { if (it.id == id) it.copy(isDone = done) else it }
            )
            Unit
        }
        coEvery { repo.updateTodoItemContent(any(), any()) } answers {
            val id = firstArg<String>()
            val text = secondArg<String>()
            currentTodo = currentTodo.copy(
                todoItems = currentTodo.todoItems.map { if (it.id == id) it.copy(content = text) else it }
            )
            Unit
        }
        coEvery { repo.deleteTodoItem(any()) } answers {
            val id = firstArg<String>()
            currentTodo = currentTodo.copy(todoItems = currentTodo.todoItems.filter { it.id != id })
            Unit
        }

        val subjectTools = SubjectTools.create(repo, mockk<SubjectSummaryDao>()).associateBy { it.name }
        val pageTools = PageTools.create(repo).associateBy { it.name }
        val fragmentTools = FragmentTools.create(repo).associateBy { it.name }
        val todoTools = TodoTools.create(repo).associateBy { it.name }
        val noteTools = NoteTools.create(repo).associateBy { it.name }
        val registry = ToolRegistry(
            repo,
            mockk<LLMService>(),
            mockk<SettingsRepository>(),
            mockk<SubjectSummaryDao>(),
            mockk<IndexService>(),
            mockk<ConversationImageStore>()
        )

        p("list_subjects()", subjectTools["list_subjects"]!!.execute(JsonObject()))

        // P0-2：类型校验
        p("add_fragment(subject_id=<TODO>, content='任意文本')",
            fragmentTools["add_fragment"]!!.execute(obj("subject_id" to todoId, "content" to "任意文本")))
        p("create_page(subject_id=<TODO>, title='不该存在的页')",
            pageTools["create_page"]!!.execute(obj("subject_id" to todoId, "title" to "不该存在的页")))

        // P0-1：TODO 待办项 CRUD + 立即读回
        p("list_todos(subject_id=<TODO>)", todoTools["list_todos"]!!.execute(obj("subject_id" to todoId)))
        p("add_todo(subject_id=<TODO>, content='验证Agent工具')",
            todoTools["add_todo"]!!.execute(obj("subject_id" to todoId, "content" to "验证Agent工具")))
        p("get_aggregated_note(subject_id=<TODO>)  # 新增后立即读回",
            noteTools["get_aggregated_note"]!!.execute(obj("subject_id" to todoId)))
        p("update_todo(subject_id=<TODO>, todo_index=1, done=true)",
            todoTools["update_todo"]!!.execute(obj("subject_id" to todoId, "todo_index" to 1, "done" to true)))
        p("get_aggregated_note(subject_id=<TODO>)  # 勾选后立即读回",
            noteTools["get_aggregated_note"]!!.execute(obj("subject_id" to todoId)))
        p("update_todo(subject_id=<TODO>, todo_text='验证Agent工具', content='改过的文字')",
            todoTools["update_todo"]!!.execute(
                obj("subject_id" to todoId, "todo_text" to "验证Agent工具", "content" to "改过的文字")
            ))
        p("delete_todo(subject_id=<TODO>, todo_index=1)",
            todoTools["delete_todo"]!!.execute(obj("subject_id" to todoId, "todo_index" to 1)))
        p("get_aggregated_note(subject_id=<TODO>)  # 删除后立即读回",
            noteTools["get_aggregated_note"]!!.execute(obj("subject_id" to todoId)))

        // P1-3：错误文案只指向真实能力
        p("edit_replace(subject_id=<TODO>, find='森空岛签到', occurrence=1, replace='x')",
            noteTools["edit_replace"]!!.execute(
                obj("subject_id" to todoId, "find" to "森空岛签到", "occurrence" to 1, "replace" to "x")
            ))

        // P2-5：可读定位（重名候选 + 唯一命中）
        p("get_subject(subject_title='委托')  # 重名",
            registry.executeTool("get_subject", obj("subject_title" to "委托")))
        p("get_subject(subject_title='每日委托')  # 唯一命中，无需复述 UUID",
            registry.executeTool("get_subject", obj("subject_title" to "每日委托")))
        p("get_subject(subject_title='委托', subject_index=1)  # 用候选 index 二段式重试，全程不抄 UUID",
            registry.executeTool("get_subject", obj("subject_title" to "委托", "subject_index" to 1)))
        p("get_subject(subject_title='委托', subject_index=9)  # 越界，返回候选",
            registry.executeTool("get_subject", obj("subject_title" to "委托", "subject_index" to 9)))

        assertTrue(currentTodo.todoItems.isNotEmpty())
    }
}

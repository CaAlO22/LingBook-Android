package com.lingji.app.domain.tool.note

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = NoteTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    private val testSubject = Subject(
        id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
        aggregatedNote = "# Math\n\nContent here",
        pages = listOf(
            NotebookPage(id = "p1", title = "代数", content = "代数内容"),
            NotebookPage(id = "p2", title = "重复", content = "abc abc abc")
        ),
        studyPlan = "Plan A"
    )

    private val fragmentSubject = Subject(
        id = "s2", title = "碎片笔记", type = SubjectType.FRAGMENT,
        aggregatedNote = "# 碎片\n\n内容A内容A"
    )

    private val todoSubject = Subject(
        id = "s3", title = "每日委托", type = SubjectType.TODO,
        todoItems = listOf(
            TodoItem(id = "t1", content = "买牛奶", isDone = false),
            TodoItem(id = "t2", content = "交作业", isDone = true)
        )
    )

    @Test
    fun create_returnsFourTools() {
        assertEquals(4, tools.size)
    }

    @Test
    fun get_aggregated_note_returnsContent() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_aggregated_note"]!!.execute(params)
        // NOTEBOOK 类型应返回页面拼接内容，而非 aggregatedNote 欢迎语
        assertTrue(result.contains("代数内容"))
    }

    @Test
    fun get_aggregated_note_todo_returnsCheckboxList() = runTest {
        coEvery { repo.getSubjectByIdOnce("s3") } returns todoSubject
        val params = JsonObject().apply { addProperty("subject_id", "s3") }
        val result = toolMap["get_aggregated_note"]!!.execute(params)
        assertTrue(result.contains("- [ ] 买牛奶"))
        assertTrue(result.contains("- [x] 交作业"))
    }

    @Test
    fun get_study_plan_returnsContent() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_study_plan"]!!.execute(params)
        assertTrue(result.contains("\"content\":\"Plan A\""))
    }

    @Test
    fun update_study_plan_callsRepo() = runTest {
        coEvery { repo.updateStudyPlan("s1", "New plan") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("content", "New plan")
        }
        val result = toolMap["update_study_plan"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updateStudyPlan("s1", "New plan") }
    }

    // ── edit_replace ──

    @Test
    fun edit_replace_notebookPage_replacesFirstOccurrence() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        var capturedContent: String? = null
        coEvery { repo.updatePage("s1", any()) } answers {
            capturedContent = secondArg<NotebookPage>().content
            Unit
        }
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
            addProperty("find", "代数")
            addProperty("occurrence", 1)
            addProperty("replace", "几何")
        }
        val result = toolMap["edit_replace"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        assertEquals("几何内容", capturedContent)
    }

    @Test
    fun edit_replace_notebookPage_byIndex_replacesSecondOccurrence() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        var capturedContent: String? = null
        coEvery { repo.updatePage("s1", any()) } answers {
            capturedContent = secondArg<NotebookPage>().content
            Unit
        }
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_index", 1)
            addProperty("find", "abc")
            addProperty("occurrence", 2)
            addProperty("replace", "xyz")
        }
        val result = toolMap["edit_replace"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        assertEquals("abc xyz abc", capturedContent)
    }

    @Test
    fun edit_replace_fragment_editsAggregatedNote() = runTest {
        coEvery { repo.getSubjectByIdOnce("s2") } returns fragmentSubject
        coEvery { repo.updateAggregatedNote("s2", any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s2")
            addProperty("find", "内容A")
            addProperty("occurrence", 2)
            addProperty("replace", "内容B")
        }
        val result = toolMap["edit_replace"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updateAggregatedNote("s2", "# 碎片\n\n内容A内容B") }
    }

    @Test
    fun edit_replace_notebookWithoutPageId_returnsError() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("find", "代数")
            addProperty("occurrence", 1)
            addProperty("replace", "几何")
        }
        val result = toolMap["edit_replace"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
        assertTrue(result.contains("page_id"))
    }

    @Test
    fun edit_replace_occurrenceNotFound_returnsError() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
            addProperty("find", "不存在的文本")
            addProperty("occurrence", 1)
            addProperty("replace", "x")
        }
        val result = toolMap["edit_replace"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
        assertTrue(result.contains("未找到"))
    }

    /** P1-3：错误文案只能指向真实存在的工具，且带机器可判别字段。 */
    @Test
    fun edit_replace_todo_returnsStructuredErrorPointingToRealTodoTools() = runTest {
        coEvery { repo.getSubjectByIdOnce("s3") } returns todoSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s3")
            addProperty("find", "买牛奶")
            addProperty("occurrence", 1)
            addProperty("replace", "买酸奶")
        }
        val result = toolMap["edit_replace"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
        assertTrue(result.contains("\"code\":\"UNSUPPORTED_FOR_SUBJECT_TYPE\""))
        assertTrue(result.contains("\"subject_type\":\"TODO\""))
        assertTrue(result.contains("add_todo"))
        assertTrue(result.contains("update_todo"))
        assertTrue(result.contains("delete_todo"))
        assertTrue(result.contains("list_todos"))
        coVerify(exactly = 0) { repo.updateAggregatedNote(any(), any()) }
    }
}

package com.lingji.app.domain.tool.note

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
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
        pages = listOf(NotebookPage(id = "p1", title = "代数", content = "代数内容")),
        studyPlan = "Plan A"
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
    fun update_aggregated_note_callsRepo() = runTest {
        coEvery { repo.updateAggregatedNote("s1", "New content") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("content", "New content")
        }
        val result = toolMap["update_aggregated_note"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updateAggregatedNote("s1", "New content") }
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
}

package com.lingji.app.domain.tool.subject

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val summaryDao = mockk<SubjectSummaryDao>()
    private val tools = SubjectTools.create(repo, summaryDao)
    private val toolMap = tools.associateBy { it.name }

    @Test
    fun create_returnsFiveTools() {
        assertEquals(5, tools.size)
        assertTrue(toolMap.containsKey("list_subjects"))
        assertTrue(toolMap.containsKey("get_subject"))
        assertTrue(toolMap.containsKey("create_subject"))
        assertTrue(toolMap.containsKey("delete_subject"))
        assertTrue(toolMap.containsKey("rename_subject"))
    }

    @Test
    fun list_subjects_returnsJsonArray() = runTest {
        coEvery { repo.getAllSubjects() } returns flowOf(
            listOf(
                Subject(id = "s1", title = "Math", type = SubjectType.NOTEBOOK),
                Subject(id = "s2", title = "Physics", type = SubjectType.FRAGMENT)
            )
        )
        val result = toolMap["list_subjects"]!!.execute(JsonObject())
        assertTrue(result.contains("\"id\":\"s1\""))
        assertTrue(result.contains("\"title\":\"Math\""))
        assertTrue(result.contains("\"type\":\"NOTEBOOK\""))
    }

    @Test
    fun get_subject_returnsOverview() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns Subject(
            id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
            fragments = listOf(), unmergedFragments = listOf(),
            pages = listOf()
        )
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_subject"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"s1\""))
        assertTrue(result.contains("\"title\":\"Math\""))
    }

    @Test
    fun get_subject_notFound_returnsError() = runTest {
        coEvery { repo.getSubjectByIdOnce("nope") } returns null
        val params = JsonObject().apply { addProperty("subject_id", "nope") }
        val result = toolMap["get_subject"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun create_subject_callsRepoInsert() = runTest {
        coEvery { repo.insert(any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("title", "New Note")
            addProperty("type", "notebook")
        }
        val result = toolMap["create_subject"]!!.execute(params)
        coVerify { repo.insert(any()) }
        assertTrue(result.contains("\"id\""))
        assertTrue(result.contains("\"title\":\"New Note\""))
    }

    @Test
    fun delete_subject_callsRepoDelete() = runTest {
        coEvery { repo.delete("s1") } returns Unit
        coEvery { summaryDao.deleteBySubjectId("s1") } returns Unit
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["delete_subject"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.delete("s1") }
        coVerify { summaryDao.deleteBySubjectId("s1") }
    }

    @Test
    fun rename_subject_callsRepoRename() = runTest {
        coEvery { repo.rename("s1", "New Title") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("new_title", "New Title")
        }
        val result = toolMap["rename_subject"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.rename("s1", "New Title") }
    }
}

package com.lingji.app.domain.tool.page

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

class PageToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = PageTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    private val testSubject = Subject(
        id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
        pages = listOf(
            NotebookPage(id = "p1", title = "Page 1", content = "Content 1"),
            NotebookPage(id = "p2", title = "Page 2", content = "Content 2")
        )
    )

    @Test
    fun create_returnsFiveTools() {
        assertEquals(5, tools.size)
    }

    @Test
    fun list_pages_returnsArray() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["list_pages"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"p1\""))
        assertTrue(result.contains("\"title\":\"Page 1\""))
    }

    @Test
    fun get_page_returnsContent() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
        }
        val result = toolMap["get_page"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"p1\""))
        assertTrue(result.contains("\"content\":\"Content 1\""))
    }

    @Test
    fun get_page_notFound_returnsError() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "nope")
        }
        val result = toolMap["get_page"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun create_page_callsAddPage() = runTest {
        coEvery { repo.addPage("s1", any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("title", "New Page")
        }
        val result = toolMap["create_page"]!!.execute(params)
        coVerify { repo.addPage("s1", any()) }
        assertTrue(result.contains("\"id\""))
    }

    @Test
    fun update_page_callsUpdatePage() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        coEvery { repo.updatePage("s1", any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
            addProperty("content", "Updated content")
        }
        val result = toolMap["update_page"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updatePage("s1", any()) }
    }

    @Test
    fun delete_page_callsDeletePage() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        coEvery { repo.deletePage("s1", "p1") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
        }
        val result = toolMap["delete_page"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.deletePage("s1", "p1") }
    }
}

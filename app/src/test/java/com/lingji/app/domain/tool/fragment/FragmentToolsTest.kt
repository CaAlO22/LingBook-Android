package com.lingji.app.domain.tool.fragment

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Fragment
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

class FragmentToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = FragmentTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    private val testSubject = Subject(
        id = "s1", title = "Notes", type = SubjectType.FRAGMENT,
        fragments = listOf(Fragment(id = "f1", content = "Old fragment")),
        unmergedFragments = listOf(Fragment(id = "f2", content = "New fragment"))
    )
    private val todoSubject = Subject(
        id = "s2", title = "每日委托", type = SubjectType.TODO,
        unmergedFragments = listOf(Fragment(id = "dirty", content = "看不见的脏数据"))
    )
    private val notebookSubject = Subject(
        id = "s3", title = "英语文章", type = SubjectType.NOTEBOOK,
        pages = listOf(NotebookPage(id = "p1", title = "Page 1", content = "正文")),
        unmergedFragments = listOf(Fragment(id = "dirty2", content = "孤儿碎片"))
    )

    @Test
    fun create_returnsFiveTools() {
        assertEquals(5, tools.size)
    }

    @Test
    fun list_fragments_returnsAll() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["list_fragments"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"f1\""))
        assertTrue(result.contains("\"id\":\"f2\""))
        assertTrue(result.contains("\"orphaned\":false"))
    }

    @Test
    fun add_fragment_callsRepo() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        coEvery { repo.addFragment("s1", any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("content", "New note")
        }
        val result = toolMap["add_fragment"]!!.execute(params)
        coVerify { repo.addFragment("s1", any()) }
        assertTrue(result.contains("\"id\""))
    }

    @Test
    fun update_fragment_callsRepo() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        coEvery { repo.updateFragment("s1", "f1", "Updated") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("fragment_id", "f1")
            addProperty("content", "Updated")
        }
        val result = toolMap["update_fragment"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updateFragment("s1", "f1", "Updated") }
    }

    @Test
    fun delete_fragment_callsRepo() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        coEvery { repo.deleteFragment("s1", "f1") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("fragment_id", "f1")
        }
        val result = toolMap["delete_fragment"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.deleteFragment("s1", "f1") }
    }

    // ── P0-2：类型校验，杜绝「写成功但 get_aggregated_note 读不到」 ──

    @Test
    fun add_fragment_toTodo_isRejectedAndNothingWritten() = runTest {
        coEvery { repo.getSubjectByIdOnce("s2") } returns todoSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s2")
            addProperty("content", "任意文本")
        }
        val result = toolMap["add_fragment"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
        assertTrue(result.contains("\"code\":\"UNSUPPORTED_FOR_SUBJECT_TYPE\""))
        assertTrue(result.contains("\"subject_type\":\"TODO\""))
        assertTrue(result.contains("add_todo"))
        coVerify(exactly = 0) { repo.addFragment(any(), any()) }
    }

    @Test
    fun add_fragment_toNotebook_isRejected() = runTest {
        coEvery { repo.getSubjectByIdOnce("s3") } returns notebookSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s3")
            addProperty("content", "任意文本")
        }
        val result = toolMap["add_fragment"]!!.execute(params)
        assertTrue(result.contains("\"code\":\"UNSUPPORTED_FOR_SUBJECT_TYPE\""))
        assertTrue(result.contains("\"subject_type\":\"NOTEBOOK\""))
        coVerify(exactly = 0) { repo.addFragment(any(), any()) }
    }

    @Test
    fun update_fragment_onTodo_isRejected() = runTest {
        coEvery { repo.getSubjectByIdOnce("s2") } returns todoSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s2")
            addProperty("fragment_id", "dirty")
            addProperty("content", "改了也看不见")
        }
        val result = toolMap["update_fragment"]!!.execute(params)
        assertTrue(result.contains("\"code\":\"UNSUPPORTED_FOR_SUBJECT_TYPE\""))
        coVerify(exactly = 0) { repo.updateFragment(any(), any(), any()) }
    }

    @Test
    fun list_fragments_onTodo_marksExistingDirtyData() = runTest {
        coEvery { repo.getSubjectByIdOnce("s2") } returns todoSubject
        val params = JsonObject().apply { addProperty("subject_id", "s2") }
        val result = toolMap["list_fragments"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"dirty\""))
        assertTrue(result.contains("\"orphaned\":true"))
        assertTrue(result.contains("get_aggregated_note 读不到"))
    }

    @Test
    fun delete_fragment_onTodo_canCleanExistingDirtyData() = runTest {
        coEvery { repo.getSubjectByIdOnce("s2") } returns todoSubject
        coEvery { repo.deleteFragment("s2", "dirty") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s2")
            addProperty("fragment_id", "dirty")
        }
        val result = toolMap["delete_fragment"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        assertTrue(result.contains("\"cleaned_orphan\":true"))
        coVerify(exactly = 1) { repo.deleteFragment("s2", "dirty") }
    }

    @Test
    fun search_fragments_skipsNonFragmentSubjects() = runTest {
        coEvery { repo.getSubjectByIdOnce("s2") } returns todoSubject
        val params = JsonObject().apply {
            addProperty("query", "脏数据")
            addProperty("subject_id", "s2")
        }
        assertEquals("[]", toolMap["search_fragments"]!!.execute(params))
    }
}

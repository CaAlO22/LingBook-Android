package com.lingji.app.domain.tool.fragment

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Fragment
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

    @Test
    fun create_returnsFourTools() {
        assertEquals(4, tools.size)
    }

    @Test
    fun list_fragments_returnsAll() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["list_fragments"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"f1\""))
        assertTrue(result.contains("\"id\":\"f2\""))
    }

    @Test
    fun add_fragment_callsRepo() = runTest {
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
        coEvery { repo.deleteFragment("s1", "f1") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("fragment_id", "f1")
        }
        val result = toolMap["delete_fragment"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.deleteFragment("s1", "f1") }
    }
}

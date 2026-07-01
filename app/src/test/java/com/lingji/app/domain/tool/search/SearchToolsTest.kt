package com.lingji.app.domain.tool.search

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.entities.SubjectSummaryEntity
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.SearchResult
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val llmService = mockk<LLMService>()
    private val settingsRepo = mockk<SettingsRepository>()
    private val summaryDao = mockk<SubjectSummaryDao>()
    private val indexService = mockk<IndexService>()

    private val tools = SearchTools.create(repo, llmService, settingsRepo, summaryDao, indexService)
    private val toolMap = tools.associateBy { it.name }

    @Test
    fun create_returnsThreeTools() {
        assertEquals(3, tools.size)
    }

    @Test
    fun get_page_index_returnsEntries() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns Subject(
            id = "s1",
            title = "Math",
            type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "Algebra", content = "x+1=2")),
            pageIndexEntries = listOf(
                PageIndexEntry(pageId = "p1", title = "Algebra", keywords = listOf("equation"), summary = "Basic algebra")
            )
        )
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_page_index"]!!.execute(params)
        assertTrue(result.contains("\"page_id\":\"p1\""))
        assertTrue(result.contains("\"summary\":\"Basic algebra\""))
    }

    @Test
    fun search_pages_returnsResults() = runTest {
        val page = NotebookPage(id = "p1", title = "Algebra", content = "x+1=2")
        val subject = Subject(
            id = "s1",
            title = "Math",
            type = SubjectType.NOTEBOOK,
            pages = listOf(page),
            pageIndexEntries = listOf(
                PageIndexEntry(pageId = "p1", title = "Algebra", keywords = listOf("equation"), summary = "Basic algebra")
            )
        )
        coEvery { repo.getAllSubjects() } returns flowOf(listOf(subject))
        every { indexService.searchPages(any(), any(), any()) } returns listOf(
            SearchResult(page = page, score = 0.8f, matchedKeywords = listOf("equation"), summarySnippet = "Basic algebra")
        )
        val params = JsonObject().apply { addProperty("query", "equation") }
        val result = toolMap["search_pages"]!!.execute(params)
        assertTrue(result.contains("\"page_id\":\"p1\""))
        assertTrue(result.contains("\"score\":0.8"))
    }

    @Test
    fun summarize_all_notes_usesCachedSummary() = runTest {
        val subject = Subject(
            id = "s1",
            title = "Math",
            type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "P1", content = "content", updatedAt = 1000L)),
            pageIndexEntries = null
        )
        coEvery { repo.getAllSubjects() } returns flowOf(listOf(subject))
        coEvery { summaryDao.getBySubjectId("s1") } returns SubjectSummaryEntity("s1", "Cached summary", 2000L)
        val params = JsonObject()
        val result = toolMap["summarize_all_notes"]!!.execute(params)
        assertTrue(result.contains("\"summary\":\"Cached summary\""))
        coVerify(exactly = 0) { llmService.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun summarize_all_notes_regeneratesWhenStale() = runTest {
        val subject = Subject(
            id = "s1",
            title = "Math",
            type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "P1", content = "content", updatedAt = 5000L)),
            pageIndexEntries = null
        )
        coEvery { repo.getAllSubjects() } returns flowOf(listOf(subject))
        coEvery { summaryDao.getBySubjectId("s1") } returns SubjectSummaryEntity("s1", "Old summary", 2000L)
        coEvery { settingsRepo.getSettingsOnce() } returns AISettings(apiKey = "test-key")
        coEvery { llmService.generate(any(), any(), any(), any(), any()) } returns "Fresh summary"
        coEvery { summaryDao.upsert(any()) } returns Unit
        val params = JsonObject()
        val result = toolMap["summarize_all_notes"]!!.execute(params)
        assertTrue(result.contains("\"summary\":\"Fresh summary\""))
        coVerify { summaryDao.upsert(any()) }
    }

    @Test
    fun summarize_all_notes_generatesWhenNoCache() = runTest {
        val subject = Subject(
            id = "s1",
            title = "Math",
            type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "P1", content = "content", updatedAt = 1000L)),
            pageIndexEntries = null
        )
        coEvery { repo.getAllSubjects() } returns flowOf(listOf(subject))
        coEvery { summaryDao.getBySubjectId("s1") } returns null
        coEvery { settingsRepo.getSettingsOnce() } returns AISettings(apiKey = "test-key")
        coEvery { llmService.generate(any(), any(), any(), any(), any()) } returns "New summary"
        coEvery { summaryDao.upsert(any()) } returns Unit
        val params = JsonObject()
        val result = toolMap["summarize_all_notes"]!!.execute(params)
        assertTrue(result.contains("\"summary\":\"New summary\""))
        coVerify { summaryDao.upsert(any()) }
    }
}

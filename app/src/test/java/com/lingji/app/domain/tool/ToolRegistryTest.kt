package com.lingji.app.domain.tool

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.tool.image.ConversationImageStore
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    private val repo = mockk<SubjectRepository>()
    private val llmService = mockk<LLMService>()
    private val settingsRepo = mockk<SettingsRepository>()
    private val summaryDao = mockk<SubjectSummaryDao>()
    private val indexService = mockk<IndexService>()
    private val conversationImageStore = mockk<ConversationImageStore>()

    private val registry = ToolRegistry(repo, llmService, settingsRepo, summaryDao, indexService, conversationImageStore)

    @Test
    fun getAllTools_returns23Tools() {
        val tools = registry.getAllTools()
        assertEquals(23, tools.size)
    }

    @Test
    fun getTool_returnsToolByName() {
        assertNotNull(registry.getTool("list_subjects"))
        assertNotNull(registry.getTool("create_page"))
        assertNotNull(registry.getTool("summarize_all_notes"))
    }

    @Test
    fun getTool_unknownReturnsNull() {
        assertEquals(null, registry.getTool("nonexistent"))
    }

    @Test
    fun executeTool_unknownReturnsError() = runTest {
        val result = registry.executeTool("nonexistent", JsonObject())
        assertTrue(result.startsWith("Error: Unknown tool"))
    }

    @Test
    fun toOpenAITools_returnsJsonArray() {
        val arr = registry.toOpenAITools()
        assertEquals(23, arr.size())
        val first = arr[0].asJsonObject
        assertEquals("function", first.get("type").asString)
        assertNotNull(first.getAsJsonObject("function").get("name"))
    }

    @Test
    fun toolNamesAreUnique() {
        val names = registry.getAllTools().map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}

package com.lingji.app.domain.tool

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.image.ConversationImageStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用可读标题替代逐 token 复述 UUID；重名返回候选而不是猜一个。 */
class ToolRegistryTitleTest {

    private val repo = mockk<SubjectRepository>()
    private val registry = ToolRegistry(
        repo,
        mockk<LLMService>(),
        mockk<SettingsRepository>(),
        mockk<SubjectSummaryDao>(),
        mockk<IndexService>(),
        mockk<ConversationImageStore>()
    )

    private val subjects = listOf(
        Subject(id = "7b7fe4f2", title = "英语单词", type = SubjectType.FRAGMENT),
        Subject(id = "cb720714-f26d-4f51-b4ed-1ba94e143cec", title = "每日委托", type = SubjectType.TODO,
            todoItems = emptyList()),
        Subject(id = "aaaaaaaa", title = "每周委托", type = SubjectType.TODO, todoItems = emptyList())
    )

    private fun withTitle(title: String) = JsonObject().apply { addProperty("subject_title", title) }

    private fun stubAll() {
        coEvery { repo.getAllSubjects() } returns flowOf(subjects)
    }

    @Test
    fun exactTitle_injectsSubjectIdEvenForUuid() = runTest {
        stubAll()
        coEvery { repo.getSubjectByIdOnce("cb720714-f26d-4f51-b4ed-1ba94e143cec") } returns subjects[1]
        val result = registry.executeTool("get_subject", withTitle("每日委托"))
        assertTrue(result, result.contains("\"id\":\"cb720714-f26d-4f51-b4ed-1ba94e143cec\""))
        assertTrue(result, result.contains("\"todo_count\":0"))
    }

    @Test
    fun partialTitle_singleMatch_injectsSubjectId() = runTest {
        stubAll()
        coEvery { repo.getSubjectByIdOnce("7b7fe4f2") } returns subjects[0]
        val result = registry.executeTool("get_subject", withTitle("英语"))
        assertTrue(result, result.contains("\"id\":\"7b7fe4f2\""))
    }

    @Test
    fun ambiguousTitle_returnsCandidateListAndDoesNotExecute() = runTest {
        stubAll()
        val result = registry.executeTool("delete_subject", withTitle("委托"))
        assertTrue(result, result.startsWith("Error:"))
        assertTrue(result, result.contains("\"code\":\"AMBIGUOUS_TITLE\""))
        assertTrue(result, result.contains("\"candidates\""))
        assertTrue(result, result.contains("每日委托"))
        assertTrue(result, result.contains("每周委托"))
        coVerify(exactly = 0) { repo.delete(any()) }
    }

    @Test
    fun noMatch_returnsSubjectNotFoundWithActionableSuggestion() = runTest {
        stubAll()
        val result = registry.executeTool("get_subject", withTitle("不存在的笔记"))
        assertTrue(result, result.contains("\"code\":\"SUBJECT_NOT_FOUND\""))
        assertTrue(result, result.contains("list_subjects"))
    }

    @Test
    fun explicitSubjectId_winsOverTitle() = runTest {
        stubAll()
        coEvery { repo.getSubjectByIdOnce("aaaaaaaa") } returns subjects[2]
        val params = withTitle("每日委托").apply { addProperty("subject_id", "aaaaaaaa") }
        val result = registry.executeTool("get_subject", params)
        assertTrue(result, result.contains("\"id\":\"aaaaaaaa\""))
    }

    @Test
    fun schema_exposesSubjectTitleAndDropsMandatorySubjectId() = runTest {
        val arr = registry.toOpenAITools()
        val getSubject = arr.first {
            it.asJsonObject.getAsJsonObject("function").get("name").asString == "get_subject"
        }.asJsonObject.getAsJsonObject("function")
        val parameters = getSubject.getAsJsonObject("parameters")
        assertTrue(parameters.getAsJsonObject("properties").has("subject_title"))
        val required = parameters.getAsJsonArray("required")
        assertTrue(required == null || required.none { it.asString == "subject_id" })
        assertTrue(getSubject.get("description").asString.contains("二选一"))
    }

    @Test
    fun schema_toolsWithoutSubjectIdAreUntouched() = runTest {
        val arr = registry.toOpenAITools()
        val create = arr.first {
            it.asJsonObject.getAsJsonObject("function").get("name").asString == "create_subject"
        }.asJsonObject.getAsJsonObject("function")
        assertFalse(create.getAsJsonObject("parameters").getAsJsonObject("properties").has("subject_title"))
    }

    @Test
    fun toolError_keepsErrorPrefixAndMachineReadableFields() {
        val err = toolError(
            ToolErrorCode.UNSUPPORTED_FOR_SUBJECT_TYPE, "类型不匹配",
            "subject_type" to "TODO", "suggestion" to null
        )
        assertTrue(err.startsWith("Error:"))
        assertTrue(err.contains("\"code\":\"UNSUPPORTED_FOR_SUBJECT_TYPE\""))
        assertTrue(err.contains("\"subject_type\":\"TODO\""))
        assertTrue(err.contains("\"suggestion\":null"))
        assertEquals(true, err.endsWith("}"))
    }

    @Test
    fun ambiguousTitle_candidatesCarryZeroBasedIndex() = runTest {
        stubAll()
        val result = registry.executeTool("get_subject", withTitle("委托"))
        assertTrue(result, result.contains("\"code\":\"AMBIGUOUS_TITLE\""))
        assertTrue(result, result.contains("\"index\":0"))
        assertTrue(result, result.contains("\"index\":1"))
        assertTrue(result, result.contains("subject_index"))
    }

    @Test
    fun subjectTitlePlusIndex_selectsIntendedSubjectWithoutCopyingId() = runTest {
        stubAll()
        coEvery { repo.getSubjectByIdOnce("aaaaaaaa") } returns subjects[2]
        val params = withTitle("委托").apply { addProperty("subject_index", 1) }
        val result = registry.executeTool("get_subject", params)
        assertFalse(result, result.startsWith("Error:"))
        assertTrue(result, result.contains("\"id\":\"aaaaaaaa\""))
    }

    @Test
    fun subjectTitlePlusIndex_zeroSelectsFirstCandidate() = runTest {
        stubAll()
        coEvery { repo.getSubjectByIdOnce("cb720714-f26d-4f51-b4ed-1ba94e143cec") } returns subjects[1]
        val params = withTitle("委托").apply { addProperty("subject_index", 0) }
        val result = registry.executeTool("get_subject", params)
        assertTrue(result, result.contains("\"id\":\"cb720714-f26d-4f51-b4ed-1ba94e143cec\""))
    }

    @Test
    fun subjectIndexOutOfRange_returnsStructuredErrorAndDoesNotExecute() = runTest {
        stubAll()
        val params = withTitle("委托").apply { addProperty("subject_index", 9) }
        val result = registry.executeTool("delete_subject", params)
        assertTrue(result, result.contains("\"code\":\"SUBJECT_INDEX_OUT_OF_RANGE\""))
        assertTrue(result, result.contains("\"candidate_count\":2"))
        assertTrue(result, result.contains("\"candidates\""))
        coVerify(exactly = 0) { repo.delete(any()) }
    }

    @Test
    fun subjectIndexWithoutTitle_isRejectedWithMissingParameter() = runTest {
        stubAll()
        val params = JsonObject().apply { addProperty("subject_index", 0) }
        val result = registry.executeTool("get_subject", params)
        assertTrue(result, result.contains("\"code\":\"MISSING_PARAMETER\""))
        assertTrue(result, result.contains("subject_title"))
    }

    @Test
    fun nonIntegerSubjectIndex_isRejectedWithInvalidParameter() = runTest {
        stubAll()
        val params = withTitle("委托").apply { addProperty("subject_index", "第一个") }
        val result = registry.executeTool("get_subject", params)
        assertTrue(result, result.contains("\"code\":\"INVALID_PARAMETER\""))
    }

    @Test
    fun explicitSubjectId_winsOverTitleAndIndex() = runTest {
        stubAll()
        coEvery { repo.getSubjectByIdOnce("aaaaaaaa") } returns subjects[2]
        val params = withTitle("委托").apply {
            addProperty("subject_id", "aaaaaaaa")
            addProperty("subject_index", 0)
        }
        val result = registry.executeTool("get_subject", params)
        assertTrue(result, result.contains("\"id\":\"aaaaaaaa\""))
    }

    @Test
    fun schema_exposesSubjectIndexAsOptionalInteger() = runTest {
        val arr = registry.toOpenAITools()
        val function = arr.first {
            it.asJsonObject.getAsJsonObject("function").get("name").asString == "get_subject"
        }.asJsonObject.getAsJsonObject("function")
        val parameters = function.getAsJsonObject("parameters")
        val properties = parameters.getAsJsonObject("properties")
        assertEquals("integer", properties.getAsJsonObject("subject_index").get("type").asString)
        val required = parameters.getAsJsonArray("required")
        assertTrue(required == null || required.none { it.asString == "subject_index" })
        assertTrue(function.get("description").asString.contains("subject_index"))
    }

    @Test
    fun scopedRegistry_injectsCurrentSubjectAndIgnoresIndex() = runTest {
        coEvery { repo.getSubjectByIdOnce("aaaaaaaa") } returns subjects[2]
        val scoped = ScopedToolRegistry(registry, "aaaaaaaa", ScopedToolRegistry.SINGLE_NOTE_TOOLS)
        val function = scoped.toOpenAITools().first {
            it.asJsonObject.getAsJsonObject("function").get("name").asString == "get_subject"
        }.asJsonObject.getAsJsonObject("function")
        assertFalse(function.getAsJsonObject("parameters").getAsJsonObject("properties").has("subject_index"))
        val result = scoped.executeTool("get_subject", JsonObject().apply { addProperty("subject_index", 0) })
        assertTrue(result, result.contains("\"id\":\"aaaaaaaa\""))
    }
}

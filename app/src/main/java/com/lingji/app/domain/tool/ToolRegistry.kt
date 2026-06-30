package com.lingji.app.domain.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.tool.fragment.FragmentTools
import com.lingji.app.domain.tool.note.NoteTools
import com.lingji.app.domain.tool.page.PageTools
import com.lingji.app.domain.tool.search.SearchTools
import com.lingji.app.domain.tool.subject.SubjectTools
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    subjectRepository: SubjectRepository,
    llmService: LLMService,
    settingsRepository: SettingsRepository,
    subjectSummaryDao: SubjectSummaryDao,
    indexService: IndexService
) {
    private val tools: Map<String, Tool> = buildList {
        addAll(SubjectTools.create(subjectRepository))
        addAll(PageTools.create(subjectRepository))
        addAll(FragmentTools.create(subjectRepository))
        addAll(NoteTools.create(subjectRepository))
        addAll(SearchTools.create(subjectRepository, llmService, settingsRepository, subjectSummaryDao, indexService))
    }.associateBy { it.name }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun toOpenAITools(): JsonArray = JsonArray().apply {
        tools.values.forEach { add(it.toOpenAITool()) }
    }

    suspend fun executeTool(name: String, params: JsonObject): String {
        val tool = tools[name] ?: return "Error: Unknown tool '$name'"
        return runCatching { tool.execute(params) }
            .getOrElse { "Error: ${it.message ?: "Unknown error"}" }
    }
}

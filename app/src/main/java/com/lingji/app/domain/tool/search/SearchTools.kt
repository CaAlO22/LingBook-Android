package com.lingji.app.domain.tool.search

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.entities.SubjectSummaryEntity
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import kotlinx.coroutines.flow.first

object SearchTools {

    fun create(
        repo: SubjectRepository,
        llmService: LLMService,
        settingsRepo: SettingsRepository,
        summaryDao: SubjectSummaryDao,
        indexService: IndexService
    ): List<Tool> = listOf(
        GetPageIndex(repo),
        SearchPages(repo, indexService),
        SummarizeAllNotes(repo, llmService, settingsRepo, summaryDao)
    )

    private class GetPageIndex(private val repo: SubjectRepository) : Tool {
        override val name = "get_page_index"
        override val description = "读取指定笔记的页面索引：每页的标题、关键词和摘要。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记 ID"
                }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val entries = subject.pageIndexEntries ?: emptyList()
            val arr = buildJsonArray {
                for (e in entries) {
                    +buildJsonObject {
                        "page_id" to e.pageId
                        "title" to e.title
                        "keywords" to buildJsonArray { e.keywords.forEach { +it } }
                        "summary" to e.summary
                    }
                }
            }
            return arr.toString()
        }
    }

    private class SearchPages(
        private val repo: SubjectRepository,
        private val indexService: IndexService
    ) : Tool {
        override val name = "search_pages"
        override val description = "基于 AI 生成的关键词索引和摘要进行语义搜索（可匹配近义词、别名、拼音等）。可指定 subject_id 搜索特定笔记，不传则搜索全部笔记。返回匹配页面的标题、摘要片段和匹配度。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "query" to buildJsonObject {
                    "type" to "string"
                    "description" to "搜索关键词"
                }
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "可选：限定搜索的笔记 ID"
                }
            }
            "required" to buildJsonArray { +"query" }
        }
        override suspend fun execute(params: JsonObject): String {
            val query = params.get("query")?.asString
                ?: return "Error: Missing required parameter: query"
            val subjectId = params.get("subject_id")?.asString

            val subjects = if (subjectId != null) {
                listOfNotNull(repo.getSubjectByIdOnce(subjectId))
            } else {
                repo.getAllSubjects().first()
            }

            val results = mutableListOf<String>()
            for (s in subjects) {
                val pages = s.pages ?: continue
                val entries = s.pageIndexEntries ?: continue
                val searchResults = indexService.searchPages(query, pages, entries)
                for (r in searchResults) {
                    results.add(buildJsonObject {
                        "subject_id" to s.id
                        "page_id" to r.page.id
                        "title" to r.page.title
                        "snippet" to r.summarySnippet
                        "score" to r.score
                    }.toString())
                }
            }
            if (results.isEmpty()) return "[]"
            return "[" + results.joinToString(",") + "]"
        }
    }

    private class SummarizeAllNotes(
        private val repo: SubjectRepository,
        private val llmService: LLMService,
        private val settingsRepo: SettingsRepository,
        private val summaryDao: SubjectSummaryDao
    ) : Tool {
        override val name = "summarize_all_notes"
        override val description = "获取所有笔记的标题和摘要，帮助了解用户有哪些笔记及各自主题。无参数。对于无摘要或摘要后有过修改的笔记会自动重新生成摘要。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {}
        }
        override suspend fun execute(params: JsonObject): String {
            val subjects = repo.getAllSubjects().first()
            val settings = runCatching { settingsRepo.getSettingsOnce() }.getOrNull()
            val rows = subjects.map { s ->
                val cached = summaryDao.getBySubjectId(s.id)
                val summary = if (needsRegeneration(s, cached)) {
                    generateSummary(s, settings)
                } else {
                    cached!!.summary
                }
                Triple(s.id, s.title, summary)
            }
            val arr = buildJsonArray {
                for ((id, title, summary) in rows) {
                    +buildJsonObject {
                        "subject_id" to id
                        "subject_title" to title
                        "summary" to summary
                    }
                }
            }
            return arr.toString()
        }

        private fun needsRegeneration(
            subject: Subject,
            cached: SubjectSummaryEntity?
        ): Boolean {
            if (cached == null) return true
            val lastModified = when (subject.type) {
                SubjectType.NOTEBOOK -> subject.pages?.maxOfOrNull { it.updatedAt } ?: 0L
                SubjectType.FRAGMENT -> {
                    val allFrags = subject.fragments + subject.unmergedFragments
                    allFrags.maxOfOrNull { it.timestamp } ?: 0L
                }
            }
            return cached.summarizedAt < lastModified
        }

        private suspend fun generateSummary(
            subject: Subject,
            settings: AISettings?
        ): String {
            if (settings == null || settings.apiKey.isBlank()) return "Error: AI 未配置，无法生成摘要"
            val content = when (subject.type) {
                SubjectType.NOTEBOOK -> {
                    val pages = subject.pages ?: emptyList()
                    pages.joinToString("\n\n") { p ->
                        val title = if (p.title.isNotBlank()) "## ${p.title}\n" else ""
                        "$title${LLMService.stripDataImages(p.content)}"
                    }
                }
                SubjectType.FRAGMENT -> {
                    if (subject.aggregatedNote.isNotBlank()) subject.aggregatedNote
                    else subject.fragments.joinToString("\n") { it.content }
                }
            }
            if (content.isBlank()) return "（无内容）"
            return runCatching {
                val raw = llmService.generate(
                    prompt = "笔记标题：${subject.title}\n\n笔记内容：\n$content",
                    settings = settings,
                    systemPrompt = "你是一个摘要专家。请用100-200字概括以下笔记的核心内容，突出主题和关键知识点。只输出摘要文本，不要添加任何额外格式或说明。"
                )
                val cleaned = LLMService.sanitizeOutput(raw).trim()
                summaryDao.upsert(SubjectSummaryEntity(subject.id, cleaned, System.currentTimeMillis()))
                cleaned
            }.getOrElse { "Error: 摘要生成失败: ${it.message ?: "未知错误"}" }
        }
    }
}

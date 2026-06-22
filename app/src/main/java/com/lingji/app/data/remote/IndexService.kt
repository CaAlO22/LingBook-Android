package com.lingji.app.data.remote

import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexService @Inject constructor(
    private val llmService: LLMService
) {
    companion object {
        fun extractImagesFromContent(content: String): List<String> {
            val regex = "!\\[.*?\\]\\((data:image/[^)]+)\\)".toRegex()
            return regex.findAll(content).map { it.groupValues[1] }.toList()
        }
    }

    suspend fun generatePageIndex(
        page: NotebookPage,
        settings: AISettings,
        onToken: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onWarning: (String) -> Unit = {}
    ): PageIndexEntry {
        val systemPrompt = """你是一个知识索引专家。请分析给定的笔记页面内容，生成以下结构化索引信息：
1. keywords: 5-10个关键词，涵盖页面核心主题和重要概念
2. summary: 一段100字以内的摘要，概括页面主要内容
3. 输出必须是严格的JSON格式，不要包含任何markdown代码块标记或其他额外文本

输出格式示例：
{"keywords":["关键词1","关键词2","关键词3"],"summary":"这是一段摘要..."}"""

        val images = extractImagesFromContent(page.content)
        val prompt = if (images.isNotEmpty()) {
            "请分析以下笔记页面内容（包含 ${images.size} 张图片），生成索引信息。请结合图片中的文字、图表、公式等视觉信息进行综合分析。\n\n页面内容：\n${page.content}"
        } else {
            "请分析以下笔记页面内容，生成索引信息：\n\n页面内容：\n${page.content}"
        }

        val raw = llmService.streamGenerate(
            prompt = prompt,
            settings = settings,
            systemPrompt = systemPrompt,
            onToken = onToken,
            onReasoning = onReasoning,
            images = images,
            onWarning = onWarning
        )

        var keywords: List<String> = emptyList()
        var summary = ""

        try {
            val cleaned = LLMService.sanitizeOutput(raw)
            val parsed = org.json.JSONObject(cleaned)
            val kwArray = parsed.optJSONArray("keywords")
            if (kwArray != null) {
                keywords = (0 until kwArray.length())
                    .map { kwArray.optString(it) }
                    .filter { it.isNotBlank() }
            }
            summary = parsed.optString("summary", "").trim()
        } catch (_: Exception) {
            val keywordMatch = Regex("keywords?[：:]\\s*\\[?([^\\]]+)\\]?", RegexOption.IGNORE_CASE).find(raw)
            if (keywordMatch != null) {
                keywords = keywordMatch.groupValues[1]
                    .split(Regex("[,，;；]"))
                    .map { it.trim().trim('"', '\'') }
                    .filter { it.isNotEmpty() }
            }
            val summaryMatch = Regex("summary[：:]\\s*[\"']?([^\"'\\n]+)[\"']?", RegexOption.IGNORE_CASE).find(raw)
            if (summaryMatch != null) {
                summary = summaryMatch.groupValues[1].trim()
            }
        }

        return PageIndexEntry(
            pageId = page.id,
            title = page.title,
            keywords = keywords,
            summary = summary,
            generatedAt = System.currentTimeMillis()
        )
    }

    suspend fun generateDirectory(
        entries: List<PageIndexEntry>,
        pages: List<NotebookPage>,
        settings: AISettings,
        onToken: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onWarning: (String) -> Unit = {}
    ): String {
        val pageMap = pages.associateBy { it.id }
        val indexText = entries.mapIndexed { idx, entry ->
            val pageNum = pages.indexOfFirst { it.id == entry.pageId } + 1
            "第${pageNum}页 [${entry.title}]: 关键词=${entry.keywords.joinToString("、")}，摘要=${entry.summary}"
        }.joinToString("\n")

        val systemPrompt = """你是一个目录生成专家。请根据以下所有页面的索引信息，生成一份结构化的目录。

要求：
1. 目录应按主题层级组织，将相关的页面归类到同一个大主题下
2. 每个条目必须包含准确的页码
3. 输出格式为Markdown格式的目录
4. 不要包含其他任何解释文字，只输出目录内容

输出格式示例：
# 目录

## 第一章 基础概念
- 1.1 概述 ............. 第1页
- 1.2 核心原理 ............. 第3页

## 第二章 进阶内容
- 2.1 高级应用 ............. 第5页"""

        val prompt = "以下是笔记本中所有页面的索引信息：\n\n$indexText\n\n请根据以上信息生成结构化目录。"

        return llmService.streamGenerate(
            prompt = prompt,
            settings = settings,
            systemPrompt = systemPrompt,
            onToken = onToken,
            onReasoning = onReasoning,
            onWarning = onWarning
        )
    }

    fun getDirtyPages(pages: List<NotebookPage>): List<NotebookPage> {
        return pages.filter { it.indexedAt <= 0 || it.updatedAt > it.indexedAt }
    }

    suspend fun batchBuildIndexesForDirtyPages(
        pages: List<NotebookPage>,
        settings: AISettings,
        onProgress: ((done: Int, total: Int, page: NotebookPage) -> Unit)? = null,
        onToken: ((String) -> Unit)? = null,
        onReasoning: ((String) -> Unit)? = null,
        onWarning: ((String) -> Unit)? = null
    ): Pair<List<PageIndexEntry>, List<String>> {
        val dirtyPages = getDirtyPages(pages)
        val entries = mutableListOf<PageIndexEntry>()
        dirtyPages.forEachIndexed { index, page ->
            onProgress?.invoke(index + 1, dirtyPages.size, page)
            entries.add(
                generatePageIndex(
                    page,
                    settings,
                    onToken ?: {},
                    onReasoning ?: {},
                    onWarning ?: {}
                )
            )
        }
        return entries to dirtyPages.map { it.id }
    }

    fun searchPages(query: String, pages: List<NotebookPage>, indexes: List<PageIndexEntry>): List<SearchResult> {
        if (query.isBlank() || indexes.isEmpty()) return emptyList()
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.length > 1 }

        val pageMap = pages.associateBy { it.id }

        return indexes.mapNotNull { entry ->
            val page = pageMap[entry.pageId] ?: return@mapNotNull null

            val matchedKeywords = entry.keywords.filter { kw ->
                val kwLower = kw.lowercase()
                queryWords.any { qw -> kwLower.contains(qw) || qw.contains(kwLower) }
            }
            val keywordScore = if (entry.keywords.isNotEmpty()) {
                matchedKeywords.size.toFloat() / entry.keywords.size
            } else 0f

            val summaryLower = entry.summary.lowercase()
            val summaryMatches = queryWords.count { summaryLower.contains(it) }
            val summaryScore = if (queryWords.isNotEmpty()) {
                summaryMatches.toFloat() / queryWords.size
            } else 0f

            val titleLower = page.title.lowercase()
            val titleMatches = queryWords.count { titleLower.contains(it) }
            val titleScore = if (queryWords.isNotEmpty()) {
                titleMatches.toFloat() / queryWords.size
            } else 0f

            val score = keywordScore * 0.45f + summaryScore * 0.35f + titleScore * 0.2f
            if (score > 0.05f) {
                SearchResult(
                    page = page,
                    score = score,
                    matchedKeywords = matchedKeywords,
                    summarySnippet = entry.summary
                )
            } else null
        }.sortedByDescending { it.score }
    }
}

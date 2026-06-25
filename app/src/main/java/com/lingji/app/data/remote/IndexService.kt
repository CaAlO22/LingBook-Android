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
        val systemPrompt = """你是一个知识索引专家。请分析给定的笔记页面内容，生成以下两块结构化索引信息：
1. summary: 摘要块，一段100字以内的摘要，概括页面主要内容，可使用更通用的概念描述
2. keywords: 联想搜索块，10-20个用户搜索本页时可能输入的关键词，必须优先保留原文中的专有名词、缩写、英文、拼音、公式、符号写法和别名，例如原文写 sinx 就必须包含 sinx，不要只改写成三角函数
3. 输出必须是严格的JSON格式，不要包含任何markdown代码块标记或其他额外文本

输出格式示例：
{"summary":"这是一段摘要...","keywords":["原文术语","别名","公式写法"]}"""

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
                    .map { kwArray.optString(it).trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }
            summary = parsed.optString("summary", "").trim()
        } catch (_: Exception) {
            val keywordMatch = Regex("(?:keywords?|联想搜索|搜索关键词)[：:]\\s*\\[?([^\\]]+)\\]?", RegexOption.IGNORE_CASE).find(raw)
            if (keywordMatch != null) {
                keywords = keywordMatch.groupValues[1]
                    .split(Regex("[,，;；]"))
                    .map { it.trim().trim('"', '\'') }
                    .filter { it.isNotEmpty() }
                    .distinct()
            }
            val summaryMatch = Regex("(?:summary|摘要)[：:]\\s*[\"']?([^\"'\\n]+)[\"']?", RegexOption.IGNORE_CASE).find(raw)
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
        // 目录页作为第 1 页单独占位，正文实际页码从第 2 页开始，因此索引偏移 +2
        val indexText = entries.mapIndexed { idx, entry ->
            val pageNum = pages.indexOfFirst { it.id == entry.pageId } + 2
            "第${pageNum}页 [${entry.title}]: 联想搜索=${entry.keywords.joinToString("、")}，摘要=${entry.summary}"
        }.joinToString("\n")

        val systemPrompt = """你是一个目录生成专家。请根据以下所有页面的索引信息，生成一份结构化的目录。

要求：
1. 目录应按主题层级组织，将相关的页面归类到同一个大主题下
2. 每个条目必须包含准确的页码，页码必须严格使用输入中给出的"第N页"，不要重新编号
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

            val contentLower = page.content.lowercase()
            val contentMatches = queryWords.count { contentLower.contains(it) }
            val contentScore = if (queryWords.isNotEmpty()) {
                contentMatches.toFloat() / queryWords.size
            } else 0f

            val score = keywordScore * 0.4f + summaryScore * 0.2f + titleScore * 0.2f + contentScore * 0.2f
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

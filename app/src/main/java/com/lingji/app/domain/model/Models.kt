package com.lingji.app.domain.model

import java.util.UUID

enum class APIProvider { OPENAI, DOUBAO, XIAOMI }
enum class SubjectType { FRAGMENT, NOTEBOOK }

fun generateId(): String = UUID.randomUUID().toString().take(8)

data class MimoModel(
    val id: String,
    val name: String,
    val description: String = ""
)

object MimoPresets {
    const val PAY_AS_YOU_GO_URL = "https://api.xiaomimimo.com/v1"
    const val TOKEN_PLAN_URL = "https://token-plan-cn.xiaomimimo.com/v1"

    val MODELS = listOf(
        MimoModel(
            id = "mimo-v2.5-pro",
            name = "MiMo-V2.5-Pro",
            description = "复杂推理、长文档、深度分析"
        ),
        MimoModel(
            id = "mimo-v2.5",
            name = "MiMo-V2.5",
            description = "全模态理解、图文音视频"
        ),
        MimoModel(
            id = "mimo-v2-flash",
            name = "MiMo-V2-Flash",
            description = "低成本、快速响应"
        )
    )
}

data class AISettings(
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val enableThinking: Boolean = false
)

data class Fragment(
    val id: String = generateId(),
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NotebookPage(
    val id: String = generateId(),
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val indexedAt: Long = 0
)

data class PageIndex(
    val id: String,
    val title: String,
    val order: Int
)

data class PageIndexEntry(
    val pageId: String,
    val title: String,
    val keywords: List<String> = emptyList(),
    val summary: String = "",
    val embedding: List<Float> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis()
)

data class SearchResult(
    val page: NotebookPage,
    val score: Float,
    val matchedKeywords: List<String>,
    val summarySnippet: String
)

data class Subject(
    val id: String = generateId(),
    val title: String,
    val type: SubjectType = SubjectType.FRAGMENT,
    val fragments: List<Fragment> = emptyList(),
    val unmergedFragments: List<Fragment> = emptyList(),
    val aggregatedNote: String = "",
    val prevAggregatedNote: String? = null,
    val studyPlan: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0,
    val pages: List<NotebookPage>? = null,
    val pageIndex: List<PageIndex>? = null,
    val pageIndexEntries: List<PageIndexEntry>? = null
) {
    companion object {
        fun create(title: String, type: SubjectType): Subject = Subject(
            id = generateId(),
            title = title,
            type = type,
            aggregatedNote = "# ${title}\n\n*创建于: ${java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date())}*\n\n欢迎来到您的新笔记本。开始在下方添加碎片以构建此笔记！",
            pages = if (type == SubjectType.NOTEBOOK) emptyList() else null,
            pageIndex = if (type == SubjectType.NOTEBOOK) emptyList() else null
        )
    }
}

fun Subject.defaultPages(): List<NotebookPage> = pages ?: emptyList()
fun Subject.defaultPageIndex(): List<PageIndex> = pageIndex ?: emptyList()

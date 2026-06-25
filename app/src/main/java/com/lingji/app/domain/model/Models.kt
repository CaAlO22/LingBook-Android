package com.lingji.app.domain.model

import java.util.UUID

enum class APIProvider { OPENAI, DOUBAO, XIAOMI, BAILIAN, ZHIPU, DEEPSEEK, KIMI }
enum class SubjectType { FRAGMENT, NOTEBOOK }
enum class HorizontalSwipeAction { NONE, TOGGLE_PREVIEW, CHANGE_PAGE }

fun generateId(): String = UUID.randomUUID().toString().take(8)

data class AISettings(
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val enableThinking: Boolean = false,
    val horizontalSwipeAction: HorizontalSwipeAction = HorizontalSwipeAction.TOGGLE_PREVIEW
)

data class Fragment(
    val id: String = generateId(),
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMerged: Boolean = false
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
    val pageIndexEntries: List<PageIndexEntry>? = null,
    val lastOpenedPageId: String? = null
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

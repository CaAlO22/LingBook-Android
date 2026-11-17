package com.lingji.app.domain.model

import java.util.Calendar
import java.util.UUID

enum class APIProvider { OPENAI, DOUBAO, XIAOMI, BAILIAN, ZHIPU, DEEPSEEK, KIMI }
enum class SubjectType { FRAGMENT, NOTEBOOK, TODO }
enum class HorizontalSwipeAction { NONE, TOGGLE_PREVIEW, CHANGE_PAGE }

// 云同步多设备场景下 8 位短 ID 碰撞风险过高，统一使用完整 UUID
fun generateId(): String = UUID.randomUUID().toString()

data class AISettings(
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val enableThinking: Boolean = false,
    val horizontalSwipeAction: HorizontalSwipeAction = HorizontalSwipeAction.TOGGLE_PREVIEW,
    /** 按供应商名缓存的 API Key，切换供应商时自动保存/恢复。 */
    val providerApiKeys: Map<String, String> = emptyMap(),
    /** 轻量模型配置：用于低成本频繁调用的小模型。 */
    val liteModel: LiteModelConfig = LiteModelConfig()
)

/**
 * 轻量模型配置：用于低成本频繁调用的小模型。
 * 配置和测试方式与主模型一致，可在设置界面中独立配置。
 */
data class LiteModelConfig(
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val enableThinking: Boolean = false
)

/** 将 LiteModelConfig 转为 AISettings，用于复用现有供应商 UI 组件。 */
fun LiteModelConfig.toAISettings(providerApiKeys: Map<String, String> = emptyMap()): AISettings = AISettings(
    provider = provider,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    enableThinking = enableThinking,
    providerApiKeys = providerApiKeys
)

/** 从 AISettings 提取模型配置部分，用于 lite model 回写。 */
fun AISettings.toLiteModelConfig(): LiteModelConfig = LiteModelConfig(
    provider = provider,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    enableThinking = enableThinking
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

// ───────────────────────── 待办列表 ─────────────────────────

/** 待办列表的重复规则。 */
enum class TodoRepeatType { ONCE, DAILY, WEEKDAYS, WEEKENDS, INTERVAL, CUSTOM }

/**
 * 待办列表的重复设置。
 *
 * @param weekDays 自定义重复选中的星期，1=周一 … 7=周日。
 * @param intervalDays 间隔重复的天数，仅 [TodoRepeatType.INTERVAL] 生效。
 * @param anchorAt 重复周期的起点（毫秒），修改重复设置时重置为当前时间。
 */
data class TodoRepeat(
    val type: TodoRepeatType = TodoRepeatType.ONCE,
    val weekDays: Set<Int> = emptySet(),
    val intervalDays: Int = 2,
    val anchorAt: Long = System.currentTimeMillis()
)

/** 待办项。 */
data class TodoItem(
    val id: String = generateId(),
    val content: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun startOfDay(time: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/** ISO 星期（1=周一 … 7=周日）。 */
private fun isoWeekDayOf(time: Long): Int {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    return if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
    else calendar.get(Calendar.DAY_OF_WEEK) - 1
}

private fun latestMatchingDayStart(now: Long, matches: (Int) -> Boolean): Long? {
    var dayStart = startOfDay(now)
    // 最多回溯一周，命中最近一个满足条件的日期。
    repeat(7) {
        if (matches(isoWeekDayOf(dayStart))) return dayStart
        dayStart -= DAY_MS
    }
    return null
}

/**
 * 信息岛：待办重复周期——按设备本地时区计算当前周期起点，
 * 每日/工作日/休息日/自定义按最近命中日的 0 点，间隔重复按 [TodoRepeat.anchorAt] 对齐天数。
 * 返回当前重复周期的起点（毫秒）；一次性待办或自定义重复未选任何星期时返回 null。
 * 用于判断是否需要在新周期开始时清除待办勾选状态。
 */
fun TodoRepeat.currentPeriodStart(now: Long): Long? = when (type) {
    TodoRepeatType.ONCE -> null
    TodoRepeatType.DAILY -> startOfDay(now)
    TodoRepeatType.WEEKDAYS -> latestMatchingDayStart(now) { it in 1..5 }
    TodoRepeatType.WEEKENDS -> latestMatchingDayStart(now) { it >= 6 }
    TodoRepeatType.CUSTOM -> {
        if (weekDays.isEmpty()) null
        else latestMatchingDayStart(now) { it in weekDays }
    }
    TodoRepeatType.INTERVAL -> {
        val interval = intervalDays.coerceAtLeast(1)
        val anchorDay = startOfDay(if (anchorAt > 0) anchorAt else now)
        val today = startOfDay(now)
        val elapsedDays = (today - anchorDay) / DAY_MS
        if (elapsedDays < 0) anchorDay else anchorDay + (elapsedDays / interval) * interval * DAY_MS
    }
}

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
    val lastOpenedPageId: String? = null,
    val folderId: String? = null,
    /** 待办列表条目，仅 [SubjectType.TODO] 使用。 */
    val todoItems: List<TodoItem> = emptyList(),
    /** 待办列表的重复设置，仅 [SubjectType.TODO] 使用。 */
    val todoRepeat: TodoRepeat = TodoRepeat(),
    /** 上一次按重复周期清除勾选的时间，仅 [SubjectType.TODO] 使用。 */
    val todoLastResetAt: Long = 0
) {
    companion object {
        fun create(title: String, type: SubjectType): Subject = Subject(
            id = generateId(),
            title = title,
            type = type,
            aggregatedNote = if (type == SubjectType.TODO) "" else
                "# ${title}\n\n*创建于: ${java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date())}*\n\n欢迎来到您的新笔记本。开始在下方添加碎片以构建此笔记！",
            pages = if (type == SubjectType.NOTEBOOK) emptyList() else null,
            pageIndex = if (type == SubjectType.NOTEBOOK) emptyList() else null,
            todoLastResetAt = System.currentTimeMillis()
        )
    }
}

data class Folder(
    val id: String = generateId(),
    val name: String,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

sealed interface HomeItem {
    data class FolderItem(val folder: Folder, val noteCount: Int) : HomeItem
    data class NoteItem(val subject: Subject) : HomeItem
}

fun Subject.defaultPages(): List<NotebookPage> = pages ?: emptyList()
fun Subject.defaultPageIndex(): List<PageIndex> = pageIndex ?: emptyList()

/**
 * 返回笔记的"整本"可读内容，用于 AI 对话（整本笔记范围）等场景：
 * - NOTEBOOK：按顺序拼接所有页（含页标题），而非初始欢迎语 aggregatedNote。
 * - FRAGMENT：优先用聚合笔记，为空时回退到碎片原文拼接。
 */
fun Subject.fullNoteContent(): String = when (type) {
    SubjectType.NOTEBOOK -> {
        val notebookPages = pages ?: emptyList()
        notebookPages.joinToString("\n\n") { p ->
            val title = if (p.title.isNotBlank()) "## ${p.title}\n" else ""
            "$title${p.content}"
        }
    }
    SubjectType.FRAGMENT -> {
        if (aggregatedNote.isNotBlank()) aggregatedNote
        else fragments.joinToString("\n") { it.content }
    }
    SubjectType.TODO -> todoItems.joinToString("\n") {
        "- [${if (it.isDone) "x" else " "}] ${it.content}"
    }
}

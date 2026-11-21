package com.lingji.app.domain.model

import java.util.Calendar
import java.util.UUID

enum class APIProvider { OPENAI, DOUBAO, XIAOMI, BAILIAN, ZHIPU, DEEPSEEK, KIMI, HUNYUAN }
enum class SubjectType { FRAGMENT, NOTEBOOK, TODO }
enum class HorizontalSwipeAction { NONE, TOGGLE_PREVIEW, CHANGE_PAGE }

/**
 * 思考程度。
 *
 * 各家模型对“思考”的粒度不同：有的只能开关（[ON]），有的提供多档强度。
 * 具体某个模型支持哪些档位由 [com.lingji.app.domain.provider.ProviderModel.thinkingLevels] 声明，
 * 请求侧再按供应商文档映射为各自字段（reasoning_effort / thinking.type / thinking_budget 等）。
 */
enum class ThinkingLevel { OFF, ON, LOW, MEDIUM, HIGH, MAX }

// 云同步多设备场景下 8 位短 ID 碰撞风险过高，统一使用完整 UUID
fun generateId(): String = UUID.randomUUID().toString()

/**
 * 用户在设置中自行添加的模型配置。
 *
 * 模型配置独立于运行期设置存放，主模型与辅助模型都只是对该列表的引用（[AISettings.primaryModelId]
 * / [AISettings.assistantModelId]），因此修改模型参数不会影响已选中的引用关系。
 */
data class ModelConfig(
    val id: String = generateId(),
    /** 用户自定义的模型 ID（别名），用于在列表中展示；留空时回退为 [modelName]。 */
    val alias: String = "",
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    /** 实际请求使用的模型 ID。 */
    val modelName: String = "",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val createdAt: Long = System.currentTimeMillis(),
    /** 最后修改时间，云同步用于增量比对。 */
    val updatedAt: Long = createdAt
)

/** 列表展示名：优先使用用户设置的模型 ID，未设置时使用模型名称。 */
val ModelConfig.displayName: String
    get() = alias.ifBlank { modelName }

/** 将模型配置展开为一次请求所需的 AISettings。 */
fun ModelConfig.toAISettings(agentTemperature: Double = 0.2): AISettings = AISettings(
    primaryModelId = id,
    provider = provider,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    thinkingLevel = thinkingLevel,
    agentTemperature = agentTemperature
)

data class AISettings(
    /** 主模型对应的 [ModelConfig.id]，为空表示尚未选择。 */
    val primaryModelId: String = "",
    /** 辅助模型对应的 [ModelConfig.id]，为空表示不使用辅助模型。 */
    val assistantModelId: String = "",
    /** 以下 provider/baseUrl/apiKey/modelName/thinkingLevel 由 [primaryModelId] 解析得到，仅用于请求。 */
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    /** Agent 工具调用温度：越低越稳定，避免模型跳过工具调用直接编造结果。 */
    val agentTemperature: Double = 0.2,
    val horizontalSwipeAction: HorizontalSwipeAction = HorizontalSwipeAction.TOGGLE_PREVIEW,
    /** 辅助模型配置，由 [assistantModelId] 解析得到；为空表示未启用。 */
    val assistantModel: ModelConfig? = null
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
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 信息岛：待办提醒总开关；关闭时不参与调度。 */
    val reminderEnabled: Boolean = false,
    /** 重复列表下的提醒时刻（当天 0 点起的分钟数，0..1439）；一次性待办为 -1。 */
    val reminderMinutes: Int = -1,
    /** 一次性待办的提醒绝对时间戳；重复列表由调度器按重复规则计算。 */
    val reminderAt: Long = 0
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

/**
 * 信息岛：按重复规则计算下一次提醒时刻。
 *
 * 重复列表的提醒日期由规则固定（不能晚于下一次刷新），用户只选择当天时刻，
 * 因此这里直接返回“下一个命中日的指定时刻”。一次性待办、非法时刻或规则永不命中时返回 null。
 */
fun TodoRepeat.nextReminderAt(minutesOfDay: Int, now: Long): Long? {
    if (type == TodoRepeatType.ONCE || minutesOfDay !in 0..1439) return null
    val today = startOfDay(now)
    val todayAt = today + minutesOfDay * 60_000L
    if (todayAt > now && isCycleDay(today)) return todayAt
    // 间隔重复最多等待一个完整间隔，其余规则最多等待一周。
    val lookahead = if (type == TodoRepeatType.INTERVAL) intervalDays.coerceAtLeast(1) else 7
    var day = today + DAY_MS
    repeat(lookahead) {
        if (isCycleDay(day)) return day + minutesOfDay * 60_000L
        day += DAY_MS
    }
    return null
}

private fun TodoRepeat.isCycleDay(dayStart: Long): Boolean = when (type) {
    TodoRepeatType.ONCE -> false
    TodoRepeatType.DAILY -> true
    TodoRepeatType.WEEKDAYS -> isoWeekDayOf(dayStart) in 1..5
    TodoRepeatType.WEEKENDS -> isoWeekDayOf(dayStart) >= 6
    TodoRepeatType.CUSTOM -> weekDays.isNotEmpty() && isoWeekDayOf(dayStart) in weekDays
    TodoRepeatType.INTERVAL -> {
        val interval = intervalDays.coerceAtLeast(1)
        val anchorDay = startOfDay(if (anchorAt > 0) anchorAt else dayStart)
        val elapsedDays = (dayStart - anchorDay) / DAY_MS
        elapsedDays >= 0 && elapsedDays % interval == 0L
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
    val todoLastResetAt: Long = 0,
    /** 信息岛：待办列表整体提醒开关；开启后在刷新前提醒仍有未完成待办。 */
    val todoReminderEnabled: Boolean = false,
    /** 重复列表的整体提醒时刻（当天 0 点起的分钟数）；一次性列表为 -1。 */
    val todoReminderMinutes: Int = -1,
    /** 一次性列表的整体提醒绝对时间戳；重复列表由调度器按规则计算。 */
    val todoReminderAt: Long = 0
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

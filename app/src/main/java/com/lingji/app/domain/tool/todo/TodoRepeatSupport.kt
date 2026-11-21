package com.lingji.app.domain.tool.todo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.domain.model.currentPeriodStart
import com.lingji.app.domain.tool.ToolErrorCode
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import com.lingji.app.domain.tool.paramInt
import com.lingji.app.domain.tool.paramString
import com.lingji.app.domain.tool.toolError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 待办重复规则的 Agent 侧投影与写入解析。
 *
 * 重要事实：产品侧的六档重复是「整篇待办列表」的设置（`Subject.todoRepeat`，
 * 落在 subjects 表的 todoRepeat* 列），**不是单条待办项的属性** —— 周期切换时
 * 整篇一起清空勾选（见 `SubjectViewModel.applyTodoRepeatResetIfNeeded`）。
 * 因此这里只在笔记层级输出一份规则，绝不复制到每条 item 上，避免 Agent
 * 误以为是条目级设置、给出「把某一条改成一次性」这类做不到的承诺。
 */
internal object TodoRepeatSupport {

    /** 档位中文名，与 UI（strings.xml 的 todo_repeat_*）保持一致。 */
    fun label(repeat: TodoRepeat): String = when (repeat.type) {
        TodoRepeatType.ONCE -> "一次性待办"
        TodoRepeatType.DAILY -> "每日委托"
        TodoRepeatType.WEEKDAYS -> "仅工作日重复"
        TodoRepeatType.WEEKENDS -> "仅休息日重复"
        TodoRepeatType.INTERVAL -> "每 ${repeat.intervalDays.coerceAtLeast(1)} 天重复"
        TodoRepeatType.CUSTOM -> {
            if (repeat.weekDays.isEmpty()) "自定义重复"
            else "自定义重复（${repeat.weekDays.sorted().joinToString("、") { weekDayLabel(it) }}）"
        }
    }

    fun weekDayLabel(day: Int): String = when (day) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    /**
     * 笔记级重复规则的结构化投影。
     *
     * - `type` / `label`：六档枚举与中文名
     * - `scope`：恒为 "subject"，明确这是整篇待办列表的设置
     * - `auto_reset`：新周期开始时是否自动清空全部勾选
     * - `week_days` / `interval_days`：仅对应档位出现，其余档位不输出噪声
     * - `current_period_start`：当前周期起点（本地时间），Agent 据此判断勾选属于哪个周期
     * - `reset_pending`：存在上一周期遗留的勾选、但尚未重置（下次打开该笔记会被清空）。
     *   为 true 时不要把这些 is_done 当成"本周期已完成"。
     */
    fun toJson(
        repeat: TodoRepeat,
        now: Long,
        lastResetAt: Long,
        hasDoneItems: Boolean
    ): JsonObject {
        val periodStart = repeat.currentPeriodStart(now)
        val resetPending = periodStart != null && hasDoneItems && lastResetAt < periodStart
        val obj = buildJsonObject {
            "type" to repeat.type.name
            "label" to label(repeat)
            "scope" to "subject"
            "auto_reset" to (periodStart != null)
            if (repeat.type == TodoRepeatType.INTERVAL) {
                "interval_days" to repeat.intervalDays.coerceAtLeast(1)
            }
            if (periodStart != null) {
                "current_period_start" to formatTime(periodStart)
            }
            if (lastResetAt > 0) {
                "last_reset_at" to formatTime(lastResetAt)
            }
            if (periodStart != null) {
                "reset_pending" to resetPending
            }
        }
        // 注意：不能用 buildJsonArray { forEach { +it } } —— Int 自带成员 unaryPlus()，
        // `+it` 会被解析成「正号」而不是加入数组，结果是静默的空数组。这里显式 add。
        if (repeat.type == TodoRepeatType.CUSTOM) {
            obj.add("week_days", JsonArray().apply {
                repeat.weekDays.sorted().forEach { add(it) }
            })
        }
        return obj
    }

    /** 给 get_aggregated_note 的一行 Markdown 注释，让平铺的 - [ ] 列表也带上重复语义。 */
    fun summaryLine(
        repeat: TodoRepeat,
        now: Long,
        lastResetAt: Long,
        hasDoneItems: Boolean
    ): String {
        val periodStart = repeat.currentPeriodStart(now)
        val resetPending = periodStart != null && hasDoneItems && lastResetAt < periodStart
        val base = if (periodStart == null) {
            "<!-- 待办重复：${label(repeat)}（不自动重置勾选）"
        } else {
            "<!-- 待办重复：${label(repeat)}（新周期自动清空勾选）；当前周期起点 ${formatTime(periodStart)}"
        }
        return if (resetPending) {
            "$base；注意：存在上一周期遗留的勾选，下次打开该笔记会被清空 -->"
        } else {
            "$base -->"
        }
    }

    fun formatTime(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))

    // ───────────────────────── 写入解析 ─────────────────────────

    /** 一次重复设置更新：成功给出新规则，失败给出结构化错误（Error: 前缀）。 */
    sealed interface RepeatUpdate {
        data class Ok(val repeat: TodoRepeat) : RepeatUpdate
        data class Invalid(val error: String) : RepeatUpdate
    }

    /**
     * 请求里是否带了重复相关参数。
     * 只有带了才写库；否则保持现有规则不动（避免「改个文字顺手重置周期」）。
     */
    fun hasRepeatParams(params: JsonObject): Boolean =
        params.has("repeat") || params.has("week_days") || params.has("interval_days")

    /**
     * 部分更新语义（PATCH）：
     * - 只传 `repeat`：改档位，其余参数沿用当前规则
     * - 只传 `week_days` / `interval_days`：按意图推断档位（CUSTOM / INTERVAL），不静默丢弃参数
     * - 传 `repeat=CUSTOM` 但 `week_days` 为空：报错，不猜一个默认星期组合
     *
     * 与 UI 一致：任何一次确认都把 anchorAt 重置为当前时间，即从此刻开始新周期；
     * 仓库层 updateTodoRepeat 同时把 lastResetAt 记为当前时间，避免刚改完就被判定需要重置。
     */
    fun resolve(
        params: JsonObject,
        current: TodoRepeat,
        now: Long = System.currentTimeMillis()
    ): RepeatUpdate {
        val rawType = params.paramString("repeat")
        val type = if (rawType == null) {
            // LLM 常只给细节不给档位：给了 week_days 就是想自定义，给了 interval_days 就是想按间隔。
            // 此时若沿用当前档位，参数会被静默丢弃、返回还显示"成功"，比推断更危险。
            when {
                params.has("week_days") && current.type != TodoRepeatType.CUSTOM -> TodoRepeatType.CUSTOM
                params.has("interval_days") && current.type != TodoRepeatType.INTERVAL -> TodoRepeatType.INTERVAL
                else -> current.type
            }
        } else {
            parseType(rawType) ?: return RepeatUpdate.Invalid(
                toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    "未知的重复档位：$rawType",
                    "parameter" to "repeat",
                    "allowed" to buildJsonArray {
                        TodoRepeatType.values().forEach { +it.name }
                    },
                    "suggestion" to "repeat 只能用 ONCE / DAILY / WEEKDAYS / WEEKENDS / INTERVAL / CUSTOM；" +
                        "也可传中文「一次性待办 / 每日委托 / 仅工作日重复 / 仅休息日重复 / 间隔时间重复 / 自定义重复」"
                )
            )
        }

        val weekDays = when (val parsed = parseWeekDays(params.get("week_days"))) {
            WeekDaysParse.Absent -> if (type == TodoRepeatType.CUSTOM) current.weekDays else emptySet()
            is WeekDaysParse.Ok -> parsed.days
            WeekDaysParse.Invalid -> return RepeatUpdate.Invalid(
                toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    "week_days 必须是 1..7 的整数数组（1=周一 … 7=周日）",
                    "parameter" to "week_days",
                    "suggestion" to "例如 [1,3,5] 表示每周一、三、五；只有 repeat=CUSTOM 时生效"
                )
            )
        }
        if (type == TodoRepeatType.CUSTOM && weekDays.isEmpty()) {
            return RepeatUpdate.Invalid(
                toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    "repeat=CUSTOM 必须同时给出 week_days",
                    "parameter" to "week_days",
                    "suggestion" to "传入 1..7 的整数数组，例如 [1,3,5]；或改用 DAILY / WEEKDAYS / WEEKENDS"
                )
            )
        }

        val intervalDays = when {
            params.has("interval_days") -> {
                val value = params.paramInt("interval_days") ?: return RepeatUpdate.Invalid(
                    toolError(
                        ToolErrorCode.INVALID_PARAMETER,
                        "interval_days 必须是整数",
                        "parameter" to "interval_days",
                        "suggestion" to "传入 1..999 的天数，例如 3"
                    )
                )
                if (value !in 1..999) {
                    return RepeatUpdate.Invalid(
                        toolError(
                            ToolErrorCode.INVALID_PARAMETER,
                            "interval_days 超出范围：$value（有效值 1..999）",
                            "parameter" to "interval_days",
                            "suggestion" to "传入 1..999 的天数，例如 3"
                        )
                    )
                }
                value
            }
            else -> current.intervalDays.coerceAtLeast(1)
        }

        return RepeatUpdate.Ok(
            TodoRepeat(
                type = type,
                weekDays = if (type == TodoRepeatType.CUSTOM) weekDays else emptySet(),
                intervalDays = intervalDays,
                anchorAt = now
            )
        )
    }

    private fun parseType(raw: String): TodoRepeatType? {
        val normalized = raw.trim()
        return when (normalized.uppercase(Locale.ROOT)) {
            "ONCE", "ONE_TIME", "SINGLE" -> TodoRepeatType.ONCE
            "DAILY", "EVERY_DAY", "DAY" -> TodoRepeatType.DAILY
            "WEEKDAYS", "WEEKDAY" -> TodoRepeatType.WEEKDAYS
            "WEEKENDS", "WEEKEND" -> TodoRepeatType.WEEKENDS
            "INTERVAL", "EVERY_N_DAYS" -> TodoRepeatType.INTERVAL
            "CUSTOM" -> TodoRepeatType.CUSTOM
            else -> when (normalized) {
                "一次性", "一次性待办", "单次" -> TodoRepeatType.ONCE
                "每日", "每日委托", "每天" -> TodoRepeatType.DAILY
                "工作日", "仅工作日", "仅工作日重复" -> TodoRepeatType.WEEKDAYS
                "休息日", "周末", "仅休息日", "仅休息日重复" -> TodoRepeatType.WEEKENDS
                "间隔", "间隔时间", "间隔时间重复", "每N天", "每 N 天" -> TodoRepeatType.INTERVAL
                "自定义", "自定义重复" -> TodoRepeatType.CUSTOM
                else -> null
            }
        }
    }

    private sealed interface WeekDaysParse {
        object Absent : WeekDaysParse
        data class Ok(val days: Set<Int>) : WeekDaysParse
        object Invalid : WeekDaysParse
    }

    /** 容错读取：接受 [1,3,5]、["1","3"]、"1,3,5"、"周一,周三"。 */
    private fun parseWeekDays(element: JsonElement?): WeekDaysParse {
        if (element == null || element.isJsonNull) return WeekDaysParse.Absent
        if (element.isJsonArray) {
            val days = mutableSetOf<Int>()
            for (item in element.asJsonArray) {
                val day = readDay(item) ?: return WeekDaysParse.Invalid
                days += day
            }
            return WeekDaysParse.Ok(days)
        }
        if (element.isJsonPrimitive) {
            val text = runCatching { element.asString }.getOrNull() ?: return WeekDaysParse.Invalid
            if (text.isBlank()) return WeekDaysParse.Ok(emptySet())
            val days = mutableSetOf<Int>()
            for (token in text.split(",", "，", "、", " ", "/")) {
                val trimmed = token.trim()
                if (trimmed.isEmpty()) continue
                val day = trimmed.toIntOrNull() ?: parseWeekDayName(trimmed) ?: return WeekDaysParse.Invalid
                if (day !in 1..7) return WeekDaysParse.Invalid
                days += day
            }
            return WeekDaysParse.Ok(days)
        }
        return WeekDaysParse.Invalid
    }

    private fun readDay(element: JsonElement): Int? {
        if (!element.isJsonPrimitive) return null
        val day = runCatching { element.asInt }.getOrNull()
            ?: parseWeekDayName(runCatching { element.asString }.getOrNull() ?: return null)
            ?: return null
        return if (day in 1..7) day else null
    }

    private fun parseWeekDayName(text: String): Int? = when (text.trim().lowercase(Locale.ROOT)) {
        "周一", "星期一", "礼拜一", "mon", "monday" -> 1
        "周二", "星期二", "礼拜二", "tue", "tuesday" -> 2
        "周三", "星期三", "礼拜三", "wed", "wednesday" -> 3
        "周四", "星期四", "礼拜四", "thu", "thursday" -> 4
        "周五", "星期五", "礼拜五", "fri", "friday" -> 5
        "周六", "星期六", "礼拜六", "sat", "saturday" -> 6
        "周日", "周天", "星期日", "星期天", "礼拜日", "sun", "sunday" -> 7
        else -> null
    }
}

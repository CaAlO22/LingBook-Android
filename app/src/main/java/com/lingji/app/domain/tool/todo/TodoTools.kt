package com.lingji.app.domain.tool.todo

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.ToolErrorCode
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import com.lingji.app.domain.tool.missingParameter
import com.lingji.app.domain.tool.paramBoolean
import com.lingji.app.domain.tool.paramInt
import com.lingji.app.domain.tool.paramString
import com.lingji.app.domain.tool.subjectNotFound
import com.lingji.app.domain.tool.toolError
import com.lingji.app.domain.tool.unsupportedForSubjectType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 待办项级工具。TODO 类型笔记的存储模型是独立的 todo_items 表，
 * 既不是 fragments 也不是 pages —— 读写必须统一走这里。
 *
 * 待办项提供三种定位方式（三选一）：
 * - todo_id：精确 id（list_todos 返回的 id）
 * - todo_index：list_todos 返回的序号（从 0 开始），整数对 LLM 最稳定
 * - todo_text：按正文文本匹配，重名时返回候选列表而不是猜一个
 *
 * 重复规则（六档）是**整篇待办列表**的设置，且创建后不可修改：
 * 读的时候只在 list_todos / get_subject 顶层给一份，写只能通过
 * create_subject 在创建时指定。
 *
 * 提醒是单条待办自己的属性：重复列表只保存当天时刻（日期由重复规则推导），
 * 一次性列表保存绝对日期时间。
 */
object TodoTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListTodos(repo),
        AddTodo(repo),
        UpdateTodo(repo),
        DeleteTodo(repo),
        SetListReminder(repo)
    )

    private sealed interface Lookup {
        data class Ok(val subject: Subject) : Lookup
        data class Fail(val error: String) : Lookup
    }

    private suspend fun requireTodoSubject(repo: SubjectRepository, params: JsonObject): Lookup {
        val id = params.paramString("subject_id") ?: return Lookup.Fail(missingParameter("subject_id"))
        val subject = repo.getSubjectByIdOnce(id) ?: return Lookup.Fail(subjectNotFound(id))
        if (subject.type != SubjectType.TODO) {
            return Lookup.Fail(
                unsupportedForSubjectType(
                    subject,
                    "待办项操作（list_todos / add_todo / update_todo / delete_todo / set_todo_list_reminder）",
                    listOf(SubjectType.TODO)
                )
            )
        }
        return Lookup.Ok(subject)
    }

    private sealed interface TodoLookup {
        data class Ok(val item: TodoItem, val index: Int) : TodoLookup
        data class Fail(val error: String) : TodoLookup
    }

    /** 按 todo_id > todo_index > todo_text 的优先级定位；文本歧义时返回候选。 */
    private fun locateTodo(subject: Subject, params: JsonObject): TodoLookup {
        val items = subject.todoItems
        val id = params.paramString("todo_id")
        val index = params.paramInt("todo_index")
        val text = params.paramString("todo_text")

        if (id == null && index == null && text == null) {
            return TodoLookup.Fail(missingParameter("todo_id", "todo_index", "todo_text"))
        }
        val candidates: List<TodoItem> = when {
            id != null -> items.filter { it.id == id }
            index != null -> {
                if (index < 0 || index >= items.size) {
                    return TodoLookup.Fail(
                        toolError(
                            ToolErrorCode.OBJECT_NOT_FOUND,
                            "待办项序号 $index 超出范围（当前共 ${items.size} 条，序号从 0 开始）",
                            "object_type" to "todo",
                            "suggestion" to "调用 list_todos 重新获取序号"
                        )
                    )
                }
                listOf(items[index])
            }
            else -> {
                val exact = items.filter { it.content == text }
                if (exact.isNotEmpty()) exact
                else items.filter { it.content.contains(text!!, ignoreCase = true) }
            }
        }
        return when {
            candidates.isEmpty() -> TodoLookup.Fail(
                objectNotFoundWithHint(subject, id, index, text)
            )
            candidates.size > 1 -> TodoLookup.Fail(
                toolError(
                    ToolErrorCode.AMBIGUOUS_MATCH,
                    "匹配到 ${candidates.size} 条待办项，请改用 todo_id 或 todo_index 精确指定",
                    "candidates" to buildJsonArray {
                        candidates.forEach { item ->
                            +buildJsonObject {
                                "index" to items.indexOf(item)
                                "id" to item.id
                                "content" to item.content
                                "is_done" to item.isDone
                            }
                        }
                    },
                    "suggestion" to "从 candidates 中选定一条，用其 id 或 index 重新调用"
                )
            )
            else -> {
                val item = candidates.first()
                TodoLookup.Ok(item, items.indexOf(item))
            }
        }
    }

    private fun objectNotFoundWithHint(subject: Subject, id: String?, index: Int?, text: String?): String = toolError(
        ToolErrorCode.OBJECT_NOT_FOUND,
        "待办项不存在：${id ?: text ?: "index=$index"}",
        "object_type" to "todo",
        "subject_id" to subject.id,
        "candidates" to buildJsonArray {},
        "suggestion" to "调用 list_todos 查看该待办笔记的全部条目"
    )

    /** 待办项的 JSON 形状，统一 index 口径（列表顺序，从 0 开始）。 */
    private fun todoItemJson(index: Int, item: TodoItem, repeat: TodoRepeat): JsonObject = buildJsonObject {
        "index" to index
        "id" to item.id
        "content" to item.content
        "is_done" to item.isDone
        if (item.reminderEnabled) {
            if (repeat.type == TodoRepeatType.ONCE) {
                "reminder_at" to formatAt(item.reminderAt)
            } else {
                "reminder_time" to formatMinutes(item.reminderMinutes)
            }
        }
    }

    /** 信息岛：工具侧的提醒参数解析——重复列表只收时刻，一次性列表收日期 + 时刻。 */
    private sealed interface ReminderInput {
        data object Unchanged : ReminderInput
        data class Value(val enabled: Boolean, val minutes: Int, val at: Long) : ReminderInput
        data class Invalid(val error: String) : ReminderInput
    }

    private fun parseReminder(repeat: TodoRepeat, params: JsonObject): ReminderInput {
        val timeText = params.paramString("reminder_time")?.trim()
        val dateText = params.paramString("reminder_date")?.trim()
        val clear = params.paramBoolean("clear_reminder") ?: false
        if (clear) {
            return if (timeText == null && dateText == null) {
                ReminderInput.Value(false, -1, 0)
            } else {
                ReminderInput.Invalid("clear_reminder 不能与 reminder_time / reminder_date 同时使用")
            }
        }
        if (timeText == null && dateText == null) return ReminderInput.Unchanged
        val time = timeText?.let { runCatching { LocalTime.parse(it, TIME_FORMATTER) }.getOrNull() }
            ?: return ReminderInput.Invalid("reminder_time 需为 HH:mm 格式（24 小时制）")
        if (repeat.type == TodoRepeatType.ONCE) {
            val date = dateText?.let { runCatching { LocalDate.parse(it, DATE_FORMATTER) }.getOrNull() }
                ?: return ReminderInput.Invalid(
                    "一次性待办需要同时提供 reminder_date（yyyy-MM-dd）与 reminder_time（HH:mm）"
                )
            val at = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= System.currentTimeMillis()) return ReminderInput.Invalid("提醒时间需晚于当前时间")
            return ReminderInput.Value(true, -1, at)
        }
        return ReminderInput.Value(true, time.hour * 60 + time.minute, 0)
    }

    private fun formatMinutes(minutes: Int): String =
        String.format("%02d:%02d", minutes / 60, minutes % 60)

    private fun formatAt(at: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), ZoneId.systemDefault())
            .format(AT_FORMATTER)

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val AT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private class ListTodos(private val repo: SubjectRepository) : Tool {
        override val name = "list_todos"
        override val description = "列出待办（TODO）笔记的全部待办项，返回 {repeat, count, items}。" +
            "items 每项含 index（从 0 开始，可直接用于 update_todo / delete_todo）、id、content、is_done；" +
            "已设提醒的条目额外返回 reminder_time（重复列表）或 reminder_at（一次性列表）。" +
            "repeat 是整篇待办列表的重复设置（创建后不可修改）：type/label/auto_reset/current_period_start/reset_pending；" +
            "auto_reset=true 表示新周期会自动清空全部勾选，reset_pending=true 表示还有上一周期遗留的勾选未被清空，" +
            "此时不要把 is_done=true 当成「本周期已完成」。仅 TODO 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "待办笔记 ID（也可用 subject_title 按标题定位）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String = when (val lookup = requireTodoSubject(repo, params)) {
            is Lookup.Fail -> lookup.error
            is Lookup.Ok -> {
                val subject = lookup.subject
                val now = System.currentTimeMillis()
                buildJsonObject {
                    "repeat" to TodoRepeatSupport.toJson(
                        subject.todoRepeat,
                        now,
                        subject.todoLastResetAt,
                        subject.todoItems.any { it.isDone }
                    )
                    "count" to subject.todoItems.size
                    "items" to buildJsonArray {
                        subject.todoItems.forEachIndexed { index, item ->
                            +todoItemJson(index, item, subject.todoRepeat)
                        }
                    }
                }.toString()
            }
        }
    }

    private class AddTodo(private val repo: SubjectRepository) : Tool {
        override val name = "add_todo"
        override val description = "向待办（TODO）笔记末尾新增一条待办项，返回新条目的 index 与 id。" +
            "可选同时设置提醒：重复列表只传 reminder_time（HH:mm，日期按列表重复规则）；" +
            "一次性列表需传 reminder_date（yyyy-MM-dd）+ reminder_time。" +
            "内容会立即出现在 get_aggregated_note 中。仅 TODO 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "待办笔记 ID（也可用 subject_title 按标题定位）" }
                "content" to buildJsonObject { "type" to "string"; "description" to "待办项正文" }
                "done" to buildJsonObject { "type" to "boolean"; "description" to "是否已完成，默认 false" }
                "reminder_time" to buildJsonObject { "type" to "string"; "description" to "提醒时刻 HH:mm（24 小时制）" }
                "reminder_date" to buildJsonObject { "type" to "string"; "description" to "一次性待办提醒日期 yyyy-MM-dd；重复列表不需要" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val lookup = requireTodoSubject(repo, params)
            if (lookup is Lookup.Fail) return lookup.error
            val subject = (lookup as Lookup.Ok).subject
            val content = params.paramString("content") ?: return missingParameter("content")
            val done = params.paramBoolean("done") ?: false
            val reminder = when (val parsed = parseReminder(subject.todoRepeat, params)) {
                is ReminderInput.Invalid -> return toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    parsed.error,
                    "suggestion" to "重复列表传 reminder_time；一次性列表传 reminder_date + reminder_time"
                )
                is ReminderInput.Unchanged -> ReminderInput.Value(false, -1, 0)
                is ReminderInput.Value -> parsed
            }
            val item = repo.addTodoItem(
                subjectId = subject.id,
                content = content,
                reminderEnabled = reminder.enabled,
                reminderMinutes = reminder.minutes,
                reminderAt = reminder.at
            )
            if (done) repo.setTodoItemDone(item.id, true)
            return buildJsonObject {
                "id" to item.id
                "index" to subject.todoItems.size
                "content" to item.content
                "is_done" to done
                "reminder" to when {
                    !reminder.enabled -> "none"
                    subject.todoRepeat.type == TodoRepeatType.ONCE -> formatAt(reminder.at)
                    else -> formatMinutes(reminder.minutes)
                }
                "suggestion" to "如需修改或勾选，调用 update_todo 并传 todo_id 或 todo_index"
            }.toString()
        }
    }

    private class UpdateTodo(private val repo: SubjectRepository) : Tool {
        override val name = "update_todo"
        override val description = "修改待办笔记中某条待办项的正文、完成状态或提醒。用 todo_id / todo_index / todo_text 三选一定位。" +
            "提醒：重复列表传 reminder_time（HH:mm）；一次性列表传 reminder_date + reminder_time；clear_reminder=true 清除提醒。" +
            "待办列表的重复规则在创建时确定，不能用本工具修改。仅 TODO 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "待办笔记 ID（也可用 subject_title 按标题定位）" }
                "todo_id" to buildJsonObject { "type" to "string"; "description" to "待办项 ID（三选一）" }
                "todo_index" to buildJsonObject { "type" to "integer"; "description" to "待办项序号，从 0 开始，取自 list_todos（三选一）" }
                "todo_text" to buildJsonObject { "type" to "string"; "description" to "按正文文本定位（三选一），精确匹配优先，其次包含匹配" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新的待办项正文；不传表示不改文字" }
                "done" to buildJsonObject { "type" to "boolean"; "description" to "新的完成状态；不传表示不改状态" }
                "reminder_time" to buildJsonObject { "type" to "string"; "description" to "提醒时刻 HH:mm（24 小时制）" }
                "reminder_date" to buildJsonObject { "type" to "string"; "description" to "一次性待办提醒日期 yyyy-MM-dd；重复列表不需要" }
                "clear_reminder" to buildJsonObject { "type" to "boolean"; "description" to "传 true 清除该条提醒" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val lookup = requireTodoSubject(repo, params)
            if (lookup is Lookup.Fail) return lookup.error
            val subject = (lookup as Lookup.Ok).subject
            val newContent = params.paramString("content")
            val newDone = params.paramBoolean("done")
            val reminder = when (val parsed = parseReminder(subject.todoRepeat, params)) {
                is ReminderInput.Invalid -> return toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    parsed.error,
                    "suggestion" to "重复列表传 reminder_time；一次性列表传 reminder_date + reminder_time"
                )
                else -> parsed
            }
            if (newContent == null && newDone == null && reminder is ReminderInput.Unchanged) {
                return toolError(
                    ToolErrorCode.MISSING_PARAMETER,
                    "update_todo 至少需要 content / done / 提醒设置之一",
                    "suggestion" to "传入 content 改文字、done 勾选、reminder_time 设提醒，或组合使用"
                )
            }
            val located = locateTodo(subject, params)
            if (located is TodoLookup.Fail) return located.error
            val item = (located as TodoLookup.Ok).item

            var enabled = item.reminderEnabled
            var minutes = item.reminderMinutes
            var at = item.reminderAt
            if (reminder is ReminderInput.Value) {
                enabled = reminder.enabled
                minutes = reminder.minutes
                at = reminder.at
            }
            if (newContent != null || reminder is ReminderInput.Value) {
                repo.updateTodoItem(item.id, newContent ?: item.content, enabled, minutes, at)
            }
            if (newDone != null) repo.setTodoItemDone(item.id, newDone)

            return buildJsonObject {
                "success" to true
                "id" to item.id
                "index" to located.index
                "content" to (newContent ?: item.content)
                "is_done" to (newDone ?: item.isDone)
                "reminder" to when {
                    !enabled -> "none"
                    subject.todoRepeat.type == TodoRepeatType.ONCE -> formatAt(at)
                    else -> formatMinutes(minutes)
                }
            }.toString()
        }
    }

    private class DeleteTodo(private val repo: SubjectRepository) : Tool {
        override val name = "delete_todo"
        override val description = "删除待办笔记中的一条待办项。用 todo_id / todo_index / todo_text 三选一定位。仅 TODO 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "待办笔记 ID（也可用 subject_title 按标题定位）" }
                "todo_id" to buildJsonObject { "type" to "string"; "description" to "待办项 ID（三选一）" }
                "todo_index" to buildJsonObject { "type" to "integer"; "description" to "待办项序号，从 0 开始（三选一）" }
                "todo_text" to buildJsonObject { "type" to "string"; "description" to "按正文文本定位（三选一）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val lookup = requireTodoSubject(repo, params)
            if (lookup is Lookup.Fail) return lookup.error
            val located = locateTodo((lookup as Lookup.Ok).subject, params)
            if (located is TodoLookup.Fail) return located.error
            val ok = located as TodoLookup.Ok
            repo.deleteTodoItem(ok.item.id)
            return buildJsonObject {
                "success" to true
                "deleted_id" to ok.item.id
                "deleted_content" to ok.item.content
            }.toString()
        }
    }

    /** 信息岛：整个待办列表的提醒——刷新前仍有未完成项时提醒。 */
    private class SetListReminder(private val repo: SubjectRepository) : Tool {
        override val name = "set_todo_list_reminder"
        override val description = "设置待办（TODO）笔记整体的提醒：在列表刷新前仍有未完成待办时提醒。重复列表只传 reminder_time（HH:mm，日期由重复规则决定）；一次性列表传 reminder_date（yyyy-MM-dd）+ reminder_time；clear_reminder=true 清除提醒。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "待办笔记 ID" }
                "reminder_time" to buildJsonObject { "type" to "string"; "description" to "提醒时刻 HH:mm（24 小时制）" }
                "reminder_date" to buildJsonObject { "type" to "string"; "description" to "一次性列表提醒日期 yyyy-MM-dd；重复列表不需要" }
                "clear_reminder" to buildJsonObject { "type" to "boolean"; "description" to "传 true 清除列表提醒" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val lookup = requireTodoSubject(repo, params)
            if (lookup is Lookup.Fail) return lookup.error
            val subject = (lookup as Lookup.Ok).subject
            val reminder = when (val parsed = parseReminder(subject.todoRepeat, params)) {
                is ReminderInput.Invalid -> return toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    parsed.error,
                    "suggestion" to "重复列表传 reminder_time；一次性列表传 reminder_date + reminder_time"
                )
                is ReminderInput.Unchanged -> return toolError(
                    ToolErrorCode.MISSING_PARAMETER,
                    "set_todo_list_reminder 需要 reminder_time / reminder_date，或 clear_reminder=true",
                    "suggestion" to "例如重复列表传 {\"subject_id\":\"...\",\"reminder_time\":\"21:00\"}"
                )
                is ReminderInput.Value -> parsed
            }
            repo.updateTodoReminder(
                subjectId = subject.id,
                reminderEnabled = reminder.enabled,
                reminderMinutes = reminder.minutes,
                reminderAt = reminder.at
            )
            return buildJsonObject {
                "success" to true
                "subject_id" to subject.id
                "repeat_type" to subject.todoRepeat.type.name.lowercase()
                "reminder" to when {
                    !reminder.enabled -> "none"
                    subject.todoRepeat.type == TodoRepeatType.ONCE -> formatAt(reminder.at)
                    else -> formatMinutes(reminder.minutes)
                }
            }.toString()
        }
    }
}

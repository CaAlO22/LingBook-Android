package com.lingji.app.domain.tool.todo

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
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

/**
 * 待办项级工具。TODO 类型笔记的存储模型是独立的 todo_items 表，
 * 既不是 fragments 也不是 pages —— 读写必须统一走这里。
 *
 * 待办项提供三种定位方式（三选一）：
 * - todo_id：精确 id（list_todos 返回的 id）
 * - todo_index：list_todos 返回的序号（从 0 开始），整数对 LLM 最稳定
 * - todo_text：按正文文本匹配，重名时返回候选列表而不是猜一个
 *
 * 重复规则（六档）是**整篇待办列表**的设置，不是单条属性：读的时候只在
 * list_todos 顶层给一份，写的时候 add_todo / update_todo 的 repeat 参数
 * 都作用在整篇上并在返回值里用 repeat_scope="subject" 说清楚。
 */
object TodoTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListTodos(repo),
        AddTodo(repo),
        UpdateTodo(repo),
        DeleteTodo(repo)
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
                unsupportedForSubjectType(subject, "待办项操作（list_todos / add_todo / update_todo / delete_todo）", listOf(SubjectType.TODO))
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
    private fun todoItemJson(index: Int, item: TodoItem): JsonObject = buildJsonObject {
        "index" to index
        "id" to item.id
        "content" to item.content
        "is_done" to item.isDone
    }

    private val repeatParamSchema = buildJsonObject {
        "type" to "string"
        "description" to "整篇待办列表的重复档位：ONCE 一次性待办 / DAILY 每日委托 / WEEKDAYS 仅工作日重复 / " +
            "WEEKENDS 仅休息日重复 / INTERVAL 间隔时间重复 / CUSTOM 自定义重复。注意这是整篇列表的设置，不是单条待办项。"
        "enum" to buildJsonArray { +"ONCE"; +"DAILY"; +"WEEKDAYS"; +"WEEKENDS"; +"INTERVAL"; +"CUSTOM" }
    }

    private val weekDaysParamSchema = buildJsonObject {
        "type" to "array"
        "description" to "自定义重复的星期，1=周一 … 7=周日，例如 [1,3,5]。只有 repeat=CUSTOM 时生效。"
        "items" to buildJsonObject { "type" to "integer"; "minimum" to 1; "maximum" to 7 }
    }

    private val intervalDaysParamSchema = buildJsonObject {
        "type" to "integer"
        "minimum" to 1
        "maximum" to 999
        "description" to "间隔天数，只有 repeat=INTERVAL 时生效，例如 3 表示每 3 天重复。"
    }

    private class ListTodos(private val repo: SubjectRepository) : Tool {
        override val name = "list_todos"
        override val description = "列出待办（TODO）笔记的全部待办项，返回 {repeat, count, items}。" +
            "items 每项含 index（从 0 开始，可直接用于 update_todo / delete_todo）、id、content、is_done。" +
            "repeat 是整篇待办列表的重复设置（不是单条）：type/label/auto_reset/current_period_start/reset_pending；" +
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
                        subject.todoItems.forEachIndexed { index, item -> +todoItemJson(index, item) }
                    }
                }.toString()
            }
        }
    }

    private class AddTodo(private val repo: SubjectRepository) : Tool {
        override val name = "add_todo"
        override val description = "向待办（TODO）笔记末尾新增一条待办项，返回新条目的 index 与 id。" +
            "可选 repeat / week_days / interval_days 同时设置整篇待办列表的重复档位（不是这一条单独设置）。" +
            "内容会立即出现在 get_aggregated_note 中。仅 TODO 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "待办笔记 ID（也可用 subject_title 按标题定位）" }
                "content" to buildJsonObject { "type" to "string"; "description" to "待办项正文" }
                "done" to buildJsonObject { "type" to "boolean"; "description" to "是否已完成，默认 false" }
                "repeat" to repeatParamSchema
                "week_days" to weekDaysParamSchema
                "interval_days" to intervalDaysParamSchema
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val lookup = requireTodoSubject(repo, params)
            if (lookup is Lookup.Fail) return lookup.error
            val subject = (lookup as Lookup.Ok).subject
            val content = params.paramString("content") ?: return missingParameter("content")
            val done = params.paramBoolean("done") ?: false

            val hasRepeat = TodoRepeatSupport.hasRepeatParams(params)
            val repeatUpdate = if (hasRepeat) TodoRepeatSupport.resolve(params, subject.todoRepeat) else null
            if (repeatUpdate is TodoRepeatSupport.RepeatUpdate.Invalid) return repeatUpdate.error

            val item = repo.addTodoItem(subject.id, content)
            if (done) repo.setTodoItemDone(item.id, true)
            val repeat = when (repeatUpdate) {
                is TodoRepeatSupport.RepeatUpdate.Ok -> {
                    repo.updateTodoRepeat(subject.id, repeatUpdate.repeat)
                    repeatUpdate.repeat
                }
                else -> subject.todoRepeat
            }
            val now = System.currentTimeMillis()
            return buildJsonObject {
                "id" to item.id
                "index" to subject.todoItems.size
                "content" to item.content
                "is_done" to done
                "repeat" to TodoRepeatSupport.toJson(
                    repeat,
                    now,
                    if (repeatUpdate is TodoRepeatSupport.RepeatUpdate.Ok) now else subject.todoLastResetAt,
                    subject.todoItems.any { it.isDone } || done
                )
                "repeat_scope" to "subject"
                "suggestion" to "如需修改或勾选，调用 update_todo 并传 todo_id 或 todo_index"
            }.toString()
        }
    }

    private class UpdateTodo(private val repo: SubjectRepository) : Tool {
        override val name = "update_todo"
        override val description = "修改待办笔记中的某条待办项（正文 / 完成状态），和/或修改整篇待办列表的重复规则。" +
            "改条目时用 todo_id / todo_index / todo_text 三选一定位；只改重复规则时无需定位条目。" +
            "repeat / week_days / interval_days 作用于整篇列表（六档：ONCE/DAILY/WEEKDAYS/WEEKENDS/INTERVAL/CUSTOM），" +
            "不是把某一条单独改成一次性。至少提供 content / done / repeat 之一。仅 TODO 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "待办笔记 ID（也可用 subject_title 按标题定位）" }
                "todo_id" to buildJsonObject { "type" to "string"; "description" to "待办项 ID（三选一；只改重复规则时可不传）" }
                "todo_index" to buildJsonObject { "type" to "integer"; "description" to "待办项序号，从 0 开始，取自 list_todos（三选一）" }
                "todo_text" to buildJsonObject { "type" to "string"; "description" to "按正文文本定位（三选一），精确匹配优先，其次包含匹配" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新的待办项正文；不传表示不改文字" }
                "done" to buildJsonObject { "type" to "boolean"; "description" to "新的完成状态；不传表示不改状态" }
                "repeat" to repeatParamSchema
                "week_days" to weekDaysParamSchema
                "interval_days" to intervalDaysParamSchema
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val lookup = requireTodoSubject(repo, params)
            if (lookup is Lookup.Fail) return lookup.error
            val subject = (lookup as Lookup.Ok).subject
            val newContent = params.paramString("content")
            val newDone = params.paramBoolean("done")
            val hasRepeat = TodoRepeatSupport.hasRepeatParams(params)
            if (newContent == null && newDone == null && !hasRepeat) {
                return toolError(
                    ToolErrorCode.MISSING_PARAMETER,
                    "update_todo 至少需要 content、done 或 repeat 之一",
                    "suggestion" to "传入 content 改文字、done 勾选、repeat 改整篇重复档位，可组合使用"
                )
            }

            val repeatUpdate = if (hasRepeat) TodoRepeatSupport.resolve(params, subject.todoRepeat) else null
            if (repeatUpdate is TodoRepeatSupport.RepeatUpdate.Invalid) return repeatUpdate.error

            // 只有要改条目本身时才做定位；单独改重复规则不依赖任何条目，空列表也能改。
            val located = if (newContent != null || newDone != null) {
                when (val found = locateTodo(subject, params)) {
                    is TodoLookup.Fail -> return found.error
                    is TodoLookup.Ok -> found
                }
            } else null

            if (newContent != null && located != null) repo.updateTodoItemContent(located.item.id, newContent)
            if (newDone != null && located != null) repo.setTodoItemDone(located.item.id, newDone)
            val repeat = when (repeatUpdate) {
                is TodoRepeatSupport.RepeatUpdate.Ok -> {
                    repo.updateTodoRepeat(subject.id, repeatUpdate.repeat)
                    repeatUpdate.repeat
                }
                else -> subject.todoRepeat
            }
            val now = System.currentTimeMillis()
            return buildJsonObject {
                "success" to true
                if (located != null) {
                    "id" to located.item.id
                    "index" to located.index
                    "content" to (newContent ?: located.item.content)
                    "is_done" to (newDone ?: located.item.isDone)
                }
                "repeat" to TodoRepeatSupport.toJson(
                    repeat,
                    now,
                    // 写了重复规则时仓库层会把 lastResetAt 记为当前时间，本地按同一口径回显
                    if (repeatUpdate is TodoRepeatSupport.RepeatUpdate.Ok) now else subject.todoLastResetAt,
                    subject.todoItems.any { it.isDone } || (newDone == true)
                )
                "repeat_scope" to "subject"
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
}

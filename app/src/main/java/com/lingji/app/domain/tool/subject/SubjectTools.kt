package com.lingji.app.domain.tool.subject

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.ToolErrorCode
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import com.lingji.app.domain.tool.missingParameter
import com.lingji.app.domain.tool.paramInt
import com.lingji.app.domain.tool.paramString
import com.lingji.app.domain.tool.subjectNotFound
import com.lingji.app.domain.tool.toolError
import kotlinx.coroutines.flow.first

object SubjectTools {

    fun create(repo: SubjectRepository, summaryDao: SubjectSummaryDao): List<Tool> = listOf(
        ListSubjects(repo),
        GetSubject(repo),
        CreateSubject(repo),
        DeleteSubject(repo, summaryDao),
        RenameSubject(repo)
    )

    private class ListSubjects(private val repo: SubjectRepository) : Tool {
        override val name = "list_subjects"
        override val description = "列出所有笔记的 id、标题和类型（NOTEBOOK / FRAGMENT / TODO）。" +
            "拿到标题后，其它工具可直接用 subject_title 按标题定位，不必复述长 id。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {}
        }
        override suspend fun execute(params: JsonObject): String {
            val subjects = repo.getAllSubjects().first()
            return buildJsonArray {
                for (s in subjects) {
                    +buildJsonObject {
                        "id" to s.id
                        "title" to s.title
                        "type" to s.type.name
                    }
                }
            }.toString()
        }
    }

    private class GetSubject(private val repo: SubjectRepository) : Tool {
        override val name = "get_subject"
        override val description = "获取指定笔记的概览信息：标题、类型、页面数、碎片数、待办项数。也可用 subject_title 按标题定位。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（也可用 subject_title 按标题定位）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val s = repo.getSubjectByIdOnce(id) ?: return subjectNotFound(id)
            return buildJsonObject {
                "id" to s.id
                "title" to s.title
                "type" to s.type.name
                "page_count" to (s.pages?.size ?: 0)
                "fragment_count" to (s.fragments.size + s.unmergedFragments.size)
                "todo_count" to s.todoItems.size
                if (s.type == SubjectType.TODO) {
                    "repeat_type" to s.todoRepeat.type.name.lowercase()
                    if (s.todoRepeat.type == TodoRepeatType.CUSTOM) {
                        "repeat_days" to s.todoRepeat.weekDays.sorted().joinToString(",")
                    }
                    if (s.todoRepeat.type == TodoRepeatType.INTERVAL) {
                        "repeat_interval_days" to s.todoRepeat.intervalDays
                    }
                    "list_reminder" to when {
                        !s.todoReminderEnabled -> "none"
                        s.todoRepeat.type == TodoRepeatType.ONCE -> formatAt(s.todoReminderAt)
                        else -> formatMinutes(s.todoReminderMinutes)
                    }
                }
            }.toString()
        }
    }

    private class CreateSubject(private val repo: SubjectRepository) : Tool {
        override val name = "create_subject"
        override val description = "创建一个新笔记。type 可选 NOTEBOOK（笔记本）、FRAGMENT（碎片）、TODO（待办列表），默认 NOTEBOOK。" +
            "TODO 类型必须在创建时确定重复规则（repeat_type，创建后不可修改）；TODO 内容用 list_todos / add_todo 等工具管理。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "title" to buildJsonObject { "type" to "string"; "description" to "笔记标题" }
                "type" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记类型：NOTEBOOK / FRAGMENT / TODO"
                    "enum" to buildJsonArray { +"NOTEBOOK"; +"FRAGMENT"; +"TODO" }
                }
                "repeat_type" to buildJsonObject {
                    "type" to "string"
                    "description" to "TODO 的重复规则：ONCE / DAILY / WEEKDAYS / WEEKENDS / INTERVAL / CUSTOM，默认 ONCE"
                    "enum" to buildJsonArray { +"ONCE"; +"DAILY"; +"WEEKDAYS"; +"WEEKENDS"; +"INTERVAL"; +"CUSTOM" }
                }
                "repeat_days" to buildJsonObject { "type" to "string"; "description" to "CUSTOM 时生效：周几，1=周一…7=周日，逗号分隔，如 1,3,5" }
                "repeat_interval_days" to buildJsonObject { "type" to "integer"; "description" to "INTERVAL 时生效：每隔多少天刷新一次，默认 1" }
            }
            "required" to buildJsonArray { +"title" }
        }
        override suspend fun execute(params: JsonObject): String {
            val title = params.paramString("title") ?: return missingParameter("title")
            val typeStr = params.paramString("type") ?: "notebook"
            val type = runCatching { SubjectType.valueOf(typeStr.uppercase()) }
                .getOrElse {
                    return toolError(
                        ToolErrorCode.INVALID_PARAMETER,
                        "未知的笔记类型：$typeStr",
                        "parameter" to "type",
                        "suggestion" to "type 只能是 NOTEBOOK / FRAGMENT / TODO"
                    )
                }
            val repeat = if (type == SubjectType.TODO) {
                parseTodoRepeat(params) ?: return toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    "TODO 的重复规则无效",
                    "parameter" to "repeat_type",
                    "suggestion" to "repeat_type 用 ONCE / DAILY / WEEKDAYS / WEEKENDS / INTERVAL / CUSTOM；CUSTOM 需 repeat_days，INTERVAL 需 repeat_interval_days"
                )
            } else {
                TodoRepeat()
            }
            var subject = Subject.create(title, type)
            if (type == SubjectType.TODO) {
                subject = subject.copy(todoRepeat = repeat, todoLastResetAt = System.currentTimeMillis())
            }
            repo.insert(subject)
            return buildJsonObject {
                "id" to subject.id
                "title" to subject.title
                "type" to subject.type.name
                if (type == SubjectType.TODO) {
                    "repeat_type" to subject.todoRepeat.type.name.lowercase()
                }
            }.toString()
        }
    }

    private class DeleteSubject(
        private val repo: SubjectRepository,
        private val summaryDao: SubjectSummaryDao
    ) : Tool {
        override val name = "delete_subject"
        override val description = "删除指定笔记及其所有页面、碎片、待办项和摘要。也可用 subject_title 按标题定位（重名时会返回候选，需确认后再删）。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "要删除的笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.paramString("subject_id") ?: return missingParameter("subject_id")
            if (repo.getSubjectByIdOnce(id) == null) return subjectNotFound(id)
            repo.delete(id)
            summaryDao.deleteBySubjectId(id)
            return """{"success":true}"""
        }
    }

    private class RenameSubject(private val repo: SubjectRepository) : Tool {
        override val name = "rename_subject"
        override val description = "重命名指定笔记。也可用 subject_title 按原标题定位。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "new_title" to buildJsonObject { "type" to "string"; "description" to "新标题" }
            }
            "required" to buildJsonArray { +"subject_id"; +"new_title" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val newTitle = params.paramString("new_title") ?: return missingParameter("new_title")
            if (repo.getSubjectByIdOnce(id) == null) return subjectNotFound(id)
            repo.rename(id, newTitle)
            return """{"success":true}"""
        }
    }

    /** 信息岛：创建 TODO 时解析重复规则；非法规则返回 null。 */
    private fun parseTodoRepeat(params: JsonObject): TodoRepeat? {
        val typeText = params.paramString("repeat_type")?.uppercase() ?: "ONCE"
        val type = runCatching { TodoRepeatType.valueOf(typeText) }.getOrNull() ?: return null
        val interval = (params.paramInt("repeat_interval_days") ?: 1).coerceAtLeast(1)
        val weekDays = params.paramString("repeat_days")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
            .orEmpty()
        if (type == TodoRepeatType.CUSTOM && weekDays.isEmpty()) return null
        return TodoRepeat(
            type = type,
            weekDays = if (type == TodoRepeatType.CUSTOM) weekDays else emptySet(),
            intervalDays = interval,
            anchorAt = System.currentTimeMillis()
        )
    }

    private fun formatMinutes(minutes: Int): String =
        String.format("%02d:%02d", minutes / 60, minutes % 60)

    private fun formatAt(at: Long): String =
        java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(at),
            java.time.ZoneId.systemDefault()
        ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

package com.lingji.app.domain.tool.note

import com.google.gson.JsonObject
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.fullNoteContent
import com.lingji.app.domain.tool.PageLookup
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import com.lingji.app.domain.tool.locatePage
import com.lingji.app.domain.tool.missingParameter
import com.lingji.app.domain.tool.paramString
import com.lingji.app.domain.tool.subjectNotFound
import com.lingji.app.domain.tool.toolError
import com.lingji.app.domain.tool.ToolErrorCode
import com.lingji.app.domain.tool.unsupportedForSubjectType

object NoteTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        GetAggregatedNote(repo),
        GetStudyPlan(repo),
        UpdateStudyPlan(repo),
        EditReplace(repo)
    )

    private class GetAggregatedNote(private val repo: SubjectRepository) : Tool {
        override val name = "get_aggregated_note"
        override val description = "读取指定笔记的完整内容：NOTEBOOK 为各页拼接，FRAGMENT 为聚合笔记或碎片原文，TODO 为待办项列表（- [ ] / - [x]）。" +
            "TODO 笔记的内容只能用 list_todos / add_todo / update_todo / delete_todo 修改，且改动会立即在这里读回。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（也可用 subject_title 按标题定位）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            return buildJsonObject { "content" to LLMService.stripDataImages(subject.fullNoteContent()) }.toString()
        }
    }

    private class GetStudyPlan(private val repo: SubjectRepository) : Tool {
        override val name = "get_study_plan"
        override val description = "读取指定笔记的学习计划。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            return buildJsonObject { "content" to subject.studyPlan }.toString()
        }
    }

    private class UpdateStudyPlan(private val repo: SubjectRepository) : Tool {
        override val name = "update_study_plan"
        override val description = "更新指定笔记的学习计划。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新的学习计划内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val content = params.get("content")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return missingParameter("content")
            if (content.isBlank()) return toolError(
                ToolErrorCode.INVALID_PARAMETER, "content 不能为空", "suggestion" to "传入非空内容"
            )
            repo.updateStudyPlan(subjectId, content)
            return """{"success":true}"""
        }
    }

    private class EditReplace(private val repo: SubjectRepository) : Tool {
        override val name = "edit_replace"
        override val description = "在笔记内容中查找并替换指定文本（局部编辑），避免整体重写导致图片损坏。" +
            "NOTEBOOK 类型需用 page_id / page_index / page_title 定位页面；FRAGMENT 类型无需页面参数，默认修改聚合笔记。" +
            "TODO 类型不支持文本替换，请改用 update_todo 改待办项文字。" +
            "编辑含图片的内容时必须使用此工具，避免重写整段内容。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID（NOTEBOOK 类型必填，也可用 page_index / page_title）" }
                "page_index" to buildJsonObject { "type" to "integer"; "description" to "页面序号，从 0 开始（NOTEBOOK 类型可用）" }
                "page_title" to buildJsonObject { "type" to "string"; "description" to "页面标题（NOTEBOOK 类型可用）" }
                "find" to buildJsonObject { "type" to "string"; "description" to "要查找的文本（精确匹配，非正则）" }
                "occurrence" to buildJsonObject { "type" to "integer"; "description" to "替换第几次出现（从 1 开始，1 = 第一个匹配）" }
                "replace" to buildJsonObject { "type" to "string"; "description" to "替换后的文本" }
            }
            "required" to buildJsonArray { +"find"; +"occurrence"; +"replace" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val find = params.get("find")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return missingParameter("find")
            val occurrence = runCatching { params.get("occurrence")?.asInt }.getOrNull()
                ?: return missingParameter("occurrence")
            val replace = params.get("replace")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return missingParameter("replace")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)

            return when (subject.type) {
                SubjectType.NOTEBOOK -> {
                    val page = when (val located = locatePage(subject, params)) {
                        is PageLookup.Fail -> return located.error
                        is PageLookup.Ok -> located.page
                    }
                    val newContent = replaceNth(page.content, find, occurrence, replace)
                        ?: return notFoundNth(occurrence)
                    repo.updatePage(subjectId, page.copy(content = newContent, updatedAt = System.currentTimeMillis()))
                    """{"success":true}"""
                }
                SubjectType.FRAGMENT -> {
                    val newContent = replaceNth(subject.aggregatedNote, find, occurrence, replace)
                        ?: return notFoundNth(occurrence)
                    repo.updateAggregatedNote(subjectId, newContent)
                    """{"success":true}"""
                }
                SubjectType.TODO -> unsupportedForSubjectType(
                    subject,
                    "edit_replace",
                    listOf(SubjectType.NOTEBOOK, SubjectType.FRAGMENT),
                    "待办列表不支持文本替换。请直接管理待办项：list_todos 查看、add_todo 新增、update_todo 改文字/勾选、delete_todo 删除。" +
                        "这些工具真实可用，改动会立即反映在 get_aggregated_note 中。"
                ) // suggestion 指向已实现的 todo 工具
            }
        }

        private fun notFoundNth(occurrence: Int): String = toolError(
            ToolErrorCode.OBJECT_NOT_FOUND,
            "未找到第 $occurrence 个匹配的文本",
            "suggestion" to "先用 get_page / get_aggregated_note 读取原文再重试；TODO 类型请改用 update_todo"
        )

        private fun replaceNth(content: String, find: String, occurrence: Int, replace: String): String? {
            if (find.isEmpty() || occurrence < 1) return null
            var startIndex = 0
            var foundIndex = -1
            for (i in 1..occurrence) {
                foundIndex = content.indexOf(find, startIndex)
                if (foundIndex < 0) return null
                startIndex = foundIndex + find.length
            }
            return content.substring(0, foundIndex) + replace + content.substring(foundIndex + find.length)
        }
    }
}

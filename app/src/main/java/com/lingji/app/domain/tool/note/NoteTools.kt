package com.lingji.app.domain.tool.note

import com.google.gson.JsonObject
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.fullNoteContent
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object NoteTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        GetAggregatedNote(repo),
        GetStudyPlan(repo),
        UpdateStudyPlan(repo),
        EditReplace(repo)
    )

    private class GetAggregatedNote(private val repo: SubjectRepository) : Tool {
        override val name = "get_aggregated_note"
        override val description = "读取指定笔记的完整内容（NOTEBOOK 为各页拼接，FRAGMENT 为聚合笔记或碎片原文）。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            return buildJsonObject { "content" to LLMService.stripDataImages(subject.fullNoteContent()) }.toString()
        }
    }

    private class UpdateAggregatedNote(private val repo: SubjectRepository) : Tool {
        override val name = "update_aggregated_note"
        override val description = "更新指定笔记的聚合笔记内容。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新的聚合笔记内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            if (content.isBlank()) return "Error: content 不能为空，清空聚合笔记请使用 edit_replace 删除相关内容"
            repo.updateAggregatedNote(subjectId, content)
            return """{"success":true}"""
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
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
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
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            if (content.isBlank()) return "Error: content 不能为空"
            repo.updateStudyPlan(subjectId, content)
            return """{"success":true}"""
        }
    }

    private class EditReplace(private val repo: SubjectRepository) : Tool {
        override val name = "edit_replace"
        override val description = "在笔记内容中查找并替换指定文本（局部编辑），避免整体重写导致图片损坏。" +
            "notebook 类型需指定 page_id 操作对应页面；fragment 类型无需 page_id，默认修改聚合笔记。" +
            "编辑含图片的内容时必须使用此工具，避免重写整段内容。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID（notebook 类型必填，fragment 类型不需要）" }
                "find" to buildJsonObject { "type" to "string"; "description" to "要查找的文本（精确匹配，非正则）" }
                "occurrence" to buildJsonObject { "type" to "integer"; "description" to "替换第几次出现（从 1 开始，1 = 第一个匹配）" }
                "replace" to buildJsonObject { "type" to "string"; "description" to "替换后的文本" }
            }
            "required" to buildJsonArray { +"find"; +"occurrence"; +"replace" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val find = params.get("find")?.asString
                ?: return "Error: Missing required parameter: find"
            val occurrence = params.get("occurrence")?.asInt
                ?: return "Error: Missing required parameter: occurrence"
            val replace = params.get("replace")?.asString
                ?: return "Error: Missing required parameter: replace"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"

            return when (subject.type) {
                SubjectType.NOTEBOOK -> {
                    val pageId = params.get("page_id")?.asString
                        ?: return "Error: notebook 类型需要 page_id 参数"
                    val page = subject.pages?.find { it.id == pageId }
                        ?: return "Error: Page not found: $pageId"
                    val newContent = replaceNth(page.content, find, occurrence, replace)
                        ?: return "Error: 未找到第 $occurrence 个匹配的文本"
                    repo.updatePage(subjectId, page.copy(content = newContent, updatedAt = System.currentTimeMillis()))
                    """{"success":true}"""
                }
                SubjectType.FRAGMENT -> {
                    val newContent = replaceNth(subject.aggregatedNote, find, occurrence, replace)
                        ?: return "Error: 未找到第 $occurrence 个匹配的文本"
                    repo.updateAggregatedNote(subjectId, newContent)
                    """{"success":true}"""
                }
            }
        }

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

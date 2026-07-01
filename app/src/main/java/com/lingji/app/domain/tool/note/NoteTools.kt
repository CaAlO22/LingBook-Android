package com.lingji.app.domain.tool.note

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object NoteTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        GetAggregatedNote(repo),
        UpdateAggregatedNote(repo),
        GetStudyPlan(repo),
        UpdateStudyPlan(repo)
    )

    private class GetAggregatedNote(private val repo: SubjectRepository) : Tool {
        override val name = "get_aggregated_note"
        override val description = "读取指定笔记的聚合笔记内容（由碎片聚合而成的 Markdown 笔记）。"
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
            return buildJsonObject { "content" to subject.aggregatedNote }.toString()
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
            repo.updateStudyPlan(subjectId, content)
            return """{"success":true}"""
        }
    }
}

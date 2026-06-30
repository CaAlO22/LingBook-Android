package com.lingji.app.domain.tool.fragment

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object FragmentTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListFragments(repo),
        AddFragment(repo),
        UpdateFragment(repo),
        DeleteFragment(repo)
    )

    private class ListFragments(private val repo: SubjectRepository) : Tool {
        override val name = "list_fragments"
        override val description = "列出指定笔记下的所有碎片（含未合并的）。返回 id、内容、时间戳、是否已合并。"
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
            val all = subject.fragments + subject.unmergedFragments
            val arr = buildJsonArray {
                for (f in all) {
                    +buildJsonObject {
                        "id" to f.id
                        "content" to f.content
                        "timestamp" to f.timestamp
                        "is_merged" to f.isMerged
                    }
                }
            }
            return arr.toString()
        }
    }

    private class AddFragment(private val repo: SubjectRepository) : Tool {
        override val name = "add_fragment"
        override val description = "向指定笔记添加一条新的碎片。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "碎片内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            val fragment = Fragment(content = content)
            repo.addFragment(subjectId, fragment)
            return buildJsonObject { "id" to fragment.id }.toString()
        }
    }

    private class UpdateFragment(private val repo: SubjectRepository) : Tool {
        override val name = "update_fragment"
        override val description = "修改指定碎片的内容。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "fragment_id" to buildJsonObject { "type" to "string"; "description" to "碎片 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"fragment_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val fragmentId = params.get("fragment_id")?.asString
                ?: return "Error: Missing required parameter: fragment_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            repo.updateFragment(subjectId, fragmentId, content)
            return """{"success":true}"""
        }
    }

    private class DeleteFragment(private val repo: SubjectRepository) : Tool {
        override val name = "delete_fragment"
        override val description = "删除指定碎片。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "fragment_id" to buildJsonObject { "type" to "string"; "description" to "碎片 ID" }
            }
            "required" to buildJsonArray { +"subject_id"; +"fragment_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val fragmentId = params.get("fragment_id")?.asString
                ?: return "Error: Missing required parameter: fragment_id"
            repo.deleteFragment(subjectId, fragmentId)
            return """{"success":true}"""
        }
    }
}

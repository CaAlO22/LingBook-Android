package com.lingji.app.domain.tool.fragment

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import kotlinx.coroutines.flow.first

object FragmentTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListFragments(repo),
        AddFragment(repo),
        UpdateFragment(repo),
        DeleteFragment(repo),
        SearchFragments(repo)
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
                "timestamp" to buildJsonObject { "type" to "number"; "description" to "碎片原始发送时间戳（毫秒）。整理首页碎片时请传入碎片原始时间戳以保留发送时间，不传则使用当前时间。" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            val timestamp = params.get("timestamp")?.asLong ?: System.currentTimeMillis()
            val fragment = Fragment(content = content, timestamp = timestamp)
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
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val allFragments = subject.fragments + subject.unmergedFragments
            if (allFragments.none { it.id == fragmentId })
                return "Error: Fragment not found: $fragmentId"
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
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val allFragments = subject.fragments + subject.unmergedFragments
            if (allFragments.none { it.id == fragmentId })
                return "Error: Fragment not found: $fragmentId"
            repo.deleteFragment(subjectId, fragmentId)
            return """{"success":true}"""
        }
    }

    private class SearchFragments(private val repo: SubjectRepository) : Tool {
        override val name = "search_fragments"
        override val description = "基于原文直接匹配搜索笔记碎片内容（精确关键词查找）。可指定 subject_id 搜索特定笔记，不传则搜索全部笔记的碎片。返回匹配碎片的内容片段和匹配度。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "query" to buildJsonObject {
                    "type" to "string"
                    "description" to "搜索关键词"
                }
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "可选：限定搜索的笔记 ID"
                }
            }
            "required" to buildJsonArray { +"query" }
        }
        override suspend fun execute(params: JsonObject): String {
            val query = params.get("query")?.asString
                ?: return "Error: Missing required parameter: query"
            val subjectId = params.get("subject_id")?.asString

            val subjects = if (subjectId != null) {
                listOfNotNull(repo.getSubjectByIdOnce(subjectId))
            } else {
                repo.getAllSubjects().first()
            }

            val queryLower = query.lowercase()
            val queryWords = queryLower.split(Regex("\\s+")).filter { it.length > 1 }
            val useWords = queryWords.isNotEmpty()

            val results = mutableListOf<String>()
            for (s in subjects) {
                val allFragments = s.fragments + s.unmergedFragments
                for (f in allFragments) {
                    val contentLower = f.content.lowercase()
                    val matched = if (useWords) {
                        queryWords.count { contentLower.contains(it) }
                    } else {
                        if (contentLower.contains(queryLower)) 1 else 0
                    }
                    if (matched > 0) {
                        val score = if (useWords) {
                            matched.toFloat() / queryWords.size
                        } else 1f
                        results.add(buildJsonObject {
                            "subject_id" to s.id
                            "subject_title" to s.title
                            "fragment_id" to f.id
                            "snippet" to buildSnippet(f.content, query)
                            "is_merged" to f.isMerged
                            "score" to score
                        }.toString())
                    }
                }
            }
            if (results.isEmpty()) return "[]"
            return "[" + results.joinToString(",") + "]"
        }

        private fun buildSnippet(content: String, query: String, radius: Int = 50): String {
            val idx = content.indexOf(query, ignoreCase = true)
            if (idx < 0) return content.take(radius * 2)
            val start = (idx - radius).coerceAtLeast(0)
            val end = (idx + query.length + radius).coerceAtMost(content.length)
            val prefix = if (start > 0) "…" else ""
            val suffix = if (end < content.length) "…" else ""
            return prefix + content.substring(start, end) + suffix
        }
    }
}

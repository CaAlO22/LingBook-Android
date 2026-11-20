package com.lingji.app.domain.tool.fragment

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import com.lingji.app.domain.tool.missingParameter
import com.lingji.app.domain.tool.objectNotFound
import com.lingji.app.domain.tool.paramString
import com.lingji.app.domain.tool.subjectNotFound
import com.lingji.app.domain.tool.unsupportedForSubjectType
import kotlinx.coroutines.flow.first

/**
 * 碎片工具的读写路径统一绑定 FRAGMENT 类型笔记。
 *
 * 历史上 add_fragment 不校验 subject.type，可向 TODO / NOTEBOOK 写入碎片：
 * 这些碎片能被 list_fragments 查到，但 fullNoteContent() 对 TODO 只读 todo_items、
 * 对 NOTEBOOK 只读 pages，导致「写成功、读不到」的孤立脏数据。现在写入口一律拒绝。
 * 删除口保留跨类型能力，用于清理历史脏数据。
 */
object FragmentTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListFragments(repo),
        AddFragment(repo),
        UpdateFragment(repo),
        DeleteFragment(repo),
        SearchFragments(repo)
    )

    private val FRAGMENT_ONLY = listOf(SubjectType.FRAGMENT)

    private class ListFragments(private val repo: SubjectRepository) : Tool {
        override val name = "list_fragments"
        override val description = "列出指定笔记下的所有碎片（含未合并的）。返回 id、内容、时间戳、是否已合并。" +
            "仅 FRAGMENT 类型笔记的碎片属于笔记正文；若笔记是 TODO / NOTEBOOK 类型，返回的碎片是历史脏数据（orphaned=true，不参与 get_aggregated_note），可用 delete_fragment 清理。"
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
            val orphaned = subject.type != SubjectType.FRAGMENT
            val all = subject.fragments + subject.unmergedFragments
            return buildJsonArray {
                for (f in all) {
                    +buildJsonObject {
                        "id" to f.id
                        "content" to f.content
                        "timestamp" to f.timestamp
                        "is_merged" to f.isMerged
                        "orphaned" to orphaned
                        if (orphaned) {
                            "subject_type" to subject.type.name
                            "warning" to "该碎片不参与笔记正文（get_aggregated_note 读不到），建议用 delete_fragment 清理"
                        }
                    }
                }
            }.toString()
        }
    }

    private class AddFragment(private val repo: SubjectRepository) : Tool {
        override val name = "add_fragment"
        override val description = "向 FRAGMENT 类型笔记添加一条新的碎片。TODO / NOTEBOOK 类型笔记会被明确拒绝（它们的正文分别是待办项和页面），请改用 add_todo 或 create_page。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "碎片笔记 ID（也可用 subject_title 按标题定位）" }
                "content" to buildJsonObject { "type" to "string"; "description" to "碎片内容" }
                "timestamp" to buildJsonObject { "type" to "number"; "description" to "碎片原始发送时间戳（毫秒）。整理首页碎片时请传入碎片原始时间戳以保留发送时间，不传则使用当前时间。" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val content = params.paramString("content") ?: return missingParameter("content")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            if (subject.type != SubjectType.FRAGMENT) {
                return unsupportedForSubjectType(subject, "add_fragment", FRAGMENT_ONLY)
            }
            val timestamp = params.paramLong("timestamp") ?: System.currentTimeMillis()
            val fragment = Fragment(content = content, timestamp = timestamp)
            repo.addFragment(subjectId, fragment)
            return buildJsonObject { "id" to fragment.id }.toString()
        }
    }

    private class UpdateFragment(private val repo: SubjectRepository) : Tool {
        override val name = "update_fragment"
        override val description = "修改 FRAGMENT 类型笔记中指定碎片的内容。仅 FRAGMENT 类型可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "碎片笔记 ID" }
                "fragment_id" to buildJsonObject { "type" to "string"; "description" to "碎片 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"fragment_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val fragmentId = params.paramString("fragment_id") ?: return missingParameter("fragment_id")
            val content = params.paramString("content") ?: return missingParameter("content")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            if (subject.type != SubjectType.FRAGMENT) {
                return unsupportedForSubjectType(subject, "update_fragment", FRAGMENT_ONLY)
            }
            val allFragments = subject.fragments + subject.unmergedFragments
            if (allFragments.none { it.id == fragmentId }) return objectNotFound("碎片", fragmentId)
            repo.updateFragment(subjectId, fragmentId, content)
            return """{"success":true}"""
        }
    }

    private class DeleteFragment(private val repo: SubjectRepository) : Tool {
        override val name = "delete_fragment"
        override val description = "删除指定碎片。FRAGMENT 类型笔记用于正常删除；TODO / NOTEBOOK 类型笔记上仅用于清理历史遗留的孤立碎片（这些碎片不参与笔记正文）。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "fragment_id" to buildJsonObject { "type" to "string"; "description" to "碎片 ID，可先用 list_fragments 查出（orphaned=true 的即脏数据）" }
            }
            "required" to buildJsonArray { +"subject_id"; +"fragment_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val fragmentId = params.paramString("fragment_id") ?: return missingParameter("fragment_id")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            val allFragments = subject.fragments + subject.unmergedFragments
            if (allFragments.none { it.id == fragmentId }) return objectNotFound("碎片", fragmentId)
            repo.deleteFragment(subjectId, fragmentId)
            return if (subject.type != SubjectType.FRAGMENT) {
                buildJsonObject {
                    "success" to true
                    "cleaned_orphan" to true
                    "subject_type" to subject.type.name
                    "message" to "已清理不参与笔记正文的历史孤立碎片"
                }.toString()
            } else """{"success":true}"""
        }
    }

    private class SearchFragments(private val repo: SubjectRepository) : Tool {
        override val name = "search_fragments"
        override val description = "基于原文直接匹配搜索碎片内容（精确关键词查找）。只搜索 FRAGMENT 类型笔记的碎片；TODO / NOTEBOOK 笔记上的历史孤立碎片不参与正文，故不返回。可指定 subject_id 搜索特定笔记，不传则搜索全部碎片笔记。返回匹配碎片的内容片段和匹配度。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "query" to buildJsonObject {
                    "type" to "string"
                    "description" to "搜索关键词"
                }
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "可选：限定搜索的笔记 ID（也可用 subject_title 按标题定位）"
                }
            }
            "required" to buildJsonArray { +"query" }
        }
        override suspend fun execute(params: JsonObject): String {
            val query = params.paramString("query") ?: return missingParameter("query")
            val subjectId = params.paramString("subject_id")

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
                // 只有 FRAGMENT 类型的碎片属于笔记正文，避免把永远看不到的脏数据当成命中
                if (s.type != SubjectType.FRAGMENT) continue
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

/** 时间戳在 JSON 里是 number，单独读取以免 asLong 在非法值上抛异常。 */
private fun JsonObject.paramLong(name: String): Long? = runCatching {
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asLong
}.getOrNull()

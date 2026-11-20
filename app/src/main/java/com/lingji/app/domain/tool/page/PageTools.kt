package com.lingji.app.domain.tool.page

import com.google.gson.JsonObject
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.PageLookup
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.ToolErrorCode
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import com.lingji.app.domain.tool.locatePage
import com.lingji.app.domain.tool.missingParameter
import com.lingji.app.domain.tool.paramString
import com.lingji.app.domain.tool.subjectNotFound
import com.lingji.app.domain.tool.suggestionFor
import com.lingji.app.domain.tool.toolError

/**
 * 页面工具只对 NOTEBOOK 类型开放。
 *
 * 原实现只拦 FRAGMENT（isFragmentType()），TODO 类型笔记同样会被写入页面，
 * 而 fullNoteContent() 对 TODO 只读 todo_items —— 与 add_fragment 一样会产生
 * 「写成功、正文读不到」的孤立数据。这里统一按 type != NOTEBOOK 拒绝。
 */
object PageTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListPages(repo),
        GetPage(repo),
        CreatePage(repo),
        UpdatePage(repo),
        DeletePage(repo)
    )


    private class ListPages(private val repo: SubjectRepository) : Tool {
        override val name = "list_pages"
        override val description = "列出指定笔记本笔记下的所有页面：id、标题、顺序。仅 NOTEBOOK 类型笔记可用；返回的 order 可作为 page_index 直接用于其它页面工具。"
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
            if (subject.type != SubjectType.NOTEBOOK) return subject.pageNotSupportedError("list_pages")
            val pages = subject.pages ?: emptyList()
            return buildJsonArray {
                pages.forEachIndexed { idx, p ->
                    +buildJsonObject {
                        "id" to p.id
                        "title" to p.title
                        "order" to idx
                    }
                }
            }.toString()
        }
    }

    private class GetPage(private val repo: SubjectRepository) : Tool {
        override val name = "get_page"
        override val description = "读取指定笔记本笔记中某页面的完整内容。仅 NOTEBOOK 类型笔记可用。可用 page_id / page_index / page_title 三选一定位页面。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID（三选一）" }
                "page_index" to buildJsonObject { "type" to "integer"; "description" to "页面序号，从 0 开始，取自 list_pages 的 order（三选一）" }
                "page_title" to buildJsonObject { "type" to "string"; "description" to "按页面标题定位（三选一），精确匹配优先" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            if (subject.type != SubjectType.NOTEBOOK) return subject.pageNotSupportedError("get_page")
            val page = when (val located = locatePage(subject, params)) {
                is PageLookup.Fail -> return located.error
                is PageLookup.Ok -> located.page
            }
            return buildJsonObject {
                "id" to page.id
                "title" to page.title
                "content" to LLMService.stripDataImages(page.content)
                "updated_at" to page.updatedAt
            }.toString()
        }
    }

    private class CreatePage(private val repo: SubjectRepository) : Tool {
        override val name = "create_page"
        override val description = "在指定笔记本笔记末尾新增一个页面。仅 NOTEBOOK 类型笔记可用；碎片笔记请用 add_fragment，待办笔记请用 add_todo。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（必须为 NOTEBOOK 类型）" }
                "title" to buildJsonObject { "type" to "string"; "description" to "页面标题" }
                "content" to buildJsonObject { "type" to "string"; "description" to "页面内容（Markdown）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            if (subject.type != SubjectType.NOTEBOOK) return subject.pageNotSupportedError("create_page")
            val title = params.paramString("title") ?: ""
            val content = params.get("content")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
            val page = NotebookPage(title = title, content = content)
            repo.addPage(subjectId, page)
            return buildJsonObject {
                "id" to page.id
                "title" to page.title
            }.toString()
        }
    }

    private class UpdatePage(private val repo: SubjectRepository) : Tool {
        override val name = "update_page"
        override val description = "更新指定笔记本笔记中某页面的标题。仅 NOTEBOOK 类型笔记可用。编辑页面内容请使用 edit_replace。可用 page_id / page_index / page_title 三选一定位。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（必须为 NOTEBOOK 类型）" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID（三选一）" }
                "page_index" to buildJsonObject { "type" to "integer"; "description" to "页面序号，从 0 开始（三选一）" }
                "page_title" to buildJsonObject { "type" to "string"; "description" to "按页面原标题定位（三选一）" }
                "title" to buildJsonObject { "type" to "string"; "description" to "新标题" }
            }
            "required" to buildJsonArray { +"subject_id"; +"title" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val title = params.paramString("title") ?: return missingParameter("title")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            if (subject.type != SubjectType.NOTEBOOK) return subject.pageNotSupportedError("update_page")
            val existing = when (val located = locatePage(subject, params)) {
                is PageLookup.Fail -> return located.error
                is PageLookup.Ok -> located.page
            }
            val updated = existing.copy(title = title, updatedAt = System.currentTimeMillis())
            repo.updatePage(subjectId, updated)
            return """{"success":true}"""
        }
    }

    private class DeletePage(private val repo: SubjectRepository) : Tool {
        override val name = "delete_page"
        override val description = "删除指定笔记本笔记中的指定页面。仅 NOTEBOOK 类型笔记可用。可用 page_id / page_index / page_title 三选一定位页面。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（必须为 NOTEBOOK 类型）" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID（三选一）" }
                "page_index" to buildJsonObject { "type" to "integer"; "description" to "页面序号，从 0 开始（三选一）" }
                "page_title" to buildJsonObject { "type" to "string"; "description" to "按页面标题定位（三选一）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.paramString("subject_id") ?: return missingParameter("subject_id")
            val subject = repo.getSubjectByIdOnce(subjectId) ?: return subjectNotFound(subjectId)
            if (subject.type != SubjectType.NOTEBOOK) return subject.pageNotSupportedError("delete_page")
            val page = when (val located = locatePage(subject, params)) {
                is PageLookup.Fail -> return located.error
                is PageLookup.Ok -> located.page
            }
            repo.deletePage(subjectId, page.id)
            return """{"success":true}"""
        }
    }

    private fun Subject.pageNotSupportedError(operation: String): String = when (type) {
        SubjectType.FRAGMENT -> toolError(
            ToolErrorCode.UNSUPPORTED_FOR_SUBJECT_TYPE,
            "碎片笔记不支持页面操作。\"$title\" 是碎片笔记（fragment），没有页面功能。请使用碎片相关操作（add_fragment / list_fragments 等）或先将其转为笔记本。",
            "operation" to operation,
            "subject_id" to id,
            "subject_type" to "FRAGMENT",
            "supported_types" to buildJsonArray { +"NOTEBOOK" },
            "suggestion" to suggestionFor(type)
        )
        SubjectType.TODO -> toolError(
            ToolErrorCode.UNSUPPORTED_FOR_SUBJECT_TYPE,
            "待办笔记不支持页面操作。\"$title\" 是待办笔记（todo），页面不属于它的正文，写入后在笔记里看不到。请使用待办项工具管理内容。",
            "operation" to operation,
            "subject_id" to id,
            "subject_type" to "TODO",
            "supported_types" to buildJsonArray { +"NOTEBOOK" },
            "suggestion" to suggestionFor(type)
        )
        SubjectType.NOTEBOOK -> toolError(
            ToolErrorCode.UNSUPPORTED_FOR_SUBJECT_TYPE,
            "页面操作仅支持 NOTEBOOK 类型。",
            "operation" to operation,
            "subject_id" to id,
            "subject_type" to "NOTEBOOK",
            "supported_types" to buildJsonArray { +"NOTEBOOK" },
            "suggestion" to null
        )
    }
}

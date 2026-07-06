package com.lingji.app.domain.tool.page

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

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
        override val description = "列出指定笔记本笔记下的所有页面：id、标题、顺序。仅 notebook 类型笔记可用。"
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
            if (subject.isFragmentType()) return subject.pageNotSupportedError()
            val pages = subject.pages ?: emptyList()
            val arr = buildJsonArray {
                pages.forEachIndexed { idx, p ->
                    +buildJsonObject {
                        "id" to p.id
                        "title" to p.title
                        "order" to idx
                    }
                }
            }
            return arr.toString()
        }
    }

    private class GetPage(private val repo: SubjectRepository) : Tool {
        override val name = "get_page"
        override val description = "读取指定笔记本笔记中某页面的完整内容。仅 notebook 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID" }
            }
            "required" to buildJsonArray { +"subject_id"; +"page_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val pageId = params.get("page_id")?.asString
                ?: return "Error: Missing required parameter: page_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            if (subject.isFragmentType()) return subject.pageNotSupportedError()
            val page = subject.pages?.find { it.id == pageId }
                ?: return "Error: Page not found: $pageId"
            return buildJsonObject {
                "id" to page.id
                "title" to page.title
                "content" to page.content
                "updated_at" to page.updatedAt
            }.toString()
        }
    }

    private class CreatePage(private val repo: SubjectRepository) : Tool {
        override val name = "create_page"
        override val description = "在指定笔记本笔记末尾新增一个页面。仅 notebook 类型笔记可用，碎片笔记不支持页面。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（必须为 notebook 类型）" }
                "title" to buildJsonObject { "type" to "string"; "description" to "页面标题" }
                "content" to buildJsonObject { "type" to "string"; "description" to "页面内容（Markdown）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            if (subject.isFragmentType()) return subject.pageNotSupportedError()
            val title = params.get("title")?.asString ?: ""
            val content = params.get("content")?.asString ?: ""
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
        override val description = "更新指定笔记本笔记中某页面的标题和/或内容。仅 notebook 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（必须为 notebook 类型）" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID" }
                "title" to buildJsonObject { "type" to "string"; "description" to "新标题（可选）" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新内容（可选）" }
            }
            "required" to buildJsonArray { +"subject_id"; +"page_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val pageId = params.get("page_id")?.asString
                ?: return "Error: Missing required parameter: page_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            if (subject.isFragmentType()) return subject.pageNotSupportedError()
            val existing = subject.pages?.find { it.id == pageId }
                ?: return "Error: Page not found: $pageId"
            val updated = existing.copy(
                title = params.get("title")?.asString ?: existing.title,
                content = params.get("content")?.asString ?: existing.content,
                updatedAt = System.currentTimeMillis()
            )
            repo.updatePage(subjectId, updated)
            return """{"success":true}"""
        }
    }

    private class DeletePage(private val repo: SubjectRepository) : Tool {
        override val name = "delete_page"
        override val description = "删除指定笔记本笔记中的指定页面。仅 notebook 类型笔记可用。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID（必须为 notebook 类型）" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID" }
            }
            "required" to buildJsonArray { +"subject_id"; +"page_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val pageId = params.get("page_id")?.asString
                ?: return "Error: Missing required parameter: page_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            if (subject.isFragmentType()) return subject.pageNotSupportedError()
            repo.deletePage(subjectId, pageId)
            return """{"success":true}"""
        }
    }

    private fun Subject.isFragmentType(): Boolean = type == SubjectType.FRAGMENT

    private fun Subject.pageNotSupportedError(): String =
        "Error: 碎片笔记不支持页面操作。\"${title}\" 是碎片笔记（fragment），没有页面功能。请使用碎片相关操作（add_fragment / list_fragments 等）或先将其转为笔记本。"
}

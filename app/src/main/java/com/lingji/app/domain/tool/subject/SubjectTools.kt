package com.lingji.app.domain.tool.subject

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
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
        override val description = "列出所有笔记的 id、标题和类型。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {}
        }
        override suspend fun execute(params: JsonObject): String {
            val subjects = repo.getAllSubjects().first()
            val arr = buildJsonArray {
                for (s in subjects) {
                    +buildJsonObject {
                        "id" to s.id
                        "title" to s.title
                        "type" to s.type.name
                    }
                }
            }
            return arr.toString()
        }
    }

    private class GetSubject(private val repo: SubjectRepository) : Tool {
        override val name = "get_subject"
        override val description = "获取指定笔记的概览信息：标题、类型、页面数、碎片数。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记 ID"
                }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val s = repo.getSubjectByIdOnce(id)
                ?: return "Error: Subject not found: $id"
            return buildJsonObject {
                "id" to s.id
                "title" to s.title
                "type" to s.type.name
                "page_count" to (s.pages?.size ?: 0)
                "fragment_count" to (s.fragments.size + s.unmergedFragments.size)
            }.toString()
        }
    }

    private class CreateSubject(private val repo: SubjectRepository) : Tool {
        override val name = "create_subject"
        override val description = "创建一个新笔记。type 可选 notebook 或 fragment，默认 notebook。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "title" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记标题"
                }
                "type" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记类型：notebook 或 fragment"
                    "enum" to buildJsonArray { +"notebook"; +"fragment" }
                }
            }
            "required" to buildJsonArray { +"title" }
        }
        override suspend fun execute(params: JsonObject): String {
            val title = params.get("title")?.asString
                ?: return "Error: Missing required parameter: title"
            val typeStr = params.get("type")?.asString ?: "notebook"
            val type = runCatching { SubjectType.valueOf(typeStr.uppercase()) }
                .getOrElse { SubjectType.NOTEBOOK }
            val subject = Subject.create(title, type)
            repo.insert(subject)
            return buildJsonObject {
                "id" to subject.id
                "title" to subject.title
                "type" to subject.type.name
            }.toString()
        }
    }

    private class DeleteSubject(
        private val repo: SubjectRepository,
        private val summaryDao: SubjectSummaryDao
    ) : Tool {
        override val name = "delete_subject"
        override val description = "删除指定笔记及其所有页面、碎片和摘要。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "要删除的笔记 ID"
                }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            repo.delete(id)
            summaryDao.deleteBySubjectId(id)
            return """{"success":true}"""
        }
    }

    private class RenameSubject(private val repo: SubjectRepository) : Tool {
        override val name = "rename_subject"
        override val description = "重命名指定笔记。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记 ID"
                }
                "new_title" to buildJsonObject {
                    "type" to "string"
                    "description" to "新标题"
                }
            }
            "required" to buildJsonArray { +"subject_id"; +"new_title" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val newTitle = params.get("new_title")?.asString
                ?: return "Error: Missing required parameter: new_title"
            repo.rename(id, newTitle)
            return """{"success":true}"""
        }
    }
}

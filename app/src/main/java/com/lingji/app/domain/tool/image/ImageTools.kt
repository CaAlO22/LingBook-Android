package com.lingji.app.domain.tool.image

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object ImageTools {

    fun create(repo: SubjectRepository, imageStore: ConversationImageStore): List<Tool> = listOf(
        InsertImage(repo, imageStore)
    )

    private class InsertImage(
        private val repo: SubjectRepository,
        private val imageStore: ConversationImageStore
    ) : Tool {
        override val name = "insert_img"
        override val description = "将当前对话中指定编号的图片插入到笔记本笔记的指定页面。只能操作 notebook 类型笔记。" +
            "图片编号是用户在对话中发送图片时自动分配的（从1开始递增）。page_id 可通过 list_pages 获取。图片会追加到页面内容末尾。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记本笔记的 ID（必须为 notebook 类型）"
                }
                "page_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "目标页面 ID（可通过 list_pages 获取）"
                }
                "image_number" to buildJsonObject {
                    "type" to "integer"
                    "description" to "对话中图片的编号（用户发送图片时分配的编号）"
                }
            }
            "required" to buildJsonArray { +"subject_id"; +"page_id"; +"image_number" }
        }

        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val pageId = params.get("page_id")?.asString
                ?: return "Error: Missing required parameter: page_id"
            val imageNumber = params.get("image_number")?.asInt
                ?: return "Error: Missing required parameter: image_number"

            // 获取图片
            val imageData = imageStore.getImage(imageNumber)
                ?: return "Error: 找不到编号为 $imageNumber 的图片。当前对话共有 ${imageStore.getImageCount()} 张图片。"

            // 获取笔记
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: 找不到笔记: $subjectId"

            if (subject.type != SubjectType.NOTEBOOK) {
                return "Error: 只能对 notebook 类型笔记操作。「${subject.title}」是 ${subject.type.name} 类型，没有页面功能。"
            }

            val pages = subject.pages
            if (pages.isNullOrEmpty()) return "Error: 笔记「${subject.title}」没有任何页面。"

            val targetPage = pages.find { it.id == pageId }
                ?: return "Error: 找不到页面: $pageId。笔记「${subject.title}」共有 ${pages.size} 页。"

            // 在页面内容末尾追加图片（Markdown 图片语法）
            val newContent = if (targetPage.content.isBlank()) {
                "![图片]($imageData)"
            } else {
                "${targetPage.content}\n\n![图片]($imageData)"
            }

            val updatedPage = targetPage.copy(
                content = newContent,
                updatedAt = System.currentTimeMillis()
            )
            repo.updatePage(subjectId, updatedPage)

            return buildJsonObject {
                "success" to true
                "page_title" to targetPage.title
                "page_id" to pageId
                "image_number" to imageNumber
            }.toString()
        }
    }
}

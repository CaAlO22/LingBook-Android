package com.lingji.app.domain.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 单笔记范围工具注册表：过滤工具子集，移除 subject_id 参数（LLM 无感知），
 * 执行时自动注入当前笔记的 subject_id。
 */
class ScopedToolRegistry(
    private val toolRegistry: ToolRegistry,
    private val subjectId: String,
    private val allowedToolNames: Set<String>
) {
    private val tools: List<Tool> = toolRegistry.getAllTools()
        .filter { it.name in allowedToolNames }

    /** 转为 OpenAI function-calling 格式，subject_id 参数已从 schema 中移除。 */
    fun toOpenAITools(): JsonArray = JsonArray().apply {
        tools.forEach { tool ->
            add(buildJsonObject {
                "type" to "function"
                "function" to buildJsonObject {
                    "name" to tool.name
                    "description" to tool.description
                    "parameters" to removeSubjectId(tool.parameters)
                }
            })
        }
    }

    /** 执行工具，自动注入 subject_id。 */
    suspend fun executeTool(name: String, params: JsonObject): String {
        val tool = tools.find { it.name == name }
            ?: return "Error: Unknown tool '$name'"
        val scopedParams = JsonObject().apply {
            params.entrySet().forEach { (key, value) ->
                add(key, value)
            }
            addProperty("subject_id", subjectId) // 注入在最后，覆盖 LLM 可能提供的值
        }
        return runCatching { tool.execute(scopedParams) }
            .getOrElse { "Error: ${it.message ?: "Unknown error"}" }
    }

    companion object {
        /** 单笔记 Agent 可用的 17 个工具（排除 list/create/delete_subject + summarize_all_notes）。 */
        val SINGLE_NOTE_TOOLS = setOf(
            "get_subject", "rename_subject",
            "list_pages", "get_page", "create_page", "update_page", "delete_page",
            "list_fragments", "add_fragment", "update_fragment", "delete_fragment",
            "get_aggregated_note", "update_aggregated_note",
            "get_study_plan", "update_study_plan",
            "get_page_index", "search_pages"
        )
    }

    private fun removeSubjectId(params: JsonObject): JsonObject {
        val copy = params.deepCopy()
        copy.getAsJsonObject("properties")?.remove("subject_id")
        copy.getAsJsonArray("required")?.let { required ->
            val filtered = JsonArray().apply {
                required.forEach { if (it.asString != "subject_id") add(it) }
            }
            if (filtered.size() > 0) copy.add("required", filtered)
            else copy.remove("required")
        }
        return copy
    }
}

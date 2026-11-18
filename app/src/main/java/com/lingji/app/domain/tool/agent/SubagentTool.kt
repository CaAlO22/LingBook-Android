package com.lingji.app.domain.tool.agent

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ModelConfig
import com.lingji.app.domain.model.toAISettings
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

/**
 * 子代理工具。
 *
 * 当用户配置了辅助模型时，主 Agent 可以把独立子任务委托给子代理：
 * 子代理使用辅助模型运行一个独立的小型工具循环，可用工具为父 Agent 的全部工具（不含 subagent 自身），
 * 完成后只把结论回传给父 Agent，避免中间过程挤占主上下文。
 */
object SubagentTool {
    const val NAME = "subagent"

    private const val MAX_ITERATIONS = 8

    /** OpenAI function-calling 格式的工具声明。 */
    val schema: JsonObject = buildJsonObject {
        "type" to "function"
        "function" to buildJsonObject {
            "name" to NAME
            "description" to
                "把一项相对独立、可自我完成的子任务委托给子代理（使用用户的辅助模型执行）。" +
                "子代理可以调用你拥有的全部工具（不含 subagent 本身），完成后返回结论。" +
                "适合：需要多步检索/整理、与当前主线无关的独立调查。"
            "parameters" to buildJsonObject {
                "type" to "object"
                "properties" to buildJsonObject {
                    "task" to buildJsonObject {
                        "type" to "string"
                        "description" to "交给子代理的完整任务描述，需包含必要的上下文与期望产出"
                    }
                }
                "required" to buildJsonArray { +"task" }
            }
        }
    }

    /**
     * 运行子代理。
     *
     * @param toolSchemas 子代理可用的工具声明（应与 [executeTool] 支持的工具集合一致，且不包含 [NAME]）
     * @param executeTool 工具执行器（传入时复用父 Agent 的同一套执行逻辑，例如笔记作用域注入）
     */
    suspend fun run(
        task: String,
        toolSchemas: JsonArray,
        executeTool: suspend (name: String, params: JsonObject) -> String,
        llmService: LLMService,
        assistant: ModelConfig,
        parentSettings: AISettings
    ): String {
        val gson = Gson()
        val settings = assistant.toAISettings(parentSettings.agentTemperature)
        val messages = mutableListOf(
            ChatMessage("system", SYSTEM_PROMPT),
            ChatMessage("user", task)
        )

        repeat(MAX_ITERATIONS) {
            val response = llmService.chatWithTools(messages, toolSchemas, settings)
            val message = response.choices?.firstOrNull()?.message
                ?: return "子代理没有返回结果。"
            val toolCalls = message.tool_calls

            if (toolCalls.isNullOrEmpty()) {
                val content = message.content?.toString().orEmpty()
                return content.ifBlank { "子代理没有返回内容。" }
            }

            messages.add(
                ChatMessage(
                    role = "assistant",
                    content = message.content,
                    tool_calls = toolCalls
                )
            )
            for (call in toolCalls) {
                val params = runCatching {
                    gson.fromJson(call.function.arguments, JsonObject::class.java)
                }.getOrNull() ?: JsonObject()
                val result = executeTool(call.function.name, params)
                messages.add(
                    ChatMessage(role = "tool", content = result, toolCallId = call.id)
                )
            }
        }

        return "子代理在 $MAX_ITERATIONS 轮工具调用内未能完成任务，请缩小任务范围后重试。"
    }

    private val SYSTEM_PROMPT = """
        你是灵记的子代理，负责独立完成父 Agent 委托的子任务。
        你可以调用提供的工具来读写笔记、搜索内容等；不要调用 subagent 工具（不会提供给你）。

        要求：
        1. 专注于给定任务，不要扩展到无关操作。
        2. 需要数据时先调用工具获取，禁止编造 id 或内容。
        3. 工具返回以 "Error:" 开头时如实记录失败原因，不要假装成功。
        4. 完成后用简洁的中文汇报结果（必要时附上关键 id 与数据类型）。
    """.trimIndent()
}

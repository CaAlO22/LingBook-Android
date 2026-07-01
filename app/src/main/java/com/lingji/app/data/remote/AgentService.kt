package com.lingji.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatResponse
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.domain.tool.ScopedToolRegistry
import com.lingji.app.domain.tool.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent function-calling 循环编排器。
 * 非流式多轮调用 LLM + 工具，直到 LLM 返回最终文本答案。
 */
@Singleton
class AgentService @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val llmService: LLMService,
    private val settingsRepository: SettingsRepository
) {
    private val gson = Gson()

    suspend fun runAgentLoop(
        subjectId: String,
        question: String,
        conversationHistory: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onToolCall: (String) -> Unit = {},
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.getSettingsOnce()
            val scopedRegistry = ScopedToolRegistry(
                toolRegistry, subjectId, ScopedToolRegistry.SINGLE_NOTE_TOOLS
            )
            val tools = scopedRegistry.toOpenAITools()

            val systemPrompt = """你是一个笔记管理助手，可以帮用户管理当前笔记的内容。
你可以通过工具读取和修改笔记的页面、碎片、聚合笔记和学习计划。
请根据用户需求选择合适的工具。操作完成后用简洁的中文总结你做了什么。"""

            val messages = mutableListOf<ChatMessage>(
                ChatMessage("system", systemPrompt)
            )
            for ((q, a) in conversationHistory) {
                messages.add(ChatMessage("user", q))
                messages.add(ChatMessage("assistant", a))
            }
            messages.add(ChatMessage("user", question))

            repeat(MAX_ITERATIONS) {
                val response: ChatResponse = llmService.chatWithTools(messages, tools, settings)
                val message = response.choices?.firstOrNull()?.message

                val toolCalls = message?.tool_calls
                if (toolCalls == null || toolCalls.isEmpty()) {
                    val content = message?.content?.let { if (it is String) it else "" } ?: ""
                    val cleaned = LLMService.sanitizeOutput(content)
                    onToken(cleaned)
                    onComplete(cleaned)
                    return@withContext
                }

                messages.add(message)

                for (tc in toolCalls) {
                    val toolName = tc.function.name
                    val params = try {
                        gson.fromJson(tc.function.arguments, JsonObject::class.java) ?: JsonObject()
                    } catch (e: Exception) {
                        JsonObject()
                    }
                    onToolCall(toolName)
                    val result = scopedRegistry.executeTool(toolName, params)
                    messages.add(ChatMessage(
                        role = "tool",
                        content = result,
                        toolCallId = tc.id
                    ))
                }
            }

            val fallback = "已达到最大工具调用次数限制，请尝试简化请求。"
            onToken(fallback)
            onComplete(fallback)
        } catch (e: Exception) {
            onError(e.message ?: "Agent 执行失败")
        }
    }

    companion object {
        private const val MAX_ITERATIONS = 10
    }
}

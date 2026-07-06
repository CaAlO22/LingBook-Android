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
        priorMessages: List<ChatMessage>,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onToolCall: (toolName: String, args: String, result: String) -> Unit = { _, _, _ -> },
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onAgentMessages: (List<ChatMessage>) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.getSettingsOnce()
            val scopedRegistry = ScopedToolRegistry(
                toolRegistry, subjectId, ScopedToolRegistry.SINGLE_NOTE_TOOLS
            )
            val tools = scopedRegistry.toOpenAITools()

            val systemPrompt = """你是一个笔记管理助手，可以帮用户管理当前笔记的内容。
笔记有两种类型：notebook（笔记本，有页面 page 功能）和 fragment（碎片，只有碎片 fragment 功能，没有页面）。
你可以通过工具读取和修改笔记的页面、碎片、聚合笔记和学习计划。
注意：页面操作（create_page/update_page/delete_page/list_pages/get_page）仅适用于 notebook 类型笔记，碎片笔记不可用。
请根据用户需求选择合适的工具。操作完成后用简洁的中文总结你做了什么。"""

            val messages = mutableListOf<ChatMessage>(
                ChatMessage("system", systemPrompt)
            )
            messages.addAll(priorMessages)
            messages.add(ChatMessage("user", question))

            var budget = MAX_ITERATIONS
            var total = 0
            var hasAssessed = false

            while (budget > 0) {
                total++
                val response: ChatResponse = llmService.chatWithTools(messages, tools, settings)
                val message = response.choices?.firstOrNull()?.message

                val reasoning = message?.reasoning_content
                if (!reasoning.isNullOrBlank()) {
                    onReasoning(reasoning)
                }

                val toolCalls = message?.tool_calls
                if (toolCalls == null || toolCalls.isEmpty()) {
                    val content = message?.content?.let { if (it is String) it else "" } ?: ""
                    val cleaned = LLMService.sanitizeOutput(content)
                    messages.add(ChatMessage("assistant", cleaned))
                    onAgentMessages(messages.toList())
                    onToken(cleaned)
                    onComplete(cleaned)
                    return@withContext
                }

                // 存储带 tool_calls 的 assistant 消息，但剥离 reasoning_content 避免 I2
                messages.add(ChatMessage(
                    role = "assistant",
                    content = message?.content,
                    tool_calls = toolCalls
                ))

                for (tc in toolCalls) {
                    val toolName = tc.function.name
                    val argsString = tc.function.arguments
                    val params = try {
                        gson.fromJson(argsString, JsonObject::class.java) ?: JsonObject()
                    } catch (e: Exception) {
                        JsonObject()
                    }
                    val result = scopedRegistry.executeTool(toolName, params)
                    onToolCall(toolName, argsString, result)
                    messages.add(ChatMessage(
                        role = "tool",
                        content = result,
                        toolCallId = tc.id
                    ))
                }
                budget--

                if (budget == 0 && !hasAssessed && total < MAX_TOTAL_ITERATIONS) {
                    val assessment = AgentLoopAssessor.assess(messages, question, llmService, settings)
                    if (assessment.decision == AgentLoopAssessor.Decision.CONTINUE) {
                        budget = assessment.extraSteps.coerceAtMost(MAX_TOTAL_ITERATIONS - total)
                    }
                    hasAssessed = true
                }
            }

            val fallback = "已达到最大工具调用次数限制，请尝试简化请求。"
            onAgentMessages(messages.toList())
            onToken(fallback)
            onComplete(fallback)
        } catch (e: Exception) {
            onError(e.message ?: "Agent 执行失败")
        }
    }

    companion object {
        private const val MAX_ITERATIONS = 10
        private const val MAX_TOTAL_ITERATIONS = 30
    }
}

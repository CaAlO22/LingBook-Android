package com.lingji.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatResponse
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.domain.tool.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAgentService @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val llmService: LLMService,
    private val settingsRepository: SettingsRepository
) {
    private val gson = Gson()

    suspend fun runHomeAgentLoop(
        question: String,
        priorMessages: List<ChatMessage>,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onToolCall: (toolName: String, args: String, result: String) -> Unit = { _, _, _ -> },
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onAgentMessages: (List<ChatMessage>) -> Unit = {},
        onAssessment: (decision: String, reason: String, extraSteps: Int) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.getSettingsOnce()
            val tools = toolRegistry.toOpenAITools()

            val systemPrompt = """你是灵记的首页助手，兼具「知识问答」与「笔记管理」两种能力，请根据用户意图选择响应方式。

【知识性问题】当用户询问概念、事实、解释、建议、做法等知识性问题时，直接用你的知识回答，不要调用笔记工具去创建笔记。回答应清晰、准确、简洁。回答结束后，主动询问用户是否需要把这部分内容整理保存为一篇笔记。

【笔记管理任务】当用户明确要求创建、查询、修改、搜索或删除笔记时，使用工具完成。笔记有两种类型：notebook（笔记本，有页面 page 功能）和 fragment（碎片，只有碎片 fragment 功能，没有页面）。你可以通过工具读取、搜索、创建、修改和删除用户的笔记（包括主题、页面、碎片、聚合笔记和学习计划）。页面操作（create_page/update_page/delete_page/list_pages/get_page）仅适用于 notebook 类型笔记；fragment 类型笔记没有页面，请改用 list_fragments/search_fragments/add_fragment 等碎片工具读取和管理其内容。当前对话可以访问用户的所有笔记，subject_id 需根据上下文或通过 list_subjects / summarize_all_notes 获取。操作完成后用简洁的中文总结你做了什么。

判断原则：优先判断用户是否在「求知」。只要用户想得到一个答案、而非想「动笔记」，就直接回答并在末尾询问是否写入笔记；只有用户明确表达要管理或记录笔记时，才调用笔记工具。"""

            val messages = mutableListOf(ChatMessage("system", systemPrompt))
            messages.addAll(priorMessages)
            messages.add(ChatMessage("user", question))

            var budget = MAX_ITERATIONS
            var total = 0
            var hasAssessed = false

            while (budget > 0) {
                total++
                // 流式调用：onToken / onReasoning 在调用过程中逐 token 推送
                val response: ChatResponse = llmService.streamChatWithTools(
                    messages, tools, settings, onToken, onReasoning
                )
                val message = response.choices?.firstOrNull()?.message

                val toolCalls = message?.tool_calls
                if (toolCalls == null || toolCalls.isEmpty()) {
                    // 最终回答：content 已在流式过程中推送，这里只需收尾
                    val content = message?.content?.let { if (it is String) it else "" } ?: ""
                    val cleaned = LLMService.sanitizeOutput(content)
                    messages.add(ChatMessage("assistant", cleaned))
                    onAgentMessages(messages.toList())
                    onComplete(cleaned)
                    return@withContext
                }

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
                    val result = toolRegistry.executeTool(toolName, params)
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
                    onAssessment(
                        assessment.decision.name,
                        assessment.reason,
                        assessment.extraSteps
                    )
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
        private const val MAX_ITERATIONS = 20
        private const val MAX_TOTAL_ITERATIONS = 50
    }
}

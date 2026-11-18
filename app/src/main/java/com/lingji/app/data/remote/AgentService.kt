package com.lingji.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatResponse
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.domain.tool.ScopedToolRegistry
import com.lingji.app.domain.tool.ToolRegistry
import com.lingji.app.domain.tool.agent.SubagentTool
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
        onAgentMessages: (List<ChatMessage>) -> Unit = {},
        onAssessment: (decision: String, reason: String, extraSteps: Int) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.getSettingsOnce()
            val scopedRegistry = ScopedToolRegistry(
                toolRegistry, subjectId, ScopedToolRegistry.SINGLE_NOTE_TOOLS
            )
            val scopedTools = scopedRegistry.toOpenAITools()
            // 子代理可用时追加 subagent 工具；子代理继承当前笔记作用域内的全部工具
            val assistant = settings.assistantModel
            val tools = if (assistant != null) {
                JsonArray().apply {
                    addAll(scopedTools)
                    add(SubagentTool.schema)
                }
            } else scopedTools

            val systemPrompt = """你是一个笔记管理助手，可以帮用户管理当前笔记的内容。
笔记有两种类型：notebook（笔记本，有页面 page 功能）和 fragment（碎片，只有碎片 fragment 功能，没有页面）。
你可以通过工具读取和修改笔记的页面、碎片、聚合笔记和学习计划。
注意：页面操作（create_page/update_page/delete_page/list_pages/get_page）仅适用于 notebook 类型笔记。fragment 类型笔记没有页面，请改用 list_fragments/search_fragments/add_fragment 等碎片工具读取和管理其内容。
编辑笔记内容时，使用 edit_replace 工具进行局部查找替换（指定要查找的文本、第几个匹配、替换文本），避免重写整段内容损坏 base64 图片数据。edit_replace 对 notebook 类型需传 page_id，对 fragment 类型默认修改聚合笔记。update_page 仅用于修改页面标题。
如果需要了解笔记内容，请使用 get_page、get_aggregated_note 等工具按需读取，不要凭空猜测。
请根据用户需求选择合适的工具。操作完成后用简洁的中文总结你做了什么。

硬性要求：
1. 用户只是在询问“能不能/可不可以/支不支持”时，先用一句话回答能力，不要直接执行写操作。
2. 任何写操作（新增/修改/删除碎片或页面）都必须先实际调用对应工具；只有工具返回成功后才能说“已完成”。
3. 工具返回值里的 id 是唯一可信的 id，禁止自行编造 UUID 或任何 id。
4. 工具返回以 "Error:" 开头时必须如实告知用户失败原因，不得声称操作已完成。${if (assistant != null) """

【子代理】已配置辅助模型：遇到相对独立、步骤较多的子任务，可调用 subagent 工具委托执行；task 需写清完整上下文与期望产出，主线任务仍由你自己完成。""" else ""}"""

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
                    val result = if (toolName == SubagentTool.NAME && assistant != null) {
                        val task = params.get("task")?.takeIf { it.isJsonPrimitive }?.asString
                        if (task.isNullOrBlank()) {
                            "Error: 缺少 task 参数"
                        } else {
                            SubagentTool.run(
                                task = task,
                                toolSchemas = scopedTools,
                                executeTool = { name, args ->
                                    scopedRegistry.executeTool(name, args)
                                },
                                llmService = llmService,
                                assistant = assistant,
                                parentSettings = settings
                            )
                        }
                    } else {
                        scopedRegistry.executeTool(toolName, params)
                    }
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
        private const val MAX_ITERATIONS = 10
        private const val MAX_TOTAL_ITERATIONS = 30
    }
}

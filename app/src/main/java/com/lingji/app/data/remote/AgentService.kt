package com.lingji.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatResponse
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.domain.provider.ProviderRegistry
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

            // 思考模式下，MiMo 等推理模型要求多轮工具调用完整回传历史 reasoning_content
            val keepReasoning = ProviderRegistry.requiresReasoningHistory(settings.provider, settings.thinkingLevel)
            val systemPrompt = """你是一个笔记管理助手，可以帮用户管理当前笔记的内容。
笔记有三种类型：notebook（笔记本，用 page 工具管理页面）、fragment（碎片，用 fragment 工具管理碎片/聚合笔记）、todo（待办列表，用 todo 工具管理待办项）。三种类型的存储模型互不相通，往 todo 笔记写碎片或页面会得到「写成功但笔记里看不到」的孤立数据，工具会直接拒绝。
你可以通过工具读取和修改笔记的页面、碎片、聚合笔记和学习计划。
注意：页面操作（create_page/update_page/delete_page/list_pages/get_page）仅适用于 notebook 类型笔记。fragment 类型笔记没有页面，请改用 list_fragments/search_fragments/add_fragment 等碎片工具读取和管理其内容。
编辑笔记内容时，使用 edit_replace 工具进行局部查找替换（指定要查找的文本、第几个匹配、替换文本），避免重写整段内容损坏 base64 图片数据。edit_replace 对 notebook 类型需传 page_id，对 fragment 类型默认修改聚合笔记。update_page 仅用于修改页面标题。
如果需要了解笔记内容，请使用 get_page、get_aggregated_note 等工具按需读取，不要凭空猜测。
请根据用户需求选择合适的工具。操作完成后用简洁的中文总结你做了什么。
【TODO 待办笔记】TODO 类型的正文是待办项，只能用 list_todos（列出，返回 {repeat,count,items}，items 每项含 index/id/content/is_done）、add_todo（新增）、update_todo（改文字/勾选/改重复规则）、delete_todo（删除）管理；get_aggregated_note 会立即读回这些改动，且首行用 HTML 注释标注重复规则。edit_replace、fragment 工具、page 工具都不支持 TODO 类型，调用会返回 code=UNSUPPORTED_FOR_SUBJECT_TYPE 的错误。定位待办项可用 todo_id / todo_index（整数序号，最稳）/ todo_text（正文文本）三选一。
【待办重复规则】六档重复（ONCE 一次性 / DAILY 每日 / WEEKDAYS 仅工作日 / WEEKENDS 仅休息日 / INTERVAL 每 N 天 / CUSTOM 指定星期）是整篇待办列表的设置，不是单条待办项的属性，周期切换时整篇一起清空勾选。因此：新增时用 add_todo 的 repeat（可配 week_days=[1..7]、interval_days）一并指定档位；已有笔记改用 update_todo 的 repeat 纠正，只传 repeat 时不必定位任何条目，空列表也能改；用户说「把某一条改成一次性」时，要说明这会改整篇列表，不要承诺单条独立重复；list_todos 的 repeat.auto_reset 表示是否自动清空勾选，reset_pending=true 表示存在上一周期遗留的勾选、下次打开笔记会被清空，不要把那些 is_done 当作本周期已完成。


硬性要求：
1. 用户只是在询问“能不能/可不可以/支不支持”时，先用一句话回答能力，不要直接执行写操作。
2. 任何写操作（新增/修改/删除碎片或页面）都必须先实际调用对应工具；只有工具返回成功后才能说“已完成”。
3. 工具返回值里的 id 是唯一可信的 id，禁止自行编造 UUID 或任何 id。
4. 工具返回以 "Error:" 开头时必须如实告知用户失败原因，不得声称操作已完成。
【ID 与定位】工具返回值里的 id 是唯一可信的 id，禁止自行编造。凡需要 subject_id 的工具都可以改用 subject_title 按笔记标题定位；标题重名会返回 code=AMBIGUOUS_TITLE 和带 index（0、1…）的 candidates，此时把原 subject_title 与候选的 index 一起传（subject_title + subject_index）重试即可选定，不需要抄写 36 位 id，也可以用候选里的 id 作为 subject_id 重试。定位页面可用 page_id / page_index / page_title 三选一。错误返回以 "Error:" 开头，其后是带 code 字段的 JSON：code=UNSUPPORTED_FOR_SUBJECT_TYPE 表示工具与笔记类型不匹配，按 suggestion 换工具，不要重试同一调用；code=AMBIGUOUS_TITLE / AMBIGUOUS_MATCH 表示需从 candidates 里选定一个再重试；code=SUBJECT_INDEX_OUT_OF_RANGE 表示候选序号越界，按返回的 candidates 重新选；code=SUBJECT_NOT_FOUND / OBJECT_NOT_FOUND 表示 id 失效，重新 list_* 获取。
${if (assistant != null) """

【子代理】已配置辅助模型：遇到相对独立、步骤较多的子任务，可调用 subagent 工具委托执行；task 需写清完整上下文与期望产出，主线任务仍由你自己完成。""" else ""}"""

            val messages = mutableListOf<ChatMessage>(
                ChatMessage("system", systemPrompt)
            )
            // 换模型或关闭思考后，剥掉历史里残留的 reasoning_content，
            // 避免把它发给不认识该字段的接口；需要时（MiMo + 思考开启）则原样保留。
            messages.addAll(priorMessages.map { msg ->
                if (keepReasoning || msg.reasoning_content == null) msg
                else msg.copy(reasoning_content = null)
            })
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
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = cleaned,
                        reasoning_content = if (keepReasoning) message?.reasoning_content else null
                    ))
                    onAgentMessages(messages.toList())
                    onComplete(cleaned)
                    return@withContext
                }

                // 历史 assistant 消息：MiMo 等推理模型开启思考时需完整回传 reasoning_content；
                // 其余供应商保持剥离，避免把未知字段发给不认识它的接口（原 I2 问题）。
                messages.add(ChatMessage(
                    role = "assistant",
                    content = message.content,
                    reasoning_content = if (keepReasoning) message.reasoning_content else null,
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

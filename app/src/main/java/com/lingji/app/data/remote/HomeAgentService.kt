package com.lingji.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatResponse
import com.lingji.app.data.remote.models.ContentPart
import com.lingji.app.data.remote.models.ImageContentPart
import com.lingji.app.data.remote.models.ImageUrl
import com.lingji.app.data.remote.models.TextContentPart
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.domain.provider.ProviderRegistry
import com.lingji.app.domain.tool.ToolRegistry
import com.lingji.app.domain.tool.agent.SubagentTool
import com.lingji.app.domain.tool.image.ConversationImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAgentService @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val llmService: LLMService,
    private val settingsRepository: SettingsRepository,
    private val conversationImageStore: ConversationImageStore
) {
    private val gson = Gson()

    suspend fun runHomeAgentLoop(
        question: String,
        priorMessages: List<ChatMessage>,
        images: List<String> = emptyList(),
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
            // 子代理可用时追加 subagent 工具；子代理自身持有除 subagent 外的全部工具
            val allTools = toolRegistry.toOpenAITools()
            val assistant = settings.assistantModel
            val tools = if (assistant != null) {
                JsonArray().apply {
                    addAll(allTools)
                    add(SubagentTool.schema)
                }
            } else allTools

            val imageNumbers = if (images.isNotEmpty()) {
                conversationImageStore.addImages(images)
            } else emptyList()

            val supportsVision = ProviderRegistry.supportsVision(settings.provider, settings.modelName)

            // 思考模式下，MiMo 等推理模型要求多轮工具调用完整回传历史 reasoning_content
            val keepReasoning = ProviderRegistry.requiresReasoningHistory(settings.provider, settings.thinkingLevel)
            val systemPrompt = """你是灵记的首页助手，兼具「知识问答」与「笔记管理」两种能力，请根据用户意图选择响应方式。

【知识性问题】当用户询问概念、事实、解释、建议、做法等知识性问题时，直接用你的知识回答，不要调用笔记工具去创建笔记。回答应清晰、准确、简洁。回答结束后，主动询问用户是否需要把这部分内容整理保存为一篇笔记。

【笔记管理任务】当用户明确要求创建、查询、修改、搜索或删除笔记时，使用工具完成。笔记有三种类型：notebook（笔记本，用 page 工具管理页面）、fragment（碎片，用 fragment 工具管理碎片/聚合笔记）、todo（待办列表，用 todo 工具管理待办项）。三种类型的存储模型互不相通，往 todo 笔记写碎片或页面会得到「写成功但笔记里看不到」的孤立数据，工具会直接拒绝。你可以通过工具读取、搜索、创建、修改和删除用户的笔记（包括主题、页面、碎片、聚合笔记和学习计划）。页面操作（create_page/update_page/delete_page/list_pages/get_page）仅适用于 notebook 类型笔记；fragment 类型笔记没有页面，请改用 list_fragments/search_fragments/add_fragment 等碎片工具读取和管理其内容。当前对话可以访问用户的所有笔记，subject_id 需根据上下文或通过 list_subjects / summarize_all_notes 获取。编辑笔记内容时，使用 edit_replace 工具进行局部查找替换（指定要查找的文本、第几个匹配、替换文本），避免重写整段内容损坏 base64 图片数据。edit_replace 对 notebook 类型需传 page_id，对 fragment 类型默认修改聚合笔记。update_page 仅用于修改页面标题。操作完成后用简洁的中文总结你做了什么。

【图片插入能力】当用户在对话中发送图片时，每张图片会被自动编号（从1开始递增）。你可以使用 insert_img 工具将指定编号的图片插入到笔记本笔记的指定页面。insert_img 参数说明：subject_id（笔记本 ID）、page_id（页面 ID，可通过 list_pages 获取）、image_number（图片编号）。仅支持 notebook 类型笔记。


【TODO 待办笔记】TODO 类型的正文是待办项，只能用 list_todos（列出，返回 index/id/content/is_done）、add_todo（新增）、update_todo（改文字/勾选/取消勾选）、delete_todo（删除）管理；get_aggregated_note 会立即读回这些改动。edit_replace、fragment 工具、page 工具都不支持 TODO 类型，调用会返回 code=UNSUPPORTED_FOR_SUBJECT_TYPE 的错误。定位待办项可用 todo_id / todo_index（整数序号，最稳）/ todo_text（正文文本）三选一。

【ID 与定位】工具返回值里的 id 是唯一可信的 id，禁止自行编造。凡需要 subject_id 的工具都可以改用 subject_title 按笔记标题定位；标题重名会返回 code=AMBIGUOUS_TITLE 和带 index（0、1…）的 candidates，此时把原 subject_title 与候选的 index 一起传（subject_title + subject_index）重试即可选定，不需要抄写 36 位 id，也可以用候选里的 id 作为 subject_id 重试。定位页面可用 page_id / page_index / page_title 三选一。错误返回以 "Error:" 开头，其后是带 code 字段的 JSON：code=UNSUPPORTED_FOR_SUBJECT_TYPE 表示工具与笔记类型不匹配，按 suggestion 换工具，不要重试同一调用；code=AMBIGUOUS_TITLE / AMBIGUOUS_MATCH 表示需从 candidates 里选定一个再重试；code=SUBJECT_INDEX_OUT_OF_RANGE 表示候选序号越界，按返回的 candidates 重新选；code=SUBJECT_NOT_FOUND / OBJECT_NOT_FOUND 表示 id 失效，重新 list_* 获取。

判断原则：优先判断用户是否在「求知」。只要用户想得到一个答案、而非想「动笔记」，就直接回答并在末尾询问是否写入笔记；只有用户明确表达要管理或记录笔记时，才调用笔记工具。

硬性要求：
1. 任何写操作（新增/修改/删除笔记、页面、碎片）都必须先实际调用对应工具；只有工具返回成功后才能说“已完成”。
2. 工具返回值里的 id 是唯一可信的 id，禁止自行编造 UUID 或任何 id。
3. 工具返回以 "Error:" 开头时必须如实告知用户失败原因，不得声称操作已完成。${if (assistant != null) """

【子代理】已配置辅助模型：遇到相对独立、步骤较多的子任务，可调用 subagent 工具委托执行；task 需写清完整上下文与期望产出，主线任务仍由你自己完成。""" else ""}"""

            val messages = mutableListOf(ChatMessage("system", systemPrompt))
            // 换模型或关闭思考后，剥掉历史里残留的 reasoning_content，
            // 避免把它发给不认识该字段的接口；需要时（MiMo + 思考开启）则原样保留。
            messages.addAll(priorMessages.map { msg ->
                if (keepReasoning || msg.reasoning_content == null) msg
                else msg.copy(reasoning_content = null)
            })
            // 构建用户消息：有图片时为多模态，无图片时为纯文本
            if (images.isNotEmpty() && supportsVision) {
                val parts = mutableListOf<ContentPart>(TextContentPart(text = question))
                images.forEach { uri ->
                    parts.add(ImageContentPart(imageUrl = ImageUrl(uri)))
                }
                messages.add(ChatMessage("user", parts))
            } else if (images.isNotEmpty() && !supportsVision) {
                val imageInfo = "编号 ${imageNumbers.first()}-${imageNumbers.last()}"
                messages.add(ChatMessage("user", "$question\n\n[附图片${images.size}张，$imageInfo，当前模型不支持图片识别]"))
            } else {
                messages.add(ChatMessage("user", question))
            }

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

                // 思考模式下按需完整回传历史 reasoning_content（见 ProviderRegistry.requiresReasoningHistory）
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
                                toolSchemas = allTools,
                                executeTool = { name, args -> toolRegistry.executeTool(name, args) },
                                llmService = llmService,
                                assistant = assistant,
                                parentSettings = settings
                            )
                        }
                    } else {
                        toolRegistry.executeTool(toolName, params)
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
        private const val MAX_ITERATIONS = 20
        private const val MAX_TOTAL_ITERATIONS = 50
    }
}

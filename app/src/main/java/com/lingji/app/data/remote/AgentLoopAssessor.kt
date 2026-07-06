package com.lingji.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.domain.model.AISettings

/**
 * Agent 循环监督者：当工具调用达到上限时，发送对话历史给 LLM 做回顾裁决。
 * LLM 返回 structured JSON { decision, reason, extra_steps } 。
 */
object AgentLoopAssessor {
    private val gson = Gson()

    private val REVIEW_PROMPT = """
你是一个 Agent 循环监督者。请仔细检查以下 AI Agent 与用户的完整对话历史，判断当前 Agent 的进展状态。

对话历史中，Agent 通过 function-calling 调用工具来完成任务。你的任务是判断最后的状态：

判定为 CONTINUE 的条件（任一）：
- 任务明显还有未完成的步骤
- Agent 每次工具调用都在朝目标推进（有新信息、新操作）
- 虽然调用次数多，但每次结果不同，任务在进展中

判定为 STOP 的条件（任一）：
- Agent 连续 3 次以上调用相同工具且每次结果高度相似（死循环）
- 用户目标已基本达成，最后几轮在做无关操作
- Agent 反复尝试同一失败操作没有变通（固着行为）

请严格只回复一个 JSON 对象（不要 markdown 代码块包裹），格式如下：
{"decision":"CONTINUE","reason":"简短说明（20字以内）","extra_steps":3}
或
{"decision":"STOP","reason":"简短说明（20字以内）","extra_steps":0}
""".trimIndent()

    data class Assessment(
        val decision: Decision,
        val reason: String,
        val extraSteps: Int
    )

    enum class Decision { CONTINUE, STOP }

    /**
     * 发送对话历史给 LLM 做裁决，返回是否继续及预估额外步数。
     * @param messages 完整对话消息（包括 system / user / assistant / tool）
     * @param previousUserQuestion 原始用户问题（帮助 LLM 理解目标）
     */
    suspend fun assess(
        messages: List<ChatMessage>,
        previousUserQuestion: String,
        llmService: LLMService,
        settings: AISettings
    ): Assessment {
        val reviewMessages = listOf(
            ChatMessage("system", REVIEW_PROMPT),
            ChatMessage("user", "用户原始需求：$previousUserQuestion\n\n以下是完整对话历史，请裁决：\n${summarizeForReview(messages)}")
        )

        // 关闭 thinking：裁决是简单分类任务，避免 reasoning_content 干扰 content 字段
        val assessSettings = settings.copy(enableThinking = false)

        return try {
            val response = llmService.chatWithTools(reviewMessages, JsonArray(), assessSettings)
            val msg = response.choices?.firstOrNull()?.message
            val content = msg?.content?.let { if (it is String) it else "" } ?: ""
            // 某些 thinking 模型把答案放在 reasoning_content 里，做后备
            val reasoning = msg?.reasoning_content ?: ""
            val text = if (content.isBlank()) reasoning else content

            parseAssessment(text)
        } catch (e: Exception) {
            Assessment(Decision.STOP, "评估请求失败: ${e.message}", 0)
        }
    }

    /**
     * 从 LLM 响应文本中提取 JSON 并解析为 Assessment。
     * 依次尝试：直接解析 → markdown 代码块提取 → 首尾花括号截取。
     */
    private fun parseAssessment(text: String): Assessment {
        if (text.isBlank()) return Assessment(Decision.STOP, "评估响应为空", 0)

        // 1. 直接解析（LLM 严格遵循了"只回复 JSON"的指令）
        tryParseJson(text)?.let { return it }

        // 2. 从 markdown 代码块中提取
        val fenceRegex = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?})\\s*\\n?```", RegexOption.IGNORE_CASE)
        fenceRegex.find(text)?.groupValues?.getOrNull(1)?.let { fenced ->
            tryParseJson(fenced)?.let { return it }
        }

        // 3. 截取第一个 '{' 到最后一个 '}' 之间的内容（处理 LLM 在 JSON 前后加说明文字的情况）
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val extracted = text.substring(firstBrace, lastBrace + 1)
            tryParseJson(extracted)?.let { return it }
        }

        return Assessment(Decision.STOP, "评估响应解析失败: ${text.take(80)}", 0)
    }

    private fun tryParseJson(text: String): Assessment? {
        return try {
            val json = gson.fromJson(text.trim(), JsonObject::class.java) ?: return null
            val decisionStr = json.get("decision")?.asString?.uppercase() ?: return null
            val reason = json.get("reason")?.asString ?: ""
            val extraSteps = json.get("extra_steps")?.asInt ?: 0
            Assessment(
                decision = if (decisionStr == "CONTINUE") Decision.CONTINUE else Decision.STOP,
                reason = reason,
                extraSteps = extraSteps.coerceIn(1, 10)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun summarizeForReview(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        for (m in messages) {
            val role = m.role
            val content = (m.content as? String) ?: ""
            val toolCalls = m.tool_calls
            if (toolCalls != null && toolCalls.isNotEmpty()) {
                sb.appendLine("[$role] 调用工具:")
                for (tc in toolCalls) {
                    sb.appendLine("  - ${tc.function.name}(${tc.function.arguments.take(200)})")
                }
            } else if (content.isNotBlank()) {
                val truncated = if (content.length > 300) content.take(300) + "..." else content
                sb.appendLine("[$role] $truncated")
            }
        }
        return sb.toString()
    }
}

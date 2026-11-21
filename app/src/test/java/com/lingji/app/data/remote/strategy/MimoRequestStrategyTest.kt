package com.lingji.app.data.remote.strategy

import com.google.gson.Gson
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ToolCall
import com.lingji.app.data.remote.models.ToolCallFunction
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderRegistry
import com.lingji.app.domain.provider.mimo.MimoConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimoRequestStrategyTest {

    private fun settings(level: ThinkingLevel) = AISettings(
        provider = APIProvider.XIAOMI,
        apiKey = "key",
        modelName = "mimo-v2.6-pro",
        thinkingLevel = level
    )

    private fun build(messages: List<ChatMessage>, level: ThinkingLevel) =
        MimoRequestStrategy().buildChatRequestBody(
            settings = settings(level),
            messages = messages,
            stream = true
        )

    @Test
    fun `disabled thinking sends explicit disabled type`() {
        assertEquals(mapOf("type" to "disabled"), build(listOf(ChatMessage("user", "hello")), ThinkingLevel.OFF).thinking)
    }

    @Test
    fun `enabled thinking sends enabled type`() {
        assertEquals(mapOf("type" to "enabled"), build(listOf(ChatMessage("user", "hello")), ThinkingLevel.ON).thinking)
    }

    @Test
    fun `default thinking level is ON for MiMo`() {
        assertEquals(ThinkingLevel.ON, MimoConfig.defaultThinkingLevel)
        assertEquals(ThinkingLevel.ON, ProviderRegistry.defaultThinkingLevel(APIProvider.XIAOMI))
        assertTrue(ProviderRegistry.defaultThinkingLevel(APIProvider.XIAOMI) in MimoConfig.defaultThinkingLevels)
    }

    @Test
    fun `other providers default to thinking on at high`() {
        // 全局策略：模型配置默认开启思考，有强度档位时取 HIGH（或第二高）
        assertEquals(ThinkingLevel.HIGH, ProviderRegistry.defaultThinkingLevel(APIProvider.OPENAI))
        assertEquals(ThinkingLevel.HIGH, ProviderRegistry.defaultThinkingLevel(APIProvider.DEEPSEEK))
    }

    @Test
    fun `reasoning history is required only for MiMo with thinking on`() {
        assertTrue(ProviderRegistry.requiresReasoningHistory(APIProvider.XIAOMI, ThinkingLevel.ON))
        assertFalse(ProviderRegistry.requiresReasoningHistory(APIProvider.XIAOMI, ThinkingLevel.OFF))
        assertFalse(ProviderRegistry.requiresReasoningHistory(APIProvider.OPENAI, ThinkingLevel.ON))
        assertFalse(ProviderRegistry.requiresReasoningHistory(APIProvider.DEEPSEEK, ThinkingLevel.ON))
        assertTrue(MimoConfig.requiresReasoningHistory)
    }

    /** 模拟多轮工具调用历史：带 tool_calls 的 assistant 消息 + 对应 tool 结果。 */
    @Test
    fun `reasoning content is carried intact into multi turn tool call request`() {
        val reasoning = "先查 list_subjects，确认「每日委托」是 TODO 类型，再调用 add_todo。"
        val history = listOf(
            ChatMessage("user", "给我加一条待办"),
            ChatMessage(
                role = "assistant",
                content = "",
                reasoning_content = reasoning,
                tool_calls = listOf(
                    ToolCall(
                        id = "call_1",
                        function = ToolCallFunction("add_todo", "{\"content\":\"买牛奶\"}")
                    )
                )
            ),
            ChatMessage(role = "tool", content = "{\"id\":\"t1\"}", toolCallId = "call_1")
        )
        val body = build(history, ThinkingLevel.ON)
        // strategy 原样把消息放进请求体，序列化层就会把 reasoning_content 回传
        val json = Gson().toJson(body)
        assertTrue(json, json.contains("\"reasoning_content\":\"$reasoning\""))
        assertTrue(json, json.contains("\"tool_calls\""))
        assertEquals(mapOf("type" to "enabled"), body.thinking)
    }

    @Test
    fun `null reasoning content is not serialized for other providers`() {
        val messages = listOf(
            ChatMessage("user", "hello"),
            ChatMessage(role = "assistant", content = "hi", reasoning_content = null,
                tool_calls = listOf(ToolCall("call_1", function = ToolCallFunction("tool", "{}"))))
        )
        val json = Gson().toJson(build(messages, ThinkingLevel.OFF))
        assertFalse(json, json.contains("reasoning_content"))
    }
}

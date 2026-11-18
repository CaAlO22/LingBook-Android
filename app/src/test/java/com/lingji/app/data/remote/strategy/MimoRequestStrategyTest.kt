package com.lingji.app.data.remote.strategy

import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.ThinkingLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class MimoRequestStrategyTest {

    @Test
    fun `disabled thinking sends explicit disabled type`() {
        val body = MimoRequestStrategy().buildChatRequestBody(
            settings = AISettings(
                provider = APIProvider.XIAOMI,
                apiKey = "key",
                modelName = "mimo-v2.6-pro",
                thinkingLevel = ThinkingLevel.OFF
            ),
            messages = listOf(ChatMessage("user", "hello")),
            stream = true
        )

        assertEquals(mapOf("type" to "disabled"), body.thinking)
    }
}

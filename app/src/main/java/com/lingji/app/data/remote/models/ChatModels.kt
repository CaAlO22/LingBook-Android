package com.lingji.app.data.remote.models

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName

sealed class ContentPart {
    abstract val type: String
}

data class TextContentPart(
    override val type: String = "text",
    val text: String
) : ContentPart()

data class ImageUrl(
    val url: String
)

data class ImageContentPart(
    override val type: String = "image_url",
    @SerializedName("image_url") val imageUrl: ImageUrl
) : ContentPart()

data class ToolCallFunction(
    val name: String,
    val arguments: String
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

data class ChatMessage(
    val role: String,
    val content: Any? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val stream: Boolean = false,
    val thinking: Map<String, String>? = null,
    @SerializedName("enable_thinking") val enableThinking: Boolean? = null,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null,
    val tools: JsonArray? = null
)

data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: ChatError? = null
)

data class Choice(
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    val reasoning_content: String? = null
)

data class ChatError(
    val message: String? = null
)

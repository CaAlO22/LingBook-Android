package com.lingji.app.domain.tool

import com.google.gson.JsonObject

interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject

    suspend fun execute(params: JsonObject): String
}

/** Convert a Tool to OpenAI function-calling format. */
fun Tool.toOpenAITool(): JsonObject = buildJsonObject {
    "type" to "function"
    "function" to buildJsonObject {
        "name" to name
        "description" to description
        "parameters" to parameters
    }
}

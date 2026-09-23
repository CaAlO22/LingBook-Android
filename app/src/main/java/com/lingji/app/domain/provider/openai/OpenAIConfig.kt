package com.lingji.app.domain.provider.openai

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object OpenAIConfig : ProviderConfig {
    override val id = "OPENAI"
    override val displayNameRes = R.string.provider_openai
    override val defaultBaseUrl = "https://api.openai.com/v1"
    override val defaultModelId = "gpt-6-astra"
    override val models = listOf(
        ProviderModel(
            id = "gpt-6-astra",
            name = "GPT-6 Astra",
            description = "最新旗舰，最强推理、视觉与计算机操作能力",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-6-sol",
            name = "GPT-6 Sol",
            description = "前沿智能中端档，编程与 Agent 任务，成本更低",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-6-luna",
            name = "GPT-6 Luna",
            description = "轻量档，适合高并发、低延迟的批量任务",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-5.6-sol",
            name = "GPT-5.6 Sol",
            description = "上一代旗舰，复杂推理与编程",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-5.6-terra",
            name = "GPT-5.6 Terra",
            description = "上一代均衡档，日常生产任务",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-5.6-luna",
            name = "GPT-5.6 Luna",
            description = "上一代轻量档，快速且成本低",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-5.5",
            name = "GPT-5.5",
            description = "Agent 型模型，多工具长流程任务",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-5.5-pro",
            name = "GPT-5.5 Pro",
            description = "高精度推理档，面向科研、金融与法务",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-5.4",
            name = "GPT-5.4",
            description = "通用均衡模型，单价更低",
            supportsVision = true
        )
    )
    override val supportsThinking = false
    override val supportsThinkingField = false
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}

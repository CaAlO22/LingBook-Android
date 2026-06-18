package com.lingji.app.domain.provider.bailian

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object BailianConfig : ProviderConfig {
    const val DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    override val id = "BAILIAN"
    override val displayNameRes = R.string.provider_bailian
    override val defaultBaseUrl = DASHSCOPE_URL
    override val defaultModelId = "qwen3.7-plus"
    override val models = listOf(
        ProviderModel(
            id = "qwen3.7-plus",
            name = "Qwen 3.7 Plus",
            description = "最新千问 Plus，均衡性能与成本，支持 1M 上下文"
        ),
        ProviderModel(
            id = "qwen3.7-max",
            name = "Qwen 3.7 Max",
            description = "最新千问旗舰，最强综合能力"
        ),
        ProviderModel(
            id = "qwen3.6-plus",
            name = "Qwen 3.6 Plus",
            description = "原生多模态，文档理解与真实世界问答能力强",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwen3.6-flash",
            name = "Qwen 3.6 Flash",
            description = "快速经济的多模态模型，视觉与代码能力突出",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwen3.5-plus",
            name = "Qwen 3.5 Plus",
            description = "支持文本/图像/视频输入，效果速度成本均衡",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwen3.5-flash",
            name = "Qwen 3.5 Flash",
            description = "速度快成本低，适合简单任务与高频调用",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwen-plus-latest",
            name = "Qwen Plus",
            description = "千问 Plus 稳定版，长上下文，支持思考模式"
        ),
        ProviderModel(
            id = "qwen-flash-latest",
            name = "Qwen Flash",
            description = "千问 Flash 稳定版，极速响应"
        ),
        ProviderModel(
            id = "qwen-max-latest",
            name = "Qwen Max",
            description = "千问 Max 稳定版，复杂任务首选"
        ),
        ProviderModel(
            id = "qwen3-vl-plus",
            name = "Qwen3 VL Plus",
            description = "视觉理解模型，图像/视频分析与推理",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwen3-vl-flash",
            name = "Qwen3 VL Flash",
            description = "快速视觉理解模型，性价比高",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwq-plus",
            name = "QwQ Plus",
            description = "专注推理的模型，数学与代码能力强"
        ),
        ProviderModel(
            id = "qwen-long-latest",
            name = "Qwen Long",
            description = "1000 万上下文，适合长文档分析与总结"
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}

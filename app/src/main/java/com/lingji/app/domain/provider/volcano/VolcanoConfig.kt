package com.lingji.app.domain.provider.volcano

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object VolcanoConfig : ProviderConfig {
    const val ARK_URL = "https://ark.cn-beijing.volces.com/api/v3"

    override val id = "DOUBAO"
    override val displayNameRes = R.string.provider_volcano
    override val defaultBaseUrl = ARK_URL
    override val defaultModelId = "doubao-seed-2-0-lite-260215"
    override val models = listOf(
        ProviderModel(
            id = "doubao-seed-2-0-lite-260215",
            name = "Doubao Seed 2.0 Lite",
            description = "官方推荐均衡型模型，适合大多数场景"
        ),
        ProviderModel(
            id = "doubao-seed-2-0-pro-260215",
            name = "Doubao Seed 2.0 Pro",
            description = "旗舰级 Agent 通用模型，复杂推理与长链路任务"
        ),
        ProviderModel(
            id = "doubao-seed-2-0-mini-260215",
            name = "Doubao Seed 2.0 Mini",
            description = "低时延、高并发、成本敏感场景"
        ),
        ProviderModel(
            id = "doubao-seed-2-0-code-preview-260215",
            name = "Doubao Seed 2.0 Code",
            description = "面向编程优化，适合 AI 编程工具"
        ),
        ProviderModel(
            id = "doubao-seed-2-0-lite-260428",
            name = "Doubao Seed 2.0 Lite (全模态)",
            description = "轻量全模态，支持文本/图片/语音/视频",
            supportsVision = true
        ),
        ProviderModel(
            id = "doubao-seed-2-0-mini-260428",
            name = "Doubao Seed 2.0 Mini (全模态)",
            description = "轻量均衡全模态，多语种及小语种",
            supportsVision = true
        ),
        ProviderModel(
            id = "doubao-vision-32k-250115",
            name = "豆包 Vision",
            description = "图片/视频/文档视觉理解",
            supportsVision = true
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}

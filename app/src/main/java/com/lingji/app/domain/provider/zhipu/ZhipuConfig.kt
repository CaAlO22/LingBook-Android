package com.lingji.app.domain.provider.zhipu

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object ZhipuConfig : ProviderConfig {
    const val BIGMODEL_URL = "https://open.bigmodel.cn/api/paas/v4"

    override val id = "ZHIPU"
    override val displayNameRes = R.string.provider_zhipu
    override val defaultBaseUrl = BIGMODEL_URL
    override val defaultModelId = "glm-5.3"
    override val models = listOf(
        ProviderModel(
            id = "glm-5.3",
            name = "GLM-5.3",
            description = "最新旗舰，编程与智能体能力最强，1M 上下文（始终开启思考）"
        ),
        ProviderModel(
            id = "glm-5.3-flash",
            name = "GLM-5.3-Flash",
            description = "原生多模态，可理解图片/视频/文件，成本极低",
            supportsVision = true
        ),
        ProviderModel(
            id = "glm-5.3-flashx",
            name = "GLM-5.3-FlashX",
            description = "GLM-5.3-Flash 高速版，推理速度约 200 tokens/s",
            supportsVision = true
        ),
        ProviderModel(
            id = "glm-5.2",
            name = "GLM-5.2",
            description = "复杂长程任务稳定执行，Coding 从代码生成走向工程交付"
        ),
        ProviderModel(
            id = "glm-5.1",
            name = "GLM-5.1",
            description = "长程任务显著提升，可自主工作长达 8 小时"
        ),
        ProviderModel(
            id = "glm-5",
            name = "GLM-5",
            description = "高智能基座，复杂推理与工具调用"
        ),
        ProviderModel(
            id = "glm-5-turbo",
            name = "GLM-5-Turbo",
            description = "Agent 场景优化，复杂长任务执行连续性好"
        ),
        ProviderModel(
            id = "glm-4.7",
            name = "GLM-4.7",
            description = "日常主力，性价比高，通用对话与推理"
        ),
        ProviderModel(
            id = "glm-4.7-flash",
            name = "GLM-4.7-Flash",
            description = "免费文本模型，快速响应，轻量任务"
        ),
        ProviderModel(
            id = "glm-4.6",
            name = "GLM-4.6",
            description = "擅长高级编码、复杂推理与工具调用"
        ),
        ProviderModel(
            id = "glm-4.5",
            name = "GLM-4.5",
            description = "开源 SOTA，编码与 Agent 能力"
        ),
        ProviderModel(
            id = "glm-4.5-air",
            name = "GLM-4.5-Air",
            description = "GLM-4.5 轻量版"
        ),
        ProviderModel(
            id = "glm-4.5v",
            name = "GLM-4.5V",
            description = "视觉理解模型",
            supportsVision = true
        ),
        ProviderModel(
            id = "glm-4.6v",
            name = "GLM-4.6V",
            description = "视觉推理模型，支持 128K 上下文",
            supportsVision = true
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}

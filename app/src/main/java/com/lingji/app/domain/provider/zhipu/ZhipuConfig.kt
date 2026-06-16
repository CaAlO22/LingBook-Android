package com.lingji.app.domain.provider.zhipu

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object ZhipuConfig : ProviderConfig {
    const val BIGMODEL_URL = "https://open.bigmodel.cn/api/paas/v4"

    override val id = "ZHIPU"
    override val displayNameRes = R.string.provider_zhipu
    override val defaultBaseUrl = BIGMODEL_URL
    override val defaultModelId = "glm-4.7"
    override val models = listOf(
        ProviderModel(
            id = "glm-5.1",
            name = "GLM-5.1",
            description = "最新旗舰，编程与长程任务能力最强"
        ),
        ProviderModel(
            id = "glm-5",
            name = "GLM-5",
            description = "高智能基座，复杂推理与工具调用"
        ),
        ProviderModel(
            id = "glm-5-turbo",
            name = "GLM-5-Turbo",
            description = "Agent 场景优化，长链路执行"
        ),
        ProviderModel(
            id = "glm-4.7",
            name = "GLM-4.7",
            description = "官方推荐日常主力，性价比最优"
        ),
        ProviderModel(
            id = "glm-4.7-flash",
            name = "GLM-4.7-Flash",
            description = "快速响应，轻量任务"
        ),
        ProviderModel(
            id = "glm-4.6",
            name = "GLM-4.6",
            description = "上一代旗舰，综合能力强劲"
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
            description = "视觉理解模型"
        ),
        ProviderModel(
            id = "glm-4.6v",
            name = "GLM-4.6V",
            description = "视觉推理模型，支持 128K 上下文"
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}

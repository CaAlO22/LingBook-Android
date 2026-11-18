package com.lingji.app.domain.provider.zhipu

import com.lingji.app.R
import com.lingji.app.domain.model.ThinkingLevel
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
            description = "最新旗舰，编程与智能体能力最强，1M 上下文（始终开启思考）",
            // GLM-5.3 不可关闭思考，reasoning_effort 仅支持 low/high/max
            thinkingLevels = listOf(ThinkingLevel.LOW, ThinkingLevel.HIGH, ThinkingLevel.MAX)
        ),
        ProviderModel(
            id = "glm-5.3-flash",
            name = "GLM-5.3-Flash",
            description = "原生多模态，可理解图片/视频/文件，成本极低",
            supportsVision = true,
            thinkingLevels = listOf(ThinkingLevel.LOW, ThinkingLevel.HIGH, ThinkingLevel.MAX)
        ),
        ProviderModel(
            id = "glm-5.3-flashx",
            name = "GLM-5.3-FlashX",
            description = "GLM-5.3-Flash 高速版，推理速度约 200 tokens/s",
            supportsVision = true,
            thinkingLevels = listOf(ThinkingLevel.LOW, ThinkingLevel.HIGH, ThinkingLevel.MAX)
        ),
        ProviderModel(
            id = "glm-5v-turbo",
            name = "GLM-5V-Turbo",
            description = "多模态 Coding 基座，视觉理解、推理与代码生成，200K 上下文",
            supportsVision = true,
            thinkingLevels = listOf(ThinkingLevel.LOW, ThinkingLevel.HIGH, ThinkingLevel.MAX)
        ),
        ProviderModel(
            id = "glm-5.2",
            name = "GLM-5.2",
            description = "复杂长程任务稳定执行，Coding 从代码生成走向工程交付",
            thinkingLevels = listOf(
                ThinkingLevel.OFF,
                ThinkingLevel.LOW,
                ThinkingLevel.MEDIUM,
                ThinkingLevel.HIGH,
                ThinkingLevel.MAX
            )
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
            id = "glm-4.6v",
            name = "GLM-4.6V",
            description = "视觉推理模型，支持 128K 上下文",
            supportsVision = true
        )
    )
    /** GLM-4.x 只支持 thinking.type 开关。 */
    override val defaultThinkingLevels = listOf(ThinkingLevel.OFF, ThinkingLevel.ON)
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}

package com.lingji.app.domain.provider

import androidx.annotation.StringRes
import com.lingji.app.domain.model.ThinkingLevel

interface ProviderConfig {
    /** 与 [com.lingji.app.domain.model.APIProvider] 枚举名一致，用于持久化。 */
    val id: String

    /** 设置页中显示的中文/英文名称。 */
    @get:StringRes
    val displayNameRes: Int

    /** 默认 Base URL。 */
    val defaultBaseUrl: String

    /** 默认模型 ID。 */
    val defaultModelId: String

    /** 该供应商支持的模型列表。 */
    val models: List<ProviderModel>

    /** 供应商默认的思考档位（自定义模型名或模型未单独声明时使用），空列表表示不支持思考。 */
    val defaultThinkingLevels: List<ThinkingLevel>

    /** 认证 Header 名称，例如 "Authorization" 或 "api-key"。 */
    val authHeaderName: String

    /** 认证 Header 前缀，例如 "Bearer " 或空字符串。 */
    val authHeaderPrefix: String

    /**
     * 新建模型配置时该供应商的默认思考档位。默认 OFF；
     * 默认开启深度思考的供应商（如 MiMo）在各自的 Config 里覆盖，不要在这里写供应商分支。
     */
    val defaultThinkingLevel: ThinkingLevel get() = ThinkingLevel.OFF

    /**
     * 开启思考时，多轮工具调用的历史 assistant 消息是否要完整回传 reasoning_content。
     *
     * 部分推理模型要求把上一轮的思考内容原样带回，否则工具调用多轮后会丢失思考链上下文；
     * 其余供应商默认不回传，避免把未知字段发给不认识该字段的接口。
     */
    val requiresReasoningHistory: Boolean get() = false
}

/** 解析某个模型实际可选的思考档位。 */
fun ProviderConfig.thinkingLevelsFor(modelId: String): List<ThinkingLevel> =
    models.find { it.id == modelId }?.thinkingLevels ?: defaultThinkingLevels

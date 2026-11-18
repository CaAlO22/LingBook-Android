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
}

/** 解析某个模型实际可选的思考档位。 */
fun ProviderConfig.thinkingLevelsFor(modelId: String): List<ThinkingLevel> =
    models.find { it.id == modelId }?.thinkingLevels ?: defaultThinkingLevels

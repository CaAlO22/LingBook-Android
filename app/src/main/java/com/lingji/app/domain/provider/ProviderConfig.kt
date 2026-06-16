package com.lingji.app.domain.provider

import androidx.annotation.StringRes

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

    /** 是否支持深度思考模式。 */
    val supportsThinking: Boolean

    /** 认证 Header 名称，例如 "Authorization" 或 "api-key"。 */
    val authHeaderName: String

    /** 认证 Header 前缀，例如 "Bearer " 或空字符串。 */
    val authHeaderPrefix: String

    /** 是否需要发送 thinking 控制字段。 */
    val supportsThinkingField: Boolean
}

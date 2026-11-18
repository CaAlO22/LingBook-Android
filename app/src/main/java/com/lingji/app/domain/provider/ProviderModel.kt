package com.lingji.app.domain.provider

import com.lingji.app.domain.model.ThinkingLevel

data class ProviderModel(
    val id: String,
    val name: String,
    val description: String = "",
    val supportsVision: Boolean = false,
    /**
     * 该模型可选的思考档位。
     *
     * - `null`：沿用供应商默认档位（[ProviderConfig.defaultThinkingLevels]）
     * - 空列表：该模型不支持思考
     */
    val thinkingLevels: List<ThinkingLevel>? = null
)

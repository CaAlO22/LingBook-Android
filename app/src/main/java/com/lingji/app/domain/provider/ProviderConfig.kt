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
     * 新建模型配置时的默认思考档位：**默认开启思考**。
     *
     * 规则见 [defaultThinkingSelection]：支持 HIGH 就取 HIGH，否则取第二高档位；
     * 只支持开关的供应商得到 ON；不支持思考（默认档位为空）才回落到 OFF。
     * 供应商无需各自覆盖，除非有特殊业务理由。
     */
    val defaultThinkingLevel: ThinkingLevel get() = defaultThinkingLevels.defaultThinkingSelection()

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

/**
 * 新建/切换模型时该模型的默认思考档位。
 *
 * 用模型级档位而不是供应商级：同一供应商下不同模型支持的档位可能不同
 * （例如 GLM-5.3 没有 OFF、Qwen-Long 完全不支持思考）。
 */
fun ProviderConfig.defaultThinkingLevelFor(modelId: String): ThinkingLevel =
    thinkingLevelsFor(modelId).defaultThinkingSelection()

/**
 * 从可用档位中挑一个「默认开启且尽量强」的值：
 *
 * 1. 过滤掉 OFF 后按枚举强度排序（LOW → MEDIUM → HIGH → MAX）
 * 2. 支持 HIGH 就用 HIGH（用户要求的首选档位，也是大多数模型 4 档里的第二高）
 * 3. 没有 HIGH 则取第二高（如 [LOW, MEDIUM, MAX] → MEDIUM）；只有一档就用它
 * 4. 没有任何可开启档位（空列表 / 只有 OFF）→ OFF，表示该模型不支持思考
 */
fun List<ThinkingLevel>.defaultThinkingSelection(): ThinkingLevel {
    val enabled = distinct()
        .filter { it != ThinkingLevel.OFF }
        .sortedBy { it.ordinal }
    if (enabled.isEmpty()) return ThinkingLevel.OFF
    if (ThinkingLevel.HIGH in enabled) return ThinkingLevel.HIGH
    return if (enabled.size >= 2) enabled[enabled.size - 2] else enabled.last()
}

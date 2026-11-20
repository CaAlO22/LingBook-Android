package com.lingji.app.domain.provider

import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.bailian.BailianConfig
import com.lingji.app.domain.provider.deepseek.DeepSeekConfig
import com.lingji.app.domain.provider.hunyuan.HunyuanConfig
import com.lingji.app.domain.provider.kimi.KimiConfig
import com.lingji.app.domain.provider.mimo.MimoConfig
import com.lingji.app.domain.provider.openai.OpenAIConfig
import com.lingji.app.domain.provider.volcano.VolcanoConfig
import com.lingji.app.domain.provider.zhipu.ZhipuConfig

object ProviderRegistry {
    private val configs: Map<APIProvider, ProviderConfig> = mapOf(
        APIProvider.OPENAI to OpenAIConfig,
        APIProvider.DOUBAO to VolcanoConfig,
        APIProvider.XIAOMI to MimoConfig,
        APIProvider.BAILIAN to BailianConfig,
        APIProvider.ZHIPU to ZhipuConfig,
        APIProvider.DEEPSEEK to DeepSeekConfig,
        APIProvider.KIMI to KimiConfig,
        APIProvider.HUNYUAN to HunyuanConfig
    )

    fun config(provider: APIProvider): ProviderConfig =
        configs[provider] ?: OpenAIConfig

    fun configOrNull(provider: APIProvider): ProviderConfig? =
        configs[provider]

    fun allConfigs(): List<ProviderConfig> = APIProvider.entries.map { config(it) }

    /** 查询指定供应商的某个模型是否支持视觉输入；未知模型按不支持处理。 */
    fun supportsVision(provider: APIProvider, modelId: String): Boolean =
        configOrNull(provider)?.models?.find { it.id == modelId }?.supportsVision ?: false

    /** 新建模型配置时该供应商的默认思考档位（保证落在其支持的档位里）。 */
    fun defaultThinkingLevel(provider: APIProvider): ThinkingLevel {
        val cfg = config(provider)
        val preferred = cfg.defaultThinkingLevel
        val levels = cfg.defaultThinkingLevels
        return if (levels.isEmpty() || preferred in levels) preferred else levels.first()
    }

    /**
     * 开启思考时，多轮工具调用的历史 assistant 消息是否要完整回传 reasoning_content。
     * 关闭思考或供应商未声明该能力时一律不回传。
     */
    fun requiresReasoningHistory(provider: APIProvider, thinkingLevel: ThinkingLevel): Boolean =
        thinkingLevel != ThinkingLevel.OFF && configOrNull(provider)?.requiresReasoningHistory == true
}

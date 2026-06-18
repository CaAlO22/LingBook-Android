package com.lingji.app.domain.provider

import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.provider.bailian.BailianConfig
import com.lingji.app.domain.provider.deepseek.DeepSeekConfig
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
        APIProvider.KIMI to KimiConfig
    )

    fun config(provider: APIProvider): ProviderConfig =
        configs[provider] ?: OpenAIConfig

    fun configOrNull(provider: APIProvider): ProviderConfig? =
        configs[provider]

    fun allConfigs(): List<ProviderConfig> = APIProvider.entries.map { config(it) }

    /** 查询指定供应商的某个模型是否支持视觉输入；未知模型按不支持处理。 */
    fun supportsVision(provider: APIProvider, modelId: String): Boolean =
        configOrNull(provider)?.models?.find { it.id == modelId }?.supportsVision ?: false
}

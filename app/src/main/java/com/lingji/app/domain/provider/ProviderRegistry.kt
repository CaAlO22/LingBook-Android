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

    /** 上一次叠加所用的目录快照（按引用比较即可，每次下发都会生成新实例）。 */
    private var overlaidFrom: RemoteModelCatalog.Snapshot? = null
    private var overlaidConfigs: Map<String, ProviderConfig> = emptyMap()

    /**
     * 叠加了云端模型目录的配置代理：只替换 `models`，
     * 并把指向已下线模型的 `defaultModelId` 换成新列表的第一个。
     */
    private class CatalogOverlaidConfig(
        base: ProviderConfig,
        override val models: List<ProviderModel>
    ) : ProviderConfig by base {
        override val defaultModelId: String =
            base.defaultModelId.takeIf { id -> models.any { it.id == id } } ?: models.first().id
    }

    @Synchronized
    private fun overlaid(base: ProviderConfig): ProviderConfig {
        val catalog = RemoteModelCatalog.snapshot
        if (catalog !== overlaidFrom) {
            overlaidConfigs = buildMap {
                for ((_, baseConfig) in configs) {
                    val models = catalog.models[baseConfig.id] ?: continue
                    put(baseConfig.id, CatalogOverlaidConfig(baseConfig, models))
                }
            }
            overlaidFrom = catalog
        }
        return overlaidConfigs[base.id] ?: base
    }

    /** 供应商配置。`models` / `defaultModelId` 已按云端目录叠加，调用点无需感知目录来源。 */
    fun config(provider: APIProvider): ProviderConfig =
        overlaid(configs[provider] ?: OpenAIConfig)

    /**
     * 内置配置（不含云端目录）。
     *
     * 数据库迁移等「结果必须可复现、与远端状态无关」的场景用这个；
     * UI 与请求链路继续用 [config]。
     */
    fun builtInConfig(provider: APIProvider): ProviderConfig = configs[provider] ?: OpenAIConfig

    fun configOrNull(provider: APIProvider): ProviderConfig? =
        configs[provider]?.let(::overlaid)

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

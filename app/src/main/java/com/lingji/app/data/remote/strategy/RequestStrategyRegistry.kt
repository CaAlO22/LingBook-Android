package com.lingji.app.data.remote.strategy

import com.lingji.app.domain.model.APIProvider

object RequestStrategyRegistry {
    fun get(provider: APIProvider): RequestStrategy = when (provider) {
        APIProvider.OPENAI -> OpenAIRequestStrategy()
        APIProvider.DOUBAO -> VolcanoRequestStrategy()
        APIProvider.XIAOMI -> MimoRequestStrategy()
        APIProvider.BAILIAN -> BailianRequestStrategy()
        APIProvider.ZHIPU -> ZhipuRequestStrategy()
        APIProvider.DEEPSEEK -> DeepSeekRequestStrategy()
        APIProvider.KIMI -> KimiRequestStrategy()
        APIProvider.HUNYUAN -> HunyuanRequestStrategy()
    }
}

package com.lingji.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider

@Composable
fun ProviderModelSettings(
    provider: APIProvider,
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    when (provider) {
        APIProvider.OPENAI -> OpenAIModelSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.DOUBAO -> VolcanoModelSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.XIAOMI -> MimoModelSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.BAILIAN -> BailianModelSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.ZHIPU -> ZhipuModelSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.DEEPSEEK -> DeepSeekModelSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.KIMI -> KimiModelSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
    }
}

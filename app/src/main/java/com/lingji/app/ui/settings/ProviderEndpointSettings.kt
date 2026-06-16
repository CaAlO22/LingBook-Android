package com.lingji.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider

@Composable
fun ProviderEndpointSettings(
    provider: APIProvider,
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    when (provider) {
        APIProvider.XIAOMI -> MimoEndpointSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.DOUBAO -> VolcanoEndpointSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.OPENAI -> { /* OpenAI 没有固定 endpoint 预设 */ }
        APIProvider.BAILIAN -> BailianEndpointSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.ZHIPU -> ZhipuEndpointSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.DEEPSEEK -> DeepSeekEndpointSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
        APIProvider.KIMI -> KimiEndpointSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            modifier = modifier
        )
    }
}

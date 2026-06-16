package com.lingji.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.provider.deepseek.DeepSeekConfig
import com.lingji.app.ui.settings.components.ModelDropdown
import com.lingji.app.ui.settings.components.ThinkingSwitch
import com.lingji.app.ui.settings.components.UrlPreset
import com.lingji.app.ui.settings.components.UrlPresetDropdown

@Composable
fun DeepSeekEndpointSettings(
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    UrlPresetDropdown(
        label = stringResource(R.string.deepseek_url_preset),
        presets = listOf(
            UrlPreset(
                label = stringResource(R.string.deepseek_official_url),
                url = DeepSeekConfig.DEEPSEEK_URL
            )
        ),
        customLabel = stringResource(R.string.deepseek_custom_url),
        baseUrl = settings.baseUrl,
        onBaseUrlChange = { onSettingsChange(settings.copy(baseUrl = it)) },
        modifier = modifier
    )
}

@Composable
fun DeepSeekModelSettings(
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    ModelDropdown(
        models = DeepSeekConfig.models,
        selectedModelId = settings.modelName,
        onModelSelected = { onSettingsChange(settings.copy(modelName = it)) },
        modifier = modifier
    )
    ThinkingSwitch(
        checked = settings.enableThinking,
        onCheckedChange = { onSettingsChange(settings.copy(enableThinking = it)) },
        modifier = modifier
    )
}

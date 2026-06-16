package com.lingji.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.provider.kimi.KimiConfig
import com.lingji.app.ui.settings.components.ModelDropdown
import com.lingji.app.ui.settings.components.ThinkingSwitch
import com.lingji.app.ui.settings.components.UrlPreset
import com.lingji.app.ui.settings.components.UrlPresetDropdown

@Composable
fun KimiEndpointSettings(
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    UrlPresetDropdown(
        label = stringResource(R.string.kimi_url_preset),
        presets = listOf(
            UrlPreset(
                label = stringResource(R.string.kimi_moonshot_url),
                url = KimiConfig.MOONSHOT_URL
            )
        ),
        customLabel = stringResource(R.string.kimi_custom_url),
        baseUrl = settings.baseUrl,
        onBaseUrlChange = { onSettingsChange(settings.copy(baseUrl = it)) },
        modifier = modifier
    )
}

@Composable
fun KimiModelSettings(
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    ModelDropdown(
        models = KimiConfig.models,
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

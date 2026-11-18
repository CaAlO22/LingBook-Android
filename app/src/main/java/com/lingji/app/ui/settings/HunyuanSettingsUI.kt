package com.lingji.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.provider.hunyuan.HunyuanConfig
import com.lingji.app.ui.settings.components.UrlPreset
import com.lingji.app.ui.settings.components.UrlPresetDropdown

@Composable
fun HunyuanEndpointSettings(
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    UrlPresetDropdown(
        label = stringResource(R.string.hunyuan_url_preset),
        presets = listOf(
            UrlPreset(
                label = stringResource(R.string.hunyuan_official_url),
                url = HunyuanConfig.TOKENHUB_URL
            )
        ),
        customLabel = stringResource(R.string.hunyuan_custom_url),
        baseUrl = settings.baseUrl,
        onBaseUrlChange = { onSettingsChange(settings.copy(baseUrl = it)) },
        modifier = modifier
    )
}

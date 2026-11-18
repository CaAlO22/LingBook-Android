package com.lingji.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.provider.zhipu.ZhipuConfig
import com.lingji.app.ui.settings.components.UrlPreset
import com.lingji.app.ui.settings.components.UrlPresetDropdown

@Composable
fun ZhipuEndpointSettings(
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    UrlPresetDropdown(
        label = stringResource(R.string.zhipu_url_preset),
        presets = listOf(
            UrlPreset(
                label = stringResource(R.string.zhipu_bigmodel_url),
                url = ZhipuConfig.BIGMODEL_URL
            )
        ),
        customLabel = stringResource(R.string.zhipu_custom_url),
        baseUrl = settings.baseUrl,
        onBaseUrlChange = { onSettingsChange(settings.copy(baseUrl = it)) },
        modifier = modifier
    )
}

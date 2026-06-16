package com.lingji.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.domain.model.AISettings
import com.lingji.app.ui.components.SettingsOutlinedTextField

@Composable
fun OpenAIModelSettings(
    settings: AISettings,
    onSettingsChange: (AISettings) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsOutlinedTextField(
        value = settings.modelName,
        onValueChange = { onSettingsChange(settings.copy(modelName = it)) },
        label = { Text(stringResource(R.string.model_name)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}

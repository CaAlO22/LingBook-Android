package com.lingji.app.ui.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.ui.components.SettingsOutlinedTextField

data class UrlPreset(
    val label: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlPresetDropdown(
    label: String,
    presets: List<UrlPreset>,
    customLabel: String,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = presets.find { it.url == baseUrl.trimEnd('/') }?.label ?: customLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        SettingsOutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onBaseUrlChange(preset.url)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(customLabel) },
                onClick = {
                    onBaseUrlChange("")
                    expanded = false
                }
            )
        }
    }
}

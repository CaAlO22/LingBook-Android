package com.lingji.app.ui.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.ui.components.DropdownMenu
import com.lingji.app.ui.components.DropdownMenuItem
import com.lingji.app.ui.components.SettingsOutlinedTextField

/**
 * 思考程度下拉栏。
 *
 * 只展示 [levels] 中的档位——由供应商 / 模型在 ProviderConfig 中声明，
 * 因此不支持思考的模型不会出现该项，只支持开关的模型只有“关闭 / 开启”。
 */
@Composable
fun ThinkingLevelField(
    levels: List<ThinkingLevel>,
    value: ThinkingLevel,
    onValueChange: (ThinkingLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    if (levels.isEmpty()) return

    Box(modifier = modifier.fillMaxWidth()) {
        SettingsOutlinedTextField(
            value = thinkingLevelLabel(value),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.enable_thinking)) },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = true
        ) {
            levels.forEach { level ->
                DropdownMenuItem(
                    text = { Text(thinkingLevelLabel(level)) },
                    onClick = {
                        onValueChange(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun thinkingLevelLabel(level: ThinkingLevel): String = when (level) {
    ThinkingLevel.OFF -> stringResource(R.string.thinking_level_off)
    ThinkingLevel.ON -> stringResource(R.string.thinking_level_on)
    ThinkingLevel.LOW -> stringResource(R.string.thinking_level_low)
    ThinkingLevel.MEDIUM -> stringResource(R.string.thinking_level_medium)
    ThinkingLevel.HIGH -> stringResource(R.string.thinking_level_high)
    ThinkingLevel.MAX -> stringResource(R.string.thinking_level_max)
}

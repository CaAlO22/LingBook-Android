package com.lingji.app.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.ModelConfig
import com.lingji.app.domain.model.displayName
import com.lingji.app.domain.provider.ProviderModel
import com.lingji.app.ui.components.DropdownMenu
import com.lingji.app.ui.components.DropdownMenuItem
import com.lingji.app.ui.components.SettingsOutlinedTextField
import com.lingji.app.ui.settings.providerDisplayName

/**
 * 模型名称输入框：既支持键盘手动输入模型 ID，也支持从供应商预设列表下拉选择。
 * 下拉入口放在尾部图标上，避免点击输入框时抢占焦点、打断手动输入。
 */
@Composable
fun ModelNameField(
    models: List<ProviderModel>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        SettingsOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.model_name)) },
            placeholder = { Text(stringResource(R.string.model_name_hint)) },
            singleLine = true,
            trailingIcon = {
                if (models.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.model_name_select)
                        )
                    }
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = true
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name)
                            if (model.description.isNotBlank()) {
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onValueChange(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 从用户添加的模型配置中选择一个（用于主模型/辅助模型）。
 *
 * @param noneLabel 传入时在列表顶部提供“不使用”选项，选中后回调空字符串。
 */
@Composable
fun ModelConfigPicker(
    label: String,
    configs: List<ModelConfig>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = configs.find { it.id == selectedId }
    val display = selected?.let { "${providerDisplayName(it.provider)} · ${it.displayName}" }
        ?: noneLabel
        ?: stringResource(R.string.model_not_selected)

    Box(modifier = modifier.fillMaxWidth()) {
        SettingsOutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.model_name_select)
                    )
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = true
        ) {
            if (noneLabel != null) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = {
                        onSelect("")
                        expanded = false
                    }
                )
            }
            configs.forEach { config ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(config.displayName)
                            Text(
                                text = "${providerDisplayName(config.provider)} · ${config.modelName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(config.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 模型配置列表中的单行条目：点击编辑，右侧可删除。 */
@Composable
fun ModelConfigRow(
    config: ModelConfig,
    isPrimary: Boolean,
    isAssistant: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = config.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPrimary) {
                    Text(
                        text = stringResource(R.string.model_badge_primary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isAssistant) {
                    Text(
                        text = stringResource(R.string.model_badge_assistant),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                text = "${providerDisplayName(config.provider)} · ${config.modelName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 模型配置中的 API Key 输入框，带明文/掩码切换。 */
@Composable
fun ModelApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf(false) }
    SettingsOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.api_key)) },
        modifier = modifier,
        singleLine = true,
        visualTransformation = if (revealed) {
            androidx.compose.ui.text.input.VisualTransformation.None
        } else {
            ModelApiKeyMaskTransformation
        },
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.cd_toggle_api_key_visibility)
                )
            }
        }
    )
}

private object ModelApiKeyMaskTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val masked = if (raw.isEmpty()) {
            ""
        } else if (raw.length <= 8) {
            "*".repeat(raw.length)
        } else {
            "${raw.take(3)}****${raw.takeLast(4)}"
        }
        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = masked.length
            override fun transformedToOriginal(offset: Int): Int = raw.length
        }
        return androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString(masked),
            offsetMapping
        )
    }
}

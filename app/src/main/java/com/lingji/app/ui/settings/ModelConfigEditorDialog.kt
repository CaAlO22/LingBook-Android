package com.lingji.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.ModelConfig
import com.lingji.app.domain.model.generateId
import com.lingji.app.domain.provider.ProviderRegistry
import com.lingji.app.domain.provider.defaultThinkingSelection
import com.lingji.app.domain.provider.thinkingLevelsFor
import com.lingji.app.ui.components.DropdownMenu
import com.lingji.app.ui.components.DropdownMenuItem
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.SettingsOutlinedTextField
import com.lingji.app.ui.settings.components.ModelApiKeyField
import com.lingji.app.ui.settings.components.ModelNameField
import com.lingji.app.ui.settings.components.ThinkingLevelField

/**
 * 添加 / 编辑一条模型配置。
 *
 * 供应商切换时会自动填充该供应商的默认 Base URL 与默认模型，模型名称仍可手动修改。
 *
 * @param initial 传入已有配置为编辑，传 null 为新增。
 */
@Composable
fun ModelConfigEditorDialog(
    initial: ModelConfig?,
    onDismiss: () -> Unit,
    onSave: (ModelConfig) -> Unit
) {
    val startProvider = initial?.provider ?: APIProvider.OPENAI
    val startConfig = ProviderRegistry.config(startProvider)

    var provider by remember { mutableStateOf(startProvider) }
    var baseUrl by remember {
        mutableStateOf(initial?.baseUrl?.takeIf { it.isNotBlank() } ?: startConfig.defaultBaseUrl)
    }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var modelName by remember {
        mutableStateOf(initial?.modelName?.takeIf { it.isNotBlank() } ?: startConfig.defaultModelId)
    }
    // 别名默认跟随模型名称，用户手动修改后不再自动覆盖
    var alias by remember {
        mutableStateOf(
            initial?.alias?.takeIf { it.isNotBlank() }
                ?: initial?.modelName?.takeIf { it.isNotBlank() }
                ?: startConfig.defaultModelId
        )
    }
    var thinkingLevel by remember {
        mutableStateOf(
            initial?.thinkingLevel
                ?: ProviderRegistry.defaultThinkingLevel(startProvider, startConfig.defaultModelId)
        )
    }
    var providerExpanded by remember { mutableStateOf(false) }

    val config = ProviderRegistry.config(provider)
    val thinkingLevels = config.thinkingLevelsFor(modelName)
    val canSave = baseUrl.isNotBlank() && modelName.isNotBlank()

    // 切换供应商 / 模型后，若原档位不再受支持则回落到该模型的默认档位：
    // 默认开启思考，优先 HIGH、其次第二高（见 defaultThinkingSelection）。
    LaunchedEffect(thinkingLevels) {
        if (thinkingLevel !in thinkingLevels) {
            thinkingLevel = thinkingLevels.defaultThinkingSelection()
        }
    }

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.add_model else R.string.edit_model
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SettingsOutlinedTextField(
                        value = providerDisplayName(provider),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.provider)) },
                        trailingIcon = {
                            IconButton(onClick = { providerExpanded = !providerExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false },
                        matchAnchorWidth = true
                    ) {
                        APIProvider.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(providerDisplayName(entry)) },
                                onClick = {
                                    provider = entry
                                    val target = ProviderRegistry.config(entry)
                                    val previousName = modelName
                                    baseUrl = target.defaultBaseUrl
                                    modelName = target.defaultModelId
                                    if (alias.isBlank() || alias == previousName) {
                                        alias = target.defaultModelId
                                    }
                                    // 换供应商时按新供应商默认模型的档位重置（默认开启思考，优先 HIGH）
                                    thinkingLevel = ProviderRegistry.defaultThinkingLevel(entry, target.defaultModelId)
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                ProviderEndpointSettings(
                    provider = provider,
                    settings = AISettings(
                        provider = provider,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        modelName = modelName,
                        thinkingLevel = thinkingLevel
                    ),
                    onSettingsChange = { baseUrl = it.baseUrl }
                )

                SettingsOutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.base_url)) },
                    singleLine = true
                )

                ModelApiKeyField(value = apiKey, onValueChange = { apiKey = it })

                ModelNameField(
                    models = config.models,
                    value = modelName,
                    onValueChange = { newName ->
                        // 别名未手动修改时跟随模型名称
                        if (alias.isBlank() || alias == modelName) alias = newName
                        modelName = newName
                    }
                )

                SettingsOutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.model_alias)) },
                    placeholder = { Text(stringResource(R.string.model_alias_hint)) },
                    singleLine = true
                )

                if (thinkingLevels.isNotEmpty()) {
                    ThinkingLevelField(
                        levels = thinkingLevels,
                        value = thinkingLevel,
                        onValueChange = { thinkingLevel = it }
                    )
                }
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.save),
                enabled = canSave,
                onClick = {
                    onSave(
                        ModelConfig(
                            id = initial?.id ?: generateId(),
                            alias = alias.trim().ifBlank { modelName.trim() },
                            provider = provider,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            modelName = modelName.trim(),
                            thinkingLevel = thinkingLevel,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            )
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

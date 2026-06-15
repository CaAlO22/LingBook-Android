package com.lingji.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.MimoModel
import com.lingji.app.domain.model.MimoPresets
import com.lingji.app.ui.components.SettingsOutlinedTextField
import com.lingji.app.ui.viewmodel.SubjectViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SubjectViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    var expanded by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = viewModel.importSubject(uri)
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.import_success) else context.getString(R.string.import_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI 配置
            SettingsCard(title = stringResource(R.string.ai_settings)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    SettingsOutlinedTextField(
                        value = settings.provider.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.provider)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        APIProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                    viewModel.saveSettings(
                                        applyProviderPreset(settings, provider)
                                    )
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                if (settings.provider == APIProvider.XIAOMI) {
                    MimoUrlPresets(
                        baseUrl = settings.baseUrl,
                        onBaseUrlChange = { viewModel.saveSettings(settings.copy(baseUrl = it)) }
                    )
                }
                SettingsTextField(
                    value = settings.baseUrl,
                    onValueChange = { viewModel.saveSettings(settings.copy(baseUrl = it)) },
                    label = stringResource(R.string.base_url)
                )
                SettingsTextField(
                    value = settings.apiKey,
                    onValueChange = { viewModel.saveSettings(settings.copy(apiKey = it)) },
                    label = stringResource(R.string.api_key)
                )
                if (settings.provider == APIProvider.XIAOMI) {
                    MimoModelDropdown(
                        selectedModelId = settings.modelName,
                        onModelSelected = { viewModel.saveSettings(settings.copy(modelName = it)) }
                    )
                    ThinkingSwitch(
                        checked = settings.enableThinking,
                        onCheckedChange = { viewModel.saveSettings(settings.copy(enableThinking = it)) }
                    )
                } else {
                    SettingsTextField(
                        value = settings.modelName,
                        onValueChange = { viewModel.saveSettings(settings.copy(modelName = it)) },
                        label = stringResource(R.string.model_name)
                    )
                }
                Button(
                    onClick = {
                        testResult = ""
                        viewModel.testConnection { testResult = it.second }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.test_connection))
                }
                Button(
                    onClick = {
                        val base64 = context.getEmbeddedTestImageBase64()
                        if (base64 != null) {
                            testResult = context.getString(R.string.multimodal_test_pending)
                            viewModel.testMultimodalConnection(base64) { testResult = it.second }
                        } else {
                            testResult = "无法加载测试图片"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.test_multimodal_connection))
                }
                if (testResult.isNotBlank()) {
                    Text(
                        text = testResult,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (testResult.contains("成功") || testResult.contains("ok", ignoreCase = true)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // 应用
            SettingsCard(title = stringResource(R.string.app_settings)) {
                Button(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Text(stringResource(R.string.import_ling_json), modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    SettingsOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MimoUrlPresets(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit
) {
    val selected = when (baseUrl.trimEnd('/')) {
        MimoPresets.PAY_AS_YOU_GO_URL -> stringResource(R.string.mimo_pay_as_you_go)
        MimoPresets.TOKEN_PLAN_URL -> stringResource(R.string.mimo_token_plan)
        else -> stringResource(R.string.mimo_custom_url)
    }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        SettingsOutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.mimo_url_preset)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimo_pay_as_you_go)) },
                onClick = {
                    onBaseUrlChange(MimoPresets.PAY_AS_YOU_GO_URL)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimo_token_plan)) },
                onClick = {
                    onBaseUrlChange(MimoPresets.TOKEN_PLAN_URL)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimo_custom_url)) },
                onClick = {
                    onBaseUrlChange("")
                    expanded = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MimoModelDropdown(
    selectedModelId: String,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = MimoPresets.MODELS.find { it.id == selectedModelId }
        ?: MimoPresets.MODELS.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        SettingsOutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.model_name)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MimoPresets.MODELS.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name)
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ThinkingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.enable_thinking),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.enable_thinking_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun applyProviderPreset(settings: AISettings, provider: APIProvider): AISettings {
    val defaults = when (provider) {
        APIProvider.OPENAI -> "https://api.openai.com/v1" to "gpt-4o"
        APIProvider.DOUBAO -> "https://ark.cn-beijing.volces.com/api/v3" to "doubao-pro"
        APIProvider.XIAOMI -> MimoPresets.PAY_AS_YOU_GO_URL to MimoPresets.MODELS.first().id
    }
    return settings.copy(
        provider = provider,
        baseUrl = defaults.first,
        modelName = defaults.second,
        enableThinking = if (provider == APIProvider.XIAOMI) settings.enableThinking else false
    )
}

private fun Context.getEmbeddedTestImageBase64(): String? {
    val bitmap = BitmapFactory.decodeResource(resources, R.drawable.multimodal_test_image) ?: return null
    val scaled = scaleBitmap(bitmap, 1200)
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
    val bytes = output.toByteArray()
    return "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}

private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap
    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (width > height) {
        newWidth = maxDimension
        newHeight = (maxDimension / ratio).toInt()
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * ratio).toInt()
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

package com.lingji.app.ui.screens

import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.data.remote.UpdateSource
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ModelConfig
import com.lingji.app.ui.components.DeleteConfirmDialog
import com.lingji.app.ui.settings.ModelConfigEditorDialog
import com.lingji.app.ui.settings.components.ModelConfigPicker
import com.lingji.app.ui.settings.components.ModelConfigRow
import com.lingji.app.ui.settings.sync.SyncSettingsContent
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.CheckMessage
import com.lingji.app.ui.viewmodel.SubjectViewModel
import com.lingji.app.ui.viewmodel.UpdateViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SubjectViewModel,
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    // 信息岛：内容绘制到导航栏之下，用底部留白让末尾设置项不被小白条遮挡。
    val density = LocalDensity.current
    val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var testResult by remember { mutableStateOf("") }
    var assistantTestResult by remember { mutableStateOf("") }
    var showModelEditor by remember { mutableStateOf(false) }
    var editingModelConfig by remember { mutableStateOf<ModelConfig?>(null) }
    var deletingModelConfig by remember { mutableStateOf<ModelConfig?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCheckingUpdate by updateViewModel.isChecking.collectAsState()
    val updateCheckMessage by updateViewModel.checkMessage.collectAsState()

    androidx.compose.runtime.LaunchedEffect(updateCheckMessage) {
        val msg = updateCheckMessage ?: return@LaunchedEffect
        val text = when (msg) {
            CheckMessage.AlreadyLatest -> context.getString(R.string.update_already_latest)
            is CheckMessage.Failed -> context.getString(R.string.update_check_failed, msg.reason)
        }
        val length = if (msg is CheckMessage.Failed) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context, text, length).show()
        updateViewModel.clearCheckMessage()
    }

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = NotoSerifCJKsc,
                            fontSize = 40.sp,
                            letterSpacing = (-0.03).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveSettings(settings)
                            Toast.makeText(
                                context,
                                R.string.settings_saved,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + navBarBottom
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 模型配置：用户添加的模型列表
            SettingsCard(title = stringResource(R.string.model_configs_title)) {
                Text(
                    text = stringResource(R.string.model_configs_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (uiState.modelConfigs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.model_configs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.modelConfigs.forEach { config ->
                            ModelConfigRow(
                                config = config,
                                isPrimary = config.id == settings.primaryModelId,
                                isAssistant = config.id == settings.assistantModelId,
                                onEdit = {
                                    editingModelConfig = config
                                    showModelEditor = true
                                },
                                onDelete = { deletingModelConfig = config }
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        editingModelConfig = null
                        showModelEditor = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.add_model),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // 主模型配置
            SettingsCard(title = stringResource(R.string.primary_model_config)) {
                if (uiState.modelConfigs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.primary_model_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ModelConfigPicker(
                        label = stringResource(R.string.primary_model),
                        configs = uiState.modelConfigs,
                        selectedId = settings.primaryModelId,
                        onSelect = { viewModel.selectPrimaryModel(it) }
                    )
                    AgentTemperatureSetting(
                        value = settings.agentTemperature,
                        onChange = { viewModel.saveSettings(settings.copy(agentTemperature = it)) }
                    )
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
            }

            // 辅助模型配置
            SettingsCard(title = stringResource(R.string.assistant_model_config)) {
                Text(
                    text = stringResource(R.string.assistant_model_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (uiState.modelConfigs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.primary_model_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ModelConfigPicker(
                        label = stringResource(R.string.assistant_model),
                        configs = uiState.modelConfigs,
                        selectedId = settings.assistantModelId,
                        noneLabel = stringResource(R.string.assistant_model_none),
                        onSelect = { viewModel.selectAssistantModel(it) }
                    )
                    Button(
                        onClick = {
                            assistantTestResult = ""
                            viewModel.testAssistantModelConnection { assistantTestResult = it.second }
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
                                assistantTestResult = context.getString(R.string.multimodal_test_pending)
                                viewModel.testAssistantMultimodalConnection(base64) { assistantTestResult = it.second }
                            } else {
                                assistantTestResult = "无法加载测试图片"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.test_multimodal_connection))
                    }
                    if (assistantTestResult.isNotBlank()) {
                        Text(
                            text = assistantTestResult,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (assistantTestResult.contains("成功") || assistantTestResult.contains("ok", ignoreCase = true)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // 信息岛：Agent 能力说明（首页 / 笔记内两处 Agent 的工具与任务）
            SettingsCard(title = stringResource(R.string.agent_guide_title)) {
                Text(
                    text = stringResource(R.string.agent_guide_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AgentGuideSection(
                    title = stringResource(R.string.agent_guide_home_title),
                    lines = listOf(
                        stringResource(R.string.agent_guide_home_entry),
                        stringResource(R.string.agent_guide_home_tools),
                        stringResource(R.string.agent_guide_home_tasks)
                    )
                )
                AgentGuideSection(
                    title = stringResource(R.string.agent_guide_note_title),
                    lines = listOf(
                        stringResource(R.string.agent_guide_note_entry),
                        stringResource(R.string.agent_guide_note_tools),
                        stringResource(R.string.agent_guide_note_tasks)
                    )
                )
                Text(
                    text = stringResource(R.string.agent_guide_subagent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // 云同步
            SettingsCard(title = stringResource(R.string.sync_settings)) {
                SyncSettingsContent()
            }

            SettingsCard(title = stringResource(R.string.app_settings)) {
                Button(
                    onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Text(stringResource(R.string.import_ling_json), modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        scope.launch {
                            val ok = if (text.isNullOrBlank()) {
                                false
                            } else {
                                viewModel.importSubject(text)
                            }
                            Toast.makeText(
                                context,
                                when {
                                    text.isNullOrBlank() -> context.getString(R.string.clipboard_empty)
                                    ok -> context.getString(R.string.import_success)
                                    else -> context.getString(R.string.import_failed)
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Text(stringResource(R.string.import_from_clipboard), modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = { updateViewModel.checkForUpdate() },
                    enabled = !isCheckingUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    if (isCheckingUpdate) {
                        Text(stringResource(R.string.checking_update))
                    } else {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Text(stringResource(R.string.check_update), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                // 更新检查源切换
                val updateSource by updateViewModel.updateSource.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.update_source_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = updateSource == UpdateSource.GITEE,
                        onClick = { updateViewModel.setUpdateSource(UpdateSource.GITEE) },
                        label = { Text("Gitee", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = updateSource == UpdateSource.GITHUB,
                        onClick = { updateViewModel.setUpdateSource(UpdateSource.GITHUB) },
                        label = { Text("GitHub", style = MaterialTheme.typography.labelSmall) }
                    )
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

            if (showModelEditor) {
                ModelConfigEditorDialog(
                    initial = editingModelConfig,
                    onDismiss = { showModelEditor = false },
                    onSave = { config ->
                        viewModel.saveModelConfig(config)
                        showModelEditor = false
                    }
                )
            }

            deletingModelConfig?.let { config ->
                val isSelected = config.id == settings.primaryModelId || config.id == settings.assistantModelId
                DeleteConfirmDialog(
                    title = stringResource(R.string.delete_model),
                    text = stringResource(
                        if (isSelected) R.string.model_config_delete_confirm_selected
                        else R.string.model_config_delete_confirm,
                        config.modelName
                    ),
                    onConfirm = {
                        viewModel.deleteModelConfig(config.id)
                        deletingModelConfig = null
                    },
                    onDismiss = { deletingModelConfig = null }
                )
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
private fun AgentGuideSection(
    title: String,
    lines: List<String>
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AgentTemperatureSetting(
    value: Double,
    onChange: (Double) -> Unit
) {
    // 拖动过程中只更新本地值，松手后再落库，避免每次移动都写设置表。
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.agent_temperature),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1f", local),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange((local * 10).roundToInt() / 10.0) },
            valueRange = 0f..1.5f,
            steps = 14,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.agent_temperature_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
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

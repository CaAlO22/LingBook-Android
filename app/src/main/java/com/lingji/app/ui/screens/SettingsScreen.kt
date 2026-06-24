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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.HorizontalSwipeAction
import com.lingji.app.domain.provider.ProviderRegistry
import com.lingji.app.ui.components.SettingsOutlinedTextField
import com.lingji.app.ui.settings.ProviderEndpointSettings
import com.lingji.app.ui.settings.ProviderModelSettings
import com.lingji.app.ui.settings.providerDisplayName
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.CheckMessage
import com.lingji.app.ui.viewmodel.SubjectViewModel
import com.lingji.app.ui.viewmodel.UpdateViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SubjectViewModel,
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    var expanded by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
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
                        value = providerDisplayName(settings.provider),
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
                                text = { Text(providerDisplayName(provider)) },
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
                ProviderEndpointSettings(
                    provider = settings.provider,
                    settings = settings,
                    onSettingsChange = { viewModel.saveSettings(it) }
                )
                SettingsTextField(
                    value = settings.baseUrl,
                    onValueChange = { viewModel.saveSettings(settings.copy(baseUrl = it)) },
                    label = stringResource(R.string.base_url)
                )
                ApiKeyField(
                    value = settings.apiKey,
                    onValueChange = { viewModel.saveSettings(settings.copy(apiKey = it)) },
                    onCopy = {
                        val key = settings.apiKey
                        if (key.isBlank()) {
                            Toast.makeText(context, R.string.api_key_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("API Key", key))
                            Toast.makeText(context, R.string.api_key_copied, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                ProviderModelSettings(
                    provider = settings.provider,
                    settings = settings,
                    onSettingsChange = { viewModel.saveSettings(it) }
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

            // 应用
            SettingsCard(title = stringResource(R.string.editor_gesture_settings)) {
                HorizontalSwipeSetting(
                    current = settings.horizontalSwipeAction,
                    onChange = { viewModel.saveSettings(settings.copy(horizontalSwipeAction = it)) }
                )
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

@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    onCopy: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    SettingsOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.api_key)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (revealed) {
            androidx.compose.ui.text.input.VisualTransformation.None
        } else {
            ApiKeyMaskTransformation
        },
        trailingIcon = {
            Row {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.cd_toggle_api_key_visibility)
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_api_key)
                    )
                }
            }
        }
    )
}

private object ApiKeyMaskTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val masked = maskApiKey(raw)
        // 由于掩码长度与原文不一致，使用映射将光标固定到掩码末尾。
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

private fun maskApiKey(key: String): String {
    if (key.isEmpty()) return ""
    if (key.length <= 8) return "*".repeat(key.length)
    val prefix = key.take(3)
    val suffix = key.takeLast(4)
    return "$prefix****$suffix"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorizontalSwipeSetting(
    current: HorizontalSwipeAction,
    onChange: (HorizontalSwipeAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (current) {
        HorizontalSwipeAction.NONE -> stringResource(R.string.horizontal_swipe_none)
        HorizontalSwipeAction.TOGGLE_PREVIEW -> stringResource(R.string.horizontal_swipe_toggle_preview)
        HorizontalSwipeAction.CHANGE_PAGE -> stringResource(R.string.horizontal_swipe_change_page)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        SettingsOutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.horizontal_swipe_action)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            HorizontalSwipeAction.entries.forEach { action ->
                val text = when (action) {
                    HorizontalSwipeAction.NONE -> stringResource(R.string.horizontal_swipe_none)
                    HorizontalSwipeAction.TOGGLE_PREVIEW -> stringResource(R.string.horizontal_swipe_toggle_preview)
                    HorizontalSwipeAction.CHANGE_PAGE -> stringResource(R.string.horizontal_swipe_change_page)
                }
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onChange(action)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun applyProviderPreset(settings: AISettings, provider: APIProvider): AISettings {
    val config = ProviderRegistry.config(provider)
    return settings.copy(
        provider = provider,
        baseUrl = config.defaultBaseUrl,
        modelName = config.defaultModelId,
        enableThinking = if (config.supportsThinking) settings.enableThinking else false
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

package com.lingji.app.ui.settings.sync

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lingji.app.R
import com.lingji.app.ui.components.SettingsOutlinedTextField
import com.lingji.app.ui.viewmodel.ScanUiState
import com.lingji.app.ui.viewmodel.SyncViewModel
import com.lingji.app.util.copyPlainText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页「云同步」分组内容。
 * 配置即改即存；服务器不可用时同步静默失败，不影响 App 其他功能。
 */
@Composable
fun SyncSettingsContent(
    viewModel: SyncViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val status by viewModel.status.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val scanState by viewModel.scanState.collectAsState()

    Text(
        text = stringResource(R.string.sync_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 4.dp)
    )

    SyncCategoryRow(
        title = stringResource(R.string.sync_enable_notes),
        hint = stringResource(R.string.sync_enable_notes_hint),
        checked = config.syncNotes,
        onCheckedChange = { viewModel.setSyncNotes(it) }
    )

    SyncCategoryRow(
        title = stringResource(R.string.sync_enable_models),
        hint = stringResource(R.string.sync_enable_models_hint),
        checked = config.syncModels,
        onCheckedChange = { viewModel.setSyncModels(it) }
    )

    SyncCategoryRow(
        title = stringResource(R.string.sync_enable_tokens),
        hint = stringResource(R.string.sync_enable_tokens_hint),
        checked = config.syncTokens,
        onCheckedChange = { viewModel.setSyncTokens(it) }
    )

    SettingsOutlinedTextField(
        value = config.host,
        onValueChange = {
            viewModel.setHost(it)
            viewModel.clearScanResult()
        },
        label = { Text(stringResource(R.string.sync_host)) },
        placeholder = { Text("192.168.1.100") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        singleLine = true
    )

    SettingsOutlinedTextField(
        value = config.port,
        onValueChange = {
            viewModel.setPort(it)
            viewModel.clearScanResult()
        },
        label = { Text(stringResource(R.string.sync_port)) },
        placeholder = { Text("8765") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        singleLine = true
    )

    ScanLanSection(
        state = scanState,
        onScan = { viewModel.scanLan() }
    )

    SettingsOutlinedTextField(
        value = config.token,
        onValueChange = { viewModel.setToken(it) },
        label = { Text(stringResource(R.string.sync_token)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        singleLine = true
    )

    // 上次同步状态
    val statusText = when {
        status.inProgress -> stringResource(R.string.sync_syncing)
        status.lastSyncAt > 0L -> {
            val time = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                .format(Date(status.lastSyncAt))
            stringResource(R.string.sync_last_format, time) +
                if (status.message.isNotBlank()) "　${status.message}" else ""
        }
        else -> stringResource(R.string.sync_never)
    }
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = if (status.message.startsWith("同步失败")) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        modifier = Modifier.padding(top = 12.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.testConnection() },
            enabled = !testing,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.sync_test_connection))
        }
        Button(
            onClick = { viewModel.syncNow() },
            enabled = config.anyEnabled && !status.inProgress,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (status.inProgress) stringResource(R.string.sync_syncing) else stringResource(R.string.sync_now))
        }
    }

    testResult?.let { (success, detail) ->
        Text(
            text = if (success) {
                stringResource(R.string.sync_connect_success)
            } else {
                stringResource(R.string.sync_connect_failed, detail)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    DeviceIdRow(
        deviceId = config.deviceId,
        onChange = { viewModel.setDeviceId(it) }
    )
}

/**
 * 局域网扫描：一个按钮 + 结果提示。
 *
 * 扫描期间按钮文案变倒计时，再次点击即取消；命中后地址已由 ViewModel 写进上面的表单。
 */
@Composable
private fun ScanLanSection(
    state: ScanUiState,
    onScan: () -> Unit
) {
    OutlinedButton(
        onClick = onScan,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (state is ScanUiState.Scanning) {
                stringResource(R.string.sync_scanning_format, state.remainingSeconds)
            } else {
                stringResource(R.string.sync_scan_lan)
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
    }

    val message: Pair<String, Boolean>? = when (state) {
        is ScanUiState.Found ->
            stringResource(R.string.sync_scan_found_format, state.host, state.port) to true
        ScanUiState.NotFound -> stringResource(R.string.sync_scan_not_found) to false
        ScanUiState.NoLocalNetwork -> stringResource(R.string.sync_scan_no_network) to false
        else -> null
    }
    message?.let { (text, success) ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * 本设备 ID：展示 + 复制 + 修改。
 *
 * 多设备共用同一份灵记的方式，就是把另一台设备的 ID 粘贴到这里。
 */
@Composable
private fun DeviceIdRow(
    deviceId: String,
    onChange: (String) -> Boolean
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sync_device_id),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { context.copyPlainText(deviceId) }) {
                Text(
                    text = stringResource(R.string.sync_device_id_copy),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            TextButton(onClick = { editing = true }) {
                Text(
                    text = stringResource(R.string.sync_device_id_edit),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Text(
            text = deviceId,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.sync_device_id_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (editing) {
        DeviceIdEditDialog(
            current = deviceId,
            onDismiss = { editing = false },
            onSave = { newId ->
                editing = false
                val changed = onChange(newId)
                Toast.makeText(
                    context,
                    if (changed) R.string.sync_device_id_changed else R.string.sync_device_id_unchanged,
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

/** 单行同步开关：左侧标题 + 说明，右侧 Switch。 */
@Composable
private fun SyncCategoryRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

package com.lingji.app.ui.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lingji.app.R
import com.lingji.app.ui.components.SettingsOutlinedTextField
import com.lingji.app.ui.viewmodel.SyncViewModel
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

    Text(
        text = stringResource(R.string.sync_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.sync_enable),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = config.enabled,
            onCheckedChange = { viewModel.setEnabled(it) }
        )
    }

    SettingsOutlinedTextField(
        value = config.host,
        onValueChange = { viewModel.setHost(it) },
        label = { Text(stringResource(R.string.sync_host)) },
        placeholder = { Text("192.168.1.100") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        singleLine = true
    )

    SettingsOutlinedTextField(
        value = config.port,
        onValueChange = { viewModel.setPort(it) },
        label = { Text(stringResource(R.string.sync_port)) },
        placeholder = { Text("8765") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        singleLine = true
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
            enabled = config.enabled && !status.inProgress,
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
}

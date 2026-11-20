package com.lingji.app.ui.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.data.sync.DeviceIdFormat
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.SettingsOutlinedTextField

/**
 * 修改设备 ID 弹窗。
 *
 * 主要用于「把另一台设备的 ID 粘贴过来」，因此不限制必须符合派生格式，
 * 只校验长度与字符集；保存后由调用方触发一次全量拉取。
 */
@Composable
fun DeviceIdEditDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var input by remember { mutableStateOf(current) }
    val normalized = DeviceIdFormat.normalize(input)
    val error = when {
        normalized.isEmpty() -> stringResource(R.string.sync_device_id_empty)
        !DeviceIdFormat.isValid(normalized) -> stringResource(R.string.sync_device_id_invalid)
        else -> null
    }

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_device_id_edit_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.sync_device_id_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                SettingsOutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.sync_device_id)) },
                    placeholder = { Text("LJ-XXXX-XXXX-XXXX") },
                    singleLine = true
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.save),
                enabled = error == null && normalized != current,
                onClick = { onSave(normalized) }
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

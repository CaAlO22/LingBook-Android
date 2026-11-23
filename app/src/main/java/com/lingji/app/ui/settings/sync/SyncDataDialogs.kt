package com.lingji.app.ui.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
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
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton

/** 「覆盖云端数据」二次确认。 */
@Composable
fun OverwriteCloudDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_overwrite_cloud_title)) },
        text = { Text(stringResource(R.string.sync_overwrite_cloud_confirm)) },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.sync_overwrite_cloud_action),
                onClick = onConfirm,
                isDestructive = true
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

/** 从云端恢复的两种模式。 */
private enum class RestoreMode { OVERWRITE_LOCAL, MERGE_LOCAL }

/**
 * 「从云端恢复数据」弹窗：先选模式，再确认执行。
 *
 * 覆盖本地是破坏性操作，确认按钮用错误色；合并本地只补充较新的云端记录。
 */
@Composable
fun RestoreFromCloudDialog(
    onDismiss: () -> Unit,
    onConfirm: (mergeLocal: Boolean) -> Unit
) {
    var mode by remember { mutableStateOf(RestoreMode.OVERWRITE_LOCAL) }
    val mergeLocal = mode == RestoreMode.MERGE_LOCAL

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_restore_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == RestoreMode.OVERWRITE_LOCAL,
                        onClick = { mode = RestoreMode.OVERWRITE_LOCAL },
                        label = { Text(stringResource(R.string.sync_restore_mode_overwrite)) }
                    )
                    FilterChip(
                        selected = mode == RestoreMode.MERGE_LOCAL,
                        onClick = { mode = RestoreMode.MERGE_LOCAL },
                        label = { Text(stringResource(R.string.sync_restore_mode_merge)) }
                    )
                }
                Text(
                    text = stringResource(
                        if (mergeLocal) R.string.sync_restore_mode_merge_hint
                        else R.string.sync_restore_mode_overwrite_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mergeLocal) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = stringResource(R.string.sync_restore_failed_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.sync_restore_action),
                onClick = { onConfirm(mergeLocal) },
                isDestructive = !mergeLocal
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

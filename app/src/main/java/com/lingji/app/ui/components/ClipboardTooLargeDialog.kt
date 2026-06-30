package com.lingji.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lingji.app.R

/**
 * 剪贴板内容过大提示弹窗。
 *
 * 当导出文本超过剪贴板容量上限时弹出，引导用户改用文件导出或 PDF 导出。
 */
@Composable
fun ClipboardTooLargeDialog(onDismiss: () -> Unit) {
    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_too_large_title)) },
        text = { Text(stringResource(R.string.copy_too_large)) },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.i_know),
                onClick = onDismiss
            )
        }
    )
}

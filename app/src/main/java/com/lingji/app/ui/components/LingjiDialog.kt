package com.lingji.app.ui.components

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.lingji.app.R

/**
 * 灵记风格弹窗容器。
 *
 * 使用 [Dialog] + [Card] 实现，替代 Material3 [AlertDialog]，
 * 统一应用内弹窗的视觉风格：暖白表面、圆角、细边框、底部操作按钮。
 */
@Composable
fun LingjiDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    )
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        // 弹窗是独立 Window：Compose 在 SDK >= 31 时不设置 softInputMode，
        // 系统对 wrap_content 浮窗默认按 ADJUST_PAN 处理（弹窗整体被平移），
        // 会与输入框获得焦点后的自动滚动互相拉扯；小窗中系统还会在弹出输入法时压缩窗口高度，
        // 两者叠加就会出现上下反复跳动、最终停在被平移后的位置。
        // 这里强制改为 ADJUST_RESIZE：窗口随输入法收缩，只由正文区滚动跟随焦点。
        val dialogWindow = LocalView.current.findDialogWindow()
        SideEffect {
            if (dialogWindow != null) {
                val attrs = dialogWindow.attributes
                val adjust = attrs.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                if (adjust != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
                    dialogWindow.setSoftInputMode(
                        (attrs.softInputMode and
                            WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    )
                    // setSoftInputMode 只更新 Window 内部记录，需要重新下发属性才会应用到窗口。
                    dialogWindow.setAttributes(dialogWindow.attributes)
                }
            }
        }

        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                title?.let {
                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall
                        ) {
                            it()
                        }
                    }
                }

                text?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // fill = false：窗口（分屏 / 小窗 / 键盘弹出后）高度不足时，
                            // 正文区收缩到剩余空间内滚动，避免弹窗超出窗口导致整窗被夹住来回跳动。
                            .weight(1f, fill = false)
                            .heightIn(max = 380.dp)
                    ) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium
                        ) {
                            it()
                        }
                    }
                }

                if (confirmButton != null || dismissButton != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        if (dismissButton != null && confirmButton != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * 从弹窗内容视图向上找到承载它的 Dialog 窗口。
 *
 * Compose 的弹窗内容视图就是 `DialogLayout`（实现了 [DialogWindowProvider]），
 * 但为兼容宿主层级差异，这里沿父视图链查找。
 */
private fun View.findDialogWindow(): Window? {
    var node: View? = this
    while (node != null) {
        if (node is DialogWindowProvider) return node.window
        node = node.parent as? View
    }
    return null
}

/**
 * 灵记风格弹窗的默认确认按钮。
 *
 * @param text 按钮文字。
 * @param onClick 点击回调。
 * @param enabled 是否可点击。
 * @param isDestructive 是否为破坏性操作（如删除），使用错误色强调。
 */
@Composable
fun LingjiDialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val containerColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 灵记风格弹窗的默认取消/次要按钮。
 */
@Composable
fun LingjiDialogDismissButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 通用删除/清空二次确认弹窗。
 *
 * 基于 [LingjiDialog] 封装，确认按钮固定使用破坏性（红色）样式，
 * 用于所有删除、清空类操作的二次确认，统一交互体验。
 *
 * @param title 弹窗标题。
 * @param text 确认提示正文。
 * @param onConfirm 点击确认按钮回调（执行删除后由调用方清除 pending 状态）。
 * @param onDismiss 取消/点外部关闭回调（由调用方清除 pending 状态）。
 * @param confirmText 确认按钮文案，默认「删除」；清空类操作可传「清空」等。
 */
@Composable
fun DeleteConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.delete)
) {
    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = confirmText,
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



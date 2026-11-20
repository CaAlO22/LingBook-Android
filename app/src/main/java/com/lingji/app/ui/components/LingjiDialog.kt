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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.lingji.app.R

/**
 * 弹窗正文滚动区的当前高度（px）。
 *
 * 弹出输入法 / 小窗适配时正文视口随让出的键盘高度连续收缩；
 * 输入框据此在视口变化后重新滚动，保证聚焦的输入框最终完整可见。
 * 不在弹窗内时为 null。
 */
val LocalDialogBodyHeight = staticCompositionLocalOf<State<Int>?> { null }

/**
 * Activity 窗口观测到的 ime 高度（px）。
 *
 * 部分 ROM 的弹窗窗口收不到 ime insets，由 Activity 根节点调用 [ObserveImeHeight] 统一发布，
 * 弹窗在 [LingjiDialog] 中消费它让出键盘高度。
 */
object ImeHeightState {
    var bottomPx by mutableIntStateOf(0)
}

/** 置于 Activity 根节点：发布当前 ime 高度供弹窗消费。 */
@Composable
fun ObserveImeHeight() {
    val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
    SideEffect { ImeHeightState.bottomPx = bottom }
}

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
        val bodyHeight = remember { mutableIntStateOf(0) }
        // 弹窗是独立 Window：部分 ROM 的 ADJUST_RESIZE 按“窗口与键盘是否重叠”压缩 wrap_content 浮窗——
        // 压缩后底边离开键盘便放开压缩，回弹后又压到键盘上再被压缩，形成布局频率的扩缩极限环
        // （小窗的任务窗口同走此路径），正文滚动被反复夹断、焦点框被推出视口。
        // 故强制 ADJUST_NOTHING 让系统不介入弹窗窗口；键盘高度由卡片按 ime 高度让出，
        // 窗口高度只随内容单调变化，反馈回路被切断。只在窗口创建时设置一次，避免重复下发属性引起 relayout 脉冲。
        val dialogWindow = LocalView.current.findDialogWindow()
        LaunchedEffect(dialogWindow) {
            if (dialogWindow != null) {
                val attrs = dialogWindow.attributes
                val adjust = attrs.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                if (adjust != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
                    dialogWindow.setSoftInputMode(
                        (attrs.softInputMode and
                            WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    )
                    // setSoftInputMode 只更新 Window 内部记录，需要重新下发属性才会应用到窗口。
                    dialogWindow.setAttributes(dialogWindow.attributes)
                }
            }
        }
        // 部分 ROM 的弹窗窗口收不到 ime insets，高度改由 Activity 根节点统一观测（ObserveImeHeight）。
        val imeBottom = with(LocalDensity.current) { ImeHeightState.bottomPx.toDp() }

        Card(
            modifier = modifier
                .padding(bottom = imeBottom)
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
                            // 正文区收缩到剩余空间内滚动，避免弹窗超出窗口。
                            .weight(1f, fill = false)
                            .heightIn(max = 380.dp)
                            .onSizeChanged { bodyHeight.intValue = it.height }
                    ) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                            LocalDialogBodyHeight provides bodyHeight
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



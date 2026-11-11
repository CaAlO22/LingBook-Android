package com.lingji.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * 所有 AI 发送类输入框统一的回车行为（与横竖屏无关）：
 * - 回车：换行，交给多行输入框默认处理，避免打断输入法组合。
 * - Ctrl + 回车：发送。
 *
 * @param onSend 发送回调（Ctrl+回车触发）。
 */
fun Modifier.enterSendBehavior(onSend: () -> Unit): Modifier = composed {
    // composed 块不会随外部回调变化重建，用 rememberUpdatedState 保证始终调用最新的发送逻辑。
    val latestOnSend by rememberUpdatedState(onSend)
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
        // 普通回车不拦截：多行输入框会自行插入换行。
        if (!event.isCtrlPressed) return@onPreviewKeyEvent false
        latestOnSend()
        true
    }
}

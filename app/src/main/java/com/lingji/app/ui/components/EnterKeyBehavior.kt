package com.lingji.app.ui.components

import android.content.res.Configuration
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 统一的回车键行为：
 * - 竖屏：回车换行（交给默认处理，不拦截）。
 * - 横屏：回车发送，Ctrl/Shift+回车换行（手动插入换行以保证跨输入法一致）。
 *
 * @param value 当前输入框内容，横屏 Ctrl/Shift+回车时据此插入换行。
 * @param onValueChange 内容变更回调。
 * @param onSend 发送回调（横屏下普通回车触发）。
 */
fun Modifier.enterSendBehavior(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit
): Modifier = composed {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
        if (isLandscape) {
            if (event.isCtrlPressed || event.isShiftPressed) {
                onValueChange(insertNewline(value))
                true
            } else {
                onSend()
                true
            }
        } else {
            // 竖屏：回车换行，交给默认处理
            false
        }
    }
}

private fun insertNewline(value: TextFieldValue): TextFieldValue {
    val newText = value.text.replaceRange(
        value.selection.start, value.selection.end, "\n"
    )
    val cursor = value.selection.start + 1
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}

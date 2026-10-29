package com.lingji.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal enum class CursorDirection { LEFT, RIGHT, UP, DOWN }

internal fun Key.toCursorDirection(): CursorDirection? = when (this) {
    Key.DirectionLeft -> CursorDirection.LEFT
    Key.DirectionRight -> CursorDirection.RIGHT
    Key.DirectionUp -> CursorDirection.UP
    Key.DirectionDown -> CursorDirection.DOWN
    else -> null
}

/**
 * 让输入框内的方向键只作用于光标，而不是被焦点系统当作 2D 焦点导航。
 *
 * 背景：外接键盘或输入法派发 DPAD 事件时，[androidx.compose.foundation.text.BasicTextField]
 * 并不总会消费方向键。事件一旦未被消费，就会落到 Compose 焦点系统的默认处理，
 * 表现为光标不动、焦点跳到相邻按钮（发送按钮、标签页、删除按钮等）。
 *
 * 这里在 Preview 阶段就把方向键拦下来并自行计算落点：
 * - 左右：按字符移动（Ctrl 按下时按词移动）
 * - 上下：借助 [TextLayoutResult] 保持水平位置移动到相邻行，跨出文本首尾时移到首/尾
 * - Shift 按下：以另一端为锚点扩展选区
 *
 * @param value 输入框当前内容，用于读取文本与选区。
 * @param onValueChange 回写新的选区。
 * @param layoutResult 提供最近一次文本布局结果，为空时上下移动退化为文本首尾。
 */
@Composable
fun Modifier.arrowKeyCursorBehavior(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    layoutResult: () -> TextLayoutResult? = { null }
): Modifier {
    val latestValue = rememberUpdatedState(value)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val latestLayoutResult = rememberUpdatedState(layoutResult)

    return this.onPreviewKeyEvent { event ->
        val direction = event.key.toCursorDirection() ?: return@onPreviewKeyEvent false
        // KeyUp 同样消费，避免抬起事件继续参与默认的焦点导航。
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true

        val current = latestValue.value
        val target = resolveCursorTarget(
            value = current,
            direction = direction,
            byWord = event.isCtrlPressed,
            layout = latestLayoutResult.value.invoke()
        )
        val selection = resolveSelection(current.selection, target, event.isShiftPressed)
        if (selection != current.selection) {
            latestOnValueChange.value(current.copy(selection = selection))
        }
        // 无论光标是否真的移动都消费事件，确保焦点不会被带走。
        true
    }
}

internal fun resolveCursorTarget(
    value: TextFieldValue,
    direction: CursorDirection,
    byWord: Boolean,
    layout: TextLayoutResult?
): Int {
    val text = value.text
    val caret = value.selection.end.coerceIn(0, text.length)
    return when (direction) {
        CursorDirection.LEFT -> when {
            byWord && layout != null && caret > 0 -> layout.getWordBoundary(caret).start
            caret <= 0 -> 0
            else -> text.offsetByCodePoints(caret, -1)
        }

        CursorDirection.RIGHT -> when {
            byWord && layout != null && caret < text.length -> layout.getWordBoundary(caret).end
            caret >= text.length -> text.length
            else -> text.offsetByCodePoints(caret, 1)
        }

        CursorDirection.UP -> moveToAdjacentLine(caret, text.length, -1, layout)
        CursorDirection.DOWN -> moveToAdjacentLine(caret, text.length, 1, layout)
    }
}

/**
 * 保持当前光标的水平位置，移动到上一行（step = -1）或下一行（step = 1）。
 * 已经处于首行/末行时直接把光标送到文本开头/结尾，与常见编辑器一致。
 */
private fun moveToAdjacentLine(
    caret: Int,
    textLength: Int,
    step: Int,
    layout: TextLayoutResult?
): Int {
    if (layout == null) return if (step < 0) 0 else textLength
    val line = layout.getLineForOffset(caret)
    if (step < 0 && line <= 0) return 0
    if (step > 0 && line >= layout.lineCount - 1) return textLength

    val targetLine = line + step
    val x = layout.getHorizontalPosition(caret, usePrimaryDirection = true)
    // 取目标行顶部偏下 1px 的位置，避免落在行缝隙上被吸附到别的行。
    val y = layout.getLineTop(targetLine) + 1f
    return layout.getOffsetForPosition(Offset(x, y))
}

internal fun resolveSelection(
    selection: TextRange,
    target: Int,
    extend: Boolean
): TextRange {
    if (!extend) return TextRange(target)
    // 光标焦点端始终是 selection.end（target 也由 end 推导），锚点因此固定为 start：
    // target 仍在区间内即为收缩，越出区间即为扩张，两者都不会让选区整体平移。
    val anchor = selection.start
    return TextRange(minOf(anchor, target), maxOf(anchor, target))
}

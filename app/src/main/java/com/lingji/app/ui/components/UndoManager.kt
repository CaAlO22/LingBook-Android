package com.lingji.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 简单的撤销/重做管理器。
 *
 * 通过 [update] 记录新值，[undo] / [redo] 在状态中导航。
 * 支持通过 [reset] 从外部强制重置当前值并清空历史。
 */
class UndoManager<T>(
    initialValue: T,
    private val maxHistory: Int = 50
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    var value by mutableStateOf(initialValue)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    /**
     * 重置当前值并清空撤销/重做历史。
     */
    fun reset(newValue: T) {
        value = newValue
        undoStack.clear()
        redoStack.clear()
        updateStates()
    }

    /**
     * 记录新值。若与当前值相同则忽略；否则压入撤销栈并清空重做栈。
     */
    fun update(newValue: T) {
        if (newValue == value) return
        undoStack.addLast(value)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
        value = newValue
        updateStates()
    }

    /**
     * 撤销到上一个记录值。返回当前值（撤销后的值），无可撤销时返回 null。
     */
    fun undo(): T? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeLast()
        redoStack.addLast(value)
        value = previous
        updateStates()
        return value
    }

    /**
     * 重做。返回当前值（重做后的值），无可重做时返回 null。
     */
    fun redo(): T? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(value)
        value = next
        updateStates()
        return value
    }

    private fun updateStates() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}

@Composable
fun <T> rememberUndoManager(
    initialValue: T,
    maxHistory: Int = 50
): UndoManager<T> = remember { UndoManager(initialValue, maxHistory) }

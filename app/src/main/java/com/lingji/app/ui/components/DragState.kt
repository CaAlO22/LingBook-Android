package com.lingji.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.lingji.app.domain.model.HomeItem

class DragState {
    var isDragging by mutableStateOf(false)
        private set
    var draggedItem by mutableStateOf<HomeItem?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var dragStartPos by mutableStateOf(Offset.Zero)
        private set
    var dropTargetFolderId by mutableStateOf<String?>(null)
        private set
    var reorderTargetIndex by mutableStateOf(-1)
        private set

    fun startDrag(item: HomeItem, startPos: Offset) {
        isDragging = true
        draggedItem = item
        dragStartPos = startPos
        dragOffset = Offset.Zero
        dropTargetFolderId = null
        reorderTargetIndex = -1
    }

    fun updateDrag(delta: Offset) {
        dragOffset += delta
    }

    fun setDropTarget(folderId: String?) {
        dropTargetFolderId = folderId
    }

    fun setReorderTarget(index: Int) {
        reorderTargetIndex = index
    }

    fun endDrag(): DragResult {
        val result = if (dropTargetFolderId != null) {
            DragResult.MoveToFolder(dropTargetFolderId!!)
        } else if (reorderTargetIndex >= 0) {
            DragResult.Reorder(reorderTargetIndex)
        } else {
            DragResult.None
        }
        reset()
        return result
    }

    fun cancelDrag() {
        reset()
    }

    private fun reset() {
        isDragging = false
        draggedItem = null
        dragOffset = Offset.Zero
        dragStartPos = Offset.Zero
        dropTargetFolderId = null
        reorderTargetIndex = -1
    }
}

sealed interface DragResult {
    data object None : DragResult
    data class MoveToFolder(val folderId: String) : DragResult
    data class Reorder(val targetIndex: Int) : DragResult
}

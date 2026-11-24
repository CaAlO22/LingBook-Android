package com.lingji.app.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.lingji.app.domain.model.HomeItem
import kotlin.math.abs
import kotlin.math.max

class DragState {
    var isDragging by mutableStateOf(false)
        private set
    var draggedItem by mutableStateOf<HomeItem?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var dragStartPos by mutableStateOf(Offset.Zero)
        private set
    /** 按下时手指在卡片内的相对位置，用于让拖拽浮层贴合手指而不是以左上角对齐。 */
    var dragGrabOffset by mutableStateOf(Offset.Zero)
        private set
    var dropTargetFolderId by mutableStateOf<String?>(null)
        private set
    /** 插入位置：0..n，n 表示放到最后一项之后。 */
    var insertIndex by mutableStateOf(-1)
        private set
    /** 插入位置标记条（网格本地坐标）。 */
    var insertionRect by mutableStateOf<Rect?>(null)
        private set
    var reorderHoverId by mutableStateOf<String?>(null)
        private set

    fun startDrag(item: HomeItem, startPos: Offset, grabOffset: Offset = Offset.Zero) {
        isDragging = true
        draggedItem = item
        dragStartPos = startPos
        dragGrabOffset = grabOffset
        dragOffset = Offset.Zero
        dropTargetFolderId = null
        insertIndex = -1
        insertionRect = null
        reorderHoverId = null
    }

    fun updateDrag(delta: Offset) {
        dragOffset += delta
    }

    fun setDropTarget(folderId: String?) {
        dropTargetFolderId = folderId
    }

    fun setInsertPosition(index: Int, hoverId: String?, marker: Rect?) {
        insertIndex = index
        reorderHoverId = hoverId
        insertionRect = marker
    }

    fun clearInsertPosition() {
        insertIndex = -1
        insertionRect = null
        reorderHoverId = null
    }

    fun clearDropAndInsert() {
        dropTargetFolderId = null
        clearInsertPosition()
    }

    fun endDrag(): DragResult {
        val result = when {
            dropTargetFolderId != null -> DragResult.MoveToFolder(dropTargetFolderId!!)
            insertIndex >= 0 -> DragResult.Reorder(insertIndex)
            else -> DragResult.None
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
        dragGrabOffset = Offset.Zero
        dropTargetFolderId = null
        insertIndex = -1
        insertionRect = null
        reorderHoverId = null
    }
}

sealed interface DragResult {
    data object None : DragResult
    data class MoveToFolder(val folderId: String) : DragResult
    data class Reorder(val targetIndex: Int) : DragResult
}

/**
 * 信息岛：统一的拖拽落点结算（首页 / 文件夹页共用）。
 *
 * 插入下标是"放进去之前"的位置，因此原位置在目标之前时需要回退一位；
 * 落在自身前后一格时视为原地不动，避免无意义的写库与闪烁。
 */
fun applyDragResult(
    result: DragResult,
    draggedItem: HomeItem?,
    items: List<HomeItem>,
    onMoveToFolder: (HomeItem, String) -> Unit,
    onReorder: (List<HomeItem>) -> Unit
) {
    val item = draggedItem ?: return
    when (result) {
        is DragResult.MoveToFolder -> onMoveToFolder(item, result.folderId)
        is DragResult.Reorder -> {
            val mutable = items.toMutableList()
            val from = mutable.indexOf(item)
            if (from < 0) return
            val target = result.targetIndex.coerceIn(0, mutable.size)
            val adjusted = if (target > from) target - 1 else target
            if (adjusted == from) return
            mutable.removeAt(from)
            mutable.add(adjusted.coerceIn(0, mutable.size), item)
            onReorder(mutable)
        }
        DragResult.None -> Unit
    }
}

/**
 * 信息岛：网格拖拽命中判定（首页与文件夹页共用）。
 *
 * 规则：
 * 1. 指针落在文件夹卡片中心区域（内缩 15%）→ 移入该文件夹（支持文件夹嵌套）；
 * 2. 否则取中心距离最近的卡片，按指针相对其中心的主轴方向决定"插到它前面还是后面"；
 * 3. 插入位置用 0..itemCount 表示，因此首位之前、末位之后这两个特殊位置都能命中并显示标记，
 *    横竖屏列数变化也不会出现无法插入的边界。
 *
 * @param position 手指位置，需与 [LazyGridState.layoutInfo] 的 offset 处于同一坐标系
 * @param itemCount 可排序项数量（不含网格首位的"新建"卡片）
 * @param canDropInto 目标文件夹是否接受移入（用于阻止文件夹拖进自身或子孙）
 * @param gapPx 网格间距（像素），用于把插入标记画在卡片间隙中央
 */
fun handleGridDragHitTest(
    position: Offset,
    gridState: LazyGridState,
    itemCount: Int,
    dragState: DragState,
    canDropInto: (folderId: String) -> Boolean,
    gapPx: Float
) {
    val visible = gridState.layoutInfo.visibleItemsInfo.filter { it.isSortableItem() }
    if (visible.isEmpty() || itemCount <= 0) {
        dragState.clearDropAndInsert()
        return
    }

    // 1) 文件夹中心区域：移入文件夹
    for (info in visible) {
        val key = info.key as? String ?: continue
        if (!key.startsWith("folder_")) continue
        val folderId = key.removePrefix("folder_")
        if (!canDropInto(folderId)) continue
        val insetX = info.size.width * 0.15f
        val insetY = info.size.height * 0.15f
        val left = info.offset.x + insetX
        val top = info.offset.y + insetY
        val right = info.offset.x + info.size.width - insetX
        val bottom = info.offset.y + info.size.height - insetY
        if (position.x in left..right && position.y in top..bottom) {
            dragState.setDropTarget(folderId)
            dragState.clearInsertPosition()
            return
        }
    }
    dragState.setDropTarget(null)

    // 2) 按“行内从左到右”的阅读顺序确定插入下标，横竖屏列数变化都成立。
    val firstRowTop = visible.minOf { it.offset.y }
    val lastRowBottom = visible.maxOf { it.offset.y + it.size.height }
    val insertIndex = when {
        // 拖到网格顶部之外：插到第一张可见卡片之前
        position.y < firstRowTop -> (visible.minOf { it.index } - 1).coerceIn(0, itemCount)
        // 拖到网格底部之外：插到最后一张可见卡片之后
        position.y > lastRowBottom -> visible.maxOf { it.index }.coerceIn(0, itemCount)
        else -> {
            val closest = visible.minByOrNull { info ->
                val cx = info.offset.x + info.size.width / 2f
                val cy = info.offset.y + info.size.height / 2f
                val dx = position.x - cx
                val dy = position.y - cy
                dx * dx + dy * dy
            } ?: return
            // 同一行内按 x 排序，指针落在哪张卡片中心线左侧就插到它前面
            val rowItems = visible
                .filter { abs(it.offset.y - closest.offset.y) < closest.size.height / 2f }
                .sortedBy { it.offset.x }
            val before = rowItems.firstOrNull { position.x < it.offset.x + it.size.width / 2f }
            if (before != null) {
                before.index - 1
            } else {
                // 该行已到末尾：插到行末卡片之后
                rowItems.last().index
            }.coerceIn(0, itemCount)
        }
    }

    val hoverItem = visible.minByOrNull { abs(it.index - (insertIndex + 1)) }
    dragState.setInsertPosition(
        index = insertIndex,
        hoverId = hoverItem?.key?.toString()?.removePrefix("folder_")?.removePrefix("note_"),
        marker = insertionMarker(visible, insertIndex, itemCount, gapPx)
    )
}

private fun LazyGridItemInfo.isSortableItem(): Boolean {
    val key = key as? String ?: return false
    return key.startsWith("note_") || key.startsWith("folder_")
}

/** 计算插入标记条：落在目标卡片左侧间隙；末位则落在最后一张卡片右侧间隙。 */
private fun insertionMarker(
    visible: List<LazyGridItemInfo>,
    insertIndex: Int,
    itemCount: Int,
    gapPx: Float
): Rect {
    val barWidth = max(4f, gapPx * 0.25f)
    val target = visible.firstOrNull { it.index - 1 == insertIndex }
    if (target != null && insertIndex < itemCount) {
        val x = target.offset.x - gapPx / 2f - barWidth / 2f
        return Rect(
            offset = Offset(x, target.offset.y.toFloat()),
            size = Size(barWidth, target.size.height.toFloat())
        )
    }
    val last = visible.maxByOrNull { it.index } ?: visible.last()
    val x = last.offset.x + last.size.width + gapPx / 2f - barWidth / 2f
    return Rect(
        offset = Offset(x, last.offset.y.toFloat()),
        size = Size(barWidth, last.size.height.toFloat())
    )
}

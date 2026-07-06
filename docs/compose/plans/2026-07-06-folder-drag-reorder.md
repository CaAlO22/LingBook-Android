# 首页文件夹卡片拖动排序 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让首页的文件夹卡片支持长按拖动排序，与笔记卡片使用同一套拖动基础设施。

**Architecture:** 复用现有 `DragState`/`DragResult` 拖动系统。在 `FolderCard` 上添加与 `SubjectCard` 一致的拖动手势回调；在 `SubjectGalleryScreen` 的命中测试 `handleDragHitTest` 与拖动结束处理 `handleDragEnd` 中增加文件夹感知逻辑——拖动文件夹时不触发"放入文件夹"，而是将所有首页项（文件夹+笔记）都作为排序目标。持久化层 `reorderHomeItems` 已支持文件夹，无需改动。

**Tech Stack:** Kotlin + Jetpack Compose, `detectDragGesturesAfterLongPress`, Hilt, Room

## Global Constraints

- 供应商适配规范：本任务不涉及 LLM 供应商，无需遵守供应商解耦规范。
- 字符串资源统一放在 `app/src/main/res/values/strings.xml`。本任务无新增字符串。
- 验证命令：`./gradlew :app:compileDebugKotlin`，通过后 `./gradlew :app:installDebug`。
- 本项目无 Compose UI 测试基础设施，验证以编译通过 + 模拟器手动验证为准。
- 改动范围严格限制在 2 个文件：`FolderCard.kt`、`SubjectGalleryScreen.kt`。

---

### Task 1: 为 FolderCard 添加拖动手势支持

**Covers:** 设计第 1 点 — FolderCard 添加拖动手势（镜像 SubjectCard）

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/components/FolderCard.kt`

**Interfaces:**
- Consumes: `SubjectCardMinHeight`（已有），`HomeItem`（已有）
- Produces: `FolderCard` 新增参数 `onDragStart: (Offset) -> Unit`、`onDrag: (Offset) -> Unit`、`onDragEnd: () -> Unit`、`onDragCancel: () -> Unit`、`isDragging: Boolean`、`isReorderTarget: Boolean`，签名与 `SubjectCard` 完全一致，供 Task 2 调用。

- [ ] **Step 1: 读取 FolderCard.kt 当前内容确认行号**

Run: 使用 read 工具读取 `app/src/main/java/com/lingji/app/ui/components/FolderCard.kt`
Expected: 确认当前 115 行结构，`FolderCard` 签名在 41-50 行，Card modifier 在 54-64 行。

- [ ] **Step 2: 添加必要的 import**

在 `FolderCard.kt` import 区添加以下 import（镜像 SubjectCard）：

```kotlin
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
```

- [ ] **Step 3: 扩展 FolderCard 函数签名**

将 `FolderCard` 签名从：

```kotlin
fun FolderCard(
    folder: Folder,
    noteCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    isDropTarget: Boolean = false,
    modifier: Modifier = Modifier
)
```

改为（新增 6 个拖动参数，放在 `isDropTarget` 之后、`modifier` 之前）：

```kotlin
fun FolderCard(
    folder: Folder,
    noteCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    isDropTarget: Boolean = false,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    isDragging: Boolean = false,
    isReorderTarget: Boolean = false,
    modifier: Modifier = Modifier
)
```

- [ ] **Step 4: 添加 cardCoords 状态变量**

在 `FolderCard` 函数体开头（`var menuExpanded by remember ...` 之后）添加：

```kotlin
var cardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
```

- [ ] **Step 5: 修改 Card 的 modifier 链**

将 Card 的 modifier 从：

```kotlin
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(if (isDropTarget) 2.dp else 1.dp, if (isDropTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    )
```

改为（镜像 SubjectCard：添加 onGloballyPositioned、pointerInput 拖动手势、alpha、isReorderTarget 边框）：

```kotlin
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .onGloballyPositioned { cardCoords = it }
            .pointerInput(folder.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val rootPos = cardCoords?.let { it.positionInRoot() + offset } ?: offset
                        onDragStart(rootPos)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() }
                )
            }
            .alpha(if (isDragging) 0.3f else 1f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            if (isReorderTarget || isDropTarget) 2.dp else 1.dp,
            if (isReorderTarget) MaterialTheme.colorScheme.primary
            else if (isDropTarget) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    )
```

- [ ] **Step 6: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。FolderCard 现在支持拖动手势，但尚未被调用（Task 2 完成后才有调用方）。

---

### Task 2: 在 SubjectGalleryScreen 接入文件夹拖动 + 文件夹感知命中测试

**Covers:** 设计第 2 点 — 接线回调 + handleDragHitTest/handleDragEnd 文件夹感知

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt`（FolderCard 调用处 322-337 行；handleDragHitTest 1059-1104 行；handleDragEnd 1106-1137 行）

**Interfaces:**
- Consumes: Task 1 产出的 `FolderCard` 新参数 `onDragStart`/`onDrag`/`onDragEnd`/`onDragCancel`/`isDragging`/`isReorderTarget`；已有 `DragState`、`containerCoords`、`gridState`、`uiState.homeItems`、`viewModel.reorderHomeItems`
- Produces: 文件夹卡片可长按拖动并在松手后持久化新顺序。

- [ ] **Step 1: 读取 SubjectGalleryScreen.kt 确认当前行号**

Run: 使用 read 工具读取 `app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt`，重点查看 322-337 行（FolderCard 调用）、1059-1104 行（handleDragHitTest）、1106-1137 行（handleDragEnd）。
Expected: 确认行号与计划一致。

- [ ] **Step 2: 修改 FolderCard 调用处，传入拖动回调**

将 322-337 行的 `FolderItem` 分支从：

```kotlin
                            is HomeItem.FolderItem -> {
                                val isDropTarget = dragState.dropTargetFolderId == homeItem.folder.id
                                FolderCard(
                                    folder = homeItem.folder,
                                    noteCount = homeItem.noteCount,
                                    onClick = { onFolderClick(homeItem.folder.id) },
                                    onLongClick = { },
                                    onRename = {
                                        renameFolderId = homeItem.folder.id
                                        renameFolderDefault = homeItem.folder.name
                                    },
                                    onDelete = { deleteFolderId = homeItem.folder.id },
                                    isDropTarget = isDropTarget
                                )
                            }
```

改为（新增 isDragging、isReorderTarget 计算 + 拖动回调，镜像 NoteItem 分支的 SubjectCard 调用）：

```kotlin
                            is HomeItem.FolderItem -> {
                                val isDropTarget = dragState.dropTargetFolderId == homeItem.folder.id
                                val isDragging = dragState.draggedItem is HomeItem.FolderItem &&
                                    (dragState.draggedItem as HomeItem.FolderItem).folder.id == homeItem.folder.id
                                FolderCard(
                                    folder = homeItem.folder,
                                    noteCount = homeItem.noteCount,
                                    onClick = { onFolderClick(homeItem.folder.id) },
                                    onLongClick = { },
                                    onRename = {
                                        renameFolderId = homeItem.folder.id
                                        renameFolderDefault = homeItem.folder.name
                                    },
                                    onDelete = { deleteFolderId = homeItem.folder.id },
                                    isDropTarget = isDropTarget,
                                    onDragStart = { offset -> dragState.startDrag(homeItem, offset) },
                                    onDrag = { dragAmount ->
                                        dragState.updateDrag(dragAmount)
                                        val containerPos = containerCoords?.positionInRoot() ?: Offset.Zero
                                        val globalPos = dragState.dragStartPos + dragState.dragOffset - containerPos
                                        handleDragHitTest(globalPos, gridState, uiState.homeItems, dragState)
                                    },
                                    onDragEnd = {
                                        val draggedItem = dragState.draggedItem
                                        val result = dragState.endDrag()
                                        handleDragEnd(result, draggedItem, viewModel, uiState.homeItems)
                                    },
                                    onDragCancel = { dragState.cancelDrag() },
                                    isDragging = isDragging,
                                    isReorderTarget = dragState.reorderHoverId == homeItem.folder.id
                                )
                            }
```

- [ ] **Step 3: 修改 handleDragHitTest，拖动文件夹时将所有项作为排序目标**

将 `handleDragHitTest` 函数（1059-1104 行）从：

```kotlin
private fun handleDragHitTest(
    globalPos: Offset,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    homeItems: List<HomeItem>,
    dragState: DragState
) {
    val visibleItems = gridState.layoutInfo.visibleItemsInfo
    val folderTarget = visibleItems.firstOrNull { itemInfo ->
        val key = itemInfo.key
        key is String && key.startsWith("folder_") &&
            globalPos.x >= itemInfo.offset.x &&
            globalPos.x <= itemInfo.offset.x + itemInfo.size.width &&
            globalPos.y >= itemInfo.offset.y &&
            globalPos.y <= itemInfo.offset.y + itemInfo.size.height
    }
    if (folderTarget != null) {
        val folderId = (folderTarget.key as String).removePrefix("folder_")
        dragState.setDropTarget(folderId)
        dragState.setReorderTarget(-1)
        dragState.setReorderHover(null)
    } else {
        dragState.setDropTarget(null)
        val noteItems = visibleItems.filter {
            it.key is String && (it.key as String).startsWith("note_")
        }
        // Find the closest note item by center distance
        val closest = noteItems.minByOrNull { itemInfo ->
            val cx = itemInfo.offset.x + itemInfo.size.width / 2
            val cy = itemInfo.offset.y + itemInfo.size.height / 2
            val dx = globalPos.x - cx
            val dy = globalPos.y - cy
            dx * dx + dy * dy
        }
        if (closest != null) {
            val noteId = (closest.key as String).removePrefix("note_")
            dragState.setReorderHover(noteId)
            val insertIndex = noteItems.indexOfFirst { itemInfo ->
                globalPos.y < itemInfo.offset.y + itemInfo.size.height / 2
            }
            dragState.setReorderTarget(if (insertIndex >= 0) insertIndex else noteItems.size)
        } else {
            dragState.setReorderHover(null)
            dragState.setReorderTarget(-1)
        }
    }
}
```

改为（关键变化：当 `draggedItem` 是 `FolderItem` 时，跳过文件夹放入检测，将所有可见项作为排序目标；`reorderHoverId` 用 key 前缀区分文件夹与笔记 ID）：

```kotlin
private fun handleDragHitTest(
    globalPos: Offset,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    homeItems: List<HomeItem>,
    dragState: DragState
) {
    val visibleItems = gridState.layoutInfo.visibleItemsInfo
    val isDraggingFolder = dragState.draggedItem is HomeItem.FolderItem

    // Only check folder drop-target when dragging a NOTE (folders can't nest)
    if (!isDraggingFolder) {
        val folderTarget = visibleItems.firstOrNull { itemInfo ->
            val key = itemInfo.key
            key is String && key.startsWith("folder_") &&
                globalPos.x >= itemInfo.offset.x &&
                globalPos.x <= itemInfo.offset.x + itemInfo.size.width &&
                globalPos.y >= itemInfo.offset.y &&
                globalPos.y <= itemInfo.offset.y + itemInfo.size.height
        }
        if (folderTarget != null) {
            val folderId = (folderTarget.key as String).removePrefix("folder_")
            dragState.setDropTarget(folderId)
            dragState.setReorderTarget(-1)
            dragState.setReorderHover(null)
            return
        }
    }

    dragState.setDropTarget(null)
    // Reorder among ALL visible items (folders + notes) when dragging a folder,
    // or among notes only when dragging a note.
    val reorderItems = if (isDraggingFolder) {
        visibleItems.filter { it.key is String && ((it.key as String).startsWith("folder_") || (it.key as String).startsWith("note_")) }
    } else {
        visibleItems.filter { it.key is String && (it.key as String).startsWith("note_") }
    }
    // Find the closest item by center distance
    val closest = reorderItems.minByOrNull { itemInfo ->
        val cx = itemInfo.offset.x + itemInfo.size.width / 2
        val cy = itemInfo.offset.y + itemInfo.size.height / 2
        val dx = globalPos.x - cx
        val dy = globalPos.y - cy
        dx * dx + dy * dy
    }
    if (closest != null) {
        val rawKey = closest.key as String
        // Store hover ID with type prefix so handleDragEnd can distinguish folder vs note
        val hoverId = rawKey.removePrefix("folder_").removePrefix("note_")
        dragState.setReorderHover(hoverId)
        val insertIndex = reorderItems.indexOfFirst { itemInfo ->
            globalPos.y < itemInfo.offset.y + itemInfo.size.height / 2
        }
        dragState.setReorderTarget(if (insertIndex >= 0) insertIndex else reorderItems.size)
    } else {
        dragState.setReorderHover(null)
        dragState.setReorderTarget(-1)
    }
}
```

- [ ] **Step 4: 修改 handleDragEnd，Reorder 分支匹配文件夹与笔记两种 hoverId**

将 `handleDragEnd` 函数（1106-1137 行）的 `Reorder` 分支从：

```kotlin
        is DragResult.Reorder -> {
            val hoverId = result.hoverId ?: return
            val mutableList = homeItems.toMutableList()
            val draggedIndex = mutableList.indexOf(item)
            if (draggedIndex < 0) return
            // Find the hovered note's position in the full homeItems list
            val hoverIndex = mutableList.indexOfFirst { homeItem ->
                homeItem is HomeItem.NoteItem && homeItem.subject.id == hoverId
            }
            if (hoverIndex < 0 || hoverIndex == draggedIndex) return
            // Remove dragged item, then insert at the hovered position
            mutableList.removeAt(draggedIndex)
            val adjustedHover = if (hoverIndex > draggedIndex) hoverIndex - 1 else hoverIndex
            mutableList.add(adjustedHover.coerceIn(0, mutableList.size), item)
            viewModel.reorderHomeItems(mutableList)
        }
```

改为（匹配 FolderItem 和 NoteItem 两种 hoverId）：

```kotlin
        is DragResult.Reorder -> {
            val hoverId = result.hoverId ?: return
            val mutableList = homeItems.toMutableList()
            val draggedIndex = mutableList.indexOf(item)
            if (draggedIndex < 0) return
            // Find the hovered item's position — could be a folder or a note
            val hoverIndex = mutableList.indexOfFirst { homeItem ->
                when (homeItem) {
                    is HomeItem.FolderItem -> homeItem.folder.id == hoverId
                    is HomeItem.NoteItem -> homeItem.subject.id == hoverId
                }
            }
            if (hoverIndex < 0 || hoverIndex == draggedIndex) return
            // Remove dragged item, then insert at the hovered position
            mutableList.removeAt(draggedIndex)
            val adjustedHover = if (hoverIndex > draggedIndex) hoverIndex - 1 else hoverIndex
            mutableList.add(adjustedHover.coerceIn(0, mutableList.size), item)
            viewModel.reorderHomeItems(mutableList)
        }
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 同步到模拟器**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL，APK 安装到模拟器/设备。
若无可用的模拟器/设备，记录原因后跳过。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/lingji/app/ui/components/FolderCard.kt app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt
git commit -m "feat: 首页文件夹卡片支持长按拖动排序"
```

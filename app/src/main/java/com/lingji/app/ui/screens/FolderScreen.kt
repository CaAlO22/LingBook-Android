package com.lingji.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.HomeItem
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.ui.components.DeleteConfirmDialog
import com.lingji.app.ui.components.DragState
import com.lingji.app.ui.components.FolderCard
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.SubjectCard
import com.lingji.app.ui.components.TodoRepeatDialog
import com.lingji.app.ui.components.applyDragResult
import com.lingji.app.ui.components.handleGridDragHitTest
import com.lingji.app.ui.viewmodel.SubjectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    viewModel: SubjectViewModel,
    folderId: String,
    onSubjectClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val folder = uiState.folders.find { it.id == folderId }
    // 信息岛：文件夹内容——直接子文件夹与直接笔记，共用一套排序顺序
    val folderContents = uiState.folderContents(folderId)
    val folderSubjects = folderContents.filterIsInstance<HomeItem.NoteItem>().map { it.subject }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 信息岛：内容绘制到导航栏之下，用底部留白让最后一行仍可完整滚动到小白条上方。
    val density = LocalDensity.current
    val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val gridState = rememberLazyGridState()
    val dragState = remember { DragState() }
    val dragGapPx = with(density) { 16.dp.toPx() }
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var renameSubjectId by remember { mutableStateOf<String?>(null) }
    var renameDefault by remember { mutableStateOf("") }
    var exportSubject by remember { mutableStateOf<Subject?>(null) }
    // 信息岛：待创建待办——确定重复规则后才创建
    var pendingTodoCreate by remember { mutableStateOf<String?>(null) }
    // 信息岛：子文件夹的改名 / 删除
    var renameFolderId by remember { mutableStateOf<String?>(null) }
    var renameFolderDefault by remember { mutableStateOf("") }
    var deleteFolderId by remember { mutableStateOf<String?>(null) }
    var dissolveFolderId by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val subject = exportSubject ?: return@rememberLauncherForActivityResult
        exportSubject = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                viewModel.exportSubject(subject, uri)
                Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { containerCoords = it }
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + navBarBottom
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "__add__") {
                    AddSubjectCard(
                        folderId = folderId,
                        onCreateFolder = { name -> viewModel.createFolder(name, folderId) },
                        onAdd = { title, type, fid ->
                            if (type == SubjectType.TODO) {
                                pendingTodoCreate = title
                            } else {
                                viewModel.addSubject(title, type, fid)
                            }
                        }
                    )
                }
                items(
                    folderContents,
                    key = { item ->
                        when (item) {
                            is HomeItem.FolderItem -> "folder_${item.folder.id}"
                            is HomeItem.NoteItem -> "note_${item.subject.id}"
                        }
                    }
                ) { homeItem ->
                    val isDragging = dragState.isDragging && dragState.draggedItem == homeItem
                    val isDropTarget = dragState.dropTargetFolderId != null &&
                        homeItem is HomeItem.FolderItem &&
                        dragState.dropTargetFolderId == homeItem.folder.id
                    when (homeItem) {
                        is HomeItem.FolderItem -> FolderCard(
                            folder = homeItem.folder,
                            noteCount = homeItem.noteCount,
                            subfolderCount = homeItem.subfolderCount,
                            onClick = { onFolderClick(homeItem.folder.id) },
                            onLongClick = { },
                            onRename = {
                                renameFolderId = homeItem.folder.id
                                renameFolderDefault = homeItem.folder.name
                            },
                            onDelete = { deleteFolderId = homeItem.folder.id },
                            onDissolve = { dissolveFolderId = homeItem.folder.id },
                            onMoveOut = { viewModel.moveFolderToFolder(homeItem.folder.id, folder?.parentId) },
                            isDropTarget = isDropTarget,
                            onDragStart = { offset, grab ->
                                dragState.startDrag(homeItem, offset, grab)
                            },
                            onDrag = { dragAmount ->
                                dragState.updateDrag(dragAmount)
                                val containerPos = containerCoords?.positionInRoot() ?: Offset.Zero
                                val globalPos = dragState.dragStartPos + dragState.dragOffset - containerPos
                                handleGridDragHitTest(
                                    position = globalPos,
                                    gridState = gridState,
                                    itemCount = folderContents.size,
                                    dragState = dragState,
                                    canDropInto = { targetId ->
                                        uiState.canMoveFolderInto(homeItem.folder.id, targetId)
                                    },
                                    gapPx = dragGapPx
                                )
                            },
                            onDragEnd = { finishDrag(dragState, folderContents, viewModel) },
                            onDragCancel = { dragState.cancelDrag() },
                            isDragging = isDragging,
                            isReorderTarget = dragState.reorderHoverId == homeItem.folder.id
                        )

                        is HomeItem.NoteItem -> {
                            val subject = homeItem.subject
                            val index = folderSubjects.indexOf(subject)
                            SubjectCard(
                                subject = subject,
                                onClick = { onSubjectClick(subject.id) },
                                onDelete = { viewModel.deleteSubject(subject.id) },
                                onRename = { renameSubjectId = subject.id; renameDefault = subject.title },
                                onExport = {
                                    exportSubject = subject
                                    exportLauncher.launch(viewModel.buildExportFileName(subject.title))
                                },
                                onCopyExport = {
                                    scope.launch {
                                        try {
                                            val encoded = viewModel.exportSubjectToText(subject)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(subject.title, encoded))
                                            Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onMoveToTop = { viewModel.moveSubjectToTop(subject.id) },
                                onMoveUp = { viewModel.moveSubjectUp(subject.id) },
                                onMoveDown = { viewModel.moveSubjectDown(subject.id) },
                                canMoveUp = index > 0,
                                canMoveDown = index < folderSubjects.lastIndex,
                                onRemoveFromFolder = { viewModel.removeSubjectFromFolder(subject.id) },
                                isDropTarget = false,
                                onDragStart = { offset, grab ->
                                    dragState.startDrag(homeItem, offset, grab)
                                },
                                onDrag = { dragAmount ->
                                    dragState.updateDrag(dragAmount)
                                    val containerPos = containerCoords?.positionInRoot() ?: Offset.Zero
                                    val globalPos = dragState.dragStartPos + dragState.dragOffset - containerPos
                                    handleGridDragHitTest(
                                        position = globalPos,
                                        gridState = gridState,
                                        itemCount = folderContents.size,
                                        dragState = dragState,
                                        canDropInto = { true },
                                        gapPx = dragGapPx
                                    )
                                },
                                onDragEnd = { finishDrag(dragState, folderContents, viewModel) },
                                onDragCancel = { dragState.cancelDrag() },
                                isDragging = isDragging,
                                isReorderTarget = dragState.reorderHoverId == subject.id
                            )
                        }
                    }
                }
            }

            // 信息岛：拖拽浮层——手指抓取点贴合原卡片位置，落点判定与视觉一致
            if (dragState.isDragging) {
                dragState.insertionRect?.let { rect ->
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                            .size(
                                width = with(density) { rect.width.toDp() },
                                height = with(density) { rect.height.toDp() }
                            )
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                val draggedItem = dragState.draggedItem
                if (draggedItem != null) {
                    Card(
                        modifier = Modifier
                            .offset {
                                val containerPos = containerCoords?.positionInRoot() ?: Offset.Zero
                                val grab = dragState.dragGrabOffset
                                IntOffset(
                                    (dragState.dragStartPos.x + dragState.dragOffset.x - grab.x - containerPos.x).toInt(),
                                    (dragState.dragStartPos.y + dragState.dragOffset.y - grab.y - containerPos.y).toInt()
                                )
                            }
                            .width(160.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = when (draggedItem) {
                                    is HomeItem.FolderItem -> draggedItem.folder.name
                                    is HomeItem.NoteItem -> draggedItem.subject.title
                                },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // 信息岛：待办创建第二步——重复规则；取消则放弃创建
    pendingTodoCreate?.let { title ->
        TodoRepeatDialog(
            initial = TodoRepeat(),
            onDismiss = { pendingTodoCreate = null },
            onConfirm = { repeat ->
                viewModel.addSubject(title, SubjectType.TODO, folderId, repeat)
                pendingTodoCreate = null
            }
        )
    }

    renameSubjectId?.let { id ->
        var title by remember(id) { mutableStateOf(renameDefault) }
        LingjiDialog(
            onDismissRequest = { renameSubjectId = null },
            title = { Text(stringResource(R.string.rename_subject)) },
            text = {
                GlassOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.new_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        if (title.isNotBlank()) viewModel.renameSubject(id, title)
                        renameSubjectId = null
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { renameSubjectId = null }
                )
            }
        )
    }

    renameFolderId?.let { id ->
        var name by remember(id) { mutableStateOf(renameFolderDefault) }
        LingjiDialog(
            onDismissRequest = { renameFolderId = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                GlassOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        if (name.isNotBlank()) viewModel.renameFolder(id, name.trim())
                        renameFolderId = null
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { renameFolderId = null }
                )
            }
        )
    }

    deleteFolderId?.let { id ->
        val target = uiState.folders.find { it.id == id }
        DeleteConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_folder_confirm, target?.name ?: ""),
            onConfirm = {
                viewModel.deleteFolder(id)
                deleteFolderId = null
            },
            onDismiss = { deleteFolderId = null }
        )
    }

    dissolveFolderId?.let { id ->
        val target = uiState.folders.find { it.id == id }
        DeleteConfirmDialog(
            title = stringResource(R.string.dissolve),
            text = stringResource(R.string.dissolve_folder_confirm, target?.name ?: ""),
            confirmText = stringResource(R.string.dissolve),
            onConfirm = {
                viewModel.dissolveFolder(id)
                dissolveFolderId = null
            },
            onDismiss = { dissolveFolderId = null }
        )
    }
}

/** 信息岛：文件夹内容拖拽结算——排序写回文件夹，拖入文件夹时移入对应子文件夹。 */
private fun finishDrag(
    dragState: DragState,
    contents: List<HomeItem>,
    viewModel: SubjectViewModel
) {
    val draggedItem = dragState.draggedItem
    val result = dragState.endDrag()
    applyDragResult(
        result = result,
        draggedItem = draggedItem,
        items = contents,
        onMoveToFolder = { item, targetFolderId ->
            when (item) {
                is HomeItem.NoteItem -> viewModel.moveSubjectToFolder(item.subject.id, targetFolderId)
                is HomeItem.FolderItem -> viewModel.moveFolderToFolder(item.folder.id, targetFolderId)
            }
        },
        onReorder = { viewModel.reorderFolderContents(it) }
    )
}

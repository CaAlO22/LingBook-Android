package com.lingji.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.HomeItem
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.ui.chat.HomeChatBar
import com.lingji.app.ui.chat.HomeChatSheet
import com.lingji.app.ui.components.ClipboardTooLargeDialog
import com.lingji.app.ui.components.DragResult
import com.lingji.app.ui.components.DragState
import com.lingji.app.ui.components.FolderCard
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.SubjectCard
import com.lingji.app.ui.components.SubjectCardMinHeight
import com.lingji.app.ui.components.TimeDisplay
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.SubjectViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

private const val CLIPBOARD_SIZE_LIMIT = 100_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectGalleryScreen(
    viewModel: SubjectViewModel,
    onSubjectClick: (String) -> Unit,
    onFolderClick: (String) -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var renameSubjectId by remember { mutableStateOf<String?>(null) }
    var renameDefault by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importDialogText by remember { mutableStateOf("") }
    var showImportMenu by remember { mutableStateOf(false) }
    var showClipboardTooLargeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    val dragState = remember { DragState() }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var renameFolderId by remember { mutableStateOf<String?>(null) }
    var renameFolderDefault by remember { mutableStateOf("") }
    var deleteFolderId by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = viewModel.importSubject(uri)
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.import_success) else context.getString(R.string.import_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var exportSubject by remember { mutableStateOf<Subject?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val subject = exportSubject ?: return@rememberLauncherForActivityResult
        exportSubject = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                viewModel.exportSubject(subject, uri)
                Toast.makeText(
                    context,
                    context.getString(R.string.export_success),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    context.getString(R.string.export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .haze(hazeState),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = stringResource(R.string.gallery_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = NotoSerifCJKsc,
                            fontSize = 40.sp,
                            letterSpacing = (-0.03).sp
                        )
                    )
                },
                actions = {
                    TimeDisplay(
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Box {
                        TextButton(onClick = { showImportMenu = true }) {
                            Text(stringResource(R.string.import_label))
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_from_file)) },
                                onClick = {
                                    showImportMenu = false
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FileOpen,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_from_clipboard)) },
                                onClick = {
                                    showImportMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                    scope.launch {
                                        val ok = if (text.isNullOrBlank()) {
                                            false
                                        } else {
                                            viewModel.importSubject(text)
                                        }
                                        when {
                                            text.isNullOrBlank() -> {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.clipboard_empty),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            ok -> {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.import_success),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            else -> {
                                                importDialogText = text
                                                showImportDialog = true
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.subjects.isEmpty() && uiState.folders.isEmpty()) {
                EmptySubjectState(onCreate = { showAddDialog = true })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "__add__") {
                        AddSubjectCard(
                            onAdd = { title, type, _ ->
                                viewModel.addSubject(title, type)
                            }
                        )
                    }
                    item(key = "__add_folder__") {
                        AddFolderCard(onCreate = { name -> viewModel.createFolder(name) })
                    }
                    items(uiState.homeItems, key = { item ->
                        when (item) {
                            is HomeItem.FolderItem -> "folder_${item.folder.id}"
                            is HomeItem.NoteItem -> "note_${item.subject.id}"
                        }
                    }) { homeItem ->
                        when (homeItem) {
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
                            is HomeItem.NoteItem -> {
                                val subject = homeItem.subject
                                val isDragging = dragState.draggedItem is HomeItem.NoteItem &&
                                    (dragState.draggedItem as HomeItem.NoteItem).subject.id == subject.id
                                SubjectCard(
                                    subject = subject,
                                    onClick = { onSubjectClick(subject.id) },
                                    onDelete = { viewModel.deleteSubject(subject.id) },
                                    onRename = {
                                        renameSubjectId = subject.id
                                        renameDefault = subject.title
                                    },
                                    onExport = {
                                        exportSubject = subject
                                        exportLauncher.launch(viewModel.buildExportFileName(subject.title))
                                    },
                                    onCopyExport = {
                                        scope.launch {
                                            try {
                                                val encoded = viewModel.exportSubjectToText(subject)
                                                if (encoded.length > CLIPBOARD_SIZE_LIMIT) {
                                                    showClipboardTooLargeDialog = true
                                                } else {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(subject.title, encoded))
                                                    Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onMoveToTop = { viewModel.moveSubjectToTop(subject.id) },
                                    onMoveUp = { viewModel.moveSubjectUp(subject.id) },
                                    onMoveDown = { viewModel.moveSubjectDown(subject.id) },
                                    canMoveUp = true,
                                    canMoveDown = true,
                                    onDragStart = { offset -> dragState.startDrag(homeItem, offset) },
                                    onDrag = { dragAmount ->
                                        dragState.updateDrag(dragAmount)
                                        val globalPos = dragState.dragStartPos + dragState.dragOffset
                                        handleDragHitTest(globalPos, gridState, uiState.homeItems, dragState)
                                    },
                                    onDragEnd = {
                                        val draggedItem = dragState.draggedItem
                                        val result = dragState.endDrag()
                                        handleDragEnd(result, draggedItem, viewModel, uiState.homeItems)
                                    },
                                    onDragCancel = { dragState.cancelDrag() },
                                    isDragging = isDragging,
                                    isReorderTarget = dragState.reorderHoverId == subject.id
                                )
                            }
                        }
                    }
                }
                // Drag overlay — floating card following finger + capture touches
                if (dragState.isDragging && dragState.draggedItem != null) {
                    val draggedTitle = when (val item = dragState.draggedItem) {
                        is HomeItem.NoteItem -> item.subject.title
                        is HomeItem.FolderItem -> item.folder.name
                        null -> ""
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { },
                                    onDrag = { change, _ -> change.consume() },
                                    onDragEnd = { },
                                    onDragCancel = { }
                                )
                            }
                    ) {
                        Card(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (dragState.dragStartPos.x + dragState.dragOffset.x).toInt(),
                                        (dragState.dragStartPos.y + dragState.dragOffset.y).toInt()
                                    )
                                }
                                .width(160.dp)
                                .alpha(0.85f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = draggedTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, type ->
                viewModel.addSubject(title, type)
                showAddDialog = false
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
                        if (name.isNotBlank()) viewModel.renameFolder(id, name)
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
        val folder = uiState.folders.find { it.id == id }
        LingjiDialog(
            onDismissRequest = { deleteFolderId = null },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(stringResource(R.string.delete_folder_confirm, folder?.name ?: ""))
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        viewModel.deleteFolder(id)
                        deleteFolderId = null
                    },
                    isDestructive = true
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { deleteFolderId = null }
                )
            }
        )
    }

    if (showImportDialog) {
        var text by remember(importDialogText) { mutableStateOf(importDialogText) }
        LingjiDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.import_from_clipboard_dialog_title)) },
            text = {
                GlassOutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.import_from_clipboard_dialog_label)) },
                    placeholder = { Text(stringResource(R.string.import_from_clipboard_dialog_hint)) },
                    minLines = 4,
                    maxLines = 8
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.import_label),
                    onClick = {
                        scope.launch {
                            val ok = text.isNotBlank() && viewModel.importSubject(text)
                            Toast.makeText(
                                context,
                                if (ok) context.getString(R.string.import_success) else context.getString(R.string.import_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                            if (ok) {
                                showImportDialog = false
                                importDialogText = ""
                            }
                        }
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showImportDialog = false }
                )
            }
        )
    }

    if (showClipboardTooLargeDialog) {
        ClipboardTooLargeDialog(onDismiss = { showClipboardTooLargeDialog = false })
    }

    // Home chat overlay — Haze 背景模糊实现毛玻璃效果
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    tint = HazeTint(Color.White.copy(alpha = 0.22f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.15f
                )
            )
    ) {
        HomeChatBar(
            currentMode = uiState.homeChatMode,
            onModeToggle = { viewModel.setHomeChatMode(it) },
            onClick = {
                viewModel.toggleHomeChat()
                if (uiState.homeConversations.isEmpty()) {
                    viewModel.loadHomeConversations()
                }
            },
        )
    }

    AnimatedVisibility(
        visible = uiState.homeChatExpanded,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.fillMaxSize()
    ) {
        HomeChatSheet(
            messages = uiState.homeMessages,
            streamLine = uiState.homeStreamLine,
            isLoading = uiState.homeIsLoading,
            currentMode = uiState.homeChatMode,
            conversations = uiState.homeConversations,
            currentConversationId = uiState.homeCurrentConversationId,
            fragments = uiState.homeFragments,
            onSend = { text -> viewModel.sendHomeMessage(text) },
            onModeChange = { viewModel.setHomeChatMode(it) },
            onNewConversation = { viewModel.startNewConversation() },
            onLoadConversation = { viewModel.loadConversation(it) },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onDeleteFragment = { viewModel.removeHomeFragment(it) },
            onOrganizeFragments = { viewModel.organizeHomeFragments() },
            onDismiss = { viewModel.toggleHomeChat() }
        )
    }
    }
}

@Composable
private fun EmptySubjectState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_subjects),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCreate) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.new_subject), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun AddSubjectCard(
    onAdd: (String, SubjectType, String?) -> Unit,
    folderId: String? = null,
    modifier: Modifier = Modifier
) {
    var isCreating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SubjectType.FRAGMENT) }
    val focusRequester = remember { FocusRequester() }
    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    LaunchedEffect(isCreating) {
        if (isCreating) focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = !isCreating,
                onClick = { isCreating = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        border = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dashedBorder(strokeColor),
            contentAlignment = Alignment.Center
        ) {
            // Invisible sizing reference to match the height of regular subject cards
            SubjectCardSizingReference(
                modifier = Modifier.alpha(0f)
            )
            if (isCreating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (title.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.new_subject_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeChip(
                            label = stringResource(R.string.fragment_mode),
                            selected = type == SubjectType.FRAGMENT,
                            onClick = { type = SubjectType.FRAGMENT },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TypeChip(
                            label = stringResource(R.string.notebook_mode),
                            selected = type == SubjectType.NOTEBOOK,
                            onClick = { type = SubjectType.NOTEBOOK },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(
                            onClick = {
                                isCreating = false
                                title = ""
                                type = SubjectType.FRAGMENT
                            }
                        ) { Text(stringResource(R.string.cancel)) }
                        TextButton(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onAdd(title.trim(), type, folderId)
                                    isCreating = false
                                    title = ""
                                    type = SubjectType.FRAGMENT
                                }
                            },
                            enabled = title.isNotBlank()
                        ) { Text(stringResource(R.string.confirm)) }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.new_subject),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectCardSizingReference(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(14.dp))
                Text(
                    text = "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Match the 48.dp minimum touch target of IconButton
                Spacer(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = modifier
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = modifier
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color) = drawBehind {
    val strokeWidth = 2.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
        size = Size(
            size.width - strokeWidth,
            size.height - strokeWidth
        ),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        ),
        cornerRadius = CornerRadius(16.dp.toPx())
    )
}

@Composable
private fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, SubjectType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var isNotebook by remember { mutableStateOf(false) }

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subject)) },
        text = {
            Column {
                GlassOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.hint_subject_title)) },
                    singleLine = true
                )
                LingjiDialogDismissButton(
                    text = stringResource(
                        R.string.mode_format,
                        stringResource(if (isNotebook) R.string.notebook_mode else R.string.fragment_mode)
                    ),
                    onClick = { isNotebook = !isNotebook }
                )
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.save),
                enabled = title.isNotBlank(),
                onClick = {
                    onConfirm(title, if (isNotebook) SubjectType.NOTEBOOK else SubjectType.FRAGMENT)
                    onDismiss()
                }
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

@Composable
private fun AddFolderCard(onCreate: (String) -> Unit, modifier: Modifier = Modifier) {
    var isCreating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    LaunchedEffect(isCreating) { if (isCreating) focusRequester.requestFocus() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isCreating, onClick = { isCreating = true }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dashedBorder(strokeColor),
            contentAlignment = Alignment.Center
        ) {
            SubjectCardSizingReference(modifier = Modifier.alpha(0f))
            if (isCreating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (name.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.new_folder_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { isCreating = false; name = "" }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onCreate(name.trim())
                                    isCreating = false
                                    name = ""
                                }
                            },
                            enabled = name.isNotBlank()
                        ) { Text(stringResource(R.string.confirm)) }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.new_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

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

private fun handleDragEnd(
    result: DragResult,
    draggedItem: HomeItem?,
    viewModel: SubjectViewModel,
    homeItems: List<HomeItem>
) {
    val item = draggedItem ?: return
    when (result) {
        is DragResult.MoveToFolder -> {
            if (item is HomeItem.NoteItem) {
                viewModel.moveSubjectToFolder(item.subject.id, result.folderId)
            }
        }
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
        is DragResult.None -> { }
    }
}

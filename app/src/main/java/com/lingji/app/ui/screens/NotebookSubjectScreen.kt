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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.Subject
import com.lingji.app.ui.components.FloatingInputContainer
import com.lingji.app.ui.components.IndexSearchPanel
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.ModeChip
import com.lingji.app.ui.components.NotebookPageEditor
import com.lingji.app.ui.components.PageChatBar
import com.lingji.app.ui.components.rememberNotebookPageEditorHostState
import com.lingji.app.ui.components.PageImagePicker
import com.lingji.app.ui.components.PageIndexEditorDialog
import com.lingji.app.ui.components.TimeDisplay
import com.lingji.app.ui.components.rememberImagePickerState
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.SubjectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookSubjectScreen(
    viewModel: SubjectViewModel,
    subject: Subject,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val liveSubject = remember(uiState.subjects, subject.id) {
        uiState.subjects.find { it.id == subject.id } ?: subject
    }
    val pages = liveSubject.pages ?: emptyList()

    var currentPageId by remember(subject.id) {
        val remembered = subject.lastOpenedPageId
        val initial = if (remembered != null && pages.any { it.id == remembered }) remembered else pages.lastOrNull()?.id
        mutableStateOf(initial)
    }
    val currentPageIndex by remember(currentPageId, pages) {
        derivedStateOf { pages.indexOfFirst { it.id == currentPageId } }
    }
    val currentPage by remember(currentPageIndex, pages) {
        derivedStateOf {
            if (pages.isEmpty()) null
            else pages.getOrNull(currentPageIndex.coerceIn(0, pages.lastIndex))
        }
    }

    // 页面列表变化时保持当前页有效
    LaunchedEffect(pages, currentPageId) {
        if (pages.isEmpty()) {
            currentPageId = null
            return@LaunchedEffect
        }
        if (currentPageId == null || pages.none { it.id == currentPageId }) {
            val index = currentPageIndex.coerceIn(0, pages.lastIndex)
            currentPageId = pages[index].id
        }
    }

    // 记忆上次打开的页面
    LaunchedEffect(currentPageId) {
        if (currentPageId != subject.lastOpenedPageId) {
            viewModel.saveLastOpenedPageId(liveSubject.id, currentPageId)
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showImagePickerForPage by remember { mutableStateOf<NotebookPage?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveText by remember { mutableStateOf("") }
    var chatAnswer by remember { mutableStateOf("") }
    var isChatLoading by remember { mutableStateOf(false) }
    var deleteConfirmPage by remember { mutableStateOf<NotebookPage?>(null) }
    var lastCreatedPageId by remember { mutableStateOf<String?>(null) }
    var showIndexEditorPage by remember { mutableStateOf<NotebookPage?>(null) }

    val editorHostState = rememberNotebookPageEditorHostState()
    val imagePickerState = rememberImagePickerState()

    val indexEntries = remember(liveSubject.pageIndexEntries) {
        (liveSubject.pageIndexEntries ?: emptyList()).associateBy { it.pageId }
    }

    val dirtyCount = pages.count { it.indexedAt <= 0 || it.updatedAt > it.indexedAt }

    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                viewModel.exportSubject(liveSubject, uri)
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

    val triggerBuildIndex = {
        Toast.makeText(
            context,
            context.getString(R.string.building_index_toast),
            Toast.LENGTH_SHORT
        ).show()
        viewModel.buildPageIndexes(
            subject = liveSubject,
            onComplete = { _, indexedIds ->
                val message = if (indexedIds.isEmpty()) {
                    context.getString(R.string.no_index_needed_toast)
                } else {
                    context.getString(R.string.index_built_toast, indexedIds.size)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(
                    context,
                    context.getString(R.string.index_build_failed_toast, error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp)
                    .background(MaterialTheme.colorScheme.background),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：返回 + 笔记名称
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                        Text(
                            text = liveSubject.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = NotoSerifCJKsc,
                                letterSpacing = (-0.02).sp
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                // 中间：编辑/预览切换
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (pages.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                ModeChip(
                                    label = stringResource(R.string.mode_edit),
                                    selected = !editorHostState.isPreview,
                                    onClick = { editorHostState.setPreview(false) }
                                )
                                ModeChip(
                                    label = stringResource(R.string.mode_preview),
                                    selected = editorHostState.isPreview,
                                    onClick = { editorHostState.setPreview(true) }
                                )
                            }
                        }
                    }
                }

                // 右侧：搜索 + 更多
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_search)
                            )
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more)
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.build_index)) },
                                    onClick = {
                                        showMoreMenu = false
                                        triggerBuildIndex()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export)) },
                                    onClick = {
                                        showMoreMenu = false
                                        exportLauncher.launch(viewModel.buildExportFileName(liveSubject.title))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.FileDownload,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_to_clipboard)) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch {
                                            try {
                                                val encoded = viewModel.exportSubjectToText(liveSubject)
                                                if (encoded.length > CLIPBOARD_SIZE_LIMIT) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.copy_too_large),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    return@launch
                                                }
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(liveSubject.title, encoded))
                                                Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NotebookPageToolbar(
                currentPage = currentPage,
                currentPageIndex = currentPageIndex,
                pagesSize = pages.size,
                dirtyCount = dirtyCount,
                isProcessing = uiState.isProcessing,
                onSave = {
                    currentPage?.let { page ->
                        viewModel.updatePage(liveSubject.id, page)
                        Toast.makeText(context, R.string.page_saved, Toast.LENGTH_SHORT).show()
                    }
                },
                onBuildIndex = triggerBuildIndex,
                onJumpToPage = { showJumpDialog = true },
                onEditPagePosition = {
                    moveText = (currentPageIndex + 1).toString()
                    showMoveDialog = true
                },
                onPrevPage = {
                    val prev = pages.getOrNull(currentPageIndex - 1)
                    if (prev != null) currentPageId = prev.id
                },
                onNextPage = {
                    val next = pages.getOrNull(currentPageIndex + 1)
                    if (next != null) currentPageId = next.id
                },
                onAddPage = {
                    viewModel.addPage(liveSubject.id) { page ->
                        currentPageId = page.id
                        lastCreatedPageId = page.id
                    }
                },
                onUndo = editorHostState.onUndo,
                onRedo = editorHostState.onRedo,
                canUndo = editorHostState.canUndo,
                canRedo = editorHostState.canRedo,
                onAddImage = { currentPage?.let { showImagePickerForPage = it } },
                onEditIndex = { currentPage?.let { showIndexEditorPage = it } },
                onGenerateIndex = triggerBuildIndex,
                onDelete = { currentPage?.let { deleteConfirmPage = it } }
            )
            Box(modifier = Modifier.weight(1f)) {
                FloatingInputContainer(
                    bottomOffset = 16.dp,
                    horizontalMargin = 24.dp,
                    floatingBar = {
                        PageChatBar(
                        targetTitle = currentPage?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.unnamed_page),
                        targetContent = currentPage?.content ?: "",
                        answer = chatAnswer,
                        isLoading = isChatLoading,
                        onSend = { question ->
                            val page = currentPage ?: return@PageChatBar
                            chatAnswer = ""
                            isChatLoading = true
                            viewModel.chatWithPage(
                                page = page,
                                question = question,
                                onToken = { token ->
                                    chatAnswer += token
                                },
                                onComplete = { answer ->
                                    chatAnswer = answer
                                    isChatLoading = false
                                },
                                onError = { error ->
                                    chatAnswer = "请求失败: $error"
                                    isChatLoading = false
                                }
                            )
                        }
                    )
                },
                content = {
                    if (pages.isEmpty()) {
                        EmptyPagesState()
                    } else {
                        val page = currentPage ?: return@FloatingInputContainer
                        NotebookPageEditor(
                            page = page,
                            indexEntry = indexEntries[page.id],
                            onUpdate = { updated ->
                                viewModel.updatePage(liveSubject.id, updated)
                            },
                            onEditIndex = { showIndexEditorPage = page },
                            onFocus = { },
                            autoFocusContent = page.id == lastCreatedPageId,
                            fillHeight = true,
                            hostState = editorHostState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                        )
                    }
                }
            )
            }
        }
    }

    if (showSearch) {
        IndexSearchPanel(
            onSearch = { query ->
                viewModel.searchPages(query, liveSubject)
            },
            onSelectPage = { pageId ->
                currentPageId = pageId
            },
            onClose = { showSearch = false }
        )
    }

    if (showJumpDialog) {
        LingjiDialog(
            onDismissRequest = { showJumpDialog = false; jumpText = "" },
            title = { Text(stringResource(R.string.jump_to_page)) },
            text = {
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { value ->
                        jumpText = value.filter { it.isDigit() }
                    },
                    label = { Text(stringResource(R.string.page_number_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.jump),
                    onClick = {
                        val number = jumpText.toIntOrNull()
                        val index = number?.minus(1)
                        if (index != null && index in pages.indices) {
                            currentPageId = pages[index].id
                        }
                        showJumpDialog = false
                        jumpText = ""
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showJumpDialog = false; jumpText = "" }
                )
            }
        )
    }

    if (showMoveDialog) {
        LingjiDialog(
            onDismissRequest = { showMoveDialog = false; moveText = "" },
            title = { Text(stringResource(R.string.edit_page_position)) },
            text = {
                OutlinedTextField(
                    value = moveText,
                    onValueChange = { value ->
                        moveText = value.filter { it.isDigit() }
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.edit_page_position_hint,
                                pages.size
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.move),
                    onClick = {
                        val number = moveText.toIntOrNull()
                        val index = number?.minus(1)
                        val page = currentPage
                        if (page != null && index != null && index in pages.indices && index != currentPageIndex) {
                            viewModel.movePage(liveSubject.id, page.id, index)
                        }
                        showMoveDialog = false
                        moveText = ""
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showMoveDialog = false; moveText = "" }
                )
            }
        )
    }

    showImagePickerForPage?.let { page ->
        PageImagePicker(
            state = imagePickerState,
            onImagePicked = { base64 ->
                val target = currentPage ?: page
                val markdownImage = "\n\n![图片]($base64)\n\n"
                val cursor = editorHostState.cursorPosition.coerceIn(0, target.content.length)
                val updatedContent = target.content.take(cursor) + markdownImage + target.content.drop(cursor)
                val updated = target.copy(
                    content = updatedContent,
                    updatedAt = System.currentTimeMillis()
                )
                viewModel.updatePage(liveSubject.id, updated)
                showImagePickerForPage = null
            },
            onDismiss = { showImagePickerForPage = null }
        )
    }

    showIndexEditorPage?.let { page ->
        val entry = indexEntries[page.id]
            ?: PageIndexEntry(
                pageId = page.id,
                title = page.title,
                keywords = emptyList(),
                summary = ""
            )
        PageIndexEditorDialog(
            entry = entry,
            onDismiss = { showIndexEditorPage = null },
            onConfirm = { updated ->
                viewModel.updatePageIndexEntry(liveSubject.id, page.id, updated)
                showIndexEditorPage = null
            }
        )
    }

    deleteConfirmPage?.let { page ->
        LingjiDialog(
            onDismissRequest = { deleteConfirmPage = null },
            title = { Text(stringResource(R.string.delete_page)) },
            text = { Text(stringResource(R.string.delete_page_confirm, page.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.unnamed_page))) },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.delete),
                    isDestructive = true,
                    onClick = {
                        val deletedIndex = pages.indexOfFirst { it.id == page.id }
                        val nextPage = pages.getOrNull(deletedIndex + 1)
                            ?: pages.getOrNull(deletedIndex - 1)
                        currentPageId = nextPage?.id
                        viewModel.deletePage(liveSubject.id, page.id)
                        deleteConfirmPage = null
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { deleteConfirmPage = null }
                )
            }
        )
    }

    uiState.aiWarningMessage?.let { warning ->
        LingjiDialog(
            onDismissRequest = { viewModel.clearAiWarning() },
            title = { Text(stringResource(R.string.vision_warning_title)) },
            text = { Text(warning) },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.i_know),
                    onClick = { viewModel.clearAiWarning() }
                )
            }
        )
    }
}

private const val CLIPBOARD_SIZE_LIMIT = 1_000_000

@Composable
private fun NotebookPageToolbar(
    currentPage: NotebookPage?,
    currentPageIndex: Int,
    pagesSize: Int,
    dirtyCount: Int,
    isProcessing: Boolean,
    onSave: () -> Unit,
    onBuildIndex: () -> Unit,
    onJumpToPage: () -> Unit,
    onEditPagePosition: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onAddPage: () -> Unit,
    onUndo: (() -> Unit)?,
    onRedo: (() -> Unit)?,
    canUndo: Boolean,
    canRedo: Boolean,
    onAddImage: () -> Unit,
    onEditIndex: () -> Unit,
    onGenerateIndex: () -> Unit,
    onDelete: () -> Unit
) {
    var showPagePositionMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
) {
        if (pagesSize > 0) {
            // 左侧：保存 + 待索引
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentPage?.let {
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.save))
                    }
                }
                if (dirtyCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = onBuildIndex
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "$dirtyCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onUndo?.invoke() },
                    enabled = canUndo && onUndo != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.cd_undo),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onRedo?.invoke() },
                    enabled = canRedo && onRedo != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.cd_redo),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 中间：页面导航
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevPage,
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_prev_page),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { showPagePositionMenu = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(
                                    R.string.page_position_format,
                                    currentPageIndex + 1,
                                    pagesSize
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showPagePositionMenu,
                        onDismissRequest = { showPagePositionMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.jump_to_page)) },
                            onClick = {
                                showPagePositionMenu = false
                                onJumpToPage()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_page_position)) },
                            onClick = {
                                showPagePositionMenu = false
                                onEditPagePosition()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
                IconButton(
                    onClick = onNextPage,
                    enabled = currentPageIndex < pagesSize - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_next_page),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (pagesSize == 0) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // 右侧：页面操作 + 新增页面
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            currentPage?.let {
                IconButton(
                    onClick = onAddImage,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = stringResource(R.string.cd_add_image),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onEditIndex,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit_page_index),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onGenerateIndex,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.cd_generate_index),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onAddPage,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_page),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyPagesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_pages_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.empty_pages_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import com.lingji.app.R
import com.lingji.app.domain.model.HorizontalSwipeAction
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.Subject
import com.lingji.app.ui.components.ChatMode
import com.lingji.app.ui.components.ChatScope
import com.lingji.app.ui.components.ClipboardTooLargeDialog
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
import com.lingji.app.ui.screens.notebook.DeletePageDialog
import com.lingji.app.ui.screens.notebook.EmptyPagesState
import com.lingji.app.ui.screens.notebook.ExportPdfRangeDialog
import com.lingji.app.ui.screens.notebook.JumpPageDialog
import com.lingji.app.ui.screens.notebook.MovePageDialog
import com.lingji.app.ui.screens.notebook.NotebookEditorArea
import com.lingji.app.ui.screens.notebook.NotebookPageToolbar
import com.lingji.app.ui.screens.notebook.NotebookSubjectTopBar
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.SubjectViewModel
import com.lingji.app.util.MarkdownPdfExporter
import kotlinx.coroutines.launch

private const val MAX_NOTEBOOK_PAGE_CONTENT_CHARS = 1_200_000

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
        // 仅在 currentPageId 为 null 时自动选择最后一页。
        // 不重置非 null 但暂未出现在列表中的 ID——这可能是刚创建、
        // 尚未通过 Flow 传播的新页面，重置会导致跳回第一页。
        if (currentPageId == null) {
            currentPageId = pages.lastOrNull()?.id
        }
    }

    // 检测当前页被删除（如 Agent 工具调用）并切换到相邻页。
    // 通过对比前后 pages 列表区分"被删除"和"尚未传播的新页面"。
    var prevPages by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) {
        val cur = currentPageId
        if (cur != null && pages.isNotEmpty()) {
            val inCurrent = pages.any { it.id == cur }
            val inPrev = prevPages.any { it.id == cur }
            if (!inCurrent && inPrev) {
                // 当前页已被删除，切换到同位置的相邻页
                val prevIndex = prevPages.indexOfFirst { it.id == cur }
                currentPageId = pages.getOrNull(prevIndex)?.id
                    ?: pages.getOrNull((prevIndex - 1).coerceAtLeast(0))?.id
                    ?: pages.lastOrNull()?.id
            }
        }
        prevPages = pages
    }

    // 记忆上次打开的页面
    LaunchedEffect(currentPageId) {
        if (currentPageId != subject.lastOpenedPageId) {
            viewModel.saveLastOpenedPageId(liveSubject.id, currentPageId)
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var showImagePickerForPage by remember { mutableStateOf<NotebookPage?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var chatAnswer by remember { mutableStateOf("") }
    var isChatLoading by remember { mutableStateOf(false) }
    var chatHistory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var deleteConfirmPage by remember { mutableStateOf<NotebookPage?>(null) }
    var lastCreatedPageId by remember { mutableStateOf<String?>(null) }
    var showIndexEditorPage by remember { mutableStateOf<NotebookPage?>(null) }
    var showExportPdfDialog by remember { mutableStateOf(false) }
    var showClipboardTooLargeDialog by remember { mutableStateOf(false) }

    var debugCursorY by remember { mutableStateOf(0f) }
    var debugBarTopY by remember { mutableStateOf(0f) }
    var debugBarHeight by remember { mutableStateOf(0f) }
    var debugVpTop by remember { mutableStateOf(0f) }
    var debugScrollInfo by remember { mutableStateOf("") }
    // 持久化调试：上次滚动的触发条件、方向、量（保持到下次触发才更新）
    var debugTrigger by remember { mutableStateOf("—") }
    var debugDir by remember { mutableStateOf("—") }
    var debugAmount by remember { mutableStateOf("—") }

    val editorHostState = rememberNotebookPageEditorHostState()
    val imagePickerState = rememberImagePickerState()

    // 切换笔记页面时重置为编辑模式
    LaunchedEffect(currentPageId) {
        editorHostState.setPreview(false)
    }

    // 跟踪 viewportTop
    LaunchedEffect(editorHostState.scrollViewportTop) {
        debugVpTop = editorHostState.scrollViewportTop
    }

    // ── 光标跟随：确保光标始终在可视区域内 ──
    val density = LocalDensity.current
    var lastAutoScrollMs by remember { mutableStateOf(0L) }
    LaunchedEffect(debugCursorY, debugBarTopY, debugBarHeight, debugVpTop) {
        val margin = with(density) { 4.dp.toPx() }
        val vpTop = debugVpTop
        val barTop = debugBarTopY
        val cursorY = debugCursorY
        if (barTop <= 0f || vpTop <= 0f || cursorY <= 0f) return@LaunchedEffect

        val now = System.currentTimeMillis()
        if (now - lastAutoScrollMs < 300) return@LaunchedEffect

        if (cursorY > barTop - 60f + margin) {
            // 光标进入对话框遮挡区域（barTop 以下）→ 上滚
            val delta = cursorY - barTop + 60f + margin
            lastAutoScrollMs = now
            debugTrigger = "cursorBelowBar"
            debugDir = "↓上滚(${delta.toInt()}px)"
            debugAmount = ""
            editorHostState.scrollBy(delta)
        } else if (cursorY < vpTop - margin) {
            // 光标在编辑区上方 → 下滚
            val delta = cursorY - vpTop + margin
            lastAutoScrollMs = now
            debugTrigger = "cursorAboveVp"
            debugDir = "↑下滚(${delta.toInt()}px)"
            debugAmount = ""
            editorHostState.scrollBy(delta)
        }
    }

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

    val triggerBuildDirectory = {
        Toast.makeText(
            context,
            context.getString(R.string.building_directory_toast),
            Toast.LENGTH_SHORT
        ).show()
        viewModel.buildDirectory(
            subject = liveSubject,
            onComplete = { directory ->
                Toast.makeText(context, context.getString(R.string.directory_built_toast), Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(
                    context,
                    context.getString(R.string.directory_build_failed_toast, error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    Scaffold(
        topBar = {
            NotebookSubjectTopBar(
                title = liveSubject.title,
                isPreview = editorHostState.isPreview,
                isPagesEmpty = pages.isEmpty(),
                onBack = onBack,
                onTogglePreview = { editorHostState.setPreview(it) },
                onSearch = { showSearch = true },
                onBuildIndex = triggerBuildIndex,
                onBuildDirectory = triggerBuildDirectory,
                onExport = { exportLauncher.launch(viewModel.buildExportFileName(liveSubject.title)) },
                onExportPdf = { if (pages.isNotEmpty()) showExportPdfDialog = true },
                onCopyToClipboard = {
                    scope.launch {
                        try {
                            val encoded = viewModel.exportSubjectToText(liveSubject)
                            if (encoded.length > CLIPBOARD_SIZE_LIMIT) {
                                showClipboardTooLargeDialog = true
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
                }
            )
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
                // ── 调试悬浮窗 ──
                /*
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .zIndex(999f)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = "cY=${debugCursorY.toInt()} barT=${debugBarTopY.toInt()} barH=${debugBarHeight.toInt()} vp=${debugVpTop.toInt()}",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Text(
                                text = debugScrollInfo,
                                color = Color.Yellow,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "触发:${debugTrigger}  方向:${debugDir}  量:${debugAmount}",
                                color = Color(0xFFFF6B6B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                */
                FloatingInputContainer(
                    bottomOffset = 16.dp,
                    horizontalMargin = 24.dp,
                    floatingBar = {
                        PageChatBar(
                        targetTitle = currentPage?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.unnamed_page),
                        targetContent = currentPage?.content ?: "",
                        noteTitle = liveSubject.title,
                        noteContent = liveSubject.aggregatedNote,
                        enableScopeToggle = true,
                        conversationHistory = chatHistory,
                        currentAnswer = chatAnswer,
                        isLoading = isChatLoading,
                        onSend = { question, mode, scope ->
                            if (mode == ChatMode.AGENT) {
                                chatAnswer = ""
                                isChatLoading = true
                                viewModel.chatWithAgent(
                                    subjectId = liveSubject.id,
                                    question = question,
                                    conversationHistory = chatHistory,
                                    onToken = { token -> chatAnswer += token },
                                    onComplete = { answer ->
                                        chatHistory = chatHistory + Pair(question, answer)
                                        chatAnswer = ""
                                        isChatLoading = false
                                    },
                                    onError = { error ->
                                        chatAnswer = "请求失败: $error"
                                        isChatLoading = false
                                    }
                                )
                                return@PageChatBar
                            }
                            // ASK 模式：根据范围选择内容源
                            if (scope == ChatScope.NOTE) {
                                chatAnswer = ""
                                isChatLoading = true
                                viewModel.chatWithNote(
                                    subject = liveSubject,
                                    question = question,
                                    conversationHistory = chatHistory,
                                    onToken = { token -> chatAnswer += token },
                                    onComplete = { answer ->
                                        chatHistory = chatHistory + Pair(question, answer)
                                        chatAnswer = ""
                                        isChatLoading = false
                                    },
                                    onError = { error ->
                                        chatAnswer = "请求失败: $error"
                                        isChatLoading = false
                                    }
                                )
                            } else {
                                val page = currentPage ?: return@PageChatBar
                                chatAnswer = ""
                                isChatLoading = true
                                viewModel.chatWithPage(
                                    page = page,
                                    question = question,
                                    conversationHistory = chatHistory,
                                    onToken = { token -> chatAnswer += token },
                                    onComplete = { answer ->
                                        chatHistory = chatHistory + Pair(question, answer)
                                        chatAnswer = ""
                                        isChatLoading = false
                                    },
                                    onError = { error ->
                                        chatAnswer = "请求失败: $error"
                                        isChatLoading = false
                                    }
                                )
                            }
                        },
                        onClearHistory = {
                            chatHistory = emptyList()
                            chatAnswer = ""
                        },
                        onCollapsedTopYChange = { debugBarTopY = it },
                        onBarLayoutChange = { top, height ->
                            debugBarTopY = top
                            debugBarHeight = height
                        }
                    )
                },
                content = {
                    if (pages.isEmpty()) {
                        EmptyPagesState()
                    } else {
                        val page = currentPage ?: return@FloatingInputContainer
                        NotebookEditorArea(
                            page = page,
                            pages = pages,
                            currentPageIndex = currentPageIndex,
                            swipeAction = uiState.settings.horizontalSwipeAction,
                            editorHostState = editorHostState,
                            lastCreatedPageId = lastCreatedPageId,
                            onUpdate = { updated ->
                                viewModel.updatePage(liveSubject.id, updated)
                            },
                            onPageChange = { pageId -> currentPageId = pageId },
                            onCursorYChange = { debugCursorY = it },
                            onDebugInfo = { debugScrollInfo = it }
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

    if (showExportPdfDialog) {
        ExportPdfRangeDialog(
            pageCount = pages.size,
            currentPageNumber = (currentPageIndex + 1).coerceAtLeast(1),
            onDismiss = { showExportPdfDialog = false },
            onConfirm = { indices, forceWhite ->
                showExportPdfDialog = false
                val selected = indices.mapNotNull { pages.getOrNull(it) }
                if (selected.isEmpty()) {
                    Toast.makeText(context, R.string.export_pdf_range_invalid, Toast.LENGTH_SHORT).show()
                    return@ExportPdfRangeDialog
                }
                val docTitle = liveSubject.title.takeIf { it.isNotBlank() }
                    ?: selected.first().title
                val sections = selected.mapIndexed { idx, page ->
                    val title = page.title.takeIf { it.isNotBlank() }
                        ?: "Page ${idx + 1}"
                    MarkdownPdfExporter.Section(title = title, markdown = page.content)
                }
                MarkdownPdfExporter.exportSectionsToPdf(
                    context = context,
                    docTitle = docTitle,
                    sections = sections,
                    forcePrintWhite = forceWhite,
                    onError = {
                        Toast.makeText(
                            context,
                            R.string.export_pdf_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )
    }

    if (showJumpDialog) {
        JumpPageDialog(
            pageCount = pages.size,
            onJump = { index -> currentPageId = pages[index].id },
            onDismiss = { showJumpDialog = false }
        )
    }

    if (showMoveDialog) {
        MovePageDialog(
            pageCount = pages.size,
            onMove = { index ->
                val page = currentPage
                if (page != null && index != currentPageIndex) {
                    viewModel.movePage(liveSubject.id, page.id, index)
                }
            },
            onDismiss = { showMoveDialog = false }
        )
    }

    showImagePickerForPage?.let { page ->
        PageImagePicker(
            state = imagePickerState,
            onImagePicked = { base64 ->
                val target = currentPage ?: page
                val cursor = editorHostState.cursorPosition.coerceIn(0, target.content.length)
                val before = target.content.take(cursor)
                val after = target.content.drop(cursor)
                val prefix = if (before.isEmpty() || before.endsWith("\n")) "" else "\n"
                val suffix = if (after.isEmpty() || after.startsWith("\n")) "" else "\n"
                val markdownImage = "$prefix![图片]($base64)$suffix"
                val updatedContent = before + markdownImage + after
                if (updatedContent.length > MAX_NOTEBOOK_PAGE_CONTENT_CHARS) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.page_content_too_large_for_image),
                        Toast.LENGTH_SHORT
                    ).show()
                    showImagePickerForPage = null
                    return@PageImagePicker
                }
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
        DeletePageDialog(
            pageTitle = page.title,
            onConfirm = {
                val deletedIndex = pages.indexOfFirst { it.id == page.id }
                val nextPage = pages.getOrNull(deletedIndex + 1)
                    ?: pages.getOrNull(deletedIndex - 1)
                currentPageId = nextPage?.id
                viewModel.deletePage(liveSubject.id, page.id)
            },
            onDismiss = { deleteConfirmPage = null }
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

    if (showClipboardTooLargeDialog) {
        ClipboardTooLargeDialog(onDismiss = { showClipboardTooLargeDialog = false })
    }
}

private const val CLIPBOARD_SIZE_LIMIT = 100_000

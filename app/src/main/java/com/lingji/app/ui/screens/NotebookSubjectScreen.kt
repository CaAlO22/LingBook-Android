package com.lingji.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.lingji.app.ui.components.NotebookPageEditor
import com.lingji.app.ui.components.PageChatBar
import com.lingji.app.ui.components.PageImagePicker
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

    var currentPageId by remember { mutableStateOf(pages.lastOrNull()?.id) }
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

    var showSearch by remember { mutableStateOf(false) }
    var showImagePickerForPage by remember { mutableStateOf<NotebookPage?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var chatAnswer by remember { mutableStateOf("") }
    var isChatLoading by remember { mutableStateOf(false) }
    var deleteConfirmPage by remember { mutableStateOf<NotebookPage?>(null) }
    var lastCreatedPageId by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = 16.dp),
                title = {
                    Text(
                        text = liveSubject.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = NotoSerifCJKsc,
                            fontSize = 40.sp,
                            letterSpacing = (-0.03).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TimeDisplay(modifier = Modifier.padding(end = 8.dp))

                    if (dirtyCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$dirtyCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    if (pages.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                            onClick = { showJumpDialog = true }
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
                                        pages.size
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val prev = pages.getOrNull(currentPageIndex - 1)
                                if (prev != null) currentPageId = prev.id
                            },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.cd_prev_page)
                            )
                        }

                        IconButton(
                            onClick = {
                                val next = pages.getOrNull(currentPageIndex + 1)
                                if (next != null) currentPageId = next.id
                            },
                            enabled = currentPageIndex < pages.lastIndex
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_next_page)
                            )
                        }
                    }

                    IconButton(onClick = {
                        viewModel.buildPageIndexes(
                            subject = liveSubject,
                            onComplete = { _, _ -> },
                            onError = {}
                        )
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_build_index))
                    }
                    IconButton(
                        onClick = {
                            val fileName = liveSubject.title
                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                .takeIf { it.isNotBlank() } ?: "notebook"
                            exportLauncher.launch("$fileName.ling")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.cd_export)
                        )
                    }
                    IconButton(
                        onClick = {
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
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy_to_clipboard)
                        )
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.addPage(liveSubject.id) { page ->
                    currentPageId = page.id
                    lastCreatedPageId = page.id
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_page))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                            onDelete = { deleteConfirmPage = page },
                            onAddImage = { showImagePickerForPage = page },
                            onGenerateIndex = {
                                viewModel.buildPageIndexes(
                                    subject = liveSubject,
                                    onComplete = { _, _ -> },
                                    onError = {}
                                )
                            },
                            onFocus = { },
                            autoFocusContent = page.id == lastCreatedPageId,
                            fillHeight = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                        )
                    }
                }
            )
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
        AlertDialog(
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
                TextButton(
                    onClick = {
                        val number = jumpText.toIntOrNull()
                        val index = number?.minus(1)
                        if (index != null && index in pages.indices) {
                            currentPageId = pages[index].id
                        }
                        showJumpDialog = false
                        jumpText = ""
                    }
                ) {
                    Text(stringResource(R.string.jump))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false; jumpText = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    showImagePickerForPage?.let { page ->
        PageImagePicker(
            state = imagePickerState,
            onImagePicked = { base64 ->
                val target = currentPage ?: page
                val markdownImage = "\n\n![图片]($base64)\n\n"
                val updated = target.copy(
                    content = target.content + markdownImage,
                    updatedAt = System.currentTimeMillis()
                )
                viewModel.updatePage(liveSubject.id, updated)
                showImagePickerForPage = null
            },
            onDismiss = { showImagePickerForPage = null }
        )
    }

    deleteConfirmPage?.let { page ->
        AlertDialog(
            onDismissRequest = { deleteConfirmPage = null },
            title = { Text(stringResource(R.string.delete_page)) },
            text = { Text(stringResource(R.string.delete_page_confirm, page.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.unnamed_page))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePage(liveSubject.id, page.id)
                        deleteConfirmPage = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmPage = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private const val CLIPBOARD_SIZE_LIMIT = 1_000_000

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

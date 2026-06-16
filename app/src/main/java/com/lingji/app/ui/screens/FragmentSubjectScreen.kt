package com.lingji.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.model.Subject
import com.lingji.app.ui.components.FloatingInputContainer
import com.lingji.app.ui.components.FragmentList
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.InputCapsule
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.MarkdownView
import com.lingji.app.ui.components.NoteEditor
import com.lingji.app.ui.components.PageChatBar
import com.lingji.app.ui.components.TimeDisplay
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.SubjectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FragmentSubjectScreen(
    viewModel: SubjectViewModel,
    subject: Subject,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    // 从全局状态实时派生学科数据，确保新增碎片/重构笔记后立即刷新页面，
    // 而不是停留在导航时捕获的旧快照。
    val liveSubject = remember(uiState.subjects, subject.id) {
        uiState.subjects.find { it.id == subject.id } ?: subject
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    var showMenu by remember { mutableStateOf(false) }
    var editingFragment by remember { mutableStateOf<Fragment?>(null) }
    var deadline by remember { mutableStateOf("") }
    var noteChatAnswer by remember { mutableStateOf("") }
    var isNoteChatLoading by remember { mutableStateOf(false) }
    var refineHint by remember { mutableStateOf("") }
    var showRefineDialog by remember { mutableStateOf(false) }
    var planDialogDeadline by remember { mutableStateOf("") }
    var showPlanDialog by remember { mutableStateOf(false) }
    var noteText by remember(liveSubject.aggregatedNote) { mutableStateOf(liveSubject.aggregatedNote) }

    LaunchedEffect(uiState.aiErrorMessage) {
        uiState.aiErrorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearAiError()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    when (pagerState.currentPage) {
                        0 -> TopBarActionButton(
                            label = stringResource(R.string.organize),
                            icon = Icons.Default.AutoFixHigh,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { viewModel.organize(liveSubject) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        1 -> TopBarActionButton(
                            label = stringResource(R.string.refine),
                            icon = Icons.Default.Brush,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            onClick = { showRefineDialog = true },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        2 -> TopBarActionButton(
                            label = stringResource(R.string.plan),
                            icon = Icons.Default.CalendarToday,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            borderColor = MaterialTheme.colorScheme.outline,
                            onClick = {
                                planDialogDeadline = deadline
                                showPlanDialog = true
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    if (pagerState.currentPage == 1) {
                        TextButton(
                            onClick = {
                                viewModel.updateAggregatedNote(noteText)
                                Toast.makeText(context, R.string.note_saved, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                    TimeDisplay(modifier = Modifier.padding(end = 8.dp))
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rollback)) },
                            onClick = {
                                viewModel.rollbackAggregatedNote()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_to_clipboard)) },
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
                                        } else {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(liveSubject.title, encoded))
                                            Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                showMenu = false
                            }
                        )
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
        ) {
            FloatingInputContainer(
                horizontalMargin = 24.dp,
                bottomOffset = 16.dp,
                floatingBar = {
                    // 输入胶囊悬浮在内容之上，透明背景仅输入框可见，
                    // 从而呈现沉浸式悬浮观感而非整版底栏。
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (pagerState.currentPage == 0) {
                            InputCapsule(
                                hint = stringResource(R.string.hint_enter_fragment),
                                onSend = { viewModel.addFragment(it) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (pagerState.currentPage == 1) {
                            PageChatBar(
                                targetTitle = liveSubject.title,
                                targetContent = liveSubject.aggregatedNote,
                                answer = noteChatAnswer,
                                isLoading = isNoteChatLoading,
                                placeholder = stringResource(R.string.note_chat_placeholder),
                                targetLabelFormat = stringResource(R.string.note_chat_target),
                                onSend = { question ->
                                    noteChatAnswer = ""
                                    isNoteChatLoading = true
                                    viewModel.chatWithNote(
                                        subject = liveSubject,
                                        question = question,
                                        onToken = { token ->
                                            noteChatAnswer += token
                                        },
                                        onComplete = { answer ->
                                            noteChatAnswer = answer
                                            isNoteChatLoading = false
                                        },
                                        onError = { error ->
                                            noteChatAnswer = "请求失败: $error"
                                            isNoteChatLoading = false
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                content = {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PillTabSwitcher(
                            selectedIndex = pagerState.currentPage,
                            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f)
                        ) { page ->
                            when (page) {
                                0 -> FragmentList(
                                    fragments = liveSubject.fragments + liveSubject.unmergedFragments,
                                    onEdit = { editingFragment = it },
                                    onDelete = { viewModel.deleteFragment(liveSubject.id, it.id) },
                                    modifier = Modifier.fillMaxSize(),
                                    // 预留底部悬浮输入胶囊的高度，使最后一条碎片可滚动到其上方。
                                    bottomContentPadding = 100.dp
                                )
                                1 -> {
                                    NoteEditor(
                                        value = noteText,
                                        onValueChange = { noteText = it },
                                        modifier = Modifier.fillMaxSize(),
                                        bottomContentPadding = 100.dp
                                    )
                                }
                                2 -> PlanPage(
                                    plan = liveSubject.studyPlan,
                                    deadline = deadline,
                                    onDeadlineChange = { deadline = it },
                                    onGenerate = {
                                        planDialogDeadline = deadline
                                        showPlanDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    if (showRefineDialog) {
        LingjiDialog(
            onDismissRequest = { showRefineDialog = false },
            title = { Text(stringResource(R.string.refine_hint_title)) },
            text = {
                GlassOutlinedTextField(
                    value = refineHint,
                    onValueChange = { refineHint = it },
                    placeholder = { Text(stringResource(R.string.refine_hint_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        viewModel.refine(liveSubject, refineHint.takeIf { it.isNotBlank() })
                        showRefineDialog = false
                        refineHint = ""
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showRefineDialog = false }
                )
            }
        )
    }

    if (showPlanDialog) {
        LingjiDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text(stringResource(R.string.plan_dialog_title)) },
            text = {
                GlassOutlinedTextField(
                    value = planDialogDeadline,
                    onValueChange = { planDialogDeadline = it },
                    placeholder = { Text(stringResource(R.string.plan_deadline_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        deadline = planDialogDeadline
                        viewModel.generatePlan(liveSubject, planDialogDeadline.takeIf { it.isNotBlank() })
                        showPlanDialog = false
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showPlanDialog = false }
                )
            }
        )
    }

    editingFragment?.let { fragment ->
        var content by remember(fragment.id) { mutableStateOf(fragment.content) }
        LingjiDialog(
            onDismissRequest = { editingFragment = null },
            title = { Text(stringResource(R.string.edit_fragment)) },
            text = {
                GlassOutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        viewModel.updateFragment(liveSubject.id, fragment.id, content)
                        editingFragment = null
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { editingFragment = null }
                )
            }
        )
    }
}

private const val CLIPBOARD_SIZE_LIMIT = 1_000_000

@Composable
private fun PillTabSwitcher(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(stringResource(R.string.fragments), Icons.Default.Star, 0),
        Triple(stringResource(R.string.note), Icons.Default.Book, 1),
        Triple(stringResource(R.string.study_plan), Icons.Default.CalendarToday, 2)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(4.dp)
            ) {
                tabs.forEach { (label, icon, index) ->
                    val selected = selectedIndex == index
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        tonalElevation = if (selected) 1.dp else 0.dp,
                        shadowElevation = if (selected) 1.dp else 0.dp,
                        onClick = { onSelect(index) },
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanPage(
    plan: String,
    deadline: String,
    onDeadlineChange: (String) -> Unit,
    onGenerate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassOutlinedTextField(
                    value = deadline,
                    onValueChange = onDeadlineChange,
                    placeholder = { Text(stringResource(R.string.plan_deadline_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (plan.isBlank()) {
                    TextButton(
                        onClick = onGenerate,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.generate_plan),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
        if (plan.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_plan_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            MarkdownView(
                markdown = plan,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun TopBarActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderColor?.let { BorderStroke(1.dp, it) }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

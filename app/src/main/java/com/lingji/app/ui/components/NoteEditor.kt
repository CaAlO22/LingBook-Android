package com.lingji.app.ui.components

import android.util.Log
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import kotlinx.coroutines.launch
import kotlin.math.abs

// 信息岛：碎片笔记目录与搜索——标题解析跳过围栏代码块，搜索保留源文本/渲染文本位置。
private data class NoteHeading(val title: String, val level: Int, val sourceOffset: Int)

private fun parseNoteHeadings(markdown: String): List<NoteHeading> {
    val headings = mutableListOf<NoteHeading>()
    val headingRegex = Regex("^ {0,3}(#{1,6})[ \\t]+(.+?)\\s*$")
    var sourceOffset = 0
    var fence: String? = null
    markdown.split('\n').forEach { line ->
        val trimmed = line.trimStart()
        val fenceMarker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fenceMarker != null) {
            if (fence == null) fence = fenceMarker else if (fence == fenceMarker) fence = null
        } else if (fence == null) {
            headingRegex.find(line)?.let { match ->
                val rawTitle = match.groupValues[2]
                    .replace(Regex("[ \\t]+#+$"), "")
                    .trim()
                val title = rawTitle
                    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
                    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
                    .replace(Regex("[*_`~]"), "")
                if (title.isNotBlank()) {
                    headings += NoteHeading(title, match.groupValues[1].length, sourceOffset)
                }
            }
        }
        sourceOffset += line.length + 1
    }
    return headings
}

private fun findNoteMatches(text: String, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    return Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        .findAll(text)
        .map { it.range }
        .toList()
}

class NoteEditorHostState internal constructor() {
    var scrollByOp by mutableStateOf<((Float) -> Unit)?>(null)
        internal set
    var scrollViewportTop by mutableStateOf(0f)
        internal set
    var isScrollInProgress by mutableStateOf(false)
        internal set

    fun scrollBy(delta: Float) {
        scrollByOp?.invoke(delta)
    }
}

@Composable
fun rememberNoteEditorHostState(): NoteEditorHostState = remember { NoteEditorHostState() }

@Composable
fun NoteEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: String? = null,
    bottomContentPadding: Dp = 8.dp,
    onCursorYChange: ((Float) -> Unit)? = null,
    onDebugInfo: ((String) -> Unit)? = null,
    showNavigationTools: Boolean = false,
    hostState: NoteEditorHostState = remember { NoteEditorHostState() }
) {
    var isPreview by remember { mutableStateOf(false) }
    val undoManager = remember { UndoManager(value) }

    LaunchedEffect(value) {
        if (value != undoManager.value) {
            undoManager.reset(value)
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(undoManager.value)) }
    LaunchedEffect(undoManager.value) {
        if (textFieldValue.text != undoManager.value) {
            textFieldValue = TextFieldValue(undoManager.value)
        }
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldCoordsY by remember { mutableStateOf(0f) }

    // 信息岛：碎片笔记目录与全文搜索，编辑态使用源文本偏移，预览态使用 Markwon 渲染偏移。
    var showToc by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(0) }
    var previewTextView by remember { mutableStateOf<TextView?>(null) }
    var previewText by remember { mutableStateOf("") }
    val headings = remember(value) { parseNoteHeadings(value) }
    val searchableText = if (isPreview) previewText else value
    val searchMatches = remember(searchableText, searchQuery) {
        findNoteMatches(searchableText, searchQuery)
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    fun scrollToTarget(target: Float) {
        isProgrammaticScroll = true
        scope.launch {
            scrollState.animateScrollTo(target.toInt().coerceIn(0, scrollState.maxValue))
        }
    }

    fun scrollToEditOffset(offset: Int) {
        val layout = textLayoutResult ?: return
        val line = layout.getLineForOffset(offset.coerceIn(0, value.length))
        scrollToTarget(layout.getLineTop(line))
    }

    fun scrollToPreviewOffset(offset: Int) {
        if (offset < 0) return
        val textView = previewTextView ?: return
        textView.post {
            val layout = textView.layout ?: return@post
            val safeOffset = offset.coerceIn(0, layout.text.length)
            val line = layout.getLineForOffset(safeOffset)
            scrollToTarget(textView.top + layout.getLineTop(line).toFloat())
        }
    }

    fun scrollToMatch(index: Int) {
        val match = searchMatches.getOrNull(index) ?: return
        currentMatchIndex = index
        if (isPreview) scrollToPreviewOffset(match.first) else scrollToEditOffset(match.first)
    }

    fun scrollToHeading(index: Int) {
        val heading = headings.getOrNull(index) ?: return
        if (!isPreview) {
            scrollToEditOffset(heading.sourceOffset)
            return
        }
        val occurrence = headings.take(index).count { it.title.equals(heading.title, ignoreCase = true) }
        var foundOffset = -1
        var fromIndex = 0
        repeat(occurrence + 1) {
            foundOffset = previewText.indexOf(heading.title, fromIndex, ignoreCase = true)
            if (foundOffset >= 0) fromIndex = foundOffset + heading.title.length
        }
        scrollToPreviewOffset(foundOffset)
    }

    LaunchedEffect(searchQuery, isPreview, value, previewText, showSearch) {
        currentMatchIndex = 0
        if (showSearch && searchQuery.isNotBlank()) scrollToMatch(0)
    }

    // 连续上报光标 Y 坐标
    LaunchedEffect(fieldCoordsY, textFieldValue.selection.start, textLayoutResult) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        onCursorYChange?.invoke(fieldCoordsY + layout.getCursorRect(textFieldValue.selection.start).top)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showNavigationTools) {
                    Box {
                        IconButton(onClick = { showToc = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Toc,
                                contentDescription = stringResource(R.string.note_toc),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showToc,
                            onDismissRequest = { showToc = false },
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .heightIn(max = 360.dp)
                        ) {
                            if (headings.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.note_no_headings)) },
                                    onClick = { showToc = false }
                                )
                            } else {
                                headings.forEachIndexed { index, heading ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = heading.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(start = ((heading.level - 1) * 10).dp)
                                            )
                                        },
                                        onClick = {
                                            showToc = false
                                            scrollToHeading(index)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            showToc = false
                            if (!showSearch) searchQuery = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.note_search),
                            tint = if (showSearch) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { undoManager.undo()?.let(onValueChange) },
                    enabled = undoManager.canUndo
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.cd_undo),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { undoManager.redo()?.let(onValueChange) },
                    enabled = undoManager.canRedo
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.cd_redo),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        ModeChip(
                            label = stringResource(R.string.mode_edit),
                            selected = !isPreview,
                            onClick = { isPreview = false }
                        )
                        ModeChip(
                            label = stringResource(R.string.mode_preview),
                            selected = isPreview,
                            onClick = { isPreview = true }
                        )
                    }
                }
            }
        }

        if (showNavigationTools && showSearch) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.note_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = if (searchMatches.isEmpty()) {
                                stringResource(R.string.search_no_results)
                            } else {
                                stringResource(
                                    R.string.note_search_match_count,
                                    (currentMatchIndex + 1).coerceAtMost(searchMatches.size),
                                    searchMatches.size
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = {
                            scrollToMatch((currentMatchIndex - 1 + searchMatches.size) % searchMatches.size)
                        },
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.cd_search_previous),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { scrollToMatch((currentMatchIndex + 1) % searchMatches.size) },
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_search_next),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            showSearch = false
                            searchQuery = ""
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close_search),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        val focusManager = LocalFocusManager.current

        var lastScrollValue by remember { mutableStateOf(0) }
        var lastFocusTimeMs by remember { mutableStateOf(0L) }

        // ── 收起输入法后恢复原滚动位置 ──
        // 键盘开始弹出时记录当前滚动值；键盘完全收起后，若期间用户没有主动拖拽滚动，
        // 则平滑滚回该位置。光标跟随产生的程序滚动不影响恢复。
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0
        var scrollBeforeIme by remember { mutableStateOf<Int?>(null) }
        var userScrolledWhileIme by remember { mutableStateOf(false) }

        SideEffect {
            hostState.scrollByOp = { delta ->
                isProgrammaticScroll = true
                scope.launch {
                    if (abs(delta) > 160f) {
                        // 大跨度修正（如点击远处文本）用动画平滑过渡。
                        val target = (scrollState.value + delta.toInt()).coerceIn(0, scrollState.maxValue)
                        scrollState.animateScrollTo(target)
                    } else {
                        // 打字时的小步跟随直接即时滚动：弹簧动画每次按键都会被打断重启，
                        // 叠加"动画中跳过修正、结束后再补一次"会造成一卡一卡的阶梯感。
                        scrollState.scroll { scrollBy(delta) }
                    }
                }
            }
        }

        // 同步滚动状态到宿主，供光标跟随逻辑判断是否跳过修正。
        // 动画结束后重置 isProgrammaticScroll，恢复用户拖拽检测能力。
        LaunchedEffect(scrollState.isScrollInProgress) {
            hostState.isScrollInProgress = scrollState.isScrollInProgress
            if (!scrollState.isScrollInProgress) {
                isProgrammaticScroll = false
            }
        }

        // 滚动状态监控
        LaunchedEffect(Unit) {
            snapshotFlow {
                floatArrayOf(
                    scrollState.value.toFloat(), scrollState.maxValue.toFloat(),
                    if (scrollState.isScrollInProgress) 1f else 0f
                )
            }.collect { arr ->
                val msg = "scroll=${arr[0].toInt()}/${arr[1].toInt()} prog=${arr[2].toInt()}"
                onDebugInfo?.invoke(msg)
                Log.d("NoteEditorDebug", msg)
            }
        }

        // 键盘弹出/收起时记录与恢复滚动位置。
        LaunchedEffect(imeVisible) {
            if (imeVisible) {
                userScrolledWhileIme = false
                if (scrollBeforeIme == null) scrollBeforeIme = scrollState.value
            } else {
                val target = scrollBeforeIme
                scrollBeforeIme = null
                if (target != null && !userScrolledWhileIme) {
                    isProgrammaticScroll = true
                    scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
                }
            }
        }

        // 用户拖拽检测 → 清除焦点
        LaunchedEffect(Unit) {
            snapshotFlow { scrollState.value }
                .collect { current ->
                    val delta = current - lastScrollValue
                    if ((delta > 8 || delta < -8) && !isProgrammaticScroll) {
                        userScrolledWhileIme = true
                        if (System.currentTimeMillis() - lastFocusTimeMs > 500) {
                            focusManager.clearFocus()
                        }
                    }
                    lastScrollValue = current
                }
        }

        // 滚动区域撑满剩余空间，底部 padding 确保文字可滚动到悬浮栏后方
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .onGloballyPositioned { coords ->
                    hostState.scrollViewportTop = coords.positionInRoot().y
                }
                .verticalScroll(scrollState)
                .padding(bottom = 160.dp)
        ) {
            if (isPreview) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    MarkdownView(
                        markdown = undoManager.value,
                        compact = true,
                        onTextViewReady = { textView ->
                            previewTextView = textView
                            val renderedText = textView.text.toString()
                            if (previewText != renderedText) previewText = renderedText
                        }
                    )
                }
            } else {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newTfv ->
                        if (newTfv.text != undoManager.value) {
                            undoManager.update(newTfv.text)
                            onValueChange(undoManager.value)
                        }
                        textFieldValue = newTfv
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                lastFocusTimeMs = System.currentTimeMillis()
                            }
                        }
                        .onGloballyPositioned { coords ->
                            fieldCoordsY = coords.positionInRoot().y
                        }
                        // 只读态交给默认处理；可编辑态下方向键只作用于光标，避免焦点跳走。
                        .then(
                            if (readOnly) {
                                Modifier
                            } else {
                                Modifier.arrowKeyCursorBehavior(
                                    textFieldValue,
                                    { textFieldValue = it },
                                ) { textLayoutResult }
                            }
                        ),
                    onTextLayout = { textLayoutResult = it },
                    readOnly = readOnly,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (textFieldValue.text.isEmpty() && value.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.content_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = if (selected) {
                MaterialTheme.colorScheme.surface
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
            tonalElevation = if (selected) 1.dp else 0.dp,
            shadowElevation = if (selected) 0.5.dp else 0.dp,
            onClick = onClick
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
            )
        }
    }
}

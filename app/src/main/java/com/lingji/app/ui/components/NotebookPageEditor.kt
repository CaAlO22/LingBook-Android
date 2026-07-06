package com.lingji.app.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.NotebookPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PageEditState(val title: String, val content: String)

/**
 * 将页面内容解析为可编辑的文本段与图片段，按原有顺序混排。
 */
private sealed class PageSegment {
    data class Text(val text: String) : PageSegment()
    data class Image(val url: String) : PageSegment()
}

private fun imageMarkdown(url: String): String = "![图片]($url)"

private fun parsePageContent(content: String): List<PageSegment> {
    if (content.isEmpty()) return listOf(PageSegment.Text(""))
    val result = mutableListOf<PageSegment>()
    val regex = "!\\[.*?\\]\\((data:image/[^)]+)\\)".toRegex()
    var lastIndex = 0
    regex.findAll(content).forEach { match ->
        if (match.range.first > lastIndex) {
            result.add(PageSegment.Text(content.substring(lastIndex, match.range.first)))
        }
        result.add(PageSegment.Image(match.groupValues[1]))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < content.length) {
        result.add(PageSegment.Text(content.substring(lastIndex)))
    } else if (result.lastOrNull() is PageSegment.Image) {
        result.add(PageSegment.Text(""))
    }
    return result
}

private fun List<PageSegment>.toContent(): String = joinToString("") {
    when (it) {
        is PageSegment.Text -> it.text
        is PageSegment.Image -> imageMarkdown(it.url)
    }
}

private fun List<PageSegment>.offsetBefore(textIndex: Int): Int {
    var offset = 0
    for (i in 0 until textIndex.coerceAtMost(size)) {
        offset += when (val segment = this[i]) {
            is PageSegment.Text -> segment.text.length
            is PageSegment.Image -> imageMarkdown(segment.url).length
        }
    }
    return offset
}

/**
 * 用于将 [NotebookPageEditor] 内部的撤销/重做等操作暴露给外部宿主的状态持有者。
 */
class NotebookPageEditorHostState internal constructor() {
    var onUndo by mutableStateOf<(() -> Unit)?>(null)
        internal set
    var onRedo by mutableStateOf<(() -> Unit)?>(null)
        internal set
    var canUndo by mutableStateOf(false)
        internal set
    var canRedo by mutableStateOf(false)
        internal set
    var isDirty by mutableStateOf(false)
        internal set
    var isPreview by mutableStateOf(false)
        internal set
    var cursorPosition by mutableStateOf(0)
        internal set
    var scrollByOp by mutableStateOf<((Float) -> Unit)?>(null)
        internal set
    var scrollViewportTop by mutableStateOf(0f)
        internal set

    fun setPreview(value: Boolean) {
        isPreview = value
    }

    fun scrollBy(delta: Float) {
        scrollByOp?.invoke(delta)
    }
}

@Composable
fun rememberNotebookPageEditorHostState(): NotebookPageEditorHostState = remember { NotebookPageEditorHostState() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookPageEditor(
    page: NotebookPage,
    onUpdate: (NotebookPage) -> Unit,
    onFocus: () -> Unit,
    autoFocusContent: Boolean = false,
    fillHeight: Boolean = false,
    hostState: NotebookPageEditorHostState = remember { NotebookPageEditorHostState() },
    modifier: Modifier = Modifier,
    onCursorYChange: ((Float) -> Unit)? = null,
    onDebugInfo: ((String) -> Unit)? = null
) {
    val undoManager = remember(page.id) { UndoManager(PageEditState(page.title, page.content)) }
    val editState = undoManager.value
    val title = editState.title
    val content = editState.content
    val isDirty = page.indexedAt <= 0 || page.updatedAt > page.indexedAt
    val isPreview = hostState.isPreview

    val segments = remember(content) { parsePageContent(content) }
    val textFieldValues = remember { mutableStateMapOf<Int, TextFieldValue>() }
    var focusedTextIndex by remember { mutableStateOf<Int?>(null) }
    val firstTextFocusRequester = remember { FocusRequester() }
    val layoutResults = remember { mutableStateMapOf<Int, TextLayoutResult>() }
    var focusedCoordsY by remember { mutableStateOf(0f) }
    var focusedSelection by remember { mutableStateOf(0) }

    // 当外部传入的页面数据与当前编辑状态不一致时（如从其他端同步），重置历史。
    LaunchedEffect(page.title, page.content) {
        val current = undoManager.value
        if (page.title != current.title || page.content != current.content) {
            undoManager.reset(PageEditState(page.title, page.content))
        }
    }

    // 外部 content 变化时同步各文本段的 TextFieldValue（例如插入图片、撤销/重做后）。
    LaunchedEffect(content) {
        segments.forEachIndexed { index, segment ->
            if (segment is PageSegment.Text) {
                val existing = textFieldValues[index]
                if (existing == null || existing.text != segment.text) {
                    textFieldValues[index] = TextFieldValue(
                        segment.text,
                        selection = TextRange(0)
                    )
                }
            }
        }
    }

    // 将撤销/重做等操作暴露给宿主（如页面上方工具栏）。
    SideEffect {
        hostState.canUndo = undoManager.canUndo
        hostState.canRedo = undoManager.canRedo
        hostState.isDirty = isDirty
        hostState.onUndo = {
            undoManager.undo()?.let { updated ->
                onUpdate(page.copy(title = updated.title, content = updated.content, updatedAt = System.currentTimeMillis()))
            }
        }
        hostState.onRedo = {
            undoManager.redo()?.let { updated ->
                onUpdate(page.copy(title = updated.title, content = updated.content, updatedAt = System.currentTimeMillis()))
            }
        }
        hostState.cursorPosition = focusedTextIndex?.let { index ->
            val offsetBefore = segments.offsetBefore(index)
            val selectionStart = textFieldValues[index]?.selection?.start ?: 0
            (offsetBefore + selectionStart).coerceIn(0, content.length)
        } ?: content.length
    }

    // 聚焦段的光标 Y 坐标变化时，通过回调对外汇报。
    LaunchedEffect(focusedTextIndex, focusedCoordsY, focusedSelection, layoutResults) {
        val index = focusedTextIndex ?: return@LaunchedEffect
        val layout = layoutResults[index] ?: return@LaunchedEffect
        onCursorYChange?.invoke(focusedCoordsY + layout.getCursorRect(focusedSelection).top)
    }

    // 聚焦段的光标 Y 坐标变化时，通过回调对外汇报。
    LaunchedEffect(focusedTextIndex, focusedCoordsY, focusedSelection, layoutResults) {
        val index = focusedTextIndex ?: return@LaunchedEffect
        val layout = layoutResults[index] ?: return@LaunchedEffect
        onCursorYChange?.invoke(focusedCoordsY + layout.getCursorRect(focusedSelection).top)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
    ) {
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        // 标记是否为程序化滚动（光标跟随），与用户手动滚动区分。
        var isProgrammaticScroll by remember { mutableStateOf(false) }
        // 跟踪上一次的 scroll 值，用于判断是否发生用户拖动
        var lastScrollValue by remember { mutableStateOf(0) }
        // 记录焦点被设定的时间，用于抑制获焦后惯性滚动导致的误清除
        var lastFocusTimeMs by remember { mutableStateOf(0L) }
        // 文本区至少撑满剩余可视空间，保证内容短时也能沉浸在输入栏后方。
        val contentHeight = if (fillHeight) {
            (maxHeight - 80.dp).coerceAtLeast(220.dp)
        } else {
            220.dp
        }

        // ── 调试：监控滚动状态 ──
        var debugMaxHeightDp by remember { mutableStateOf(0f) }
        SideEffect { debugMaxHeightDp = maxHeight.value }
        LaunchedEffect(Unit) {
            snapshotFlow {
                floatArrayOf(
                    scrollState.value.toFloat(), scrollState.maxValue.toFloat(),
                    debugMaxHeightDp,
                    (focusedTextIndex ?: -1).toFloat(),
                    if (scrollState.isScrollInProgress) 1f else 0f
                )
            }.collect { arr ->
                val msg = "scroll=${arr[0].toInt()}/${arr[1].toInt()} maxH=${arr[2].toInt()}dp focus=${arr[3].toInt()} prog=${arr[4].toInt()}"
                onDebugInfo?.invoke(msg)
                Log.d("NbEditorDebug", msg)
            }
        }

        // 将平滑滚动能力暴露给宿主（光标跟随功能）。
        SideEffect {
            hostState.scrollByOp = { delta ->
                isProgrammaticScroll = true
                scope.launch {
                    val target = (scrollState.value + delta.toInt()).coerceIn(0, scrollState.maxValue)
                    Log.d("NbEditorDebug", "scrollBy delta=$delta target=$target current=${scrollState.value} max=${scrollState.maxValue}")
                    scrollState.animateScrollTo(target)
                }
            }
        }

        // 用户手动拖拽滚动时取消焦点，避免光标跟随冲突。
        // 用增量检测区分用户拖拽（大增量）和惯性滚动（小增量）。
        LaunchedEffect(Unit) {
            snapshotFlow { scrollState.value }
                .collect { current ->
                    val delta = current - lastScrollValue
                    if ((delta > 8 || delta < -8) && !isProgrammaticScroll) {
                        if (focusedTextIndex != null && System.currentTimeMillis() - lastFocusTimeMs > 500) {
                            focusManager.clearFocus()
                        }
                    }
                    lastScrollValue = current
                }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            UnderlinedTitleField(
                value = title,
                onValueChange = {
                    undoManager.update(PageEditState(it, content))
                    val updated = undoManager.value
                    onUpdate(page.copy(title = updated.title, content = updated.content, updatedAt = System.currentTimeMillis()))
                },
                onFocus = onFocus,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (autoFocusContent) {
                androidx.compose.runtime.LaunchedEffect(page.id) {
                    firstTextFocusRequester.requestFocus()
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        hostState.scrollViewportTop = coords.positionInRoot().y
                    }
                    .verticalScroll(scrollState)
                    .padding(bottom = 160.dp)
            ) {
                Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = contentHeight)
            ) {
                if (isPreview) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    ) {
                        MarkdownView(
                            markdown = content,
                            modifier = Modifier.fillMaxWidth(),
                            compact = true
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    ) {
                        var textFieldCount = 0
                        segments.forEachIndexed { index, segment ->
                            when (segment) {
                                is PageSegment.Text -> {
                                    val isFirstTextField = textFieldCount == 0
                                    textFieldCount++
                                    val value = textFieldValues[index]
                                        ?: TextFieldValue(segment.text)
                                    BasicTextField(
                                        value = value,
                                        onValueChange = { newValue ->
                                            textFieldValues[index] = newValue
                                            if (focusedTextIndex == index) {
                                                focusedSelection = newValue.selection.start
                                            }
                                            val newContent = segments.mapIndexed { i, seg ->
                                                when {
                                                    i == index && seg is PageSegment.Text -> newValue.text
                                                    seg is PageSegment.Text -> seg.text
                                                    seg is PageSegment.Image -> imageMarkdown(seg.url)
                                                    else -> ""
                                                }
                                            }.joinToString("")
                                            if (newContent != content) {
                                                undoManager.update(PageEditState(title, newContent))
                                                val updated = undoManager.value
                                                onUpdate(
                                                    page.copy(
                                                        title = updated.title,
                                                        content = updated.content,
                                                        updatedAt = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                            hostState.cursorPosition =
                                                (segments.offsetBefore(index) + newValue.selection.start)
                                                    .coerceIn(0, newContent.length)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (isFirstTextField) {
                                                    Modifier.focusRequester(firstTextFocusRequester)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .onFocusChanged { focusState ->
                                                Log.d("NbEditorDebug", "focusChanged: idx=$index focused=${focusState.isFocused} scrollVal=${scrollState.value}")
                                                if (focusState.isFocused) {
                                                    lastFocusTimeMs = System.currentTimeMillis()
                                                    focusedTextIndex = index
                                                    onFocus()
                                                } else if (focusedTextIndex == index) {
                                                    focusedTextIndex = null
                                                }
                                            }
                                            .onGloballyPositioned { coords ->
                                                val y = coords.positionInRoot().y
                                                Log.d("NbEditorDebug", "tf positioned: idx=$index y=$y scrollVal=${scrollState.value}")
                                                if (focusedTextIndex == index) {
                                                    focusedCoordsY = y
                                                }
                                            },
                                        onTextLayout = { result ->
                                            layoutResults[index] = result
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (value.text.isEmpty() && content.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.content_hint),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                }
                                is PageSegment.Image -> {
                                    ImageCard(
                                        imageUrl = segment.url,
                                        onRemove = {
                                            val newSegments = segments.toMutableList().apply {
                                                removeAt(index)
                                            }
                                            val newContent = newSegments.toContent()
                                            if (newContent != content) {
                                                undoManager.update(PageEditState(title, newContent))
                                                val updated = undoManager.value
                                                onUpdate(
                                                    page.copy(
                                                        title = updated.title,
                                                        content = updated.content,
                                                        updatedAt = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                            if (index != segments.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun UnderlinedTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .padding(end = 8.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Column {
                innerTextField()
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }
    )
}

@Composable
private fun ImageCard(
    imageUrl: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imageUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(imageUrl) {
        bitmap = withContext(Dispatchers.IO) {
            decodeBase64Image(imageUrl)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                val aspectRatio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(0.001f)
                val targetHeight = (maxWidth / aspectRatio).coerceAtLeast(120.dp)
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_remove_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(targetHeight),
                    contentScale = ContentScale.Fit
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
        ) {
            Surface(shape = RoundedCornerShape(percent = 50), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_remove_image),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(4.dp).size(16.dp)
                )
            }
        }
    }
}

private fun decodeBase64Image(imageUrl: String): android.graphics.Bitmap? {
    return try {
        val base64Part = imageUrl.substringAfter(",", "")
        if (base64Part.isEmpty()) return null
        val bytes = Base64.decode(base64Part, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 720)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var inSampleSize = 1
    val halfWidth = width / 2
    val halfHeight = height / 2
    while (halfWidth / inSampleSize >= maxDimension || halfHeight / inSampleSize >= maxDimension) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}


package com.lingji.app.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import kotlinx.coroutines.launch

class NoteEditorHostState internal constructor() {
    var scrollByOp by mutableStateOf<((Float) -> Unit)?>(null)
        internal set
    var scrollViewportTop by mutableStateOf(0f)
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

        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        var isProgrammaticScroll by remember { mutableStateOf(false) }
        var lastScrollValue by remember { mutableStateOf(0) }
        var lastFocusTimeMs by remember { mutableStateOf(0L) }

        SideEffect {
            hostState.scrollByOp = { delta ->
                isProgrammaticScroll = true
                scope.launch {
                    val target = (scrollState.value + delta.toInt()).coerceIn(0, scrollState.maxValue)
                    scrollState.animateScrollTo(target)
                }
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

        // 用户拖拽检测 → 清除焦点
        LaunchedEffect(Unit) {
            snapshotFlow { scrollState.value }
                .collect { current ->
                    val delta = current - lastScrollValue
                    if ((delta > 8 || delta < -8) && !isProgrammaticScroll) {
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
                    MarkdownView(markdown = undoManager.value, compact = true)
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
                        },
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

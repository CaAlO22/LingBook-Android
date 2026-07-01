package com.lingji.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lingji.app.R

enum class ChatMode { ASK, AGENT }

@Composable
fun PageChatBar(
    targetTitle: String,
    targetContent: String,
    conversationHistory: List<Pair<String, String>>,
    currentAnswer: String,
    isLoading: Boolean,
    onSend: (String, ChatMode) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.chat_placeholder),
    targetLabelFormat: String = stringResource(R.string.chat_target)
) {
    var question by remember { mutableStateOf(TextFieldValue("")) }
    var answerExpanded by remember { mutableStateOf(true) }
    var chatMode by remember { mutableStateOf(ChatMode.ASK) }
    val agentLabelFormat = stringResource(R.string.chat_agent_target)
    val hasHistory = conversationHistory.isNotEmpty() || currentAnswer.isNotBlank() || isLoading
    val inputReady = if (chatMode == ChatMode.AGENT) {
        question.text.isNotBlank() && !isLoading
    } else {
        question.text.isNotBlank() && targetContent.isNotBlank() && !isLoading
    }
    val enabled = inputReady
    val listState = rememberLazyListState()

    val submitQuestion: () -> Unit = {
        val text = question.text.trim()
        if (text.isNotBlank() && inputReady) {
            answerExpanded = true
            onSend(text, chatMode)
            question = TextFieldValue("")
        }
    }

    LaunchedEffect(conversationHistory.size, currentAnswer) {
        if (conversationHistory.isNotEmpty() || currentAnswer.isNotBlank()) {
            listState.animateScrollToItem(0)
        }
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            AnimatedVisibility(visible = hasHistory) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ai_answer),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Row {
                            if (conversationHistory.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearHistory,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = stringResource(R.string.cd_clear_history),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { answerExpanded = !answerExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (answerExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = stringResource(R.string.cd_expand),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (answerExpanded) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .padding(vertical = 4.dp),
                            reverseLayout = true
                        ) {
                            if (isLoading && currentAnswer.isBlank()) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = " ${stringResource(R.string.thinking)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (currentAnswer.isNotBlank()) {
                                item {
                                    MarkdownView(
                                        markdown = currentAnswer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                            if (!isLoading && currentAnswer.isBlank() && conversationHistory.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(0.dp))
                                }
                            }
                            items(conversationHistory.reversed()) { (q, a) ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = q,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    MarkdownView(
                                        markdown = a,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            val displayTitle = targetTitle.takeIf { it.isNotBlank() } ?: stringResource(R.string.unnamed_page)
            val modeText = when (chatMode) {
                ChatMode.ASK -> targetLabelFormat.format(displayTitle)
                ChatMode.AGENT -> agentLabelFormat.format(displayTitle)
            }
            val modeColor = when (chatMode) {
                ChatMode.ASK -> MaterialTheme.colorScheme.onSurfaceVariant
                ChatMode.AGENT -> MaterialTheme.colorScheme.tertiary
            }
            Text(
                text = modeText,
                style = MaterialTheme.typography.labelSmall,
                color = modeColor,
                modifier = Modifier
                    .clickable { chatMode = if (chatMode == ChatMode.ASK) ChatMode.AGENT else ChatMode.ASK }
                    .padding(start = 4.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val inputEnabled = if (chatMode == ChatMode.AGENT) {
                    !isLoading
                } else {
                    targetContent.isNotBlank() && !isLoading
                }
                BasicTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
                                return@onPreviewKeyEvent false
                            }
                            if (event.isCtrlPressed || event.isShiftPressed) {
                                // Ctrl/Shift+Enter 插入换行，由系统行为接管
                                val current = question
                                val newText = current.text.replaceRange(
                                    current.selection.start,
                                    current.selection.end,
                                    "\n"
                                )
                                val cursor = current.selection.start + 1
                                question = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(cursor)
                                )
                                true
                            } else {
                                submitQuestion()
                                true
                            }
                        },
                    enabled = inputEnabled,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (inputEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (question.text.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                IconButton(
                    onClick = submitQuestion,
                    enabled = enabled
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.Transparent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.cd_send),
                                tint = if (enabled) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

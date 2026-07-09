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
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingji.app.R

enum class ChatMode { ASK, AGENT, FRAGMENT }
enum class ChatScope { PAGE, NOTE }

@Composable
fun PageChatBar(
    targetTitle: String,
    targetContent: String,
    conversationHistory: List<Pair<String, String>>,
    currentAnswer: String,
    isLoading: Boolean,
    onSend: (String, ChatMode, ChatScope) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    noteTitle: String = "",
    noteContent: String = "",
    enableScopeToggle: Boolean = false,
    initialScope: ChatScope = ChatScope.PAGE,
    onCollapsedTopYChange: ((Float) -> Unit)? = null,
    onBarLayoutChange: ((Float, Float) -> Unit)? = null
) {
    var question by remember { mutableStateOf(TextFieldValue("")) }
    var answerExpanded by remember { mutableStateOf(true) }
    var chatMode by remember { mutableStateOf(ChatMode.ASK) }
    var chatScope by remember { mutableStateOf(initialScope) }
    val hasHistory = conversationHistory.isNotEmpty() || currentAnswer.isNotBlank() || isLoading

    val modeAskLabel = stringResource(R.string.chat_mode_ask)
    val modeAgentLabel = stringResource(R.string.chat_mode_agent)
    val scopeNoteLabel = stringResource(R.string.chat_scope_note)
    val unnamedPage = stringResource(R.string.unnamed_page)
    val pagePlaceholder = stringResource(R.string.chat_placeholder)
    val notePlaceholder = stringResource(R.string.note_chat_placeholder)
    val agentPlaceholder = stringResource(R.string.chat_agent_placeholder)

    // 根据模式和范围选择有效内容
    val effectiveContent = when {
        chatMode == ChatMode.AGENT -> ""  // Agent 不需要静态内容
        chatScope == ChatScope.NOTE -> noteContent
        else -> targetContent
    }
    val inputReady = if (chatMode == ChatMode.AGENT) {
        question.text.isNotBlank() && !isLoading
    } else {
        question.text.isNotBlank() && effectiveContent.isNotBlank() && !isLoading
    }
    val enabled = inputReady
    val listState = rememberLazyListState()

    val submitQuestion: () -> Unit = {
        val text = question.text.trim()
        if (text.isNotBlank() && inputReady) {
            answerExpanded = true
            onSend(text, chatMode, chatScope)
            question = TextFieldValue("")
        }
    }

    LaunchedEffect(conversationHistory.size, currentAnswer) {
        if (conversationHistory.isNotEmpty() || currentAnswer.isNotBlank()) {
            listState.animateScrollToItem(0)
        }
    }

    // 当前 placeholder
    val currentPlaceholder = when {
        chatMode == ChatMode.AGENT -> agentPlaceholder
        chatScope == ChatScope.NOTE -> notePlaceholder
        else -> pagePlaceholder
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                val height = coordinates.size.height.toFloat()
                onBarLayoutChange?.invoke(top, height)
                if (!answerExpanded || !hasHistory) {
                    onCollapsedTopYChange?.invoke(top)
                }
            },
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

            // ── 双区域可点击标签：左侧切换模式(ASK/AGENT)，右侧切换范围(当前页/整本笔记) ──
            val modeLabel = if (chatMode == ChatMode.ASK) modeAskLabel else modeAgentLabel
            val modeColor = if (chatMode == ChatMode.AGENT)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.onSurfaceVariant

            val scopeLabel = when (chatScope) {
                ChatScope.PAGE -> targetTitle.takeIf { it.isNotBlank() } ?: unnamedPage
                ChatScope.NOTE -> if (noteTitle.isNotBlank()) noteTitle else scopeNoteLabel
            }
            val scopeColor = MaterialTheme.colorScheme.primary

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：模式切换芯片
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = modeColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            chatMode = if (chatMode == ChatMode.ASK) ChatMode.AGENT else ChatMode.ASK
                        }
                ) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = modeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                // 右侧：范围切换芯片
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (enableScopeToggle) scopeColor.copy(alpha = 0.10f) else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enableScopeToggle) {
                            chatScope = if (chatScope == ChatScope.PAGE) ChatScope.NOTE else ChatScope.PAGE
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = scopeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (enableScopeToggle) scopeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = if (enableScopeToggle) 2.dp else 0.dp)
                        )
                        if (enableScopeToggle) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = scopeColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val inputEnabled = if (chatMode == ChatMode.AGENT) {
                    !isLoading
                } else {
                    effectiveContent.isNotBlank() && !isLoading
                }
                BasicTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .enterSendBehavior(question, { question = it }, submitQuestion),
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
                                    text = currentPlaceholder,
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

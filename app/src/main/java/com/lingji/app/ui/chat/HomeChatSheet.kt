package com.lingji.app.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.ui.components.ChatMode
import com.lingji.app.ui.components.GlassSurface
import com.lingji.app.ui.components.MarkdownView
import com.lingji.app.ui.components.enterSendBehavior
import com.lingji.app.ui.components.uriToBase64
import com.lingji.app.ui.viewmodel.HomeChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeChatSheet(
    messages: List<HomeChatMessage>,
    streamLine: String,
    isLoading: Boolean,
    currentMode: ChatMode,
    conversations: List<HomeConversationEntity>,
    currentConversationId: String?,
    fragments: List<String>,
    onSend: (String, List<String>) -> Unit,
    onModeChange: (ChatMode) -> Unit,
    onNewConversation: () -> Unit,
    onLoadConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteFragment: (Int) -> Unit,
    onOrganizeFragments: () -> Unit,
    onDismiss: () -> Unit,
    supportsVision: Boolean = false
) {
    val TAG = "HomeChat"

    // Log.d(TAG, "UI: HomeChatSheet recompose | msgs=${messages.size} streamLineLen=${streamLine.length} loading=$isLoading mode=$currentMode convList=${conversations.size} currentConvId=${currentConversationId?.take(8)}")
    // if (messages.isNotEmpty()) {
    //     messages.forEachIndexed { i, m ->
    //         Log.d(TAG, "UI:   msg[$i] role=${m.role} contentLen=${m.content.length} content=${m.content.take(60)}")
    //     }
    // }

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    val context = LocalContext.current
    val selectedImages = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = uriToBase64(context, uri)
            if (base64 != null) {
                selectedImages.add(base64)
            }
        }
    }

    // Responsive bubble width: 72% of screen, clamped between 320dp (phone) and 560dp (tablet)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxBubbleWidth = (screenWidthDp * 0.72f).coerceIn(320f, 560f).dp

    val submitInput: () -> Unit = {
        val text = inputText.text.trim()
        if ((text.isNotBlank() || selectedImages.isNotEmpty()) && !isLoading) {
            onSend(text, selectedImages.toList())
            inputText = TextFieldValue("")
            selectedImages.clear()
        }
    }

    LaunchedEffect(messages.size, streamLine) {
        if (messages.isNotEmpty() || streamLine.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .pointerInput(currentMode) {
                val swipeThreshold = 150f
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (totalDrag > swipeThreshold && currentMode == ChatMode.AGENT) {
                            onModeChange(ChatMode.FRAGMENT)
                        } else if (totalDrag < -swipeThreshold && currentMode == ChatMode.FRAGMENT) {
                            onModeChange(ChatMode.AGENT)
                        }
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, amount -> totalDrag += amount }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.Center)
                .statusBarsPadding()
                .imePadding()
        ) {
            // --- Top Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_chat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    IconButton(
                        onClick = {
                            Log.d(TAG, "EVENT: history icon clicked | conversations.size=${conversations.size} currentId=${currentConversationId}")
                            showHistory = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.home_chat_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showHistory,
                        onDismissRequest = { showHistory = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_chat_new_conversation)) },
                            onClick = {
                                Log.d(TAG, "EVENT: new conversation clicked")
                                onNewConversation()
                                showHistory = false
                            },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                        )
                        if (conversations.isNotEmpty()) {
                            conversations.take(20).forEach { conv ->
                                val isActive = conv.id == currentConversationId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = conv.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isActive) MaterialTheme.colorScheme.tertiary
                                                    else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = dateFormat.format(Date(conv.updated_at)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        Log.d(TAG, "EVENT: history item clicked | id=${conv.id} title=${conv.title}")
                                        onLoadConversation(conv.id)
                                        showHistory = false
                                    },
                                    trailingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable {
                                                    Log.d(TAG, "EVENT: delete conversation clicked | id=${conv.id}")
                                                    onDeleteConversation(conv.id)
                                                    showHistory = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.DeleteSweep,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // --- Mode Switch ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        val fragmentSelected = currentMode == ChatMode.FRAGMENT
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = if (fragmentSelected) MaterialTheme.colorScheme.surface
                                else androidx.compose.ui.graphics.Color.Transparent,
                            tonalElevation = if (fragmentSelected) 1.dp else 0.dp,
                            shadowElevation = if (fragmentSelected) 0.5.dp else 0.dp,
                            onClick = { onModeChange(ChatMode.FRAGMENT) }
                        ) {
                            Text(
                                text = "碎片输入",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (fragmentSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        val agentSelected = currentMode == ChatMode.AGENT
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = if (agentSelected) MaterialTheme.colorScheme.surface
                                else androidx.compose.ui.graphics.Color.Transparent,
                            tonalElevation = if (agentSelected) 1.dp else 0.dp,
                            shadowElevation = if (agentSelected) 0.5.dp else 0.dp,
                            onClick = { onModeChange(ChatMode.AGENT) }
                        ) {
                            Text(
                                text = "Agent",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (agentSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (currentMode == ChatMode.FRAGMENT) {
                // --- Fragment list ---
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    items(fragments.size) { index ->
                        val fragment = fragments[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            GlassSurface(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "[${index + 1}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(end = 6.dp, top = 2.dp)
                                    )
                                    Text(
                                        text = fragment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onDeleteFragment(index) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "删除碎片",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Organize button ---
                if (fragments.isNotEmpty()) {
                    Surface(
                        onClick = onOrganizeFragments,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "整理 (${fragments.size} 条碎片)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            } else {
                // --- Messages (non-FRAGMENT mode) ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    if (messages.isEmpty() && streamLine.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_chat_empty),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        val isTool = msg.role == "tool"
                        if (isTool) {
                            // 工具调用：简洁的单行样式，不显示头像
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.widthIn(max = maxBubbleWidth)
                                ) {
                                    Text(
                                        text = "🔧 ${msg.content}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = if (isUser) Alignment.End
                                    else Alignment.Start
                            ) {
                                Text(
                                    text = if (isUser) "你" else "灵记",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                                )
                                GlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.widthIn(max = maxBubbleWidth)
                                ) {
                                    MarkdownView(
                                        markdown = msg.content,
                                        textSizeSp = if (isUser) 13f else 15f,
                                        alignEnd = isUser,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (streamLine.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "灵记",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                                )
                                GlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.widthIn(max = maxBubbleWidth)
                                ) {
                                    MarkdownView(
                                        markdown = streamLine,
                                        textSizeSp = 15f,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isLoading && streamLine.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
            }

            // --- 图片预览区 ---
            if (selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedImages.forEachIndexed { index, base64 ->
                        val bitmap = remember(base64) {
                            try {
                                val bytes = Base64.decode(base64.substringAfter(","), Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) { null }
                        }
                        if (bitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "图片${index + 1}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                        .clickable { selectedImages.removeAt(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "×",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Input Area ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 图片选择按钮（仅 AGENT 模式 + 支持视觉时显示）
                if (currentMode == ChatMode.AGENT && supportsVision && !isLoading) {
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "添加图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .enterSendBehavior(inputText, { inputText = it }, submitInput),
                        enabled = !isLoading || currentMode == ChatMode.FRAGMENT,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = if (!isLoading) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (inputText.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.home_chat_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val isFragmentMode = currentMode == ChatMode.FRAGMENT
                val canSend = (inputText.text.isNotBlank() || selectedImages.isNotEmpty()) && (isFragmentMode || !isLoading)
                TextButton(
                    onClick = submitInput,
                    enabled = canSend,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = if (isFragmentMode) "记录" else "发送",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSend) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

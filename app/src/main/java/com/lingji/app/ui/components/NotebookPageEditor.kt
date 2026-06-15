package com.lingji.app.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.data.remote.IndexService
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndexEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IMAGE_MARKDOWN_REGEX = "!\\[.*?\\]\\((data:image/[^)]+)\\)".toRegex()

private fun stripImageMarkdown(content: String): String {
    return content
        .replace(IMAGE_MARKDOWN_REGEX, "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookPageEditor(
    page: NotebookPage,
    indexEntry: PageIndexEntry?,
    onUpdate: (NotebookPage) -> Unit,
    onDelete: () -> Unit,
    onAddImage: () -> Unit,
    onGenerateIndex: () -> Unit,
    onFocus: () -> Unit,
    autoFocusContent: Boolean = false,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    var title by remember(page.id) { mutableStateOf(page.title) }
    var content by remember(page.id) { mutableStateOf(page.content) }
    val images = remember(content) { IndexService.extractImagesFromContent(content) }
    val imageMarkdownBlocks = remember(images) {
        images.joinToString("") { "\n\n![图片]($it)\n\n" }
    }
    var displayContent by remember(page.id) { mutableStateOf(stripImageMarkdown(content)) }
    val isDirty = page.indexedAt <= 0 || page.updatedAt > page.indexedAt
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(page.content) {
        if (content != page.content) {
            content = page.content
            displayContent = stripImageMarkdown(content)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val scrollState = rememberScrollState()
        // 文本区至少撑满剩余可视空间，保证内容短时也能沉浸在输入栏后方。
        val contentHeight = if (fillHeight) {
            (maxHeight - 80.dp).coerceAtLeast(220.dp)
        } else {
            220.dp
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 160.dp)
        ) {
        // Header: title + actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnderlinedTitleField(
                value = title,
                onValueChange = {
                    title = it
                    onUpdate(page.copy(title = it, updatedAt = System.currentTimeMillis()))
                },
                onFocus = onFocus,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDirty) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "待索引",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                IconButton(onClick = onAddImage) {
                    Icon(Icons.Default.Image, contentDescription = stringResource(R.string.cd_add_image))
                }
                IconButton(onClick = onGenerateIndex) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_generate_index))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Keywords
        if (!indexEntry?.keywords.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                indexEntry?.keywords?.forEach { kw ->
                    KeywordTag(text = kw)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Images
        if (images.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { imageUrl ->
                    ImageCard(
                        imageUrl = imageUrl,
                        onRemove = {
                            val escaped = Regex.escape(imageUrl)
                            val cleaned = content.replace(
                                Regex("!\\[.*?\\]\\($escaped\\)"),
                                ""
                            )
                            content = cleaned
                            displayContent = stripImageMarkdown(cleaned)
                            onUpdate(page.copy(content = cleaned, updatedAt = System.currentTimeMillis()))
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Content：纯文本输入区，无额外底色/卡片背景，避免与页面背景形成色差。
        if (autoFocusContent) {
            androidx.compose.runtime.LaunchedEffect(page.id) {
                contentFocusRequester.requestFocus()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .clip(RoundedCornerShape(16.dp))
        ) {
            BasicTextField(
                value = displayContent,
                onValueChange = {
                    displayContent = it
                    val updatedContent = it + imageMarkdownBlocks
                    content = updatedContent
                    onUpdate(page.copy(content = updatedContent, updatedAt = System.currentTimeMillis()))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState())
                    .focusRequester(contentFocusRequester)
                    .onFocusChanged { if (it.isFocused) onFocus() },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (displayContent.isEmpty()) {
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
private fun KeywordTag(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ImageCard(imageUrl: String, onRemove: () -> Unit) {
    var bitmap by remember(imageUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(imageUrl) {
        bitmap = withContext(Dispatchers.IO) {
            decodeBase64Image(imageUrl)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 400.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            val aspectRatio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(0.001f)
            val targetHeight = (maxWidth / aspectRatio).coerceIn(120.dp, 400.dp)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_remove_image),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(targetHeight),
                contentScale = ContentScale.Fit
            )
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
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

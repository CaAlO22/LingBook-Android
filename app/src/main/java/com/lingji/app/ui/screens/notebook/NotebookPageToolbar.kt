package com.lingji.app.ui.screens.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.NotebookPage

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun NotebookPageToolbar(
    currentPage: NotebookPage?,
    currentPageIndex: Int,
    pagesSize: Int,
    dirtyCount: Int,
    isProcessing: Boolean,
    onSave: () -> Unit,
    onBuildIndex: () -> Unit,
    onJumpToPage: () -> Unit,
    onEditPagePosition: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onAddPage: () -> Unit,
    onUndo: (() -> Unit)?,
    onRedo: (() -> Unit)?,
    canUndo: Boolean,
    canRedo: Boolean,
    onAddImage: () -> Unit,
    onEditIndex: () -> Unit,
    onGenerateIndex: () -> Unit,
    onDelete: () -> Unit
) {
    var showPagePositionMenu by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (pagesSize > 0) {
            // 左侧：保存 + 待索引
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentPage?.let {
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.save))
                    }
                }
                if (dirtyCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = onBuildIndex
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "$dirtyCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onUndo?.invoke() },
                    enabled = canUndo && onUndo != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.cd_undo),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onRedo?.invoke() },
                    enabled = canRedo && onRedo != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.cd_redo),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 中间：页面导航
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevPage,
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_prev_page),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { showPagePositionMenu = true }
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
                                    pagesSize
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showPagePositionMenu,
                        onDismissRequest = { showPagePositionMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.jump_to_page)) },
                            onClick = {
                                showPagePositionMenu = false
                                onJumpToPage()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_page_position)) },
                            onClick = {
                                showPagePositionMenu = false
                                onEditPagePosition()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
                IconButton(
                    onClick = onNextPage,
                    enabled = currentPageIndex < pagesSize - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_next_page),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (pagesSize == 0) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // 右侧：页面操作 + 新增页面
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            currentPage?.let {
                IconButton(
                    onClick = onAddImage,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = stringResource(R.string.cd_add_image),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onEditIndex,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit_page_index),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onGenerateIndex,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.cd_generate_index),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onAddPage,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_page),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyPagesState() {
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

package com.lingji.app.ui.screens.notebook

import android.widget.Toast
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.ui.components.ModeChip
import com.lingji.app.ui.theme.NotoSerifCJKsc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun NotebookSubjectTopBar(
    title: String,
    isPreview: Boolean,
    isPagesEmpty: Boolean,
    onBack: () -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onBuildIndex: () -> Unit,
    onBuildDirectory: () -> Unit,
    onExport: () -> Unit,
    onExportPdf: () -> Unit,
    onCopyToClipboard: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = NotoSerifCJKsc,
                    letterSpacing = (-0.02).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            )
        }

        if (!isPagesEmpty) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(3.dp)
                ) {
                    ModeChip(
                        label = stringResource(R.string.mode_edit),
                        selected = !isPreview,
                        onClick = { onTogglePreview(false) }
                    )
                    ModeChip(
                        label = stringResource(R.string.mode_preview),
                        selected = isPreview,
                        onClick = { onTogglePreview(true) }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search)
                )
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.build_index)) },
                        onClick = {
                            showMoreMenu = false
                            onBuildIndex()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.build_directory)) },
                        onClick = {
                            showMoreMenu = false
                            onBuildDirectory()
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export)) },
                        onClick = {
                            showMoreMenu = false
                            onExport()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_pdf)) },
                        onClick = {
                            showMoreMenu = false
                            onExportPdf()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_to_clipboard)) },
                        onClick = {
                            showMoreMenu = false
                            onCopyToClipboard()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

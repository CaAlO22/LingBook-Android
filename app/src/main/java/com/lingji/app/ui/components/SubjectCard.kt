package com.lingji.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SubjectCardMinHeight = 132.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubjectCard(
    subject: Subject,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onCopyExport: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = subject.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(subject.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubjectStats(subject)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = Color(0xFFD6D3D1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onRename(); menuExpanded = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.cd_rename),
                            tint = Color(0xFFD6D3D1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export)) },
                    onClick = { onExport(); menuExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_to_clipboard)) },
                    onClick = { onCopyExport(); menuExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to_top)) },
                    onClick = { onMoveToTop(); menuExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    onClick = { onMoveUp(); menuExpanded = false },
                    enabled = canMoveUp
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    onClick = { onMoveDown(); menuExpanded = false },
                    enabled = canMoveDown
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        showDeleteDialog = true
                        menuExpanded = false
                    }
                )
            }

            if (showDeleteDialog) {
                LingjiDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.delete)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.delete_subject_confirm,
                                subject.title.takeIf { it.isNotBlank() } ?: ""
                            )
                        )
                    },
                    confirmButton = {
                        LingjiDialogConfirmButton(
                            text = stringResource(R.string.delete),
                            onClick = {
                                onDelete()
                                showDeleteDialog = false
                            },
                            isDestructive = true
                        )
                    },
                    dismissButton = {
                        LingjiDialogDismissButton(
                            text = stringResource(R.string.cancel),
                            onClick = { showDeleteDialog = false }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SubjectStats(subject: Subject) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subject.type == SubjectType.FRAGMENT) {
            StatItem(
                icon = Icons.Default.Description,
                count = subject.fragments.size + subject.unmergedFragments.size,
                tint = tint
            )
            StatItem(
                icon = Icons.Default.Book,
                count = subject.aggregatedNote.length,
                tint = tint
            )
        } else {
            StatItem(
                icon = Icons.Default.Book,
                count = subject.pages?.size ?: 0,
                label = "页",
                tint = tint
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String? = null,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label?.let { "$count$it" } ?: count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = tint
        )
    }
}

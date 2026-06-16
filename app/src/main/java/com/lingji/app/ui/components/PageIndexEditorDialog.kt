package com.lingji.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.PageIndexEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageIndexEditorDialog(
    entry: PageIndexEntry,
    onDismiss: () -> Unit,
    onConfirm: (PageIndexEntry) -> Unit
) {
    val keywords = remember(entry.pageId) {
        mutableStateListOf<String>().apply { addAll(entry.keywords) }
    }
    var summary by remember(entry.pageId) { mutableStateOf(entry.summary) }
    var newKeyword by remember(entry.pageId) { mutableStateOf("") }

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_page_index)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.index_keywords),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    keywords.forEach { keyword ->
                        EditableKeywordTag(
                            text = keyword,
                            onRemove = { keywords.remove(keyword) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text(stringResource(R.string.add_keyword_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val trimmed = newKeyword.trim()
                            if (trimmed.isNotEmpty() && !keywords.contains(trimmed)) {
                                keywords.add(trimmed)
                                newKeyword = ""
                            }
                        },
                        enabled = newKeyword.trim().isNotEmpty()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_keyword))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.index_summary),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.summary_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.save),
                onClick = {
                    onConfirm(
                        entry.copy(
                            keywords = keywords.toList(),
                            summary = summary.trim(),
                            generatedAt = System.currentTimeMillis()
                        )
                    )
                }
            )
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun EditableKeywordTag(text: String, onRemove: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .height(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_keyword),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(14.dp)
                )
            }
        }
    }
}

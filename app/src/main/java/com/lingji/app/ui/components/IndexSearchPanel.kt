package com.lingji.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingji.app.domain.model.SearchResult

@Composable
fun IndexSearchPanel(
    onSearch: (String) -> List<SearchResult>,
    onSelectPage: (String) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }

    LingjiDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.search_notes)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_keyword_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.search_clear)
                                )
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        results = onSearch(query.trim())
                        hasSearched = true
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = query.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(stringResource(R.string.search_action), modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    if (hasSearched && results.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_no_results),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(results) { result ->
                        SearchResultItem(
                            result = result,
                            onClick = {
                                onSelectPage(result.page.id)
                                onClose()
                            }
                        )
                    }
                }
            }
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onClose
            )
        }
    )
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.page.title,
                    style = MaterialTheme.typography.titleSmall
                )
                if (result.summarySnippet.isNotBlank()) {
                    Text(
                        text = result.summarySnippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (result.matchedKeywords.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.search_match_format,
                            result.matchedKeywords.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.jump)
            )
        }
    }
}

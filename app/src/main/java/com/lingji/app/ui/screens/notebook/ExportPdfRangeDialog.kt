package com.lingji.app.ui.screens.notebook

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton

private enum class ExportRange { CURRENT, ALL, CUSTOM }

@Composable
fun ExportPdfRangeDialog(
    pageCount: Int,
    currentPageNumber: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>, Boolean) -> Unit
) {
    var selected by remember { mutableStateOf(ExportRange.CURRENT) }
    var customText by remember { mutableStateOf("") }
    var forceWhite by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_pdf_range_title)) },
        text = {
            Column {
                ExportRangeOption(
                    selected = selected == ExportRange.CURRENT,
                    text = stringResource(R.string.export_pdf_range_current),
                    onClick = { selected = ExportRange.CURRENT }
                )
                ExportRangeOption(
                    selected = selected == ExportRange.ALL,
                    text = stringResource(R.string.export_pdf_range_all, pageCount),
                    onClick = { selected = ExportRange.ALL }
                )
                ExportRangeOption(
                    selected = selected == ExportRange.CUSTOM,
                    text = stringResource(R.string.export_pdf_range_custom),
                    onClick = { selected = ExportRange.CUSTOM }
                )
                if (selected == ExportRange.CUSTOM) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text(stringResource(R.string.export_pdf_range_custom_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Switch(
                        checked = forceWhite,
                        onCheckedChange = { forceWhite = it }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.export_pdf_force_white),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.export_pdf_force_white_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.export_pdf),
                onClick = {
                    val indices: List<Int> = when (selected) {
                        ExportRange.CURRENT -> listOf((currentPageNumber - 1).coerceAtLeast(0))
                        ExportRange.ALL -> (0 until pageCount).toList()
                        ExportRange.CUSTOM -> parsePageRanges(customText, pageCount)
                    }
                    if (indices.isEmpty()) {
                        Toast.makeText(context, R.string.export_pdf_range_invalid, Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(indices, forceWhite)
                    }
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
private fun ExportRangeOption(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun parsePageRanges(input: String, pageCount: Int): List<Int> {
    if (pageCount <= 0) return emptyList()
    val result = sortedSetOf<Int>()
    val tokens = input.split(',', '，', ';', '；', ' ', '\t').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    for (raw in tokens) {
        val token = raw.trim().replace('－', '-')
        if (token.contains('-')) {
            val parts = token.split('-').map { it.trim() }
            if (parts.size != 2) return emptyList()
            val from = parts[0].toIntOrNull() ?: return emptyList()
            val to = parts[1].toIntOrNull() ?: return emptyList()
            if (from <= 0 || to <= 0) return emptyList()
            val lo = minOf(from, to)
            val hi = maxOf(from, to)
            for (n in lo..hi) {
                val idx = n - 1
                if (idx in 0 until pageCount) result.add(idx)
            }
        } else {
            val n = token.toIntOrNull() ?: return emptyList()
            val idx = n - 1
            if (idx in 0 until pageCount) result.add(idx)
        }
    }
    return result.toList()
}

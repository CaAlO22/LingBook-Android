package com.lingji.app.ui.screens.notebook

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.lingji.app.R
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton

@Composable
fun JumpPageDialog(
    pageCount: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    LingjiDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.jump_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value.filter { it.isDigit() }
                },
                label = { Text(stringResource(R.string.page_number_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.jump),
                onClick = {
                    val index = text.toIntOrNull()?.minus(1)
                    if (index != null && index in 0 until pageCount) {
                        onJump(index)
                    }
                    onDismiss()
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
fun MovePageDialog(
    pageCount: Int,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    LingjiDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.edit_page_position)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value.filter { it.isDigit() }
                },
                label = {
                    Text(
                        stringResource(
                            R.string.edit_page_position_hint,
                            pageCount
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.move),
                onClick = {
                    val index = text.toIntOrNull()?.minus(1)
                    if (index != null && index in 0 until pageCount) {
                        onMove(index)
                    }
                    onDismiss()
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
fun DeletePageDialog(
    pageTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_page)) },
        text = {
            Text(
                stringResource(
                    R.string.delete_page_confirm,
                    pageTitle.ifBlank { stringResource(R.string.unnamed_page) }
                )
            )
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.delete),
                isDestructive = true,
                onClick = {
                    onConfirm()
                    onDismiss()
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

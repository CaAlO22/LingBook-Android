package com.lingji.app.ui.screens.notebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import com.lingji.app.domain.model.HorizontalSwipeAction
import com.lingji.app.ui.components.DropdownMenu
import com.lingji.app.ui.components.DropdownMenuItem
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.SettingsOutlinedTextField

/**
 * 笔记级设置弹窗：编辑器偏好属于具体笔记的编辑体验，放在笔记本内而不是全局设置。
 */
@Composable
fun NoteSettingsDialog(
    horizontalSwipeAction: HorizontalSwipeAction,
    onHorizontalSwipeActionChange: (HorizontalSwipeAction) -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_settings)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SettingsOutlinedTextField(
                        value = horizontalSwipeLabel(horizontalSwipeAction),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.horizontal_swipe_action)) },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        matchAnchorWidth = true
                    ) {
                        HorizontalSwipeAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(horizontalSwipeLabel(action)) },
                                onClick = {
                                    onHorizontalSwipeActionChange(action)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.done),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun horizontalSwipeLabel(action: HorizontalSwipeAction): String = when (action) {
    HorizontalSwipeAction.NONE -> stringResource(R.string.horizontal_swipe_none)
    HorizontalSwipeAction.TOGGLE_PREVIEW -> stringResource(R.string.horizontal_swipe_toggle_preview)
    HorizontalSwipeAction.CHANGE_PAGE -> stringResource(R.string.horizontal_swipe_change_page)
}

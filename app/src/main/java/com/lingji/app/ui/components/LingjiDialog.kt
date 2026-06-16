package com.lingji.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 灵记风格弹窗容器。
 *
 * 使用 [Dialog] + [Card] 实现，替代 Material3 [AlertDialog]，
 * 统一应用内弹窗的视觉风格：暖白表面、圆角、细边框、底部操作按钮。
 */
@Composable
fun LingjiDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    )
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                title?.let {
                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall
                        ) {
                            it()
                        }
                    }
                }

                text?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium
                        ) {
                            it()
                        }
                    }
                }

                if (confirmButton != null || dismissButton != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        if (dismissButton != null && confirmButton != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * 灵记风格弹窗的默认确认按钮。
 *
 * @param text 按钮文字。
 * @param onClick 点击回调。
 * @param enabled 是否可点击。
 * @param isDestructive 是否为破坏性操作（如删除），使用错误色强调。
 */
@Composable
fun LingjiDialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val containerColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 灵记风格弹窗的默认取消/次要按钮。
 */
@Composable
fun LingjiDialogDismissButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



package com.lingji.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A flat outlined text field for settings screens.
 *
 * Unlike the floating glass inputs, this variant keeps a transparent
 * container so its background matches the page, avoiding the
 * "row color different from surroundings" effect in a stacked form.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val dialogBodyHeight = LocalDialogBodyHeight.current

    // 小窗中弹出输入法时，系统会连续压缩窗口高度（弹窗正文随之变矮）。
    // 系统只在焦点建立的那一刻按当时的视口滚动一次，视口收缩完成后输入框就被裁掉一截，
    // 表现为先上滚、再回落，最后停在输入框只露出半截的位置。
    // 这里在聚焦期间跟随正文视口高度变化重新对齐，保证布局稳定后输入框完整可见；
    // 视口高度不变时不做任何滚动，手动滚动不会被拉回。
    LaunchedEffect(focused) {
        if (!focused || dialogBodyHeight == null) return@LaunchedEffect
        snapshotFlow { dialogBodyHeight.value }
            .collect { bringIntoViewRequester.bringIntoView() }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        maxLines = maxLines,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focused = it.isFocused }
            // 上下键不再把焦点带离输入框。
            .arrowKeyFocusGuard(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

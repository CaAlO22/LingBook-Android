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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive

/**
 * A flat outlined text field for settings screens.
 *
 * Unlike the floating glass inputs, this variant keeps a transparent
 * container so its background matches the page, avoiding the
 * "row color different from surroundings" effect in a stacked form.
 */
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
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
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val dialogBodyHeight = LocalDialogBodyHeight.current
    val constrainedViewportPx = with(LocalDensity.current) { 280.dp.roundToPx() }

    // 视口被输入法 / 小窗压缩时，系统还会按焦点位置继续调整窗口，
    // 而“最小滚动”总把焦点贴在视口底边，两者互为因果形成反馈回路，表现为上下反复跳动。
    // 故视口受限时改为把聚焦框顶边对齐视口顶边：滚动目标只取决于内容坐标、与视口高度无关，
    // 压缩动画期间重复调用幂等，回路断开；焦点下方留有余量，系统也不再追着焦点继续压缩。
    // 视口充足时保持最小滚动；视口高度不变时不做任何滚动，手动滚动不会被拉回。
    LaunchedEffect(focused) {
        if (!focused || dialogBodyHeight == null) return@LaunchedEffect
        suspend fun align() {
            val viewport = dialogBodyHeight.value
            if (viewport <= 0 || fieldSize == IntSize.Zero) return
            if (viewport < constrainedViewportPx) {
                // 与视口等高的请求矩形：唯一能容纳它的位置即矩形顶边贴视口顶边
                bringIntoViewRequester.bringIntoView(
                    Rect(0f, 0f, fieldSize.width.toFloat(), viewport.toFloat())
                )
            } else {
                bringIntoViewRequester.bringIntoView()
            }
        }
        // bringIntoView 会被竞争性滚动请求取消并抛 CancellationException，
        // 不接住的话跟随协程第一次被取消后就死掉、不再恢复。
        suspend fun alignSafely() {
            try {
                align()
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
            }
        }
        alignSafely()
        // 视口稳定后才响应：忽略布局频率的扩缩脉冲，
        // 避免每次脉冲都启动滚动动画、又在动画中途被夹断。
        snapshotFlow { dialogBodyHeight.value }
            .debounce(120)
            .collect { alignSafely() }
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
            .onSizeChanged { fieldSize = it }
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

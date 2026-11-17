package com.lingji.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 灵记风格的浮层菜单：纸张质感底色 + 大圆角 + 细描边，
 * 取代 Material3 默认菜单（小圆角、系统基线配色、无描边）。
 *
 * 用法与 material3 的 DropdownMenu 保持一致：
 * `DropdownMenu(expanded, onDismissRequest) { DropdownMenuItem(...) }`。
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    matchAnchorWidth: Boolean = false,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val density = LocalDensity.current
    var anchorWidth by remember { mutableStateOf(0) }
    // 与锚点等宽时，菜单在铺满全屏的弹窗窗口内的水平偏移
    var menuLeftPx by remember { mutableStateOf(0) }

    // 1 = 完全展开，0 = 完全收起。展开时从锚点一侧向打开方向“展开”，收起时反向收回。
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            progress.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
        } else {
            // 退出是进入的时间反演：时长一致、缓动取反，避免收起显得比展开更快。
            progress.animateTo(0f, tween(durationMillis = 180, easing = Easing { t -> 1f - FastOutSlowInEasing.transform(1f - t) }))
        }
    }
    var expandDown by remember { mutableStateOf(true) }
    val popupVisible by remember {
        derivedStateOf { progress.value > 0f || progress.targetValue > 0f }
    }
    if (!popupVisible) return

    val positionProvider = remember(density, offset, matchAnchorWidth) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                if (matchAnchorWidth) anchorWidth = anchorBounds.width
                val gapX = with(density) { offset.x.roundToPx() }
                val gapY = with(density) { offset.y.roundToPx() }
                val margin = with(density) { 8.dp.roundToPx() }
                val belowY = anchorBounds.bottom + gapY
                val aboveY = anchorBounds.top - popupContentSize.height - gapY
                val openDown = belowY + popupContentSize.height <= windowSize.height - margin
                expandDown = openDown
                val y = if (openDown) belowY else aboveY.coerceAtLeast(margin)
                if (matchAnchorWidth) {
                    // 与锚点等宽时，让弹窗窗口横向铺满且位置固定在 0：
                    // 窗口宽度一旦随首帧测量结果变化，系统会重新摆放窗口，
                    // 表现为菜单从左侧滑入。菜单的水平位置改由窗口内的偏移承担。
                    val maxLeft = (windowSize.width - anchorBounds.width - margin).coerceAtLeast(margin)
                    menuLeftPx = (anchorBounds.left + gapX).coerceIn(margin, maxLeft)
                    return IntOffset(0, y)
                }
                val x = (anchorBounds.left + gapX).coerceIn(
                    margin,
                    (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                )
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = expanded),
        popupPositionProvider = positionProvider
    ) {
        val widthModifier = if (matchAnchorWidth && anchorWidth > 0) {
            Modifier.width(with(density) { anchorWidth.toDp() })
        } else {
            Modifier
        }
        val menu: @Composable () -> Unit = {
            Surface(
                modifier = modifier
                    .then(widthModifier)
                    .graphicsLayer {
                        // 只做缩放，透明度放到内层内容上：
                        // alpha 包裹带 elevation 的 Surface 会生成离屏图层，把阴影按矩形裁切，
                        // 表现为动画收尾瞬间四角阴影变成直角。
                        scaleY = progress.value
                        // 以贴合锚点的那条边为原点向打开方向展开
                        transformOrigin = TransformOrigin(0f, if (expandDown) 0f else 1f)
                    },
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .graphicsLayer { alpha = progress.value }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    content = content
                )
            }
        }
        if (matchAnchorWidth) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 点在菜单以外的区域时关闭
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest
                        )
                )
                Box(modifier = Modifier.padding(start = with(density) { menuLeftPx.toDp() })) {
                    menu()
                }
            }
        } else {
            menu()
        }
    }
}

/** 与 [DropdownMenu] 配套的菜单项，圆角水波纹只在条目内绘制。 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: Dp = 9.dp
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    leadingIcon()
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    text()
                }
            }
        }
        if (trailingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    trailingIcon()
                }
            }
        }
    }
}

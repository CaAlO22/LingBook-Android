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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalView
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
    val anchorView = LocalView.current
    var anchorWidth by remember { mutableStateOf(0) }
    // 与锚点等宽时，菜单在铺满全屏的弹窗窗口内的水平偏移
    var menuLeftPx by remember { mutableStateOf(0) }
    // 菜单最大高度：所选方向空间不足时收窄菜单，使其紧贴锚点、超出部分内部滚动
    var menuMaxHeightPx by remember { mutableStateOf(Int.MAX_VALUE) }

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

    val positionProvider = remember(density, offset, matchAnchorWidth, anchorView) {
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
                // anchorBounds 与返回的 IntOffset 同处锚点所在窗口的坐标系（弹窗内即为弹窗窗口），
                // 因此 x 直接取 anchorBounds.left 即可与锚点左边缘对齐；
                // 再叠加窗口屏幕原点会在弹窗窗口（窗口原点不为 0）里把菜单整体右移一个原点宽度。
                val windowOrigin = IntArray(2)
                anchorView.getLocationOnScreen(windowOrigin)
                // 屏幕坐标只用于计算纵向两侧可用空间
                val anchorTopScreen = anchorBounds.top + windowOrigin[1]
                val anchorBottomScreen = anchorBounds.bottom + windowOrigin[1]
                val spaceBelow = (windowSize.height - margin - anchorBottomScreen - gapY).coerceAtLeast(0)
                val spaceAbove = (anchorTopScreen - gapY - margin).coerceAtLeast(0)
                // 优先向下；放不下时选空间更大的一侧，菜单高度限制在该侧可用空间内，
                // 避免整屏高度的菜单被夹到屏幕边缘而远离锚点
                val openDown = popupContentSize.height <= spaceBelow || spaceBelow >= spaceAbove
                expandDown = openDown
                menuMaxHeightPx = if (openDown) spaceBelow else spaceAbove
                val y = if (openDown) {
                    anchorBounds.bottom + gapY
                } else {
                    anchorBounds.top - gapY - minOf(popupContentSize.height, spaceAbove)
                }
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
            // 不与锚点等宽时按内容自适应宽度：
            // 弹窗窗口的测量上限是整屏，菜单项里的 fillMaxWidth 会被撑满整屏，
            // 因此这里显式收窄到内容固有宽度，并限制最大宽度避免长文本贴边。
            Modifier
                .widthIn(max = 320.dp)
                .width(IntrinsicSize.Max)
        }
        val menu: @Composable () -> Unit = {
            Surface(
                modifier = modifier
                    .then(widthModifier)
                    .heightIn(max = with(density) { menuMaxHeightPx.toDp() })
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

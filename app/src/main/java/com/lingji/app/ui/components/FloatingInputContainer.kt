package com.lingji.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * 在可滚动内容上方承载一个悬浮输入栏，并使用 Haze 实现真正的硬件加速背景模糊。
 *
 * Haze 通过 [haze] 把底层页面内容注册为模糊源，再通过 [hazeChild] 让悬浮栏区域
 * 对源内容进行实时 backdrop blur。效果与 Web 的 `backdrop-filter: blur()` 等价，
 * 且由 GPU/系统模糊管线处理，不会出现自定义 CPU 模糊的卡顿、发热与颗粒感。
 *
 * @param bottomOffset 悬浮栏与屏幕底部（或键盘顶部）的额外间距。
 * @param horizontalMargin 悬浮栏左右边距。
 * @param barShape 悬浮栏圆角形状，需与外层玻璃容器保持一致。
 * @param floatingBar 悬浮输入 UI。
 * @param content 出现在悬浮栏后方的可滚动页面内容。
 */
@Composable
fun FloatingInputContainer(
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 16.dp,
    horizontalMargin: Dp = 24.dp,
    barShape: Shape = RoundedCornerShape(24.dp),
    floatingBar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val targetKeyboardPadding = with(density) { imeBottomPx.toDp() }

    val animatedKeyboardPadding by animateDpAsState(
        targetValue = targetKeyboardPadding,
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        ),
        label = "floating-input-ime"
    )

    // HazeState 连接模糊源（页面内容）与模糊目标（悬浮栏）。
    val hazeState = remember { HazeState() }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 页面内容：注册为 Haze 模糊源。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
        ) {
            content()
        }

        // 2. 悬浮输入栏：作为 Haze 子节点应用 backdrop blur。
        // Haze 1.1.x 的 hazeChild 不再接收 shape，需先 clip 再应用模糊；
        // 必须提供 backgroundColor，否则会在绘制时抛出异常。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = horizontalMargin)
                .padding(bottom = bottomOffset + animatedKeyboardPadding)
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = barShape, clip = false)
                .clip(barShape)
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.background,
                        tint = HazeTint(Color.White.copy(alpha = 0.22f)),
                        blurRadius = 18.dp,
                        noiseFactor = 0.10f
                    )
                )
        ) {
            floatingBar()
        }
    }
}

package com.lingji.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInputScale
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
    barShape: Shape = RoundedCornerShape(28.dp),
    floatingBar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    // WindowInsets.ime 本身随系统键盘动画逐帧更新，直接使用才能与输入法完全同步；
    // 再叠加一层补间动画会始终落后于键盘，产生可见的跟随延迟。
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    // 调用方位于 Scaffold 内容区，底部已让出导航栏；而 ime inset 包含导航栏区域，
    // 直接叠加会把悬浮栏推得比输入法高出「导航栏 + 常规间距」，键盘弹出后离输入法过远。
    // 扣除导航栏高度后，键盘弹出时仍保持 bottomOffset 的贴合间距。
    val keyboardPadding = with(density) {
        if (imeBottomPx > 0) (imeBottomPx - navigationBottomPx).coerceAtLeast(0).toDp() else 0.dp
    }

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
                .padding(bottom = bottomOffset + keyboardPadding)
                .fillMaxWidth()
                // 信息岛：Web 质感玻璃——柔和外阴影 + 不透明模糊承接层 + 无颗粒背景模糊。
                .shadow(
                    elevation = 12.dp,
                    shape = barShape,
                    clip = false,
                    ambientColor = Color(0xFF78716C).copy(alpha = 0.16f),
                    spotColor = Color(0xFF78716C).copy(alpha = 0.20f)
                )
                .clip(barShape)
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        // 必须不透明：否则原始清晰内容会穿透模糊快照，看起来像没有模糊。
                        backgroundColor = Color.White,
                        tint = HazeTint(Color.White.copy(alpha = 0.18f)),
                        // 半径需远大于文字行高，否则滚动时文字暗带透过玻璃移动、
                        // 亮度随段落脉动，观感即闪烁。
                        blurRadius = 48.dp,
                        noiseFactor = 0f
                    )
                ) {
                    // 大半径配合降采样：降采样空间内等效半径仍足够均化文字，
                    // 既消除别名闪烁又避免全分辨率大半径的帧成本。
                    inputScale = HazeInputScale.Fixed(0.5f)
                }
        ) {
            floatingBar()
        }
    }
}

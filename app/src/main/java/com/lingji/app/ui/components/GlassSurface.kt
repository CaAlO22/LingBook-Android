package com.lingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 玻璃质感表面。
 *
 * 信息岛：Web 质感玻璃——半透明白色渐变、亮边、顶部反射与极浅的内阴影，
 * 叠加在 Haze 的背景模糊之上，复刻 Web 输入胶囊的层次而非单纯提高不透明度。
 *
 * @param shape 裁切形状，需与 [FloatingInputContainer] 的 [barShape] 保持一致。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.12f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color.White.copy(alpha = 0.48f),
                        Color.White.copy(alpha = 0.24f)
                    )
                ),
                shape = shape
            )
            .drawBehind {
                // 顶部内反光与底部内阴影，让纯色背景上也有玻璃厚度。
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.42f
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF292524).copy(alpha = 0.045f)),
                        startY = size.height * 0.72f,
                        endY = size.height
                    )
                )
            }
    ) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

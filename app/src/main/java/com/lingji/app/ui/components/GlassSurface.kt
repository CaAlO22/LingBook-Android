package com.lingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 半透明暖白填充表面。
 *
 * 在 Haze 模糊结果之上叠加一层暖白填充（alpha ~0.50），
 * 在保持一定毛玻璃感的同时避免底层页面内容、阴影边缘或 Haze 边缘在输入栏内形成可见色块。
 *
 * @param shape 裁切形状，需与 [FloatingInputContainer] 的 [barShape] 保持一致。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.50f))
    ) {
        content()
    }
}

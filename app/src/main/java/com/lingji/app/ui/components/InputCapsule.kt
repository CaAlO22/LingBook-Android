package com.lingji.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lingji.app.R

@Composable
fun InputCapsule(
    hint: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 4
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    // 方向键移动光标需要文本布局信息（上下行落点），由输入框的 onTextLayout 持续更新。
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val submitText: () -> Unit = {
        if (text.text.isNotBlank()) {
            onSend(text.text.trim())
            text = TextFieldValue("")
        }
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    // 必须先于默认焦点导航消费方向键，否则光标不动、焦点跳到相邻按钮。
                    .arrowKeyCursorBehavior(text, { text = it }) { textLayout }
                    .enterSendBehavior(text, { text = it }, submitText),
                maxLines = maxLines,
                onTextLayout = { textLayout = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (text.text.isEmpty()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            val enabled = text.text.isNotBlank()
            IconButton(
                onClick = submitText,
                enabled = enabled,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.cd_send),
                            tint = if (enabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

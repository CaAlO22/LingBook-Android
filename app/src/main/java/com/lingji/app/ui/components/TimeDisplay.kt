package com.lingji.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局 24h/12h 偏好，两个详情页顶栏共享同一份状态。
 */
internal object TimeFormatPreference {
    val is24Hour = mutableStateOf(true)
}

/**
 * 常驻时间显示，精确到秒；点击在 24h 制与 12h 制之间切换。
 */
@Composable
fun TimeDisplay(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium
) {
    val is24Hour by TimeFormatPreference.is24Hour
    val timeText by produceState(
        initialValue = formatTime(System.currentTimeMillis(), is24Hour),
        key1 = is24Hour
    ) {
        while (true) {
            value = formatTime(System.currentTimeMillis(), is24Hour)
            delay(1000L)
        }
    }

    Text(
        text = timeText,
        style = style,
        modifier = modifier.clickable(
            onClick = { TimeFormatPreference.is24Hour.value = !is24Hour }
        )
    )
}

private fun formatTime(timestamp: Long, is24Hour: Boolean): String {
    val pattern = if (is24Hour) "HH:mm:ss" else "hh:mm:ss a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

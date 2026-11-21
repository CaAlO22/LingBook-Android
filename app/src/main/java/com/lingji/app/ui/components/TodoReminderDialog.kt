package com.lingji.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 信息岛：提醒时刻展示（当天 0 点起的分钟数 → HH:mm）。 */
fun reminderTimeText(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

/** 信息岛：一次性提醒的完整日期时间。 */
fun reminderDateTimeText(at: Long): String =
    SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(at))

/**
 * 信息岛：条目/列表上的提醒短文案。
 *
 * 重复列表只显示时刻（日期由重复规则固定），一次性提醒显示日期与时刻。
 */
fun reminderShortText(repeat: TodoRepeat, minutes: Int, at: Long): String =
    if (repeat.type == TodoRepeatType.ONCE) {
        SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(at))
    } else {
        reminderTimeText(minutes)
    }

/**
 * 信息岛：待办提醒设置弹窗（单条待办与整个列表共用）。
 *
 * - 一次性列表：展示日历与时间选择，可选任意未来时刻。
 * - 重复列表：只选择当天时刻，日期由重复规则推导（提醒日期不会晚于下一次刷新）。
 */
@Composable
fun TodoReminderDialog(
    title: String,
    repeat: TodoRepeat,
    initialEnabled: Boolean,
    initialMinutes: Int,
    initialAt: Long,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (enabled: Boolean, minutes: Int, at: Long) -> Unit
) {
    val context = LocalContext.current
    val isOnce = repeat.type == TodoRepeatType.ONCE
    var enabled by remember { mutableStateOf(initialEnabled) }

    val initialCalendar = remember {
        Calendar.getInstance().apply {
            if (isOnce && initialAt > 0) {
                timeInMillis = initialAt
            } else {
                val minutes = if (initialMinutes in 0..1439) initialMinutes else DEFAULT_REMINDER_MINUTES
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (isOnce && timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
    }
    var year by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH)) }
    var day by remember { mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initialCalendar.get(Calendar.MINUTE)) }

    val selectedAt = remember(year, month, day, hour, minute) {
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val valid = !enabled || !isOnce || selectedAt > System.currentTimeMillis()

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.todo_reminder_enable),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isOnce) {
                        ReminderFieldRow(
                            label = stringResource(R.string.todo_reminder_date_label),
                            value = String.format(
                                Locale.getDefault(),
                                "%d年%d月%d日",
                                year,
                                month + 1,
                                day
                            ),
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        year = y
                                        month = m
                                        day = d
                                    },
                                    year,
                                    month,
                                    day
                                ).show()
                            }
                        )
                    }
                    ReminderFieldRow(
                        label = stringResource(R.string.todo_reminder_time_label),
                        value = reminderTimeText(hour * 60 + minute),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    hour = h
                                    minute = m
                                },
                                hour,
                                minute,
                                true
                            ).show()
                        }
                    )
                    if (isOnce && !valid) {
                        Text(
                            text = stringResource(R.string.todo_reminder_past_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (initialEnabled) {
                        TextButton(
                            onClick = { onConfirm(false, -1, 0) },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.todo_reminder_clear),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.save),
                enabled = valid,
                onClick = {
                    onConfirm(
                        enabled,
                        if (!enabled) -1 else if (isOnce) -1 else hour * 60 + minute,
                        if (enabled && isOnce) selectedAt else 0
                    )
                }
            )
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun ReminderFieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private const val DEFAULT_REMINDER_MINUTES = 20 * 60

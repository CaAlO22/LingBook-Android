package com.lingji.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType

/**
 * 信息岛：待办重复设置弹窗。
 *
 * 创建待办列表时强制选择，创建后不可修改——重复规则决定刷新周期，提醒日期据此推导。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodoRepeatDialog(
    initial: TodoRepeat,
    onDismiss: () -> Unit,
    onConfirm: (TodoRepeat) -> Unit,
    title: String = stringResource(R.string.todo_repeat_settings),
    confirmText: String = stringResource(R.string.save)
) {
    var type by remember { mutableStateOf(initial.type) }
    var intervalText by remember { mutableStateOf(initial.intervalDays.toString()) }
    var weekDays by remember { mutableStateOf(initial.weekDays) }
    val intervalDays = intervalText.toIntOrNull() ?: 0
    val canConfirm = (type != TodoRepeatType.INTERVAL || intervalDays >= 1) &&
        (type != TodoRepeatType.CUSTOM || weekDays.isNotEmpty())

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RepeatOptionRow(
                    label = stringResource(R.string.todo_repeat_once),
                    selected = type == TodoRepeatType.ONCE,
                    onClick = { type = TodoRepeatType.ONCE }
                )
                RepeatOptionRow(
                    label = stringResource(R.string.todo_repeat_daily),
                    selected = type == TodoRepeatType.DAILY,
                    onClick = { type = TodoRepeatType.DAILY }
                )
                RepeatOptionRow(
                    label = stringResource(R.string.todo_repeat_weekdays),
                    selected = type == TodoRepeatType.WEEKDAYS,
                    onClick = { type = TodoRepeatType.WEEKDAYS }
                )
                RepeatOptionRow(
                    label = stringResource(R.string.todo_repeat_weekends),
                    selected = type == TodoRepeatType.WEEKENDS,
                    onClick = { type = TodoRepeatType.WEEKENDS }
                )
                RepeatOptionRow(
                    label = stringResource(R.string.todo_repeat_interval),
                    selected = type == TodoRepeatType.INTERVAL,
                    onClick = { type = TodoRepeatType.INTERVAL }
                )
                if (type == TodoRepeatType.INTERVAL) {
                    GlassOutlinedTextField(
                        value = intervalText,
                        onValueChange = { input ->
                            intervalText = input.filter { it.isDigit() }.take(3)
                        },
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        label = { Text(stringResource(R.string.todo_repeat_interval_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                RepeatOptionRow(
                    label = stringResource(R.string.todo_repeat_custom),
                    selected = type == TodoRepeatType.CUSTOM,
                    onClick = { type = TodoRepeatType.CUSTOM }
                )
                if (type == TodoRepeatType.CUSTOM) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (day in 1..7) {
                            FilterChip(
                                selected = day in weekDays,
                                onClick = {
                                    weekDays = if (day in weekDays) weekDays - day else weekDays + day
                                },
                                label = { Text(weekdayLabel(day)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = confirmText,
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        TodoRepeat(
                            type = type,
                            weekDays = if (type == TodoRepeatType.CUSTOM) weekDays else emptySet(),
                            intervalDays = intervalDays.coerceAtLeast(1),
                            anchorAt = System.currentTimeMillis()
                        )
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
fun todoRepeatSummary(repeat: TodoRepeat): String = when (repeat.type) {
    TodoRepeatType.ONCE -> stringResource(R.string.todo_repeat_once)
    TodoRepeatType.DAILY -> stringResource(R.string.todo_repeat_daily)
    TodoRepeatType.WEEKDAYS -> stringResource(R.string.todo_repeat_weekdays)
    TodoRepeatType.WEEKENDS -> stringResource(R.string.todo_repeat_weekends)
    TodoRepeatType.INTERVAL -> stringResource(R.string.todo_repeat_interval_days, repeat.intervalDays)
    TodoRepeatType.CUSTOM -> {
        if (repeat.weekDays.isEmpty()) {
            stringResource(R.string.todo_repeat_custom)
        } else {
            val labels = repeat.weekDays.sorted().map { weekdayLabel(it) }
            stringResource(R.string.todo_repeat_custom_summary, labels.joinToString("、"))
        }
    }
}

@Composable
fun weekdayLabel(day: Int): String = stringResource(
    when (day) {
        1 -> R.string.weekday_mon
        2 -> R.string.weekday_tue
        3 -> R.string.weekday_wed
        4 -> R.string.weekday_thu
        5 -> R.string.weekday_fri
        6 -> R.string.weekday_sat
        else -> R.string.weekday_sun
    }
)

@Composable
private fun RepeatOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

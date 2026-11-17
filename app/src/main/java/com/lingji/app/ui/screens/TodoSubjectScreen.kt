package com.lingji.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.SubjectViewModel

/**
 * 信息岛：待办列表——条目勾选 + 重复设置（一次性 / 每日 / 工作日 / 休息日 / 间隔 / 自定义），
 * 新周期开始时由 ViewModel 惰性清除上一周期的勾选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoSubjectScreen(
    viewModel: SubjectViewModel,
    subject: Subject,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val liveSubject = remember(uiState.subjects, subject.id) {
        uiState.subjects.find { it.id == subject.id } ?: subject
    }
    var showRepeatDialog by remember { mutableStateOf(false) }

    // 进入页面时检查是否已跨入新的重复周期，若是则清除上一周期的勾选。
    LaunchedEffect(liveSubject.id, liveSubject.todoRepeat, liveSubject.todoLastResetAt) {
        viewModel.applyTodoRepeatResetIfNeeded(liveSubject)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = liveSubject.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = NotoSerifCJKsc)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            RepeatSummaryCard(
                repeat = liveSubject.todoRepeat,
                onClick = { showRepeatDialog = true }
            )
            if (liveSubject.todoItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.todo_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.todo_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(liveSubject.todoItems, key = { it.id }) { item ->
                        TodoItemRow(
                            item = item,
                            onToggle = { viewModel.setTodoItemDone(item.id, it) },
                            onDelete = { viewModel.deleteTodoItem(item.id) }
                        )
                    }
                }
            }
            TodoInputBar(onAdd = { viewModel.addTodoItem(liveSubject.id, it) })
        }
    }

    if (showRepeatDialog) {
        TodoRepeatDialog(
            initial = liveSubject.todoRepeat,
            onDismiss = { showRepeatDialog = false },
            onConfirm = { repeat ->
                viewModel.updateTodoRepeat(liveSubject.id, repeat)
                showRepeatDialog = false
            }
        )
    }
}

@Composable
private fun RepeatSummaryCard(repeat: TodoRepeat, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.todo_repeat_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = todoRepeatSummary(repeat),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TodoItemRow(item: TodoItem, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.isDone, onCheckedChange = onToggle)
            Text(
                text = item.content,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TodoInputBar(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassOutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.todo_item_hint)) },
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun todoRepeatSummary(repeat: TodoRepeat): String = when (repeat.type) {
    TodoRepeatType.ONCE -> stringResource(R.string.todo_repeat_once)
    TodoRepeatType.DAILY -> stringResource(R.string.todo_repeat_daily)
    TodoRepeatType.WEEKDAYS -> stringResource(R.string.todo_repeat_weekdays)
    TodoRepeatType.WEEKENDS -> stringResource(R.string.todo_repeat_weekends)
    TodoRepeatType.INTERVAL -> stringResource(R.string.todo_repeat_interval_days, repeat.intervalDays)
    TodoRepeatType.CUSTOM -> {
        if (repeat.weekDays.isEmpty()) {
            stringResource(R.string.todo_repeat_custom)
        } else {
            val labels = mutableListOf<String>()
            for (day in repeat.weekDays.sorted()) labels.add(weekdayLabel(day))
            stringResource(R.string.todo_repeat_custom_summary, labels.joinToString("、"))
        }
    }
}

@Composable
private fun weekdayLabel(day: Int): String = stringResource(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoRepeatDialog(
    initial: TodoRepeat,
    onDismiss: () -> Unit,
    onConfirm: (TodoRepeat) -> Unit
) {
    var type by remember { mutableStateOf(initial.type) }
    var intervalText by remember { mutableStateOf(initial.intervalDays.toString()) }
    var weekDays by remember { mutableStateOf(initial.weekDays) }
    val intervalDays = intervalText.toIntOrNull() ?: 0
    val canConfirm = (type != TodoRepeatType.INTERVAL || intervalDays >= 1) &&
        (type != TodoRepeatType.CUSTOM || weekDays.isNotEmpty())

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_repeat_settings)) },
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
                text = stringResource(R.string.save),
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

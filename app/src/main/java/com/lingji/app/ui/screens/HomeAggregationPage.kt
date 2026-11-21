package com.lingji.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.currentPeriodStart
import com.lingji.app.ui.viewmodel.HomeFragmentData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 聚合页待办条目：未完成待办及其所属待办列表。 */
data class AggregationTodo(
    val subjectId: String,
    val subjectTitle: String,
    val item: TodoItem
)

/**
 * 汇总所有待办列表中的未完成待办，按写入时间最新在前。
 * 重复周期已到期但尚未重置勾选的列表，其条目按新周期视为未完成，
 * 与进入待办列表时的自动重置行为保持一致。
 */
internal fun buildAggregationTodos(
    subjects: List<Subject>,
    now: Long = System.currentTimeMillis()
): List<AggregationTodo> = subjects
    .filter { it.type == SubjectType.TODO }
    .flatMap { subject ->
        val periodStart = subject.todoRepeat.currentPeriodStart(now)
        val resetPending = periodStart != null && subject.todoLastResetAt < periodStart
        subject.todoItems
            .filter { !it.isDone || resetPending }
            .map { AggregationTodo(subject.id, subject.title, it) }
    }
    .sortedByDescending { it.item.createdAt }

/** 未收录碎片按写入时间最新在前；无时间戳的历史碎片视为最早。 */
internal fun sortFragmentsNewestFirst(fragments: List<HomeFragmentData>): List<HomeFragmentData> =
    fragments.sortedByDescending { it.timestamp ?: 0L }

/**
 * 首页聚合页：未完成待办与未收录碎片两个分区，各自按写入时间最新在前。
 * 点击待办跳转所属待办列表，点击碎片打开底部碎片输入面板。
 */
@Composable
fun HomeAggregationPage(
    subjects: List<Subject>,
    fragments: List<HomeFragmentData>,
    onTodoClick: (String) -> Unit,
    onFragmentClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todos = remember(subjects) { buildAggregationTodos(subjects) }
    val sortedFragments = remember(fragments) { sortFragmentsNewestFirst(fragments) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    if (todos.isEmpty() && sortedFragments.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.aggregation_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.aggregation_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // 底部预留悬浮对话胶囊的高度，与卡片列表页一致。
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (todos.isNotEmpty()) {
            item(key = "__todo_header__") {
                AggregationSectionHeader(stringResource(R.string.aggregation_todo_section, todos.size))
            }
            items(todos, key = { "todo_${it.item.id}" }) { todo ->
                AggregationCard(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    content = todo.item.content,
                    source = todo.subjectTitle,
                    time = dateFormat.format(Date(todo.item.createdAt)),
                    onClick = { onTodoClick(todo.subjectId) }
                )
            }
        }
        if (sortedFragments.isNotEmpty()) {
            item(key = "__fragment_header__") {
                AggregationSectionHeader(
                    stringResource(R.string.aggregation_fragment_section, sortedFragments.size)
                )
            }
            items(sortedFragments.size, key = { "frag_$it" }) { index ->
                val fragment = sortedFragments[index]
                AggregationCard(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.StickyNote2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    content = fragment.content,
                    source = stringResource(R.string.fragments),
                    time = fragment.timestamp?.let { dateFormat.format(Date(it)) },
                    onClick = onFragmentClick
                )
            }
        }
    }
}

@Composable
private fun AggregationSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun AggregationCard(
    icon: @Composable () -> Unit,
    content: String,
    source: String,
    time: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.padding(top = 2.dp, end = 10.dp)) { icon() }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (time != null) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
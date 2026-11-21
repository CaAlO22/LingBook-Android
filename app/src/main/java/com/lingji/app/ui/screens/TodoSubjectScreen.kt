package com.lingji.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lingji.app.R
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.ui.components.DeleteConfirmDialog
import com.lingji.app.ui.components.FloatingInputContainer
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.GlassSurface
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.TodoReminderDialog
import com.lingji.app.ui.components.arrowKeyCursorBehavior
import com.lingji.app.ui.components.enterSendBehavior
import com.lingji.app.ui.components.reminderDateTimeText
import com.lingji.app.ui.components.reminderShortText
import com.lingji.app.ui.components.reminderTimeText
import com.lingji.app.ui.components.todoRepeatSummary
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.SubjectViewModel
import com.lingji.app.util.copyPlainText
import kotlin.math.roundToInt

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
    var menuExpanded by remember { mutableStateOf(false) }
    var showListReminderDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) } // 信息岛：待办条目编辑
    var pendingDeleteItem by remember { mutableStateOf<TodoItem?>(null) } // 删除二次确认
    val context = LocalContext.current

    // 信息岛：Android 13+ 首次设置提醒时申请通知权限，拒绝也不阻断保存。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}
    val ensureNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 进入页面时检查是否已跨入新的重复周期，若是则清除上一周期的勾选。
    LaunchedEffect(liveSubject.id, liveSubject.todoRepeat, liveSubject.todoLastResetAt) {
        viewModel.applyTodoRepeatResetIfNeeded(liveSubject)
    }

    Scaffold(
        // 信息岛：内容全屏绘制到系统导航栏之下，导航栏不再占据独立底栏。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                actions = {
                    // 信息岛：列表提醒与重复规则收进右上角菜单，不再占用列表显示空间
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            val reminderValue = when {
                                !liveSubject.todoReminderEnabled ->
                                    stringResource(R.string.todo_reminder_none)
                                liveSubject.todoRepeat.type == TodoRepeatType.ONCE ->
                                    reminderDateTimeText(liveSubject.todoReminderAt)
                                else -> stringResource(
                                    R.string.todo_reminder_menu_summary_repeat,
                                    todoRepeatSummary(liveSubject.todoRepeat),
                                    reminderTimeText(liveSubject.todoReminderMinutes)
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.todo_menu_reminder),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.todo_reminder_menu_summary,
                                                reminderValue
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    menuExpanded = false
                                    showListReminderDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.NotificationsNone, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.todo_menu_repeat),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.todo_menu_repeat_readonly,
                                                todoRepeatSummary(liveSubject.todoRepeat)
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {},
                                enabled = false,
                                leadingIcon = {
                                    Icon(Icons.Default.Repeat, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        // 信息岛：待办输入栏改为悬浮在内容之上（与碎片/笔记页一致），
        // 不再作为独占底栏占据布局高度。
        FloatingInputContainer(
            modifier = Modifier.padding(padding),
            horizontalMargin = 24.dp,
            bottomOffset = 16.dp,
            barShape = RoundedCornerShape(24.dp),
            floatingBar = {
                TodoInputBar(
                    hint = stringResource(R.string.todo_item_hint),
                    repeat = liveSubject.todoRepeat,
                    onEnsureNotificationPermission = ensureNotificationPermission,
                    onSend = { content, enabled, minutes, at ->
                        viewModel.addTodoItem(liveSubject.id, content, enabled, minutes, at)
                    }
                )
            },
            content = {
                Column(modifier = Modifier.fillMaxSize()) {
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
                        TodoList(
                            items = liveSubject.todoItems,
                            repeat = liveSubject.todoRepeat,
                            modifier = Modifier.weight(1f),
                            onToggle = { item, done -> viewModel.setTodoItemDone(item.id, done) },
                            onEdit = { editingItem = it },
                            onDelete = { pendingDeleteItem = it },
                            onCopy = { context.copyPlainText(it.content) },
                            onReorder = { viewModel.reorderTodoItems(it) }
                        )
                    }
                }
            }
        )
    }

    if (showListReminderDialog) {
        TodoReminderDialog(
            title = stringResource(R.string.todo_reminder_title_list),
            repeat = liveSubject.todoRepeat,
            initialEnabled = liveSubject.todoReminderEnabled,
            initialMinutes = liveSubject.todoReminderMinutes,
            initialAt = liveSubject.todoReminderAt,
            hint = stringResource(
                if (liveSubject.todoRepeat.type == TodoRepeatType.ONCE) {
                    R.string.todo_reminder_once_hint
                } else {
                    R.string.todo_reminder_repeat_hint
                }
            ) + " " + stringResource(R.string.todo_reminder_list_hint),
            onDismiss = { showListReminderDialog = false },
            onConfirm = { enabled, minutes, at ->
                showListReminderDialog = false
                if (enabled) ensureNotificationPermission()
                viewModel.updateTodoReminder(liveSubject.id, enabled, minutes, at)
            }
        )
    }

    // 信息岛：待办条目编辑弹窗（正文 + 提醒）
    editingItem?.let { item ->
        TodoEditDialog(
            item = item,
            repeat = liveSubject.todoRepeat,
            onEnsureNotificationPermission = ensureNotificationPermission,
            onDismiss = { editingItem = null },
            onConfirm = { content, enabled, minutes, at ->
                viewModel.updateTodoItem(item.id, content, enabled, minutes, at)
                editingItem = null
            }
        )
    }

    // 删除待办：二次确认，避免误触丢失条目
    pendingDeleteItem?.let { item ->
        DeleteConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.todo_delete_confirm),
            onConfirm = {
                viewModel.deleteTodoItem(item.id)
                pendingDeleteItem = null
            },
            onDismiss = { pendingDeleteItem = null }
        )
    }
}

/**
 * 信息岛：待办输入框——固定四行高度并可滚动，内部右侧自带单条提醒设置入口。
 */
@Composable
private fun TodoInputBar(
    hint: String,
    repeat: TodoRepeat,
    onEnsureNotificationPermission: () -> Unit,
    onSend: (
        content: String,
        reminderEnabled: Boolean,
        reminderMinutes: Int,
        reminderAt: Long
    ) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableIntStateOf(-1) }
    var reminderAt by remember { mutableLongStateOf(0L) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val submit: () -> Unit = {
        if (text.text.isNotBlank()) {
            onSend(text.text.trim(), reminderEnabled, reminderMinutes, reminderAt)
            text = TextFieldValue("")
            reminderEnabled = false
            reminderMinutes = -1
            reminderAt = 0
        }
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .arrowKeyCursorBehavior(text, { text = it }) { textLayout }
                    .enterSendBehavior(submit),
                minLines = 4,
                maxLines = 4,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showReminderDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = stringResource(R.string.todo_reminder_title_item),
                        tint = if (reminderEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = if (reminderEnabled) {
                        reminderShortText(repeat, reminderMinutes, reminderAt)
                    } else {
                        stringResource(R.string.todo_reminder_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reminderEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                val sendEnabled = text.text.isNotBlank()
                IconButton(onClick = submit, enabled = sendEnabled) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (sendEnabled) 0.9f else 0.3f
                        ),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.cd_send),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReminderDialog) {
        TodoReminderDialog(
            title = stringResource(R.string.todo_reminder_title_item),
            repeat = repeat,
            initialEnabled = reminderEnabled,
            initialMinutes = reminderMinutes,
            initialAt = reminderAt,
            hint = stringResource(
                if (repeat.type == TodoRepeatType.ONCE) {
                    R.string.todo_reminder_once_hint
                } else {
                    R.string.todo_reminder_repeat_hint
                }
            ) + " " + stringResource(R.string.todo_reminder_item_hint),
            onDismiss = { showReminderDialog = false },
            onConfirm = { enabled, minutes, at ->
                showReminderDialog = false
                reminderEnabled = enabled
                reminderMinutes = minutes
                reminderAt = at
                if (enabled) onEnsureNotificationPermission()
            }
        )
    }
}

/**
 * 待办列表：左侧手柄拖动排序、长按条目复制正文、新增条目后自动滚到底部。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoList(
    items: List<TodoItem>,
    repeat: TodoRepeat,
    onToggle: (TodoItem, Boolean) -> Unit,
    onEdit: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit,
    onCopy: (TodoItem) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 拖动期间的临时顺序：以 id 列表驱动渲染，松手时一次性提交。
    var order by remember(items) { mutableStateOf(items.map { it.id }) }
    val itemsById = remember(items) { items.associateBy { it.id } }
    // 拖动检测是长生命周期闭包（pointerInput(Unit)），这里始终指向最新的条目列表。
    val latestIds by rememberUpdatedState(items.map { it.id })
    val orderedItems = order.mapNotNull { itemsById[it] }

    // 拖动的预览卡片完全独立于列表绘制：位置只跟手指走，不再依赖列表项自身的位移，
    // 因此不会出现"列表在换位、卡片却黏在原处"的问题。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var previewTop by remember { mutableFloatStateOf(0f) }  // 指尖在列表坐标系中的 y
    var grabOffsetY by remember { mutableFloatStateOf(0f) } // 按下点相对条目顶部的偏移
    var rowHeightPx by remember { mutableFloatStateOf(0f) } // 单行高度，用于预览卡中心
    val previewItem = draggingId?.let { itemsById[it] }

    // 落点：拖动期间不改动 order，只算出"松手会落在第几位"，其余条目按这个位次让位。
    // 拖动中反复重排会触发 LazyColumn 的锚点跟随（视口被被拖条目拖着跑），所以只在松手时改一次。
    var dropIndex by remember { mutableIntStateOf(-1) }

    fun syncDropIndex() {
        val itemId = draggingId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return
        val from = order.indexOf(itemId)
        if (from < 0) return
        val center = previewTop - grabOffsetY + rowHeightPx / 2f
        var target = order.size - 1
        for (info in visible) {
            if (info.key == itemId) continue
            val idx = order.indexOf(info.key as String)
            if (idx < 0) continue
            // 去掉被拖条目后的位次
            val reduced = if (idx < from) idx else idx - 1
            if (center < info.offset + info.size / 2f) {
                target = reduced
                break
            }
            target = reduced + 1
        }
        dropIndex = target.coerceIn(0, (order.size - 1).coerceAtLeast(0))
    }

    /** 结束拖动：只有正常抬指才提交新顺序，被打断则原样还原。 */
    fun finishDrag(commit: Boolean) {
        val droppedId = draggingId ?: return
        val target = dropIndex
        draggingId = null
        dropIndex = -1
        if (!commit || target < 0) return
        val next = order.toMutableList()
        if (!next.remove(droppedId)) return
        next.add(target.coerceIn(0, next.size), droppedId)
        if (next == order || next == latestIds) return
        // 重排会让 LazyColumn 按 key 把视口"锚"到被拖条目上，松手瞬间整个列表会跳两行。
        // 先本地换序，再在下一帧测量时按位次把视口拉回原处，列表就不会动了。
        val anchorIndex = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        order = next
        onReorder(next)
        listState.requestScrollToItem(anchorIndex, anchorOffset)
    }

    // 自动滚动：拖动期间逐帧检查预览卡是否贴近上下边缘，用同一个协程连续滚动。
    // 不能再由 onDrag 事件驱动——手指按住不动等它滚时事件就停了，表现为"卡住"；
    // 也不能每个事件 launch 一次 scrollBy，几十个并发滚动互相取消同样会卡顿。
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        val step = with(density) { 4.dp.toPx() }
        val edge = with(density) { 56.dp.toPx() }
        while (true) {
            syncDropIndex()
            val info = listState.layoutInfo
            val top = previewTop - grabOffsetY
            val bottom = top + rowHeightPx
            val act = when {
                top - info.viewportStartOffset < edge && listState.canScrollBackward -> -step
                info.viewportEndOffset - bottom < edge && listState.canScrollForward -> step
                else -> 0f
            }
            if (act != 0f) listState.scrollBy(act)
            withFrameNanos { }
        }
    }

    // 仅在新条目加入（数量增加）时滚到底部，初始加载、删除、排序不触发。
    var prevSize by remember { mutableIntStateOf(items.size) }
    LaunchedEffect(items.size) {
        if (items.size > prevSize) {
            listState.animateScrollToItem(items.lastIndex)
        }
        prevSize = items.size
    }

    // 拖动检测挂在列表容器上，而不是挂在每一行：
    // 行一旦滚出视口，LazyColumn 会回收该行，挂在这一行上的 pointerInput 也随之销毁，
    // 手势于是以"取消"结束——这正是自动滚动到一半待办卡住、松手不落位的原因。
    // 容器始终存在，拖动因此能一直跟到最后一行。
    // （clipToBounds：预览卡跟出列表边界时不会画到"重复设置"卡片上去。）
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                // 手柄在行内的位置：内容内边距 16dp + 行内 2dp 起，宽 28dp。
                val zoneStart = 12.dp.toPx()
                val zoneEnd = 44.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    if (down.position.x !in zoneStart..zoneEnd) return@awaitEachGesture
                    val row = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        down.position.y >= it.offset && down.position.y < it.offset + it.size
                    } ?: return@awaitEachGesture
                    val id = row.key as? String ?: return@awaitEachGesture

                    // 按在手柄上就由容器接管这次按压，列表不会再把它当成滚动。
                    down.consume()
                    draggingId = id
                    grabOffsetY = down.position.y - row.offset
                    previewTop = down.position.y
                    rowHeightPx = row.size.toFloat()
                    dropIndex = order.indexOf(id)

                    var completed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            completed = true
                            break
                        }
                        val dy = change.positionChange().y
                        if (dy != 0f) {
                            change.consume()
                            previewTop += dy
                            syncDropIndex()
                        }
                    }
                    finishDrag(completed)
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 底部预留悬浮输入框（四行 + 工具行）的高度，使最后一条待办可滚动到其上方。
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 190.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orderedItems, key = { it.id }) { item ->
                val isDragging = item.id == draggingId
                val index = order.indexOf(item.id)
                val from = draggingId?.let { order.indexOf(it) } ?: -1
                // 让位：其他条目按落点前后平移一格，空出来的那个位置就是松手后的落点。
                // 这里只做绘制位移、不改 order，避免拖动中重排带来视口跳动。
                val pitch = rowHeightPx + with(density) { 8.dp.toPx() }
                val shift = when {
                    draggingId == null || from < 0 || isDragging -> 0f
                    index < from && index >= dropIndex -> pitch
                    index > from && index <= dropIndex -> -pitch
                    else -> 0f
                }
                TodoItemRow(
                    item = item,
                    repeat = repeat,
                    modifier = Modifier
                        .graphicsLayer { translationY = shift }
                        .alpha(if (isDragging) 0.15f else 1f),
                    onToggle = { onToggle(item, it) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    onCopy = { onCopy(item) },
                    isDragging = isDragging
                )
            }
        }

        // 拖动预览卡片：抬起的阴影 + 始终贴在指尖，松手即消失。
        // 位置夹在列表可视区内，否则拖到底部时会钻到悬浮输入胶囊下面"消失"。
        if (previewItem != null) {
            TodoDragPreview(
                item = previewItem,
                repeat = repeat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset {
                        val info = listState.layoutInfo
                        // 底部留出与列表一致的 100dp，否则拖到底部时预览卡会被悬浮输入胶囊盖住。
                        val bottomBand = with(density) { 100.dp.toPx() }
                        val maxTop = (info.viewportEndOffset - bottomBand - rowHeightPx).roundToInt()
                        IntOffset(
                            0,
                            (previewTop - grabOffsetY).roundToInt()
                                .coerceIn(0, maxTop.coerceAtLeast(0))
                        )
                    }
            )
        }
    }
}

/** 拖动中的浮层预览：只做展示，不参与交互，保证跟手且不与列表自身的手势打架。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoDragPreview(
    item: TodoItem,
    repeat: TodoRepeat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(width = 28.dp, height = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
            // 必须给一个空回调：Material3 的 Checkbox 只在 onCheckedChange 非空时
            // 才套 48dp 最小点击区，传 null 会让勾选框尺寸变小、整行内容左移错位。
            Checkbox(checked = item.isDone, onCheckedChange = {})
            TodoItemContent(item = item, repeat = repeat, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoItemRow(
    item: TodoItem,
    repeat: TodoRepeat,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    Card(
        // 整张卡片不要再挂 combinedClickable：它和左侧手柄的拖动检测在同一个手势里竞争，
        // 按住超过 500ms（真实拖动几乎必然超过）就会触发长按，拖动随即被取消，
        // 卡片于是"黏"在原地、只剩一层灰色按压涟漪。长按复制改由正文文本自己处理。
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // 拖动中的卡片抬起一层阴影，避免与相邻卡片糊在一起看不清跟手效果。
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖动手柄：只有此处响应垂直拖动，从而与长按复制互不冲突。
            Box(
                modifier = Modifier.size(width = 28.dp, height = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Checkbox(checked = item.isDone, onCheckedChange = onToggle)
            TodoItemContent(
                item = item,
                repeat = repeat,
                modifier = Modifier
                    .weight(1f)
                    // 长按复制正文；点击留空，避免与内部控件争夺事件。
                    .combinedClickable(onClick = {}, onLongClick = onCopy)
            )
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.cd_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
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

/** 待办正文 + 提醒摘要；长按复制由外层统一处理。 */
@Composable
private fun TodoItemContent(item: TodoItem, repeat: TodoRepeat, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
        )
        if (item.reminderEnabled) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = reminderShortText(repeat, item.reminderMinutes, item.reminderAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** 信息岛：待办条目编辑弹窗——正文 + 提醒设置一起保存。 */
@Composable
private fun TodoEditDialog(
    item: TodoItem,
    repeat: TodoRepeat,
    onEnsureNotificationPermission: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        content: String,
        reminderEnabled: Boolean,
        reminderMinutes: Int,
        reminderAt: Long
    ) -> Unit
) {
    var text by remember(item.id) { mutableStateOf(item.content) }
    var reminderEnabled by remember(item.id) { mutableStateOf(item.reminderEnabled) }
    var reminderMinutes by remember(item.id) { mutableIntStateOf(item.reminderMinutes) }
    var reminderAt by remember(item.id) { mutableLongStateOf(item.reminderAt) }
    var showReminderDialog by remember { mutableStateOf(false) }

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_edit_title)) },
        text = {
            Column {
                GlassOutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.todo_item_hint)) },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 6
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReminderDialog = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = if (reminderEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.todo_reminder_title_item),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (reminderEnabled) {
                            reminderShortText(repeat, reminderMinutes, reminderAt)
                        } else {
                            stringResource(R.string.todo_reminder_none)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reminderEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.save),
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text, reminderEnabled, reminderMinutes, reminderAt) }
            )
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )

    if (showReminderDialog) {
        TodoReminderDialog(
            title = stringResource(R.string.todo_reminder_title_item),
            repeat = repeat,
            initialEnabled = reminderEnabled,
            initialMinutes = reminderMinutes,
            initialAt = reminderAt,
            hint = stringResource(
                if (repeat.type == TodoRepeatType.ONCE) {
                    R.string.todo_reminder_once_hint
                } else {
                    R.string.todo_reminder_repeat_hint
                }
            ) + " " + stringResource(R.string.todo_reminder_item_hint),
            onDismiss = { showReminderDialog = false },
            onConfirm = { enabled, minutes, at ->
                showReminderDialog = false
                reminderEnabled = enabled
                reminderMinutes = minutes
                reminderAt = at
                if (enabled) onEnsureNotificationPermission()
            }
        )
    }
}

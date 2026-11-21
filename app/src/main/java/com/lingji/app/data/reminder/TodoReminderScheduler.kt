package com.lingji.app.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lingji.app.MainActivity
import com.lingji.app.R
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.TodoItemDao
import com.lingji.app.data.db.entities.SubjectEntity
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import com.lingji.app.domain.model.currentPeriodStart
import com.lingji.app.domain.model.nextReminderAt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 信息岛：待办提醒调度。
 *
 * 调度规则：
 * - 一次性待办/列表：按用户选择的绝对日期时间精确触发，触发后自动清除。
 * - 重复待办/列表：只保存“当天时刻”，日期由重复规则推导，保证提醒不晚于下一次刷新；
 *   每次触发后自动排下一次，已完成或列表无未完成项时跳过通知。
 *
 * 闹钟挂在 AlarmManager 上，应用被杀或重启后由 [ReminderRestoreReceiver] 重新排定。
 */
@Singleton
class TodoReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subjectDao: SubjectDao,
    private val todoItemDao: TodoItemDao
) {

    companion object {
        const val EXTRA_KIND = "reminder_kind"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_AT = "reminder_at"
        const val KIND_ITEM = "item"
        const val KIND_LIST = "list"
        private const val CHANNEL_ID = "todo_reminders"
        private const val TAG = "TodoReminder"
    }

    /** 待办列表或条目变更后，重排该列表下的全部提醒。 */
    suspend fun refreshSubject(subjectId: String) {
        val subject = subjectDao.getSubjectById(subjectId)
        if (subject == null || !subject.type.equals("todo", ignoreCase = true)) {
            cancel(KIND_LIST, subjectId)
            return
        }
        val repeat = subject.toTodoRepeat()
        val now = System.currentTimeMillis()

        scheduleListReminder(subject, repeat, now)

        todoItemDao.getTodoItemsBySubjectOnce(subjectId).forEach { item ->
            if (!item.reminderEnabled) {
                cancel(KIND_ITEM, item.id)
                return@forEach
            }
            val at = when (repeat.type) {
                TodoRepeatType.ONCE ->
                    if (item.isDone || item.reminderAt <= now) null else item.reminderAt
                else -> repeat.nextReminderAt(item.reminderMinutes, now)
            }
            if (at == null) cancel(KIND_ITEM, item.id) else schedule(KIND_ITEM, item.id, at)
        }
    }

    /** 开机、应用更新或时区变化后恢复全部提醒。 */
    suspend fun refreshAll() {
        subjectDao.getAllOnce()
            .filter { it.type.equals("todo", ignoreCase = true) }
            .forEach { refreshSubject(it.id) }
    }

    /** 条目被删除时立即取消对应闹钟。 */
    fun cancelItem(itemId: String) = cancel(KIND_ITEM, itemId)

    /** 列表被删除时取消列表提醒；调用方负责再取消各条目提醒。 */
    fun cancelSubject(subjectId: String) = cancel(KIND_LIST, subjectId)

    /** 闹钟触发后处理：条件满足则通知，随后排下一次。 */
    suspend fun handleAlarm(kind: String, id: String, scheduledAt: Long) {
        when (kind) {
            KIND_ITEM -> handleItemAlarm(id, scheduledAt)
            KIND_LIST -> handleListAlarm(id, scheduledAt)
        }
    }

    private suspend fun handleItemAlarm(itemId: String, scheduledAt: Long) {
        val item = todoItemDao.getByIdOnce(itemId) ?: return
        val subject = subjectDao.getSubjectById(item.subjectId) ?: return
        if (!subject.type.equals("todo", ignoreCase = true)) return
        val repeat = subject.toTodoRepeat()
        val inCurrentCycle = isInCurrentCycle(repeat, scheduledAt)
        if (item.reminderEnabled && !isEffectivelyDone(item, subject, repeat) && inCurrentCycle) {
            notify(
                notificationId = notificationId(KIND_ITEM, item.id),
                title = subject.title.ifBlank { context.getString(R.string.todo_reminder_channel) },
                text = item.content,
                openSubjectId = subject.id
            )
        }
        if (repeat.type == TodoRepeatType.ONCE) {
            todoItemDao.updateReminder(item.id, false, -1, 0, System.currentTimeMillis())
        }
        refreshSubject(item.subjectId)
    }

    private suspend fun handleListAlarm(subjectId: String, scheduledAt: Long) {
        val subject = subjectDao.getSubjectById(subjectId) ?: return
        if (!subject.type.equals("todo", ignoreCase = true)) return
        if (subject.todoReminderEnabled) {
            val repeat = subject.toTodoRepeat()
            // 列表进入新周期后，旧勾选在下一次打开列表前仍是持久化状态，这里按周期折算
            val pending = todoItemDao.getTodoItemsBySubjectOnce(subjectId)
                .count { !isEffectivelyDone(it, subject, repeat) }
            if (pending > 0 && isInCurrentCycle(repeat, scheduledAt)) {
                notify(
                    notificationId = notificationId(KIND_LIST, subjectId),
                    title = subject.title,
                    text = context.getString(R.string.todo_reminder_list_notification, pending),
                    openSubjectId = subject.id
                )
            }
            if (repeat.type == TodoRepeatType.ONCE) {
                subjectDao.updateTodoReminder(subjectId, false, -1, 0, System.currentTimeMillis())
            }
        }
        refreshSubject(subjectId)
    }

    /**
     * 判断条目在当前周期是否已完成。
     *
     * 重复列表的勾选只在打开列表时按周期清除。闹钟可能在用户打开应用之前触发，
     * 此时若上次清除时间早于当前周期起点，旧的勾选视为过期，按未完成处理。
     */
    private fun isEffectivelyDone(
        item: com.lingji.app.data.db.entities.TodoItemEntity,
        subject: SubjectEntity,
        repeat: TodoRepeat
    ): Boolean {
        if (!item.isDone) return false
        if (repeat.type == TodoRepeatType.ONCE) return true
        val periodStart = repeat.currentPeriodStart(System.currentTimeMillis()) ?: return true
        return subject.todoLastResetAt >= periodStart
    }

    /**
     * 迟到的重复提醒不再打扰：只有闹钟排定的周期与当前周期一致才通知。
     * 例如每日提醒在设备关机后次日才触发，此时应直接跳过。
     */
    private fun isInCurrentCycle(repeat: TodoRepeat, scheduledAt: Long): Boolean {
        if (repeat.type == TodoRepeatType.ONCE || scheduledAt <= 0) return true
        val now = System.currentTimeMillis()
        return repeat.currentPeriodStart(now) == repeat.currentPeriodStart(scheduledAt)
    }

    private fun scheduleListReminder(subject: SubjectEntity, repeat: TodoRepeat, now: Long) {
        if (!subject.todoReminderEnabled) {
            cancel(KIND_LIST, subject.id)
            return
        }
        val at = when (repeat.type) {
            TodoRepeatType.ONCE ->
                if (subject.todoReminderAt > now) subject.todoReminderAt else null
            else -> repeat.nextReminderAt(subject.todoReminderMinutes, now)
        }
        if (at == null) cancel(KIND_LIST, subject.id) else schedule(KIND_LIST, subject.id, at)
    }

    private fun schedule(kind: String, id: String, at: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(kind, id, at, PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                // 未授予精确闹钟权限时退化为省电模式下的近似闹钟，提醒可能略有延迟。
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm denied, fallback to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        }
    }

    private fun cancel(kind: String, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TodoReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(kind, id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(kind: String, id: String, at: Long, flags: Int): PendingIntent {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_AT, at)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(kind, id),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(kind: String, id: String): Int = "$kind:$id".hashCode()

    private fun notificationId(kind: String, id: String): Int = ("todo:$kind:$id").hashCode()

    private fun notify(notificationId: Int, title: String, text: String, openSubjectId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "notification permission not granted, skip notify")
            return
        }
        ensureChannel()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_subject_id", openSubjectId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_todo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.todo_reminder_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.todo_reminder_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun SubjectEntity.toTodoRepeat(): TodoRepeat = TodoRepeat(
        type = runCatching { TodoRepeatType.valueOf(todoRepeatType) }.getOrDefault(TodoRepeatType.ONCE),
        weekDays = todoRepeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
        intervalDays = todoIntervalDays.coerceAtLeast(1),
        anchorAt = todoRepeatAnchorAt
    )
}

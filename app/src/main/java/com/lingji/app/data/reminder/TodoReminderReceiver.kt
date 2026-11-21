package com.lingji.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 信息岛：待办提醒闹钟触发入口，交由 [TodoReminderScheduler] 判断是否通知并排下一次。 */
@AndroidEntryPoint
class TodoReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: TodoReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(TodoReminderScheduler.EXTRA_KIND) ?: return
        val id = intent.getStringExtra(TodoReminderScheduler.EXTRA_ID) ?: return
        val scheduledAt = intent.getLongExtra(TodoReminderScheduler.EXTRA_AT, 0L)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.handleAlarm(kind, id, scheduledAt)
            } catch (t: Throwable) {
                Log.w("TodoReminder", "handle alarm failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

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

/**
 * 信息岛：系统重启、应用更新、时间/时区变化后 AlarmManager 中的闹钟会失效或偏移，
 * 这里统一重排全部待办提醒。
 */
@AndroidEntryPoint
class ReminderRestoreReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: TodoReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.refreshAll()
            } catch (t: Throwable) {
                Log.w("TodoReminder", "restore reminders failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

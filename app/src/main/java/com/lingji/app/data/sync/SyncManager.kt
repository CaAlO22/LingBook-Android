package com.lingji.app.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 设置页可见的同步状态。 */
data class SyncUiStatus(
    val inProgress: Boolean = false,
    val lastSyncAt: Long = 0,
    val message: String = ""
)

/**
 * 云同步调度器：App 启动时同步一次，之后前台周期同步；
 * 所有失败静默降级，不影响 App 正常使用。
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncRepository: SyncRepository,
    private val configStore: SyncConfigStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val _status = MutableStateFlow(
        SyncUiStatus(lastSyncAt = configStore.lastSyncAt, message = configStore.lastSyncMessage)
    )
    val status: StateFlow<SyncUiStatus> = _status.asStateFlow()

    /** 由 Application.onCreate 调用一次。 */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            syncIfEnabled()
            while (true) {
                delay(PERIODIC_INTERVAL_MS)
                syncIfEnabled()
            }
        }
    }

    /** 设置页「立即同步」手动触发。 */
    fun requestSync() {
        scope.launch { doSync() }
    }

    private suspend fun syncIfEnabled() {
        val config = configStore.loadConfig()
        if (config.enabled && config.isValid) doSync()
    }

    private suspend fun doSync() {
        if (_status.value.inProgress) return
        _status.update { it.copy(inProgress = true) }
        val result = syncRepository.syncNow()
        val now = System.currentTimeMillis()
        when (result) {
            is SyncResult.Success -> {
                val msg = "同步成功（上传 ${result.pushed}，下载 ${result.pulled}，删除 ${result.deleted}）"
                configStore.lastSyncMessage = msg
                _status.update { it.copy(inProgress = false, lastSyncAt = now, message = msg) }
            }
            is SyncResult.Error -> {
                val msg = "同步失败：${result.message}"
                configStore.lastSyncMessage = msg
                _status.update { it.copy(inProgress = false, lastSyncAt = now, message = msg) }
            }
            SyncResult.Disabled -> {
                _status.update { it.copy(inProgress = false) }
            }
        }
    }

    companion object {
        private const val PERIODIC_INTERVAL_MS = 3 * 60 * 1000L
    }
}

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
    val message: String = "",
    /** 最近一轮成功通信时服务端上报的协议版本；从未连通时为 null。 */
    val serverSchemaVersion: Int? = null,
    /** 最近一次同步是否以失败/拒绝收尾（设置页据此标红）。 */
    val isError: Boolean = false,
    /** 最近一次同步是否因「服务端协议版本过旧」被拒绝。 */
    val serverOutdated: Boolean = false
)

/**
 * 云同步调度器：App 启动时同步一次，之后前台周期同步；
 * 所有失败静默降级，不影响 App 正常使用。
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncRepository: SyncRepository,
    private val configStore: SyncConfigStore,
    private val modelCatalogRepository: ModelCatalogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val _status = MutableStateFlow(
        run {
            // 冷启动时把上次的结果文案恢复成对应状态，否则设置页会把「已拒绝/失败」显示成普通文案
            val message = configStore.lastSyncMessage
            SyncUiStatus(
                lastSyncAt = configStore.lastSyncAt,
                message = message,
                isError = message.startsWith("同步失败") || message.startsWith("同步已拒绝"),
                serverOutdated = message.startsWith("同步已拒绝")
            )
        }
    )
    val status: StateFlow<SyncUiStatus> = _status.asStateFlow()

    /** 云端模型目录的版本 / 刷新状态，设置页展示与手动刷新用。 */
    val catalogStatus: StateFlow<ModelCatalogStatus> = modelCatalogRepository.status

    /** 由 Application.onCreate 调用一次。 */
    fun start() {
        if (started) return
        started = true
        // 先恢复本地缓存的模型目录，保证首屏与设置页读到的是上次下发的结果（离线也生效）
        modelCatalogRepository.loadCached()
        scope.launch {
            syncIfEnabled()
            refreshCatalogIfEnabled()
            while (true) {
                delay(PERIODIC_INTERVAL_MS)
                syncIfEnabled()
                refreshCatalogIfEnabled()
            }
        }
    }

    /** 设置页「立即同步」手动触发。 */
    fun requestSync() {
        // 先同步再顺带刷新目录：目录接口慢/不通时不该拖住「立即同步」的进度显示
        scope.launch {
            doSync()
            refreshCatalogIfEnabled()
        }
    }

    /** 设置页「刷新模型列表」：强制全量，不看本地版本。 */
    fun requestCatalogRefresh() {
        scope.launch { modelCatalogRepository.refresh(force = true) }
    }

    // 信息岛：设置页数据管理入口（覆盖云端 / 从云端恢复）
    /** 设置页「覆盖云端数据」：清空云端启用的同步表后把本机数据全量重推。 */
    fun requestOverwriteCloud() {
        scope.launch { doSync(SyncOperation.OVERWRITE_CLOUD) }
    }

    /** 设置页「从云端恢复」：mergeLocal=true 合并本地，false 覆盖本地。 */
    fun requestRestoreFromCloud(mergeLocal: Boolean) {
        scope.launch {
            doSync(
                if (mergeLocal) SyncOperation.RESTORE_MERGE_LOCAL
                else SyncOperation.RESTORE_OVERWRITE_LOCAL
            )
        }
    }

    private suspend fun syncIfEnabled() {
        val config = configStore.loadConfig()
        if (config.enabled && config.isValid) doSync()
    }

    /** 模型目录与「云同步」总开关走：任一类别开启就顺带刷新，未配置服务器时直接跳过。 */
    private suspend fun refreshCatalogIfEnabled() {
        val config = configStore.loadConfig()
        if (config.enabled && config.isValid) {
            modelCatalogRepository.refresh()
        }
    }

    private suspend fun doSync(operation: SyncOperation = SyncOperation.NORMAL) {
        if (_status.value.inProgress) return
        _status.update { it.copy(inProgress = true) }
        val result = when (operation) {
            SyncOperation.NORMAL -> syncRepository.syncNow()
            SyncOperation.OVERWRITE_CLOUD -> syncRepository.overwriteCloud()
            SyncOperation.RESTORE_OVERWRITE_LOCAL -> syncRepository.restoreFromCloud(mergeLocal = false)
            SyncOperation.RESTORE_MERGE_LOCAL -> syncRepository.restoreFromCloud(mergeLocal = true)
        }
        val now = System.currentTimeMillis()
        when (result) {
            is SyncResult.Success -> {
                val msg = when (operation) {
                    SyncOperation.NORMAL ->
                        "同步成功（上传 ${result.pushed}，下载 ${result.pulled}，删除 ${result.deleted}）"
                    SyncOperation.OVERWRITE_CLOUD -> "已覆盖云端数据（上传 ${result.pushed}）"
                    SyncOperation.RESTORE_OVERWRITE_LOCAL ->
                        "已从云端恢复并覆盖本地（下载 ${result.pulled}，删除 ${result.deleted}）"
                    SyncOperation.RESTORE_MERGE_LOCAL ->
                        "已从云端合并本地数据（下载 ${result.pulled}，删除 ${result.deleted}）"
                }
                configStore.lastSyncMessage = msg
                _status.update {
                    it.copy(
                        inProgress = false,
                        lastSyncAt = now,
                        message = msg,
                        serverSchemaVersion = result.serverSchemaVersion,
                        isError = false,
                        serverOutdated = false
                    )
                }
            }
            is SyncResult.ServerOutdated -> {
                val msg = "同步已拒绝：服务器协议 v${result.serverVersion} 低于应用 v${result.clientVersion}，" +
                    "请先升级服务器（本机数据未上传）"
                configStore.lastSyncMessage = msg
                _status.update {
                    it.copy(
                        inProgress = false,
                        lastSyncAt = now,
                        message = msg,
                        serverSchemaVersion = result.serverVersion,
                        isError = true,
                        serverOutdated = true
                    )
                }
            }
            is SyncResult.Error -> {
                val msg = "同步失败：${result.message}"
                configStore.lastSyncMessage = msg
                _status.update {
                    it.copy(
                        inProgress = false,
                        lastSyncAt = now,
                        message = msg,
                        isError = true,
                        serverOutdated = false
                    )
                }
            }
            SyncResult.Disabled -> {
                _status.update { it.copy(inProgress = false) }
            }
        }
    }

    /** 本轮触发的操作类型，仅用于选择结果文案（同步流程本身共用）。 */
    private enum class SyncOperation {
        NORMAL, OVERWRITE_CLOUD, RESTORE_OVERWRITE_LOCAL, RESTORE_MERGE_LOCAL
    }

    companion object {
        private const val PERIODIC_INTERVAL_MS = 3 * 60 * 1000L
    }
}

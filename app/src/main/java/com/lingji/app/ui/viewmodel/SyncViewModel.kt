package com.lingji.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingji.app.data.remote.sync.SyncApi
import com.lingji.app.data.sync.DeviceIdFormat
import com.lingji.app.data.sync.LanScanResult
import com.lingji.app.data.sync.LanServerScanner
import com.lingji.app.data.sync.SyncConfigStore
import com.lingji.app.data.sync.SyncManager
import com.lingji.app.data.sync.SyncUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 云同步设置表单的 UI 状态（端口以字符串编辑）。三类内容各自独立开关。 */
data class SyncConfigUiState(
    val syncNotes: Boolean = false,
    val syncModels: Boolean = false,
    val syncTokens: Boolean = false,
    val host: String = "",
    val port: String = SyncConfigStore.DEFAULT_PORT.toString(),
    val token: String = "",
    /** 本机唯一标识，可复制到其他设备以共用同一份数据。 */
    val deviceId: String = ""
) {
    /** 任一类别开启即可执行同步。 */
    val anyEnabled: Boolean get() = syncNotes || syncModels || syncTokens
}

/** 局域网扫描的界面状态。 */
sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Scanning(val remainingSeconds: Int) : ScanUiState
    data class Found(val host: String, val port: Int) : ScanUiState
    data object NotFound : ScanUiState
    data object NoLocalNetwork : ScanUiState
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val configStore: SyncConfigStore,
    private val syncManager: SyncManager,
    private val syncApi: SyncApi,
    private val lanScanner: LanServerScanner
) : ViewModel() {

    private val _config = MutableStateFlow(
        SyncConfigUiState(
            syncNotes = configStore.syncNotes,
            syncModels = configStore.syncModels,
            syncTokens = configStore.syncTokens,
            host = configStore.host,
            port = configStore.port.toString(),
            token = configStore.token,
            deviceId = configStore.ensureDeviceId()
        )
    )
    val config: StateFlow<SyncConfigUiState> = _config.asStateFlow()

    val status: StateFlow<SyncUiStatus> = syncManager.status

    /** 测试结果：first=是否成功，second=描述信息；null 表示未测试。 */
    private val _testResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testResult: StateFlow<Pair<Boolean, String>?> = _testResult.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    fun setSyncNotes(enabled: Boolean) {
        val wasOn = _config.value.syncNotes
        _config.value = _config.value.copy(syncNotes = enabled)
        persist()
        onCategoryToggled(wasOn, enabled)
    }

    fun setSyncModels(enabled: Boolean) {
        val wasOn = _config.value.syncModels
        _config.value = _config.value.copy(syncModels = enabled)
        persist()
        onCategoryToggled(wasOn, enabled)
    }

    fun setSyncTokens(enabled: Boolean) {
        val wasOn = _config.value.syncTokens
        _config.value = _config.value.copy(syncTokens = enabled)
        persist()
        onCategoryToggled(wasOn, enabled)
    }

    /**
     * 首次开启某一类时清空 lastRev，让下一轮做一次全量拉取，
     * 补齐关闭期间其他设备已经存在的该类数据；随后恢复正常增量同步。
     */
    private fun onCategoryToggled(wasOn: Boolean, nowOn: Boolean) {
        if (!wasOn && nowOn) configStore.lastRev = 0
        if (_config.value.anyEnabled) syncManager.requestSync()
    }

    fun setHost(host: String) {
        _config.value = _config.value.copy(host = host)
        persist()
    }

    fun setPort(port: String) {
        _config.value = _config.value.copy(port = port.filter { it.isDigit() }.take(5))
        persist()
    }

    fun setToken(token: String) {
        _config.value = _config.value.copy(token = token)
        persist()
    }

    fun syncNow() = syncManager.requestSync()

    /**
     * 扫描局域网服务器，最多持续 [LanServerScanner.DEFAULT_DURATION_MS]。
     *
     * 扫描期间再次调用视为「取消」。找到后把地址与端口直接写进配置表单，
     * 用户无需手工抄 IP；端口按当前填写值（非法时用默认端口）扫描。
     */
    fun scanLan() {
        if (scanJob?.isActive == true) {
            scanJob?.cancel()
            scanJob = null
            _scanState.value = ScanUiState.Idle
            return
        }
        val port = _config.value.port.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: SyncConfigStore.DEFAULT_PORT
        val token = _config.value.token.trim()
        val durationSeconds = (LanServerScanner.DEFAULT_DURATION_MS / 1000).toInt()

        scanJob = viewModelScope.launch {
            _scanState.value = ScanUiState.Scanning(durationSeconds)
            val result = try {
                withContext(Dispatchers.IO) {
                    lanScanner.scan(port, token) { remaining ->
                        _scanState.value = ScanUiState.Scanning(remaining)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LanScanResult.NotFound
            }
            when (result) {
                is LanScanResult.Found -> {
                    // 直接落到表单上，用户当场就能点「测试连接」
                    setHost(result.host)
                    setPort(result.port.toString())
                    _scanState.value = ScanUiState.Found(result.host, result.port)
                }
                LanScanResult.NotFound -> _scanState.value = ScanUiState.NotFound
                LanScanResult.NoLocalNetwork -> _scanState.value = ScanUiState.NoLocalNetwork
            }
        }
    }

    /** 清除扫描结果提示（用户再次编辑地址时调用）。 */
    fun clearScanResult() {
        if (_scanState.value is ScanUiState.Found) _scanState.value = ScanUiState.Idle
    }

    /**
     * 修改设备 ID。
     *
     * 改成其他设备的值后，本机就与该设备共用同一份灵记：把 lastRev 归零触发一次全量拉取，
     * 补齐「对方已有、本机还没拉到」的历史数据；水位线保持不动，避免全量重推。
     *
     * @return 是否保存成功（格式非法或与当前值相同都返回 false）。
     */
    fun setDeviceId(value: String): Boolean {
        val normalized = DeviceIdFormat.normalize(value)
        if (!DeviceIdFormat.isValid(normalized)) return false
        if (normalized == _config.value.deviceId) return false
        configStore.deviceId = normalized
        _config.value = _config.value.copy(deviceId = normalized)
        configStore.lastRev = 0
        if (_config.value.anyEnabled) syncManager.requestSync()
        return true
    }

    /** 恢复本机派生的 ID（「重新生成」用）。 */
    fun regenerateDeviceId(): String {
        val regenerated = configStore.regenerateDeviceId()
        _config.value = _config.value.copy(deviceId = regenerated)
        configStore.lastRev = 0
        if (_config.value.anyEnabled) syncManager.requestSync()
        return regenerated
    }

    fun testConnection() {
        if (_testing.value) return
        val current = _config.value
        _testing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val port = current.port.toIntOrNull() ?: 0
                require(current.host.isNotBlank() && port in 1..65535) { "配置不完整" }
                syncApi.ping("http://${current.host.trim()}:$port", current.token.trim())
                true to ""
            } catch (e: Exception) {
                false to (e.message ?: e.javaClass.simpleName)
            }
            withContext(Dispatchers.Main) {
                _testing.value = false
                _testResult.value = result
            }
        }
    }

    private fun persist() {
        val c = _config.value
        configStore.saveConfig(
            syncNotes = c.syncNotes,
            syncModels = c.syncModels,
            syncTokens = c.syncTokens,
            host = c.host,
            port = c.port.toIntOrNull() ?: SyncConfigStore.DEFAULT_PORT,
            token = c.token
        )
    }
}

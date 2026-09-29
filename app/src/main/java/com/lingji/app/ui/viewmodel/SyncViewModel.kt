package com.lingji.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingji.app.data.remote.sync.SyncApi
import com.lingji.app.data.sync.SyncConfigStore
import com.lingji.app.data.sync.SyncManager
import com.lingji.app.data.sync.SyncUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 云同步设置表单的 UI 状态（端口以字符串编辑）。 */
data class SyncConfigUiState(
    val enabled: Boolean = false,
    val host: String = "",
    val port: String = SyncConfigStore.DEFAULT_PORT.toString(),
    val token: String = ""
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val configStore: SyncConfigStore,
    private val syncManager: SyncManager,
    private val syncApi: SyncApi
) : ViewModel() {

    private val _config = MutableStateFlow(
        SyncConfigUiState(
            enabled = configStore.enabled,
            host = configStore.host,
            port = configStore.port.toString(),
            token = configStore.token
        )
    )
    val config: StateFlow<SyncConfigUiState> = _config.asStateFlow()

    val status: StateFlow<SyncUiStatus> = syncManager.status

    /** 测试结果：first=是否成功，second=描述信息；null 表示未测试。 */
    private val _testResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testResult: StateFlow<Pair<Boolean, String>?> = _testResult.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(enabled = enabled)
        persist()
        if (enabled) syncManager.requestSync()
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
            enabled = c.enabled,
            host = c.host,
            port = c.port.toIntOrNull() ?: SyncConfigStore.DEFAULT_PORT,
            token = c.token
        )
    }
}

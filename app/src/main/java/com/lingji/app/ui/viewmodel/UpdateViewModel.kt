package com.lingji.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingji.app.data.remote.UpdateChecker
import com.lingji.app.data.remote.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow(-1) // -1 = not downloading, 0-100 = progress
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    /** 检查更新的一次性结果消息（已是最新 / 失败 / 等）。UI 消费后调用 [clearCheckMessage]。 */
    private val _checkMessage = MutableStateFlow<CheckMessage?>(null)
    val checkMessage: StateFlow<CheckMessage?> = _checkMessage.asStateFlow()

    fun checkForUpdate() {
        if (_isChecking.value) return
        viewModelScope.launch {
            _isChecking.value = true
            try {
                val info = updateChecker.checkForUpdate()
                when {
                    info == null -> _checkMessage.value = CheckMessage.Failed
                    info.hasUpdate -> _updateInfo.value = info
                    else -> _checkMessage.value = CheckMessage.AlreadyLatest
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun clearCheckMessage() {
        _checkMessage.value = null
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun downloadAndInstallApk() {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            _downloadProgress.value = 0
            val apkFile = updateChecker.downloadApk(info.downloadUrl) { progress ->
                _downloadProgress.value = progress
            }
            if (apkFile != null && apkFile.exists()) {
                installApk(apkFile)
            }
            _downloadProgress.value = -1
        }
    }

    private fun installApk(file: java.io.File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: open via generic intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    android.net.Uri.fromFile(file),
                    "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

enum class CheckMessage { AlreadyLatest, Failed }

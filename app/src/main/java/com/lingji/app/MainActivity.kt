package com.lingji.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.lingji.app.data.share.SharedContentReader
import com.lingji.app.data.share.SharedPayload
import com.lingji.app.ui.components.UpdateDialog
import com.lingji.app.ui.navigation.LingjiNavigation
import com.lingji.app.ui.theme.LingjiTheme
import com.lingji.app.ui.components.ObserveImeHeight
import com.lingji.app.ui.viewmodel.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sharedContentReader: SharedContentReader

    // 信息岛：系统分享——暂存待处理的分享内容，交给 Compose 层消费后清空
    private val sharedPayload = MutableStateFlow<SharedPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 配置变更重建时 savedInstanceState 非空，原分享已消费过，避免重复触发
        if (savedInstanceState == null) handleShareIntent(intent)
        // 信息岛：底部导航栏使用完全透明样式，让手势小白条悬浮在页面内容之上。
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            LingjiTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ObserveImeHeight()
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    val updateInfo by updateViewModel.updateInfo.collectAsState()
                    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
                    val updateSource by updateViewModel.updateSource.collectAsState()
                    val pendingShare by sharedPayload.collectAsState()

                    // Check for update on first composition
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        updateViewModel.checkForUpdate()
                    }

                    LingjiNavigation(
                        updateViewModel = updateViewModel,
                        sharedPayload = pendingShare,
                        onSharedPayloadConsumed = { sharedPayload.value = null }
                    )

                    // Show update dialog if update available
                    updateInfo?.let { info ->
                        UpdateDialog(
                            updateInfo = info,
                            downloadProgress = downloadProgress,
                            updateSource = updateSource,
                            onUpdate = { updateViewModel.downloadAndInstallApk() },
                            onDismiss = { updateViewModel.dismissUpdate() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /** 信息岛：系统分享——把 ACTION_SEND / ACTION_SEND_MULTIPLE 的分享内容交给读取器解析。 */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return
        // URI 读取权限只随本次 Intent 授予，拿到后立刻读取
        lifecycleScope.launch {
            val payload = sharedContentReader.read(intent)
            if (payload != null) sharedPayload.value = payload
        }
    }
}

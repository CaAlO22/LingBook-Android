package com.lingji.app

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
import com.lingji.app.ui.components.UpdateDialog
import com.lingji.app.ui.navigation.LingjiNavigation
import com.lingji.app.ui.theme.LingjiTheme
import com.lingji.app.ui.viewmodel.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    val updateInfo by updateViewModel.updateInfo.collectAsState()
                    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
                    val updateSource by updateViewModel.updateSource.collectAsState()

                    // Check for update on first composition
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        updateViewModel.checkForUpdate()
                    }

                    LingjiNavigation(updateViewModel = updateViewModel)

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
}

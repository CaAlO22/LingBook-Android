package com.lingji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            LingjiTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    val updateInfo by updateViewModel.updateInfo.collectAsState()
                    val downloadProgress by updateViewModel.downloadProgress.collectAsState()

                    // Check for update on first composition
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        updateViewModel.checkForUpdate()
                    }

                    LingjiNavigation()

                    // Show update dialog if update available
                    updateInfo?.let { info ->
                        UpdateDialog(
                            updateInfo = info,
                            downloadProgress = downloadProgress,
                            onUpdate = { updateViewModel.downloadAndInstallApk() },
                            onDismiss = { updateViewModel.dismissUpdate() }
                        )
                    }
                }
            }
        }
    }
}

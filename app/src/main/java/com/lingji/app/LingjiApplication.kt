package com.lingji.app

import android.app.Application
import com.lingji.app.data.sync.SyncConfigStore
import com.lingji.app.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LingjiApplication : Application() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var syncConfigStore: SyncConfigStore

    override fun onCreate() {
        super.onCreate()
        // 云同步：设备 ID 为空时在启动阶段生成并落盘，供同步与设置页使用
        syncConfigStore.ensureDeviceId()
        // 云同步：启动时同步一次并开启前台周期同步；未启用时自动空转
        syncManager.start()
    }
}

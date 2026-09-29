package com.lingji.app.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 云同步配置快照。 */
data class SyncConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val token: String
) {
    val baseUrl: String get() = "http://$host:$port"
    val isValid: Boolean get() = host.isNotBlank() && port in 1..65535
}

/**
 * 云同步配置与游标存储（SharedPreferences）。
 * 注意：同步配置本身不参与云同步；API Key 等敏感设置也不会被同步。
 */
@Singleton
class SyncConfigStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var host: String
        get() = prefs.getString(KEY_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    /** 本设备唯一标识，首次访问时生成并持久化。 */
    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    /** 服务端同步游标：已确认同步到的 rev。 */
    var lastRev: Long
        get() = prefs.getLong(KEY_LAST_REV, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REV, value).apply()

    /** 本地推送水位线：updatedAt/deletedAt 大于该值的记录需要推送。 */
    var watermark: Long
        get() = prefs.getLong(KEY_WATERMARK, 0L)
        set(value) = prefs.edit().putLong(KEY_WATERMARK, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    /** 最近一次同步结果描述（用于设置页展示）。 */
    var lastSyncMessage: String
        get() = prefs.getString(KEY_LAST_SYNC_MSG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_MSG, value).apply()

    fun loadConfig(): SyncConfig = SyncConfig(
        enabled = enabled,
        host = host,
        port = port,
        token = token
    )

    fun saveConfig(enabled: Boolean, host: String, port: Int, token: String) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port)
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_REV = "last_rev"
        private const val KEY_WATERMARK = "watermark"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_MSG = "last_sync_msg"
        const val DEFAULT_PORT = 8765
    }
}

package com.lingji.app.data.sync

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 云同步配置快照：笔记 / 模型配置 / 访问令牌三类各自独立开关。 */
data class SyncConfig(
    val syncNotes: Boolean,
    val syncModels: Boolean,
    val syncTokens: Boolean,
    val host: String,
    val port: Int,
    val token: String
) {
    val baseUrl: String get() = "http://$host:$port"
    val isValid: Boolean get() = host.isNotBlank() && port in 1..65535

    /** 任一类别开启即视为启用同步。 */
    val enabled: Boolean get() = syncNotes || syncModels || syncTokens
}

/**
 * 云同步配置与游标存储（SharedPreferences）。
 *
 * 三类内容的水位线各自独立：关闭某一类时，它既不推送也不应用远端变更，
 * 且自己的水位线不推进，重新开启后仍能补上关闭期间的本地改动；
 * 重新开启时 SyncViewModel 会把 lastRev 归零，触发一次全量拉取，
 * 以补齐其他设备已有的数据。
 *
 * 更新源（GitHub / Gitee）访问令牌由用户在设置页主动填写，
 * 勾选「云同步访问令牌」后随同步下发。
 */
@Singleton
class SyncConfigStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 旧版总开关，只用于升级时给三个分类开关提供默认值。 */
    private val legacyEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED_LEGACY, false)

    var syncNotes: Boolean
        get() = prefs.getBoolean(KEY_SYNC_NOTES, legacyEnabled)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_NOTES, value).apply()

    var syncModels: Boolean
        get() = prefs.getBoolean(KEY_SYNC_MODELS, legacyEnabled)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_MODELS, value).apply()

    var syncTokens: Boolean
        get() = prefs.getBoolean(KEY_SYNC_TOKENS, legacyEnabled)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_TOKENS, value).apply()

    var host: String
        get() = prefs.getString(KEY_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    /**
     * 本机唯一标识（云同步据此区分设备）。
     *
     * 首次访问（含 App 启动时的 [ensureDeviceId]）若发现为空，会按设备标识派生一个并落盘；
     * 用户可以在设置页改成其他设备的值，让多台设备共用同一份灵记数据。
     */
    var deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)?.trim().orEmpty()
            if (existing.isNotEmpty()) return existing
            return persistDerivedDeviceId()
        }
        set(value) {
            val normalized = DeviceIdFormat.normalize(value)
            if (!DeviceIdFormat.isValid(normalized)) return
            prefs.edit()
                .putString(KEY_DEVICE_ID, normalized)
                // 标记为「用户已确认过的值」，避免下次启动被升级逻辑覆盖
                .putBoolean(KEY_DEVICE_ID_UPGRADED, true)
                .apply()
        }

    /**
     * 启动时调用：确保设备 ID 已存在并持久化。
     *
     * 旧版本存的是随机 UUID（不以 [DeviceIdFormat.PREFIX] 开头），升级后首次启动会
     * 按设备标识重新派生一次，让 ID 与设备绑定；此后无论用户改成什么值都不再覆盖。
     */
    fun ensureDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)?.trim().orEmpty()
        if (prefs.getBoolean(KEY_DEVICE_ID_UPGRADED, false) && existing.isNotEmpty()) return existing
        return persistDerivedDeviceId()
    }

    /** 丢弃当前 ID，按本机标识重新派生一个（设置页「重新生成」用）。 */
    fun regenerateDeviceId(): String = persistDerivedDeviceId()

    private fun persistDerivedDeviceId(): String {
        val created = DeviceIdFormat.derive(deviceSeed())
        prefs.edit()
            .putString(KEY_DEVICE_ID, created)
            .putBoolean(KEY_DEVICE_ID_UPGRADED, true)
            .apply()
        return created
    }

    /**
     * 派生种子：优先使用系统分配的 ANDROID_ID（同一设备重装应用后不变），
     * 取不到时退化为随机值 —— 此时 ID 仍唯一，只是不再与设备绑定。
     * 拼接包名，避免同一台设备上不同应用派生出相同 ID。
     */
    private fun deviceSeed(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        val raw = androidId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return "$raw|${context.packageName}"
    }

    /** 服务端同步游标：已确认同步到的 rev。 */
    var lastRev: Long
        get() = prefs.getLong(KEY_LAST_REV, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REV, value).apply()

    /** 旧版全局水位线，只用于升级时给三个分类水位提供默认值。 */
    private val legacyWatermark: Long
        get() = prefs.getLong(KEY_WATERMARK_LEGACY, 0L)

    /** 笔记类（笔记 / 碎片 / 页面 / 会话 / 摘要 / 待办）的推送水位线。 */
    var watermarkNotes: Long
        get() = prefs.getLong(KEY_WATERMARK_NOTES, legacyWatermark)
        set(value) = prefs.edit().putLong(KEY_WATERMARK_NOTES, value).apply()

    /** 模型配置的推送水位线。 */
    var watermarkModels: Long
        get() = prefs.getLong(KEY_WATERMARK_MODELS, legacyWatermark)
        set(value) = prefs.edit().putLong(KEY_WATERMARK_MODELS, value).apply()

    /** 更新源令牌的推送水位线。 */
    var watermarkTokens: Long
        get() = prefs.getLong(KEY_WATERMARK_TOKENS, legacyWatermark)
        set(value) = prefs.edit().putLong(KEY_WATERMARK_TOKENS, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    /** 最近一次同步结果描述（用于设置页展示）。 */
    var lastSyncMessage: String
        get() = prefs.getString(KEY_LAST_SYNC_MSG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_MSG, value).apply()

    fun loadConfig(): SyncConfig = SyncConfig(
        syncNotes = syncNotes,
        syncModels = syncModels,
        syncTokens = syncTokens,
        host = host,
        port = port,
        token = token
    )

    fun saveConfig(
        syncNotes: Boolean,
        syncModels: Boolean,
        syncTokens: Boolean,
        host: String,
        port: Int,
        token: String
    ) {
        prefs.edit()
            .putBoolean(KEY_SYNC_NOTES, syncNotes)
            .putBoolean(KEY_SYNC_MODELS, syncModels)
            .putBoolean(KEY_SYNC_TOKENS, syncTokens)
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port)
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_ENABLED_LEGACY = "enabled"

        private const val KEY_SYNC_NOTES = "sync_notes"
        private const val KEY_SYNC_MODELS = "sync_models"
        private const val KEY_SYNC_TOKENS = "sync_tokens"

        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_ID_UPGRADED = "device_id_upgraded"
        private const val KEY_LAST_REV = "last_rev"

        private const val KEY_WATERMARK_LEGACY = "watermark"
        private const val KEY_WATERMARK_NOTES = "watermark_notes"
        private const val KEY_WATERMARK_MODELS = "watermark_models"
        private const val KEY_WATERMARK_TOKENS = "watermark_tokens"

        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_MSG = "last_sync_msg"
        const val DEFAULT_PORT = 8765
    }
}

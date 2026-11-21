package com.lingji.app.data.sync

import android.util.Log
import com.google.gson.Gson
import com.lingji.app.data.remote.sync.ModelCatalogResponse
import com.lingji.app.data.remote.sync.SyncApi
import com.lingji.app.data.remote.sync.SyncApiException
import com.lingji.app.domain.provider.RemoteModelCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** 一次目录刷新的结果。 */
sealed interface CatalogRefreshResult {
    data class Updated(val version: Int, val models: Int) : CatalogRefreshResult

    /** 服务端版本与本机一致，没有重发目录。 */
    data object Unchanged : CatalogRefreshResult

    /** 没配服务器地址 / 没开云同步，本轮不请求。 */
    data object NotConfigured : CatalogRefreshResult

    data class Failed(val message: String) : CatalogRefreshResult
}

/** 设置页展示的模型目录状态。 */
data class ModelCatalogStatus(
    /** 0 表示还没拿到过云端目录（此时用的是随包内置列表）。 */
    val version: Int = 0,
    /** 服务端最后一次修改目录的时间。 */
    val updatedAt: Long = 0,
    /** 本机最后一次成功刷新的时间。 */
    val fetchedAt: Long = 0,
    val refreshing: Boolean = false,
    val message: String = ""
)

/**
 * 模型目录的抓取 / 缓存 / 应用。
 *
 * - 启动时先读 SharedPreferences 里的缓存，保证离线也有最新列表（[loadCached]）；
 * - 云同步每轮顺带刷新一次，版本没变时服务端只回一个 `unchanged`；
 * - 设置页可手动强制刷新（传 `force = true`，不带版本号，强制全量）。
 *
 * 拉到目录后写进 [RemoteModelCatalog]，[com.lingji.app.domain.provider.ProviderRegistry]
 * 会自动按它叠加，设置页的模型下拉与请求链路的视觉能力判断看到的是同一份数据。
 */
@Singleton
class ModelCatalogRepository @Inject constructor(
    private val configStore: SyncConfigStore,
    private val api: SyncApi
) {
    private val gson = Gson()
    private val mutex = Mutex()

    private val _status = MutableStateFlow(
        ModelCatalogStatus(
            version = configStore.modelCatalogVersion,
            updatedAt = configStore.modelCatalogUpdatedAt,
            fetchedAt = configStore.modelCatalogFetchedAt
        )
    )
    val status: StateFlow<ModelCatalogStatus> = _status.asStateFlow()

    /** 从本地缓存恢复目录；App 启动时调用一次。 */
    fun loadCached() {
        val json = configStore.modelCatalogJson
        if (json.isBlank()) return
        runCatching {
            val response = gson.fromJson(json, ModelCatalogResponse::class.java)
            val models = ModelCatalogParser.parse(response)
            if (models.isNotEmpty()) {
                RemoteModelCatalog.apply(
                    models = models,
                    version = configStore.modelCatalogVersion,
                    updatedAt = configStore.modelCatalogUpdatedAt
                )
            }
        }.onFailure {
            Log.w(TAG, "cached model catalog invalid: ${it.message}")
        }
    }

    /**
     * 拉取目录并应用。网络异常一律吞掉（同步本身是静默降级），只把结果反映到 [status]。
     */
    suspend fun refresh(force: Boolean = false): CatalogRefreshResult = mutex.withLock {
        val config = configStore.loadConfig()
        if (!config.isValid) return@withLock CatalogRefreshResult.NotConfigured

        // 只有手动刷新才点亮转圈：后台每轮顺带刷新时不要把设置页的按钮一直切来切去
        if (force) _status.update { it.copy(refreshing = true, message = "") }
        try {
            // 手动刷新时不带版本号：即使服务端版本号没变，也把整份目录重新拉一遍。
            // 换过服务器（baseUrl 变了）同理：新服务器的版本号与旧缓存没有可比性。
            val sameServer = configStore.modelCatalogServer == config.baseUrl
            val knownVersion =
                if (force || !sameServer) null else configStore.modelCatalogVersion.takeIf { it > 0 }
            val response = api.fetchModelCatalog(config.baseUrl, config.token, knownVersion)
            val now = System.currentTimeMillis()

            if (response.unchanged == true) {
                configStore.modelCatalogFetchedAt = now
                configStore.modelCatalogServer = config.baseUrl
                _status.update { it.copy(refreshing = false, fetchedAt = now, message = "已是最新") }
                return@withLock CatalogRefreshResult.Unchanged
            }

            val version = response.catalogVersion ?: 0
            val models = ModelCatalogParser.parse(response)
            if (version <= 0) {
                return@withLock fail("服务器未返回目录版本")
            }
            if (models.isEmpty()) {
                return@withLock fail("服务器目录为空")
            }

            RemoteModelCatalog.apply(models, version, response.updatedAt ?: 0L)
            configStore.modelCatalogJson = gson.toJson(response)
            configStore.modelCatalogVersion = version
            configStore.modelCatalogUpdatedAt = response.updatedAt ?: 0L
            configStore.modelCatalogFetchedAt = now
            configStore.modelCatalogServer = config.baseUrl
            _status.value = ModelCatalogStatus(
                version = version,
                updatedAt = response.updatedAt ?: 0L,
                fetchedAt = now,
                refreshing = false,
                message = "已更新"
            )
            CatalogRefreshResult.Updated(version, models.values.sumOf { it.size })
        } catch (e: SyncApiException) {
            // 旧服务器还没有 /api/model_catalog：不是故障，继续用内置列表即可，不必标红
            if (e.statusCode == 404 || e.statusCode == 405) {
                _status.update {
                    it.copy(refreshing = false, message = "服务器未提供模型目录（旧版本），继续使用内置列表")
                }
                return@withLock CatalogRefreshResult.Failed("服务器未提供模型目录")
            }
            Log.w(TAG, "refresh model catalog failed: ${e.message}")
            fail(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            Log.w(TAG, "refresh model catalog failed: ${e.message}")
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(message: String): CatalogRefreshResult {
        _status.update { it.copy(refreshing = false, message = "刷新失败：$message") }
        return CatalogRefreshResult.Failed(message)
    }

    private companion object {
        const val TAG = "ModelCatalog"
    }
}

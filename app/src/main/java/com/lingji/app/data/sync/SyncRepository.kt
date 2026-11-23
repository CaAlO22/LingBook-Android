package com.lingji.app.data.sync

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.dao.HomeChatDao
import com.lingji.app.data.db.dao.ModelConfigDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.SettingsDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.dao.SyncTombstoneDao
import com.lingji.app.data.db.dao.TodoItemDao
import com.lingji.app.data.db.entities.FolderEntity
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import com.lingji.app.data.db.entities.ModelConfigEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.SubjectEntity
import com.lingji.app.data.db.entities.SubjectSummaryEntity
import com.lingji.app.data.db.entities.TodoItemEntity
import com.lingji.app.data.remote.UpdateChecker
import com.lingji.app.data.remote.sync.SyncApi
import com.lingji.app.data.remote.sync.SyncApiException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** 一轮同步的结果。 */
sealed interface SyncResult {
    /** 未启用或配置不完整，本轮未执行。 */
    data object Disabled : SyncResult

    data class Success(
        val pushed: Int,
        val pulled: Int,
        val deleted: Int,
        /** 服务端协议版本（正常同步时必然 >= 本端版本）。 */
        val serverSchemaVersion: Int = SyncProtocol.SCHEMA_VERSION
    ) : SyncResult

    /**
     * 服务端协议版本低于本端，本轮**未推送任何数据**。
     *
     * 旧服务器不认识新增的同步表 / 字段，继续同步会把新数据静默丢在服务端，
     * 所以宁可停下并提示升级服务器。
     */
    data class ServerOutdated(
        val serverVersion: Int,
        val clientVersion: Int = SyncProtocol.SCHEMA_VERSION
    ) : SyncResult

    data class Error(val message: String) : SyncResult
}

/**
 * 云同步核心：收集本地变更 → 推送 → 拉取远端变更 → 应用。
 *
 * 协议与合并规则见 server/lingji_sync_server.py 头部注释。
 * 可靠性约定：
 *  - 仅在服务器返回成功后推进 watermark / lastRev，失败则下轮全量重发，不丢数据；
 *  - watermark 取「本轮推送 + 应用」的最大时间戳，避免把刚拉取的远端数据再次推回造成回声；
 *  - 任何单条记录应用失败仅跳过该条，不影响整轮结果。
 */
@Singleton
class SyncRepository @Inject constructor(
    private val configStore: SyncConfigStore,
    private val api: SyncApi,
    private val subjectDao: SubjectDao,
    private val fragmentDao: FragmentDao,
    private val pageDao: NotebookPageDao,
    private val folderDao: FolderDao,
    private val homeChatDao: HomeChatDao,
    private val summaryDao: SubjectSummaryDao,
    private val tombstoneDao: SyncTombstoneDao,
    private val todoItemDao: TodoItemDao,
    private val modelConfigDao: ModelConfigDao,
    private val settingsDao: SettingsDao,
    private val updateChecker: UpdateChecker,
    private val reminderScheduler: com.lingji.app.data.reminder.TodoReminderScheduler
) {
    private val gson = Gson()
    private val syncMutex = Mutex()

    suspend fun syncNow(): SyncResult = syncMutex.withLock { syncLocked() }

    // 信息岛：云同步「覆盖云端数据」——清空云端启用表后全量回推本机数据
    /**
     * 用本机数据整体覆盖云端。
     *
     * 先清空云端启用的同步表（保留模型目录等非同步数据），再把本机数据全量重推；
     * 水位线归零保证「本机有的全部上传」，云端独有的记录随之消失。
     * 需要服务端支持 `/api/sync/reset`，旧服务器会回 404，此时提示升级服务端。
     */
    suspend fun overwriteCloud(): SyncResult = syncMutex.withLock {
        val config = configStore.loadConfig()
        if (!config.enabled || !config.isValid) return@withLock SyncResult.Disabled

        try {
            val serverInfo = api.serverInfo(config.baseUrl, config.token)
            if (!SyncProtocol.isServerCompatible(serverInfo.schemaVersion)) {
                return@withLock SyncResult.ServerOutdated(serverInfo.schemaVersion)
            }
            val resetRev = api.resetCloud(
                baseUrl = config.baseUrl,
                token = config.token,
                deviceId = configStore.deviceId,
                tables = enabledCloudTables(config)
            )
            // 云端已清空：把水位线压到 -1，下一轮会把所有本机记录（含 updatedAt=0 的旧数据）重新推上去
            if (config.syncNotes) configStore.watermarkNotes = -1
            if (config.syncModels) configStore.watermarkModels = -1
            if (config.syncTokens) configStore.watermarkTokens = -1
            configStore.lastRev = resetRev
            syncLocked()
        } catch (e: SyncApiException) {
            when (e.statusCode) {
                409 -> SyncResult.ServerOutdated(
                    e.serverSchemaVersion ?: SyncProtocol.LEGACY_SCHEMA_VERSION
                )
                404 -> SyncResult.Error("服务器版本过旧，不支持覆盖云端数据，请更新服务端程序")
                else -> SyncResult.Error(e.message ?: e.javaClass.simpleName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "overwrite cloud failed: ${e.message}")
            SyncResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    // 信息岛：云同步「从云端恢复」——先拉快照，再按「覆盖本地 / 合并本地」落地
    /**
     * 从云端恢复数据。
     *
     * @param mergeLocal true = 合并本地：保留本机数据，仅用云端较新的记录覆盖；
     *   false = 覆盖本地：先清空本机启用的同步数据，再整体应用云端快照。
     *
     * 两者都先完整拉取快照再动本地，网络失败时本机数据保持原样。
     */
    suspend fun restoreFromCloud(mergeLocal: Boolean): SyncResult = syncMutex.withLock {
        val config = configStore.loadConfig()
        if (!config.enabled || !config.isValid) return@withLock SyncResult.Disabled

        try {
            val serverInfo = api.serverInfo(config.baseUrl, config.token)
            if (!SyncProtocol.isServerCompatible(serverInfo.schemaVersion)) {
                return@withLock SyncResult.ServerOutdated(serverInfo.schemaVersion)
            }

            // ── 1. 先拉全量快照（lastRev=0 即云端全部数据），此阶段不动本地 ──
            val snapshot = api.sync(config.baseUrl, config.token, buildPullPayload())
            val changes = snapshot.getAsJsonObject("changes") ?: JsonObject()
            val snapshotTombstones = snapshot.getAsJsonArray("tombstones") ?: JsonArray()
            val snapshotRev = snapshot.get("rev")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L

            // ── 2. 覆盖模式：清空本机对应类别（含待推送墓碑），云端快照成为唯一真相 ──
            if (!mergeLocal) wipeLocalData(config, changes)

            // ── 3. 应用快照：合并模式只让「更新」的远端记录覆盖本地 ──
            var pulled = 0
            var deleted = 0
            if (config.syncNotes) {
                pulled += applyFolders(changes, onlyIfNewer = mergeLocal)
                pulled += applySubjects(changes, onlyIfNewer = mergeLocal)
                pulled += applyConversations(changes, onlyIfNewer = mergeLocal)
                pulled += applyMessages(changes, onlyIfNewer = mergeLocal)
                pulled += applyFragments(changes, onlyIfNewer = mergeLocal)
                pulled += applyPages(changes, onlyIfNewer = mergeLocal)
                pulled += applySummaries(changes, onlyIfNewer = mergeLocal)
                pulled += applyTodoItems(changes, onlyIfNewer = mergeLocal)
            }
            if (config.syncModels) {
                pulled += applyModelConfigs(changes, onlyIfNewer = mergeLocal)
            }
            if (config.syncTokens) {
                pulled += applyAppSettings(changes, force = !mergeLocal)
            }

            for (element in snapshotTombstones) {
                val ts = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val tbl = ts.get("tbl")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                val id = ts.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                val deletedAt = ts.get("deletedAt")?.takeIf { it.isJsonPrimitive }?.asLong ?: continue
                val categoryOn = if (tbl == TBL_MODEL_CONFIGS) config.syncModels else config.syncNotes
                if (!categoryOn) continue
                if (applyTombstone(tbl, id, deletedAt, onlyIfNewer = mergeLocal)) deleted++
            }

            // ── 4. 合并模式：本机独有的、或比云端更新的记录补推一次，避免因水位线推进而漏传 ──
            var pushed = 0
            var finalRev = snapshotRev
            val localRows = if (mergeLocal) collectLocalRows(config) else emptyList()
            val pendingTombstones = if (mergeLocal) buildLocalTombstones(config) else JsonArray()
            if (mergeLocal) {
                val pending = buildPendingChanges(changes, localRows)
                if (pending.size() > 0 || pendingTombstones.size() > 0) {
                    val pushedResponse = api.sync(
                        config.baseUrl,
                        config.token,
                        buildPushPayload(snapshotRev, pending, pendingTombstones)
                    )
                    // 服务端串行处理，这一轮通常没有其他设备的新数据；有则按较新者合并
                    val pushedChanges = pushedResponse.getAsJsonObject("changes") ?: JsonObject()
                    if (config.syncNotes) {
                        pulled += applyFolders(pushedChanges, onlyIfNewer = true)
                        pulled += applySubjects(pushedChanges, onlyIfNewer = true)
                        pulled += applyConversations(pushedChanges, onlyIfNewer = true)
                        pulled += applyMessages(pushedChanges, onlyIfNewer = true)
                        pulled += applyFragments(pushedChanges, onlyIfNewer = true)
                        pulled += applyPages(pushedChanges, onlyIfNewer = true)
                        pulled += applySummaries(pushedChanges, onlyIfNewer = true)
                        pulled += applyTodoItems(pushedChanges, onlyIfNewer = true)
                    }
                    if (config.syncModels) {
                        pulled += applyModelConfigs(pushedChanges, onlyIfNewer = true)
                    }
                    finalRev = pushedResponse.get("rev")?.takeIf { it.isJsonPrimitive }?.asLong
                        ?: snapshotRev
                }
                pendingTombstones.forEach { pushed++ }
                pushed += pending.entrySet().sumOf { it.value.asJsonArray.size() }
            }

            // ── 5. 游标与水位线：恢复到什么程度就记到什么程度，避免下一轮回声重推 ──
            configStore.lastRev = finalRev
            if (config.syncNotes) {
                configStore.watermarkNotes = maxOf(
                    configStore.watermarkNotes,
                    snapshotMaxTimestamp(changes, snapshotTombstones, NOTES_TABLES),
                    localRows.filter { it.tbl in NOTES_TABLES }.maxOfOrNull { it.updatedAt } ?: 0L,
                    pendingTombstones.maxTimestamp(NOTES_TABLES)
                )
            }
            if (config.syncModels) {
                configStore.watermarkModels = maxOf(
                    configStore.watermarkModels,
                    snapshotMaxTimestamp(changes, snapshotTombstones, MODEL_TABLES),
                    localRows.filter { it.tbl == TBL_MODEL_CONFIGS }.maxOfOrNull { it.updatedAt } ?: 0L,
                    pendingTombstones.maxTimestamp(MODEL_TABLES)
                )
            }
            if (config.syncTokens) {
                configStore.watermarkTokens = maxOf(
                    configStore.watermarkTokens,
                    snapshotMaxTimestamp(changes, snapshotTombstones, TOKEN_TABLES)
                )
            }
            configStore.lastSyncAt = System.currentTimeMillis()
            if (config.syncNotes) reminderScheduler.refreshAll()

            SyncResult.Success(
                pushed = pushed,
                pulled = pulled,
                deleted = deleted,
                serverSchemaVersion = serverInfo.schemaVersion
            )
        } catch (e: SyncApiException) {
            if (e.statusCode == 409) {
                return@withLock SyncResult.ServerOutdated(
                    e.serverSchemaVersion ?: SyncProtocol.LEGACY_SCHEMA_VERSION
                )
            }
            Log.w(TAG, "restore from cloud failed: ${e.message}")
            SyncResult.Error(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            Log.w(TAG, "restore from cloud failed: ${e.message}")
            SyncResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun syncLocked(): SyncResult {
        val config = configStore.loadConfig()
        if (!config.enabled || !config.isValid) return SyncResult.Disabled

        try {
            // ── 0. 协议版本前置检查（主闸门）──
            // 服务端版本低于本端时直接停下：旧服务器的同步表白名单不认识本版新增的表 / 字段，
            // 推上去会被静默丢弃（同步显示成功，换设备却没有数据）。
            val serverInfo = api.serverInfo(config.baseUrl, config.token)
            if (!SyncProtocol.isServerCompatible(serverInfo.schemaVersion)) {
                Log.w(
                    TAG,
                    "sync refused: server schema v${serverInfo.schemaVersion} < client v${SyncProtocol.SCHEMA_VERSION}"
                )
                return SyncResult.ServerOutdated(serverInfo.schemaVersion)
            }

            val wmNotes = configStore.watermarkNotes
            val wmModels = configStore.watermarkModels
            val wmTokens = configStore.watermarkTokens
            val notesOn = config.syncNotes

            // ── 1. 收集本地变更（按开关过滤，各类用自己的水位线）──
            val subjects = if (notesOn) subjectDao.getUpdatedSince(wmNotes) else emptyList()
            val fragments = if (notesOn) fragmentDao.getUpdatedSince(wmNotes) else emptyList()
            val pages = if (notesOn) pageDao.getUpdatedSince(wmNotes) else emptyList()
            val folders = if (notesOn) folderDao.getUpdatedSince(wmNotes) else emptyList()
            val conversations =
                if (notesOn) homeChatDao.getConversationsUpdatedSince(wmNotes) else emptyList()
            val messages =
                if (notesOn) homeChatDao.getMessagesUpdatedSince(wmNotes) else emptyList()
            val summaries = if (notesOn) summaryDao.getUpdatedSince(wmNotes) else emptyList()
            val todoItems = if (notesOn) todoItemDao.getUpdatedSince(wmNotes) else emptyList()
            val modelConfigs =
                if (config.syncModels) modelConfigDao.getUpdatedSince(wmModels) else emptyList()

            // 墓碑按类别过滤：模型配置只随「模型配置」开关走，其余都算笔记类。
            // 取两类水位中更早的一个作为查询下界，避免刚开启的那一类漏推墓碑。
            val tombstones = tombstoneDao.getSince(minOf(wmNotes, wmModels)).filter { ts ->
                if (ts.tbl == TBL_MODEL_CONFIGS) config.syncModels else notesOn
            }
            val noteTombstones = tombstones.filter { it.tbl != TBL_MODEL_CONFIGS }
            val modelTombstones = tombstones.filter { it.tbl == TBL_MODEL_CONFIGS }

            // 更新源令牌：单行记录（id=singleton），按 updatedAt 做 LWW
            val tokens = updateChecker.getTokenSnapshot()
            val pushTokens = config.syncTokens && tokens.updatedAt > wmTokens

            val payload = JsonObject().apply {
                addProperty("deviceId", configStore.deviceId)
                // 服务端据此兜底：客户端版本高于自己时返回 409，防止「检查通过后服务器被换回旧版」
                addProperty("schemaVersion", SyncProtocol.SCHEMA_VERSION)
                addProperty("lastRev", configStore.lastRev)
                add("changes", JsonObject().apply {
                    addEntityArray(TBL_SUBJECTS, subjects)
                    addEntityArray(TBL_FRAGMENTS, fragments)
                    addEntityArray(TBL_PAGES, pages)
                    addEntityArray(TBL_FOLDERS, folders)
                    addEntityArray(TBL_CONVERSATIONS, conversations)
                    addEntityArray(TBL_MESSAGES, messages)
                    addEntityArray(TBL_SUMMARIES, summaries)
                    addEntityArray(TBL_TODOS, todoItems)
                    addEntityArray(TBL_MODEL_CONFIGS, modelConfigs)
                    if (pushTokens) {
                        add(TBL_APP_SETTINGS, JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("id", APP_SETTINGS_ID)
                                addProperty("githubToken", tokens.githubToken)
                                addProperty("giteeToken", tokens.giteeToken)
                                addProperty("updatedAt", tokens.updatedAt)
                            })
                        })
                    }
                })
                add("tombstones", JsonArray().apply {
                    tombstones.forEach { ts ->
                        add(JsonObject().apply {
                            addProperty("tbl", ts.tbl)
                            addProperty("id", ts.id)
                            addProperty("deletedAt", ts.deletedAt)
                        })
                    }
                })
            }

            // ── 2. 推送并拉取 ──
            val response = api.sync(config.baseUrl, config.token, payload)

            // ── 3. 应用远端变更（按外键安全顺序，且只处理已开启的类别）──
            var pulled = 0
            var deleted = 0
            var maxNotes = wmNotes
            var maxModels = wmModels
            var maxTokens = wmTokens

            // 本地本轮推送的记录时间戳计入各自水位线
            if (notesOn) {
                maxNotes = maxOf(
                    maxNotes,
                    maxOfEntities(
                        subjects.maxOfOrNull { it.updatedAt },
                        fragments.maxOfOrNull { it.updatedAt },
                        pages.maxOfOrNull { it.updatedAt },
                        folders.maxOfOrNull { it.updatedAt },
                        conversations.maxOfOrNull { it.updated_at },
                        messages.maxOfOrNull { it.updatedAt },
                        summaries.maxOfOrNull { it.updatedAt },
                        todoItems.maxOfOrNull { it.updatedAt },
                        noteTombstones.maxOfOrNull { it.deletedAt }
                    )
                )
            }
            if (config.syncModels) {
                maxModels = maxOf(
                    maxModels,
                    maxOfEntities(
                        modelConfigs.maxOfOrNull { it.updatedAt },
                        modelTombstones.maxOfOrNull { it.deletedAt }
                    )
                )
            }
            if (pushTokens) {
                maxTokens = maxOf(maxTokens, tokens.updatedAt)
            }

            val changes = response.getAsJsonObject("changes") ?: JsonObject()
            // 远端记录的时间戳也必须计入水位线，否则下一轮会把这些刚拉下来的数据再推回去（回声）
            if (notesOn) {
                maxNotes = maxOf(maxNotes, maxUpdatedAtFor(changes, NOTES_TABLES))
                pulled += applyFolders(changes)
                pulled += applySubjects(changes)
                pulled += applyConversations(changes)
                pulled += applyMessages(changes)
                pulled += applyFragments(changes)
                pulled += applyPages(changes)
                pulled += applySummaries(changes)
                pulled += applyTodoItems(changes)
            }
            if (config.syncModels) {
                maxModels = maxOf(maxModels, maxUpdatedAtFor(changes, MODEL_TABLES))
                pulled += applyModelConfigs(changes)
            }
            if (config.syncTokens) {
                maxTokens = maxOf(maxTokens, maxUpdatedAtFor(changes, TOKEN_TABLES))
                pulled += applyAppSettings(changes)
            }

            val remoteTombstones = response.getAsJsonArray("tombstones") ?: JsonArray()
            for (element in remoteTombstones) {
                val ts = element.asJsonObject
                val tbl = ts.get("tbl")?.asString ?: continue
                val id = ts.get("id")?.asString ?: continue
                val deletedAt = ts.get("deletedAt")?.asLong ?: continue
                val categoryOn = if (tbl == TBL_MODEL_CONFIGS) config.syncModels else notesOn
                if (!categoryOn) continue
                if (applyTombstone(tbl, id, deletedAt)) deleted++
                if (tbl == TBL_MODEL_CONFIGS) {
                    maxModels = maxOf(maxModels, deletedAt)
                } else {
                    maxNotes = maxOf(maxNotes, deletedAt)
                }
            }

            // ── 4. 推进游标（仅在整轮成功后）──
            configStore.lastRev = response.get("rev")?.asLong ?: configStore.lastRev
            configStore.watermarkNotes = maxNotes
            configStore.watermarkModels = maxModels
            configStore.watermarkTokens = maxTokens
            configStore.lastSyncAt = System.currentTimeMillis()
            // 清理 30 天前的墓碑（此时所有在线设备早已收到）
            tombstoneDao.deleteOlderThan(System.currentTimeMillis() - TOMBSTONE_RETENTION_MS)
            // 信息岛：远端可能带来新的待办提醒，同步落库后重排本机闹钟
            if (notesOn) reminderScheduler.refreshAll()

            val pushed = subjects.size + fragments.size + pages.size + folders.size +
                conversations.size + messages.size + summaries.size + todoItems.size +
                modelConfigs.size + tombstones.size + if (pushTokens) 1 else 0
            return SyncResult.Success(
                pushed = pushed,
                pulled = pulled,
                deleted = deleted,
                serverSchemaVersion = serverInfo.schemaVersion
            )
        } catch (e: SyncApiException) {
            // 服务端兜底拦截：本端版本高于服务端（理论上已被前置检查挡住，这里覆盖竞态）
            if (e.statusCode == 409) {
                val serverVersion = e.serverSchemaVersion ?: SyncProtocol.LEGACY_SCHEMA_VERSION
                Log.w(TAG, "sync refused by server: server v$serverVersion < client v${SyncProtocol.SCHEMA_VERSION}")
                return SyncResult.ServerOutdated(serverVersion)
            }
            Log.w(TAG, "sync failed: ${e.message}")
            return SyncResult.Error(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}")
            return SyncResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    // ── 远端变更应用 ──

    /** 取指定表在本轮拉取数据中的最大更新时间（会话/消息用 updated_at，其余用 updatedAt）。 */
    private fun maxUpdatedAtFor(changes: JsonObject, tables: Set<String>): Long {
        var max = 0L
        for (tbl in tables) {
            val array = changes.getAsJsonArray(tbl) ?: continue
            for (element in array) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val ts = obj.get("updatedAt")?.takeIf { it.isJsonPrimitive }?.asLong
                    ?: obj.get("updated_at")?.takeIf { it.isJsonPrimitive }?.asLong
                    ?: 0L
                if (ts > max) max = ts
            }
        }
        return max
    }

    /** 取多个时间戳中的最大值，null 视为不参与比较。 */
    private fun maxOfEntities(vararg values: Long?): Long =
        values.filterNotNull().maxOrNull() ?: 0L

    private fun entityRows(changes: JsonObject, tbl: String): List<JsonObject> =
        changes.getAsJsonArray(tbl)?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            ?: emptyList()

    private suspend fun applyFolders(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_FOLDERS)) {
            runCatching {
                val entity = gson.fromJson(json, FolderEntity::class.java)
                if (onlyIfNewer && (folderDao.getFolderById(entity.id)?.updatedAt ?: -1L) >= entity.updatedAt) {
                    return@runCatching
                }
                folderDao.insert(entity)
                count++
            }
        }
        return count
    }

    private suspend fun applySubjects(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_SUBJECTS)) {
            runCatching {
                val entity = gson.fromJson(json, SubjectEntity::class.java)
                if (onlyIfNewer && (subjectDao.getSubjectById(entity.id)?.updatedAt ?: -1L) >= entity.updatedAt) {
                    return@runCatching
                }
                subjectDao.insert(normalizeSubjectEntity(entity))
                count++
            }
        }
        return count
    }

    /**
     * 兼容旧版本客户端同步上来的实体：Gson 不执行 Kotlin 默认参数，
     * 缺失的待办字段会变成 null / 0，这里补齐默认值。
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun normalizeSubjectEntity(entity: SubjectEntity): SubjectEntity = entity.copy(
        todoRepeatType = entity.todoRepeatType ?: "ONCE",
        todoRepeatDays = entity.todoRepeatDays ?: "",
        todoIntervalDays = if (entity.todoIntervalDays >= 1) entity.todoIntervalDays else 2
    )

    private suspend fun applyConversations(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_CONVERSATIONS)) {
            runCatching {
                val entity = gson.fromJson(json, HomeConversationEntity::class.java)
                val local = homeChatDao.getConversationById(entity.id)
                if (onlyIfNewer && local != null && local.updated_at >= entity.updated_at) {
                    return@runCatching
                }
                // REPLACE 会先删行触发 CASCADE 清掉本地消息，故存在时走纯 UPDATE
                val updated = homeChatDao.updateConversationFull(
                    entity.id, entity.title, entity.created_at, entity.updated_at
                )
                if (updated == 0) homeChatDao.insertConversation(entity)
                count++
            }
        }
        return count
    }

    private suspend fun applyMessages(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_MESSAGES)) {
            runCatching {
                val entity = gson.fromJson(json, HomeMessageEntity::class.java)
                if (onlyIfNewer && (homeChatDao.getMessageById(entity.id)?.updatedAt ?: -1L) >= entity.updatedAt) {
                    return@runCatching
                }
                // 外键约束：会话不存在（如已被删除）时跳过，避免崩溃
                if (homeChatDao.getConversationById(entity.conversation_id) == null) return@runCatching
                homeChatDao.insertMessage(entity)
                count++
            }
        }
        return count
    }

    private suspend fun applyFragments(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_FRAGMENTS)) {
            runCatching {
                val entity = gson.fromJson(json, FragmentEntity::class.java)
                if (onlyIfNewer && (fragmentDao.getById(entity.id)?.updatedAt ?: -1L) >= entity.updatedAt) {
                    return@runCatching
                }
                fragmentDao.insert(entity)
                count++
            }
        }
        return count
    }

    private suspend fun applyPages(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_PAGES)) {
            runCatching {
                val entity = gson.fromJson(json, NotebookPageEntity::class.java)
                // indexedAt 是本机索引状态，不随同步覆盖
                val local = pageDao.getById(entity.id)
                if (onlyIfNewer && local != null && local.updatedAt >= entity.updatedAt) {
                    return@runCatching
                }
                pageDao.insert(if (local != null) entity.copy(indexedAt = local.indexedAt) else entity)
                count++
            }
        }
        return count
    }

    private suspend fun applySummaries(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_SUMMARIES)) {
            runCatching {
                val entity = gson.fromJson(json, SubjectSummaryEntity::class.java)
                if (onlyIfNewer && (summaryDao.getBySubjectId(entity.subjectId)?.updatedAt ?: -1L) >= entity.updatedAt) {
                    return@runCatching
                }
                summaryDao.upsert(entity)
                count++
            }
        }
        return count
    }

    private suspend fun applyTodoItems(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_TODOS)) {
            runCatching {
                val entity = gson.fromJson(json, TodoItemEntity::class.java)
                if (onlyIfNewer && (todoItemDao.getByIdOnce(entity.id)?.updatedAt ?: -1L) >= entity.updatedAt) {
                    return@runCatching
                }
                todoItemDao.insert(entity)
                count++
            }
        }
        return count
    }

    private suspend fun applyModelConfigs(changes: JsonObject, onlyIfNewer: Boolean = false): Int {
        var count = 0
        for (json in entityRows(changes, TBL_MODEL_CONFIGS)) {
            runCatching {
                val entity = gson.fromJson(json, ModelConfigEntity::class.java)
                if (onlyIfNewer && (modelConfigDao.getById(entity.id)?.updatedAt ?: -1L) >= entity.updatedAt) {
                    return@runCatching
                }
                modelConfigDao.upsert(entity)
                count++
            }
        }
        return count
    }

    /**
     * 应用远端「更新源令牌」（单行 app_settings#singleton）。
     * LWW 判定在 [UpdateChecker.applyRemoteTokens] 内完成，本机更新的修改不会被覆盖。
     */
    private fun applyAppSettings(changes: JsonObject, force: Boolean = false): Int {
        for (obj in entityRows(changes, TBL_APP_SETTINGS)) {
            val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString
            if (id != APP_SETTINGS_ID) continue
            val updatedAt = obj.get("updatedAt")?.takeIf { it.isJsonPrimitive }?.asLong
                ?: continue
            val github = obj.get("githubToken")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val gitee = obj.get("giteeToken")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (updateChecker.applyRemoteTokens(github, gitee, updatedAt, force)) return 1
        }
        return 0
    }

    private suspend fun applyTombstone(
        tbl: String,
        id: String,
        deletedAt: Long,
        onlyIfNewer: Boolean = false
    ): Boolean = runCatching {
        // 合并恢复时，本机更新过的记录不应被云端旧墓碑删掉
        if (onlyIfNewer && (localUpdatedAt(tbl, id) ?: -1L) >= deletedAt) return@runCatching false
        when (tbl) {
            TBL_SUBJECTS -> {
                // 信息岛：远端删除待办笔记时一并取消本机闹钟
                todoItemDao.getTodoItemsBySubjectOnce(id).forEach {
                    reminderScheduler.cancelItem(it.id)
                }
                reminderScheduler.cancelSubject(id)
                subjectDao.deleteById(id)
            }
            TBL_FRAGMENTS -> fragmentDao.deleteById(id)
            TBL_PAGES -> pageDao.deleteById(id)
            TBL_FOLDERS -> folderDao.deleteById(id)
            TBL_CONVERSATIONS -> homeChatDao.deleteConversation(id) // FK 级联删消息
            TBL_MESSAGES -> homeChatDao.deleteMessageById(id)
            TBL_SUMMARIES -> summaryDao.deleteBySubjectId(id)
            TBL_TODOS -> {
                todoItemDao.deleteById(id)
                reminderScheduler.cancelItem(id)
            }
            TBL_MODEL_CONFIGS -> {
                modelConfigDao.deleteById(id)
                settingsDao.clearModelReference(id)
            }
            else -> return false
        }
        true
    }.getOrDefault(false)

    private fun JsonObject.addEntityArray(name: String, entities: List<Any>) {
        if (entities.isEmpty()) return
        add(name, JsonArray().apply { entities.forEach { add(gson.toJsonTree(it)) } })
    }

    // ── 覆盖云端 / 从云端恢复的辅助逻辑 ──

    /** 记录一条可推送的本机实体，用于「合并本地」时与云端快照做逐条比较。 */
    private data class SyncRow(
        val tbl: String,
        val id: String,
        val updatedAt: Long,
        val entity: Any
    )

    /** 当前启用的类别对应的云端表；「覆盖云端」只清空这些表。 */
    private fun enabledCloudTables(config: SyncConfig): List<String> = buildList {
        if (config.syncNotes) addAll(NOTES_TABLES)
        if (config.syncModels) add(TBL_MODEL_CONFIGS)
        if (config.syncTokens) add(TBL_APP_SETTINGS)
    }

    private fun buildPullPayload(): JsonObject = JsonObject().apply {
        addProperty("deviceId", configStore.deviceId)
        addProperty("schemaVersion", SyncProtocol.SCHEMA_VERSION)
        addProperty("lastRev", 0)
        add("changes", JsonObject())
        add("tombstones", JsonArray())
    }

    private fun buildPushPayload(
        lastRev: Long,
        changes: JsonObject,
        tombstones: JsonArray
    ): JsonObject = JsonObject().apply {
        addProperty("deviceId", configStore.deviceId)
        addProperty("schemaVersion", SyncProtocol.SCHEMA_VERSION)
        addProperty("lastRev", lastRev)
        add("changes", changes)
        add("tombstones", tombstones)
    }

    /**
     * 覆盖本地前清空本机启用的同步数据。
     * 云端快照中不存在的模型配置会被删除，因此其引用也要一并清理。
     */
    private suspend fun wipeLocalData(config: SyncConfig, cloudChanges: JsonObject) {
        if (config.syncNotes) {
            // 先删消息再删会话，避免外键级联顺序依赖
            homeChatDao.deleteAllMessages()
            homeChatDao.deleteAllConversations()
            fragmentDao.deleteAll()
            pageDao.deleteAll()
            summaryDao.deleteAll()
            todoItemDao.deleteAll()
            subjectDao.deleteAll()
            folderDao.deleteAll()
            tombstoneDao.deleteByTables(NOTES_TABLES.toList())
        }
        if (config.syncModels) {
            val cloudModelIds = entityRows(cloudChanges, TBL_MODEL_CONFIGS)
                .mapNotNull { it.get("id")?.takeIf { id -> id.isJsonPrimitive }?.asString }
                .toSet()
            val localModelIds = modelConfigDao.getModelConfigsOnce().map { it.id }
            modelConfigDao.deleteAll()
            localModelIds.filterNot { it in cloudModelIds }.forEach {
                settingsDao.clearModelReference(it)
            }
            tombstoneDao.deleteByTables(listOf(TBL_MODEL_CONFIGS))
        }
    }

    /** 收集本机全部可同步实体（不做水位线过滤），供合并恢复的补推使用。 */
    private suspend fun collectLocalRows(config: SyncConfig): List<SyncRow> {
        val rows = mutableListOf<SyncRow>()
        if (config.syncNotes) {
            subjectDao.getAllOnce().forEach { rows += SyncRow(TBL_SUBJECTS, it.id, it.updatedAt, it) }
            fragmentDao.getAllOnce().forEach { rows += SyncRow(TBL_FRAGMENTS, it.id, it.updatedAt, it) }
            pageDao.getAllOnce().forEach { rows += SyncRow(TBL_PAGES, it.id, it.updatedAt, it) }
            folderDao.getAllOnce().forEach { rows += SyncRow(TBL_FOLDERS, it.id, it.updatedAt, it) }
            homeChatDao.getConversationsOnce().forEach {
                rows += SyncRow(TBL_CONVERSATIONS, it.id, it.updated_at, it)
            }
            homeChatDao.getAllMessagesOnce().forEach {
                rows += SyncRow(TBL_MESSAGES, it.id, it.updatedAt, it)
            }
            summaryDao.getAll().forEach {
                rows += SyncRow(TBL_SUMMARIES, it.subjectId, it.updatedAt, it)
            }
            todoItemDao.getAllOnce().forEach { rows += SyncRow(TBL_TODOS, it.id, it.updatedAt, it) }
        }
        if (config.syncModels) {
            modelConfigDao.getModelConfigsOnce().forEach {
                rows += SyncRow(TBL_MODEL_CONFIGS, it.id, it.updatedAt, it)
            }
        }
        return rows
    }

    /** 本机独有的、或比云端更新的记录（合并恢复补推用）。 */
    private fun buildPendingChanges(remote: JsonObject, rows: List<SyncRow>): JsonObject {
        val out = JsonObject()
        val remoteVersions = HashMap<String, Map<String, Long>>()
        for (row in rows) {
            val versions = remoteVersions.getOrPut(row.tbl) { remoteVersionMap(remote, row.tbl) }
            val remoteUpdatedAt = versions[row.id]
            if (remoteUpdatedAt != null && remoteUpdatedAt >= row.updatedAt) continue
            val array = out.getAsJsonArray(row.tbl) ?: JsonArray().also { out.add(row.tbl, it) }
            array.add(gson.toJsonTree(row.entity))
        }
        return out
    }

    private fun remoteVersionMap(changes: JsonObject, tbl: String): Map<String, Long> {
        val idField = if (tbl == TBL_SUMMARIES) "subjectId" else "id"
        val result = HashMap<String, Long>()
        for (json in entityRows(changes, tbl)) {
            val id = json.get(idField)?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val ts = json.get("updatedAt")?.takeIf { it.isJsonPrimitive }?.asLong
                ?: json.get("updated_at")?.takeIf { it.isJsonPrimitive }?.asLong
                ?: 0L
            result[id] = ts
        }
        return result
    }

    /** 合并恢复时把本地待推送的墓碑一起带上，避免云端已删除的数据被本机旧数据复活。 */
    private suspend fun buildLocalTombstones(config: SyncConfig): JsonArray {
        val out = JsonArray()
        for (ts in tombstoneDao.getSince(-1)) {
            val categoryOn = if (ts.tbl == TBL_MODEL_CONFIGS) config.syncModels else config.syncNotes
            if (!categoryOn) continue
            out.add(JsonObject().apply {
                addProperty("tbl", ts.tbl)
                addProperty("id", ts.id)
                addProperty("deletedAt", ts.deletedAt)
            })
        }
        return out
    }

    /** 快照中指定表的实体与墓碑的最大时间戳。 */
    private fun snapshotMaxTimestamp(
        changes: JsonObject,
        tombstones: JsonArray,
        tables: Set<String>
    ): Long = maxOf(maxUpdatedAtFor(changes, tables), tombstones.maxTimestamp(tables))

    private fun JsonArray.maxTimestamp(tables: Set<String>): Long {
        var max = 0L
        for (element in this) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val tbl = obj.get("tbl")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            if (tbl !in tables) continue
            val deletedAt = obj.get("deletedAt")?.takeIf { it.isJsonPrimitive }?.asLong ?: continue
            if (deletedAt > max) max = deletedAt
        }
        return max
    }

    /** 本机指定实体的更新时间；不存在时返回 null。 */
    private suspend fun localUpdatedAt(tbl: String, id: String): Long? = when (tbl) {
        TBL_SUBJECTS -> subjectDao.getSubjectById(id)?.updatedAt
        TBL_FRAGMENTS -> fragmentDao.getById(id)?.updatedAt
        TBL_PAGES -> pageDao.getById(id)?.updatedAt
        TBL_FOLDERS -> folderDao.getFolderById(id)?.updatedAt
        TBL_CONVERSATIONS -> homeChatDao.getConversationById(id)?.updated_at
        TBL_MESSAGES -> homeChatDao.getMessageById(id)?.updatedAt
        TBL_SUMMARIES -> summaryDao.getBySubjectId(id)?.updatedAt
        TBL_TODOS -> todoItemDao.getByIdOnce(id)?.updatedAt
        TBL_MODEL_CONFIGS -> modelConfigDao.getById(id)?.updatedAt
        else -> null
    }

    companion object {
        private const val TAG = "CloudSync"
        private const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

        const val TBL_SUBJECTS = "subjects"
        const val TBL_FRAGMENTS = "fragments"
        const val TBL_PAGES = "notebook_pages"
        const val TBL_FOLDERS = "folders"
        const val TBL_CONVERSATIONS = "home_conversations"
        const val TBL_MESSAGES = "home_messages"
        const val TBL_SUMMARIES = "subject_summaries"
        const val TBL_TODOS = "todo_items"
        const val TBL_MODEL_CONFIGS = "model_configs"

        /** 更新源令牌：不落在 Room 里，只作为同步载荷中的一条单行记录。 */
        const val TBL_APP_SETTINGS = "app_settings"
        const val APP_SETTINGS_ID = "singleton"

        /** 笔记类表集合：用于按类别汇总水位线与最大时间戳。 */
        private val NOTES_TABLES = setOf(
            TBL_SUBJECTS, TBL_FRAGMENTS, TBL_PAGES, TBL_FOLDERS,
            TBL_CONVERSATIONS, TBL_MESSAGES, TBL_SUMMARIES, TBL_TODOS
        )
        private val MODEL_TABLES = setOf(TBL_MODEL_CONFIGS)
        private val TOKEN_TABLES = setOf(TBL_APP_SETTINGS)
    }
}

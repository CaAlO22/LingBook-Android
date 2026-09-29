package com.lingji.app.data.sync

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.dao.HomeChatDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.dao.SyncTombstoneDao
import com.lingji.app.data.db.entities.FolderEntity
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.SubjectEntity
import com.lingji.app.data.db.entities.SubjectSummaryEntity
import com.lingji.app.data.remote.sync.SyncApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** 一轮同步的结果。 */
sealed interface SyncResult {
    /** 未启用或配置不完整，本轮未执行。 */
    data object Disabled : SyncResult

    data class Success(val pushed: Int, val pulled: Int, val deleted: Int) : SyncResult

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
    private val tombstoneDao: SyncTombstoneDao
) {
    private val gson = Gson()
    private val syncMutex = Mutex()

    suspend fun syncNow(): SyncResult = syncMutex.withLock {
        val config = configStore.loadConfig()
        if (!config.enabled || !config.isValid) return@withLock SyncResult.Disabled

        try {
            val watermark = configStore.watermark

            // ── 1. 收集本地变更（updatedAt/deletedAt > watermark）──
            val subjects = subjectDao.getUpdatedSince(watermark)
            val fragments = fragmentDao.getUpdatedSince(watermark)
            val pages = pageDao.getUpdatedSince(watermark)
            val folders = folderDao.getUpdatedSince(watermark)
            val conversations = homeChatDao.getConversationsUpdatedSince(watermark)
            val messages = homeChatDao.getMessagesUpdatedSince(watermark)
            val summaries = summaryDao.getUpdatedSince(watermark)
            val tombstones = tombstoneDao.getSince(watermark)

            val payload = JsonObject().apply {
                addProperty("deviceId", configStore.deviceId)
                addProperty("lastRev", configStore.lastRev)
                add("changes", JsonObject().apply {
                    addEntityArray(TBL_SUBJECTS, subjects)
                    addEntityArray(TBL_FRAGMENTS, fragments)
                    addEntityArray(TBL_PAGES, pages)
                    addEntityArray(TBL_FOLDERS, folders)
                    addEntityArray(TBL_CONVERSATIONS, conversations)
                    addEntityArray(TBL_MESSAGES, messages)
                    addEntityArray(TBL_SUMMARIES, summaries)
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

            // ── 3. 应用远端变更（按外键安全顺序）──
            var pulled = 0
            var deleted = 0
            var maxSeen = watermark
            listOf(
                subjects.maxOfOrNull { it.updatedAt },
                fragments.maxOfOrNull { it.updatedAt },
                pages.maxOfOrNull { it.updatedAt },
                folders.maxOfOrNull { it.updatedAt },
                conversations.maxOfOrNull { it.updated_at },
                messages.maxOfOrNull { it.updatedAt },
                summaries.maxOfOrNull { it.updatedAt },
                tombstones.maxOfOrNull { it.deletedAt }
            ).filterNotNull().maxOrNull()?.let { maxSeen = maxOf(maxSeen, it) }

            val changes = response.getAsJsonObject("changes") ?: JsonObject()
            pulled += applyFolders(changes)
            pulled += applySubjects(changes)
            pulled += applyConversations(changes)
            pulled += applyMessages(changes)
            pulled += applyFragments(changes)
            pulled += applyPages(changes)
            pulled += applySummaries(changes)

            val remoteTombstones = response.getAsJsonArray("tombstones") ?: JsonArray()
            for (element in remoteTombstones) {
                val ts = element.asJsonObject
                val tbl = ts.get("tbl")?.asString ?: continue
                val id = ts.get("id")?.asString ?: continue
                val deletedAt = ts.get("deletedAt")?.asLong ?: continue
                if (applyTombstone(tbl, id)) deleted++
                maxSeen = maxOf(maxSeen, deletedAt)
            }

            // ── 4. 推进游标（仅在整轮成功后）──
            configStore.lastRev = response.get("rev")?.asLong ?: configStore.lastRev
            configStore.watermark = maxSeen
            configStore.lastSyncAt = System.currentTimeMillis()
            // 清理 30 天前的墓碑（此时所有在线设备早已收到）
            tombstoneDao.deleteOlderThan(System.currentTimeMillis() - TOMBSTONE_RETENTION_MS)

            val pushed = subjects.size + fragments.size + pages.size + folders.size +
                conversations.size + messages.size + summaries.size + tombstones.size
            SyncResult.Success(pushed = pushed, pulled = pulled, deleted = deleted)
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}")
            SyncResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    // ── 远端变更应用 ──

    private fun entityRows(changes: JsonObject, tbl: String): List<JsonObject> =
        changes.getAsJsonArray(tbl)?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            ?: emptyList()

    private suspend fun applyFolders(changes: JsonObject): Int {
        var count = 0
        for (json in entityRows(changes, TBL_FOLDERS)) {
            runCatching {
                folderDao.insert(gson.fromJson(json, FolderEntity::class.java))
                count++
            }
        }
        return count
    }

    private suspend fun applySubjects(changes: JsonObject): Int {
        var count = 0
        for (json in entityRows(changes, TBL_SUBJECTS)) {
            runCatching {
                subjectDao.insert(gson.fromJson(json, SubjectEntity::class.java))
                count++
            }
        }
        return count
    }

    private suspend fun applyConversations(changes: JsonObject): Int {
        var count = 0
        for (json in entityRows(changes, TBL_CONVERSATIONS)) {
            runCatching {
                val entity = gson.fromJson(json, HomeConversationEntity::class.java)
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

    private suspend fun applyMessages(changes: JsonObject): Int {
        var count = 0
        for (json in entityRows(changes, TBL_MESSAGES)) {
            runCatching {
                val entity = gson.fromJson(json, HomeMessageEntity::class.java)
                // 外键约束：会话不存在（如已被删除）时跳过，避免崩溃
                if (homeChatDao.getConversationById(entity.conversation_id) == null) return@runCatching
                homeChatDao.insertMessage(entity)
                count++
            }
        }
        return count
    }

    private suspend fun applyFragments(changes: JsonObject): Int {
        var count = 0
        for (json in entityRows(changes, TBL_FRAGMENTS)) {
            runCatching {
                fragmentDao.insert(gson.fromJson(json, FragmentEntity::class.java))
                count++
            }
        }
        return count
    }

    private suspend fun applyPages(changes: JsonObject): Int {
        var count = 0
        for (json in entityRows(changes, TBL_PAGES)) {
            runCatching {
                val entity = gson.fromJson(json, NotebookPageEntity::class.java)
                // indexedAt 是本机索引状态，不随同步覆盖
                val local = pageDao.getById(entity.id)
                pageDao.insert(if (local != null) entity.copy(indexedAt = local.indexedAt) else entity)
                count++
            }
        }
        return count
    }

    private suspend fun applySummaries(changes: JsonObject): Int {
        var count = 0
        for (json in entityRows(changes, TBL_SUMMARIES)) {
            runCatching {
                summaryDao.upsert(gson.fromJson(json, SubjectSummaryEntity::class.java))
                count++
            }
        }
        return count
    }

    private suspend fun applyTombstone(tbl: String, id: String): Boolean = runCatching {
        when (tbl) {
            TBL_SUBJECTS -> subjectDao.deleteById(id)
            TBL_FRAGMENTS -> fragmentDao.deleteById(id)
            TBL_PAGES -> pageDao.deleteById(id)
            TBL_FOLDERS -> folderDao.deleteById(id)
            TBL_CONVERSATIONS -> homeChatDao.deleteConversation(id) // FK 级联删消息
            TBL_MESSAGES -> homeChatDao.deleteMessageById(id)
            TBL_SUMMARIES -> summaryDao.deleteBySubjectId(id)
            else -> return false
        }
        true
    }.getOrDefault(false)

    private fun JsonObject.addEntityArray(name: String, entities: List<Any>) {
        if (entities.isEmpty()) return
        add(name, JsonArray().apply { entities.forEach { add(gson.toJsonTree(it)) } })
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
    }
}

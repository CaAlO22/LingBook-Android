package com.lingji.app.data.db

import android.content.ContentValues
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.dao.HomeChatDao
import com.lingji.app.data.db.dao.ModelConfigDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.NoteRevisionDao
import com.lingji.app.data.db.dao.SettingsDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.dao.SyncTombstoneDao
import com.lingji.app.data.db.dao.TodoItemDao
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.FolderEntity
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeFragmentEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import com.lingji.app.data.db.entities.ModelConfigEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.NoteRevisionEntity
import com.lingji.app.data.db.entities.SettingsEntity
import com.lingji.app.data.db.entities.SubjectEntity
import com.lingji.app.data.db.entities.SubjectSummaryEntity
import com.lingji.app.data.db.entities.SyncTombstoneEntity
import com.lingji.app.data.db.entities.TodoItemEntity
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.provider.ProviderRegistry
import org.json.JSONObject

@Database(
    entities = [
        SubjectEntity::class,
        FragmentEntity::class,
        NotebookPageEntity::class,
        SettingsEntity::class,
        SubjectSummaryEntity::class,
        HomeConversationEntity::class,
        HomeMessageEntity::class,
        HomeFragmentEntity::class,
        FolderEntity::class,
        NoteRevisionEntity::class,
        SyncTombstoneEntity::class,
        TodoItemEntity::class,
        ModelConfigEntity::class
    ],
    version = 23,
    exportSchema = false
)
abstract class LingjiDatabase : RoomDatabase() {

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebook_pages ADD COLUMN indexedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subjects ADD COLUMN pageIndexJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN lastOpenedPageId TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN horizontalSwipeAction TEXT NOT NULL DEFAULT 'TOGGLE_PREVIEW'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS subject_summaries (" +
                        "subjectId TEXT NOT NULL PRIMARY KEY, " +
                        "summary TEXT NOT NULL, " +
                        "summarizedAt INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN providerApiKeys TEXT NOT NULL DEFAULT '{}'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS home_conversations (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "title TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, " +
                        "updated_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS home_messages (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "conversation_id TEXT NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "tool_calls_json TEXT, " +
                        "timestamp INTEGER NOT NULL, " +
                        "FOREIGN KEY(conversation_id) REFERENCES home_conversations(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_messages_conversation_id ON home_messages(conversation_id)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS home_fragments (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "position INTEGER NOT NULL, " +
                        "content TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS folders (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "orderIndex INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE subjects ADD COLUMN folderId TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN liteProvider TEXT NOT NULL DEFAULT 'OPENAI'")
                db.execSQL("ALTER TABLE settings ADD COLUMN liteBaseUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN liteApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN liteModelName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN liteEnableThinking INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_fragments ADD COLUMN timestamp INTEGER")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS note_revisions (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "subjectId TEXT NOT NULL, " +
                        "pageId TEXT, " +
                        "field TEXT NOT NULL, " +
                        "prevContent TEXT NOT NULL, " +
                        "prevTitle TEXT, " +
                        "batchId TEXT, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_revisions_subjectId ON note_revisions(subjectId)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 云同步：为各表补充 updatedAt 时间戳（以既有时间列回填）
                db.execSQL("ALTER TABLE subjects ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE subjects SET updatedAt = createdAt")
                db.execSQL("ALTER TABLE fragments ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE fragments SET updatedAt = timestamp")
                db.execSQL("ALTER TABLE folders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE folders SET updatedAt = createdAt")
                db.execSQL("ALTER TABLE home_messages ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE home_messages SET updatedAt = timestamp")
                db.execSQL("ALTER TABLE subject_summaries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE subject_summaries SET updatedAt = summarizedAt")
                // 云同步：删除墓碑表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_tombstones (" +
                        "tbl TEXT NOT NULL, " +
                        "id TEXT NOT NULL, " +
                        "deletedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(tbl, id))"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 待办列表：新增待办项表与重复设置列
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS todo_items (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "subjectId TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "isDone INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_subjectId ON todo_items(subjectId)")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoRepeatType TEXT NOT NULL DEFAULT 'ONCE'")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoRepeatDays TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoIntervalDays INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoRepeatAnchorAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoLastResetAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Agent 工具调用温度可配置，默认 0.2
                db.execSQL("ALTER TABLE settings ADD COLUMN agentTemperature REAL NOT NULL DEFAULT 0.2")
            }
        }

        /** 1.x 设置页可一键填充的预设 Base URL（MiMo 有两种套餐）。
         *  切换供应商时也会自动填充默认 URL，因此“无 key + 预设 URL”只代表浏览过，不代表配置过。 */
        private val LEGACY_PRESET_URLS: Map<String, Set<String>> = mapOf(
            "OPENAI" to setOf("https://api.openai.com/v1"),
            "DOUBAO" to setOf("https://ark.cn-beijing.volces.com/api/v3"),
            "XIAOMI" to setOf("https://api.xiaomimimo.com/v1", "https://token-plan-cn.xiaomimimo.com/v1"),
            "BAILIAN" to setOf("https://dashscope.aliyuncs.com/compatible-mode/v1"),
            "ZHIPU" to setOf("https://open.bigmodel.cn/api/paas/v4"),
            "DEEPSEEK" to setOf("https://api.deepseek.com"),
            "KIMI" to setOf("https://api.moonshot.cn/v1")
        )

        private fun isPresetUrl(provider: String, baseUrl: String): Boolean {
            if (baseUrl.isBlank()) return false
            if (baseUrl in (LEGACY_PRESET_URLS[provider] ?: emptySet())) return true
            val p = runCatching { APIProvider.valueOf(provider) }.getOrNull() ?: return false
            return baseUrl == ProviderRegistry.config(p).defaultBaseUrl
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 模型配置独立成表，主模型/辅助模型改为引用 model_configs.id
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS model_configs (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "provider TEXT NOT NULL, " +
                        "baseUrl TEXT NOT NULL, " +
                        "apiKey TEXT NOT NULL, " +
                        "modelName TEXT NOT NULL, " +
                        "enableThinking INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt INTEGER NOT NULL)"
                )
                // 旧版 settings 单例行只反映升级前最后停留的供应商界面，
                // 真正填过 key 的供应商记录在 providerApiKeys JSON 中，需逐供应商迁移，
                // 否则已配置的模型会丢失、最后浏览的供应商反而变成幻影模型。
                var primaryId = ""
                var assistantId = ""
                db.query("SELECT * FROM settings WHERE id = 'singleton'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        fun str(column: String): String {
                            val i = cursor.getColumnIndex(column)
                            return if (i >= 0) cursor.getString(i) ?: "" else ""
                        }
                        fun int(column: String): Int {
                            val i = cursor.getColumnIndex(column)
                            return if (i >= 0) cursor.getInt(i) else 0
                        }
                        val now = System.currentTimeMillis()
                        val rows = LinkedHashMap<String, ContentValues>()
                        fun putRow(id: String, provider: String, baseUrl: String, apiKey: String, modelName: String, enableThinking: Int) {
                            rows[id] = ContentValues().apply {
                                put("id", id)
                                put("provider", provider)
                                put("baseUrl", baseUrl)
                                put("apiKey", apiKey)
                                put("modelName", modelName)
                                put("enableThinking", enableThinking)
                                put("createdAt", now)
                            }
                        }
                        // 1) 每个填过 key 的供应商迁移为一条模型配置（URL/模型用当前默认值）
                        val keys = runCatching { JSONObject(str("providerApiKeys")) }.getOrNull()
                        if (keys != null) {
                            for (name in keys.keys()) {
                                val key = keys.optString(name, "")
                                val p = runCatching { APIProvider.valueOf(name) }.getOrNull() ?: continue
                                if (key.isBlank()) continue
                                val config = ProviderRegistry.config(p)
                                putRow("migrated-$name", name, config.defaultBaseUrl, key, config.defaultModelId, 0)
                            }
                        }
                        // 2) 最后停留的主模型界面：有 key 或自定义 URL 才算配置过；
                        //    配置过则覆盖同供应商的 map 行，保留界面上改过的 URL/模型/思考开关
                        val provider = str("provider").uppercase()
                        val activeProvider = runCatching { APIProvider.valueOf(provider) }.getOrNull()
                        val apiKey = str("apiKey")
                        val baseUrl = str("baseUrl")
                        val activeConfigured = activeProvider != null &&
                            (apiKey.isNotBlank() || (baseUrl.isNotBlank() && !isPresetUrl(provider, baseUrl)))
                        if (activeProvider != null) {
                            val id = "migrated-$provider"
                            val existing = rows[id]
                            if (activeConfigured) {
                                val config = ProviderRegistry.config(activeProvider)
                                putRow(
                                    id, provider,
                                    baseUrl.ifBlank { existing?.getAsString("baseUrl") ?: config.defaultBaseUrl },
                                    apiKey.ifBlank { existing?.getAsString("apiKey") ?: "" },
                                    str("modelName").ifBlank { existing?.getAsString("modelName") ?: config.defaultModelId },
                                    int("enableThinking")
                                )
                                primaryId = id
                            } else if (existing != null) {
                                primaryId = id
                            }
                        }
                        // 3) 轻量模型迁移为辅助模型（独立行，避免与主模型同供应商时主键冲突）
                        val liteProvider = str("liteProvider").uppercase()
                        val liteProviderEnum = runCatching { APIProvider.valueOf(liteProvider) }.getOrNull()
                        val liteApiKey = str("liteApiKey")
                        val liteBaseUrl = str("liteBaseUrl")
                        val liteConfigured = liteProviderEnum != null &&
                            (liteApiKey.isNotBlank() || (liteBaseUrl.isNotBlank() && !isPresetUrl(liteProvider, liteBaseUrl)))
                        if (liteConfigured && liteProviderEnum != null) {
                            val config = ProviderRegistry.config(liteProviderEnum)
                            putRow(
                                "migrated-assistant", liteProvider,
                                liteBaseUrl.ifBlank { config.defaultBaseUrl },
                                liteApiKey,
                                str("liteModelName").ifBlank { config.defaultModelId },
                                int("liteEnableThinking")
                            )
                            assistantId = "migrated-assistant"
                        }
                        rows.values.forEach { db.insert("model_configs", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, it) }
                    }
                }
                // 重建 settings 表，移除已迁移到 model_configs 的列
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS settings_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "primaryModelId TEXT NOT NULL DEFAULT '', " +
                        "assistantModelId TEXT NOT NULL DEFAULT '', " +
                        "agentTemperature REAL NOT NULL DEFAULT 0.2, " +
                        "horizontalSwipeAction TEXT NOT NULL DEFAULT 'TOGGLE_PREVIEW')"
                )
                db.execSQL(
                    "INSERT INTO settings_new (id, primaryModelId, assistantModelId, agentTemperature, horizontalSwipeAction) " +
                        "SELECT id, ?, ?, agentTemperature, horizontalSwipeAction FROM settings",
                    arrayOf(primaryId, assistantId)
                )
                db.execSQL("DROP TABLE settings")
                db.execSQL("ALTER TABLE settings_new RENAME TO settings")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 模型配置新增用户自定义别名，默认与模型名称一致
                db.execSQL("ALTER TABLE model_configs ADD COLUMN alias TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE model_configs SET alias = modelName WHERE alias = ''")
            }
        }
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 思考开关升级为思考程度，同时补充云同步所需的 updatedAt。
                // 旧列 enableThinking 不再保留，因此重建表以与实体结构严格一致。
                db.execSQL("ALTER TABLE model_configs RENAME TO model_configs_legacy")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS model_configs (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "alias TEXT NOT NULL, " +
                        "provider TEXT NOT NULL, " +
                        "baseUrl TEXT NOT NULL, " +
                        "apiKey TEXT NOT NULL, " +
                        "modelName TEXT NOT NULL, " +
                        "thinkingLevel TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO model_configs " +
                        "(id, alias, provider, baseUrl, apiKey, modelName, thinkingLevel, createdAt, updatedAt) " +
                        "SELECT id, alias, provider, baseUrl, apiKey, modelName, " +
                        "CASE WHEN enableThinking = 1 THEN 'HIGH' ELSE 'OFF' END, createdAt, createdAt " +
                        "FROM model_configs_legacy"
                )
                db.execSQL("DROP TABLE model_configs_legacy")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 旧版 16_17 只迁移了升级前停留的供应商界面：未填 key 时因切换供应商
                // 自动填充的预设 URL 会留下幻影模型行（如空 key 的 Kimi）。清除这类行并解除引用。
                val phantomIds = mutableListOf<String>()
                db.query(
                    "SELECT id, provider, baseUrl, apiKey FROM model_configs " +
                        "WHERE id IN ('migrated-primary', 'migrated-assistant')"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(3).isNotBlank()) continue
                        if (isPresetUrl(cursor.getString(1), cursor.getString(2))) {
                            phantomIds += cursor.getString(0)
                        }
                    }
                }
                if (phantomIds.isNotEmpty()) {
                    val placeholders = phantomIds.joinToString(", ") { "?" }
                    val args = phantomIds.toTypedArray()
                    db.execSQL("DELETE FROM model_configs WHERE id IN ($placeholders)", args)
                    db.execSQL("UPDATE settings SET primaryModelId = '' WHERE primaryModelId IN ($placeholders)", args)
                    db.execSQL("UPDATE settings SET assistantModelId = '' WHERE assistantModelId IN ($placeholders)", args)
                }
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 待办支持拖动排序：新增 sortOrder，并按原有创建顺序补齐位次，
                // 避免升级后所有条目位次同为 0 导致顺序不稳定。
                db.execSQL("ALTER TABLE todo_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE todo_items SET sortOrder = (" +
                        "SELECT COUNT(*) FROM todo_items AS t " +
                        "WHERE t.subjectId = todo_items.subjectId " +
                        "AND (t.createdAt < todo_items.createdAt " +
                        "OR (t.createdAt = todo_items.createdAt AND t.id < todo_items.id)))"
                )
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 首页默认落地页可配置：卡片列表 / 聚合页
                db.execSQL("ALTER TABLE settings ADD COLUMN homePageMode TEXT NOT NULL DEFAULT 'CARD_LIST'")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 信息岛：待办提醒——单条待办与整个待办列表各增加提醒设置。
                db.execSQL("ALTER TABLE todo_items ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE todo_items ADD COLUMN reminderMinutes INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE todo_items ADD COLUMN reminderAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoReminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoReminderMinutes INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE subjects ADD COLUMN todoReminderAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
    abstract fun subjectDao(): SubjectDao
    abstract fun fragmentDao(): FragmentDao
    abstract fun notebookPageDao(): NotebookPageDao
    abstract fun settingsDao(): SettingsDao
    abstract fun subjectSummaryDao(): SubjectSummaryDao
    abstract fun homeChatDao(): HomeChatDao
    abstract fun folderDao(): FolderDao
    abstract fun noteRevisionDao(): NoteRevisionDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
    abstract fun todoItemDao(): TodoItemDao
    abstract fun modelConfigDao(): ModelConfigDao
}

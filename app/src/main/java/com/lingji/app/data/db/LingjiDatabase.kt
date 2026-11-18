package com.lingji.app.data.db

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
    version = 19,
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
                // 把旧的主模型配置迁移为一条模型配置
                db.execSQL(
                    "INSERT INTO model_configs (id, provider, baseUrl, apiKey, modelName, enableThinking, createdAt) " +
                        "SELECT 'migrated-primary', provider, baseUrl, apiKey, modelName, enableThinking, " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000 FROM settings " +
                        "WHERE id = 'singleton' AND (apiKey != '' OR baseUrl != '')"
                )
                // 把旧的轻量模型配置迁移为辅助模型
                db.execSQL(
                    "INSERT INTO model_configs (id, provider, baseUrl, apiKey, modelName, enableThinking, createdAt) " +
                        "SELECT 'migrated-assistant', liteProvider, liteBaseUrl, liteApiKey, liteModelName, liteEnableThinking, " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000 FROM settings " +
                        "WHERE id = 'singleton' AND (liteApiKey != '' OR liteBaseUrl != '')"
                )
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
                        "SELECT id, " +
                        "CASE WHEN apiKey != '' OR baseUrl != '' THEN 'migrated-primary' ELSE '' END, " +
                        "CASE WHEN liteApiKey != '' OR liteBaseUrl != '' THEN 'migrated-assistant' ELSE '' END, " +
                        "agentTemperature, horizontalSwipeAction FROM settings"
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

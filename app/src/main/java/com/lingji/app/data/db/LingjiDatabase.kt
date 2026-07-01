package com.lingji.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.SettingsDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.SettingsEntity
import com.lingji.app.data.db.entities.SubjectEntity
import com.lingji.app.data.db.entities.SubjectSummaryEntity

@Database(
    entities = [
        SubjectEntity::class,
        FragmentEntity::class,
        NotebookPageEntity::class,
        SettingsEntity::class,
        SubjectSummaryEntity::class
    ],
    version = 6,
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
    }
    abstract fun subjectDao(): SubjectDao
    abstract fun fragmentDao(): FragmentDao
    abstract fun notebookPageDao(): NotebookPageDao
    abstract fun settingsDao(): SettingsDao
    abstract fun subjectSummaryDao(): SubjectSummaryDao
}

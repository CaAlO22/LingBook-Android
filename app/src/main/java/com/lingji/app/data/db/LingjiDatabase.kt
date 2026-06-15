package com.lingji.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.SettingsDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.SettingsEntity
import com.lingji.app.data.db.entities.SubjectEntity

@Database(
    entities = [
        SubjectEntity::class,
        FragmentEntity::class,
        NotebookPageEntity::class,
        SettingsEntity::class
    ],
    version = 3,
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
    }
    abstract fun subjectDao(): SubjectDao
    abstract fun fragmentDao(): FragmentDao
    abstract fun notebookPageDao(): NotebookPageDao
    abstract fun settingsDao(): SettingsDao
}

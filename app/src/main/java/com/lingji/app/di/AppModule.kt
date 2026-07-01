package com.lingji.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lingji.app.data.db.LingjiDatabase
import com.lingji.app.data.db.dao.SubjectSummaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LingjiDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            LingjiDatabase::class.java,
            "lingji_database"
        )
            .addMigrations(
                LingjiDatabase.MIGRATION_1_2,
                LingjiDatabase.MIGRATION_2_3,
                LingjiDatabase.MIGRATION_3_4,
                LingjiDatabase.MIGRATION_4_5,
                LingjiDatabase.MIGRATION_5_6
            )
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "UPDATE notebook_pages SET content = '', updatedAt = strftime('%s','now') * 1000, indexedAt = 0 WHERE length(content) > 1200000"
                        )
                    }
                }
            )
            .build()
    }

    @Provides
    fun provideSubjectDao(database: LingjiDatabase) = database.subjectDao()

    @Provides
    fun provideFragmentDao(database: LingjiDatabase) = database.fragmentDao()

    @Provides
    fun provideNotebookPageDao(database: LingjiDatabase) = database.notebookPageDao()

    @Provides
    fun provideSettingsDao(database: LingjiDatabase) = database.settingsDao()

    @Provides
    fun provideSubjectSummaryDao(database: LingjiDatabase) = database.subjectSummaryDao()
}

package com.lingji.app.di

import android.content.Context
import androidx.room.Room
import com.lingji.app.data.db.LingjiDatabase
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
            .addMigrations(LingjiDatabase.MIGRATION_1_2, LingjiDatabase.MIGRATION_2_3)
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
}

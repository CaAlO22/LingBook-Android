package com.lingji.app.data.repository

import com.lingji.app.data.db.dao.SettingsDao
import com.lingji.app.data.db.entities.SettingsEntity
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    fun getSettings(): Flow<AISettings> = settingsDao.getSettings().map { it?.toDomain() ?: AISettings() }

    suspend fun getSettingsOnce(): AISettings = settingsDao.getSettingsOnce()?.toDomain() ?: AISettings()

    suspend fun save(settings: AISettings) = settingsDao.upsert(settings.toEntity())

    private fun SettingsEntity.toDomain() = AISettings(
        provider = runCatching { APIProvider.valueOf(provider.uppercase()) }.getOrDefault(APIProvider.OPENAI),
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        enableThinking = enableThinking
    )

    private fun AISettings.toEntity() = SettingsEntity(
        provider = provider.name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        enableThinking = enableThinking
    )
}

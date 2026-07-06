package com.lingji.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lingji.app.data.db.dao.SettingsDao
import com.lingji.app.data.db.entities.SettingsEntity
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.HorizontalSwipeAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    fun getSettings(): Flow<AISettings> = settingsDao.getSettings().map { it?.toDomain() ?: AISettings() }

    suspend fun getSettingsOnce(): AISettings = settingsDao.getSettingsOnce()?.toDomain() ?: AISettings()

    suspend fun save(settings: AISettings) = settingsDao.upsert(settings.toEntity())

    private fun SettingsEntity.toDomain(): AISettings {
        val keys = runCatching { gson.fromJson<Map<String, String>>(providerApiKeys, mapType) }
            .getOrNull() ?: emptyMap()
        return AISettings(
            provider = runCatching { APIProvider.valueOf(provider.uppercase()) }.getOrDefault(APIProvider.OPENAI),
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            enableThinking = enableThinking,
            horizontalSwipeAction = runCatching { HorizontalSwipeAction.valueOf(horizontalSwipeAction) }
                .getOrDefault(HorizontalSwipeAction.TOGGLE_PREVIEW),
            providerApiKeys = keys
        )
    }

    private fun AISettings.toEntity() = SettingsEntity(
        provider = provider.name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        enableThinking = enableThinking,
        horizontalSwipeAction = horizontalSwipeAction.name,
        providerApiKeys = gson.toJson(providerApiKeys)
    )
}

package com.lingji.app.data.repository

import com.lingji.app.data.db.dao.ModelConfigDao
import com.lingji.app.data.db.dao.SettingsDao
import com.lingji.app.data.db.dao.SyncTombstoneDao
import com.lingji.app.data.db.entities.ModelConfigEntity
import com.lingji.app.data.db.entities.SettingsEntity
import com.lingji.app.data.db.entities.SyncTombstoneEntity
import com.lingji.app.data.sync.SyncRepository
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.HorizontalSwipeAction
import com.lingji.app.domain.model.ModelConfig
import com.lingji.app.domain.model.ThinkingLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val modelConfigDao: ModelConfigDao,
    private val tombstoneDao: SyncTombstoneDao
) {
    /** 设置与模型配置合并后输出：主模型字段由 [AISettings.primaryModelId] 解析得到。 */
    fun getSettings(): Flow<AISettings> = combine(
        settingsDao.getSettings(),
        modelConfigDao.getModelConfigs()
    ) { entity, configs ->
        entity?.toDomain(configs.map { it.toDomain() }) ?: AISettings()
    }

    suspend fun getSettingsOnce(): AISettings {
        val entity = settingsDao.getSettingsOnce() ?: return AISettings()
        return entity.toDomain(modelConfigDao.getModelConfigsOnce().map { it.toDomain() })
    }

    suspend fun save(settings: AISettings) = settingsDao.upsert(settings.toEntity())

    fun getModelConfigs(): Flow<List<ModelConfig>> =
        modelConfigDao.getModelConfigs().map { list -> list.map { it.toDomain() } }

    suspend fun saveModelConfig(config: ModelConfig) = modelConfigDao.upsert(
        config.copy(updatedAt = System.currentTimeMillis()).toEntity()
    )

    /** 删除模型配置；若它正被主模型或辅助模型引用，一并清空对应选择。 */
    suspend fun deleteModelConfig(id: String) {
        modelConfigDao.deleteById(id)
        // 云同步：把删除动作传播到服务器与其他设备
        tombstoneDao.insertAll(
            listOf(SyncTombstoneEntity(SyncRepository.TBL_MODEL_CONFIGS, id, System.currentTimeMillis()))
        )
        val current = getSettingsOnce()
        if (current.primaryModelId == id || current.assistantModelId == id) {
            save(
                current.copy(
                    primaryModelId = if (current.primaryModelId == id) "" else current.primaryModelId,
                    assistantModelId = if (current.assistantModelId == id) "" else current.assistantModelId
                )
            )
        }
    }

    private fun SettingsEntity.toDomain(configs: List<ModelConfig>): AISettings {
        val primary = configs.find { it.id == primaryModelId }
        val assistant = configs.find { it.id == assistantModelId }
        return AISettings(
            primaryModelId = primaryModelId,
            assistantModelId = assistantModelId,
            provider = primary?.provider ?: APIProvider.OPENAI,
            baseUrl = primary?.baseUrl ?: "",
            apiKey = primary?.apiKey ?: "",
            modelName = primary?.modelName ?: "",
            thinkingLevel = primary?.thinkingLevel ?: ThinkingLevel.OFF,
            agentTemperature = agentTemperature,
            horizontalSwipeAction = runCatching { HorizontalSwipeAction.valueOf(horizontalSwipeAction) }
                .getOrDefault(HorizontalSwipeAction.TOGGLE_PREVIEW),
            assistantModel = assistant
        )
    }

    private fun AISettings.toEntity() = SettingsEntity(
        primaryModelId = primaryModelId,
        assistantModelId = assistantModelId,
        agentTemperature = agentTemperature,
        horizontalSwipeAction = horizontalSwipeAction.name
    )

    private fun ModelConfigEntity.toDomain() = ModelConfig(
        id = id,
        alias = alias,
        provider = runCatching { APIProvider.valueOf(provider.uppercase()) }.getOrDefault(APIProvider.OPENAI),
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        thinkingLevel = runCatching { ThinkingLevel.valueOf(thinkingLevel) }
            .getOrDefault(ThinkingLevel.OFF),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ModelConfig.toEntity() = ModelConfigEntity(
        id = id,
        alias = alias,
        provider = provider.name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        thinkingLevel = thinkingLevel.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

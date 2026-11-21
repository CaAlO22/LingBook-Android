package com.lingji.app.domain.provider

import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.openai.OpenAIConfig
import com.lingji.app.domain.provider.zhipu.ZhipuConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 云端目录叠加到 [ProviderRegistry] 的规则。
 *
 * 目标：调用点（设置页、请求策略、视觉能力判断）完全不用知道模型列表来自云端还是内置。
 */
class ProviderRegistryCatalogTest {

    @Before
    @After
    fun resetCatalog() {
        RemoteModelCatalog.clear()
    }

    @Test
    fun withoutCatalog_builtInListIsUsed() {
        val config = ProviderRegistry.config(APIProvider.OPENAI)
        assertEquals(OpenAIConfig.models.map { it.id }, config.models.map { it.id })
        assertEquals(OpenAIConfig.defaultModelId, config.defaultModelId)
    }

    @Test
    fun remoteListReplacesBuiltInList() {
        RemoteModelCatalog.apply(
            mapOf("OPENAI" to listOf(ProviderModel(id = "future-model", name = "Future Model"))),
            version = 3,
            updatedAt = 1000L
        )
        val config = ProviderRegistry.config(APIProvider.OPENAI)
        assertEquals(listOf("future-model"), config.models.map { it.id })
        // 内置默认模型已不在新列表里 -> 回落到列表第一个，避免默认值指向已下线模型
        assertEquals("future-model", config.defaultModelId)
    }

    @Test
    fun defaultModelIsKeptWhenStillAvailable() {
        RemoteModelCatalog.apply(
            mapOf(
                "OPENAI" to listOf(
                    ProviderModel(id = "gpt-6-astra", name = "GPT-6 Astra"),
                    ProviderModel(id = "gpt-6.2-nova", name = "GPT-6.2 Nova")
                )
            ),
            version = 1,
            updatedAt = 0L
        )
        assertEquals("gpt-6-astra", ProviderRegistry.config(APIProvider.OPENAI).defaultModelId)
    }

    @Test
    fun providersWithoutRemoteEntry_keepBuiltInList() {
        RemoteModelCatalog.apply(
            mapOf("OPENAI" to listOf(ProviderModel(id = "only", name = "Only"))),
            version = 1,
            updatedAt = 0L
        )
        assertEquals(
            ZhipuConfig.models.map { it.id },
            ProviderRegistry.config(APIProvider.ZHIPU).models.map { it.id }
        )
    }

    @Test
    fun emptyRemoteList_isIgnored() {
        // 空列表等同于未下发：不能把设置页的下拉框变成空的
        RemoteModelCatalog.apply(mapOf("OPENAI" to emptyList()), version = 1, updatedAt = 0L)
        assertEquals(
            OpenAIConfig.models.map { it.id },
            ProviderRegistry.config(APIProvider.OPENAI).models.map { it.id }
        )
    }

    @Test
    fun supportsVisionFollowsRemoteCatalog() {
        // 内置 gpt-6-astra 支持视觉；云端改口后，请求链路要立刻按新值判断
        assertTrue(ProviderRegistry.supportsVision(APIProvider.OPENAI, "gpt-6-astra"))
        RemoteModelCatalog.apply(
            mapOf("OPENAI" to listOf(ProviderModel(id = "gpt-6-astra", name = "A", supportsVision = false))),
            version = 1,
            updatedAt = 0L
        )
        assertFalse(ProviderRegistry.supportsVision(APIProvider.OPENAI, "gpt-6-astra"))
    }

    @Test
    fun thinkingLevelsFollowRemoteCatalog() {
        RemoteModelCatalog.apply(
            mapOf(
                "OPENAI" to listOf(
                    ProviderModel(id = "m1", name = "M1", thinkingLevels = listOf(ThinkingLevel.LOW))
                )
            ),
            version = 1,
            updatedAt = 0L
        )
        assertEquals(
            listOf(ThinkingLevel.LOW),
            ProviderRegistry.config(APIProvider.OPENAI).thinkingLevelsFor("m1")
        )
    }

    @Test
    fun builtInConfigIgnoresCatalog() {
        // 数据库迁移必须与远端状态无关
        RemoteModelCatalog.apply(
            mapOf("OPENAI" to listOf(ProviderModel(id = "only", name = "Only"))),
            version = 1,
            updatedAt = 0L
        )
        assertEquals(
            OpenAIConfig.models.map { it.id },
            ProviderRegistry.builtInConfig(APIProvider.OPENAI).models.map { it.id }
        )
        assertEquals(OpenAIConfig.defaultModelId, ProviderRegistry.builtInConfig(APIProvider.OPENAI).defaultModelId)
    }

    @Test
    fun clearingCatalogRestoresBuiltInList() {
        RemoteModelCatalog.apply(
            mapOf("OPENAI" to listOf(ProviderModel(id = "only", name = "Only"))),
            version = 1,
            updatedAt = 0L
        )
        RemoteModelCatalog.clear()
        assertEquals(
            OpenAIConfig.models.map { it.id },
            ProviderRegistry.config(APIProvider.OPENAI).models.map { it.id }
        )
    }
}

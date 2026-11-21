package com.lingji.app.data.sync

import com.lingji.app.data.remote.sync.ModelCatalogResponse
import com.lingji.app.data.remote.sync.RemoteModel
import com.lingji.app.domain.model.ThinkingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 云端目录的解析：宽松校验，坏数据不能拖垮整份列表。 */
class ModelCatalogParserTest {

    private fun response(vararg models: RemoteModel) = ModelCatalogResponse(
        catalogVersion = 2,
        updatedAt = 1000L,
        providers = mapOf("OPENAI" to models.toList())
    )

    @Test
    fun parsesBasicFields() {
        val parsed = ModelCatalogParser.parse(
            response(
                RemoteModel(
                    id = " gpt-6-astra ",
                    name = " GPT-6 Astra ",
                    description = "旗舰",
                    supportsVision = true,
                    thinkingLevels = listOf("OFF", "HIGH")
                )
            )
        )
        val model = parsed.getValue("OPENAI").single()
        assertEquals("gpt-6-astra", model.id)
        assertEquals("GPT-6 Astra", model.name)
        assertEquals("旗舰", model.description)
        assertTrue(model.supportsVision)
        assertEquals(listOf(ThinkingLevel.OFF, ThinkingLevel.HIGH), model.thinkingLevels)
    }

    @Test
    fun missingOptionalFields_useSafeDefaults() {
        val model = ModelCatalogParser.parse(
            response(RemoteModel(id = "m1", name = "M1"))
        ).getValue("OPENAI").single()
        assertEquals("", model.description)
        assertFalse(model.supportsVision)
        // 服务器不下发 thinkingLevels = 沿用供应商默认档位
        assertNull(model.thinkingLevels)
    }

    @Test
    fun skipsEntriesWithoutIdOrName() {
        val parsed = ModelCatalogParser.parse(
            response(
                RemoteModel(id = "ok", name = "OK"),
                RemoteModel(id = "", name = "缺 id"),
                RemoteModel(id = "no-name", name = "  ")
            )
        )
        assertEquals(listOf("ok"), parsed.getValue("OPENAI").map { it.id })
    }

    @Test
    fun dropsProviderWithNoValidModels() {
        val parsed = ModelCatalogParser.parse(
            ModelCatalogResponse(providers = mapOf("OPENAI" to listOf(RemoteModel(id = "", name = ""))))
        )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun emptyThinkingLevels_meansNoThinking() {
        assertEquals(emptyList<ThinkingLevel>(), ModelCatalogParser.parseThinkingLevels(emptyList()))
    }

    @Test
    fun unknownThinkingLevels_fallBackToProviderDefault() {
        // 全是无法识别的值时按“未下发”处理，避免把思考能力误关掉
        assertNull(ModelCatalogParser.parseThinkingLevels(listOf("TURBO", "ULTRA")))
    }

    @Test
    fun thinkingLevels_areCaseInsensitiveAndSkipUnknown() {
        assertEquals(
            listOf(ThinkingLevel.OFF, ThinkingLevel.HIGH),
            ModelCatalogParser.parseThinkingLevels(listOf("off", "HIGH", "TURBO"))
        )
    }

    @Test
    fun nullProviders_produceEmptyMap() {
        assertTrue(ModelCatalogParser.parse(ModelCatalogResponse()).isEmpty())
    }
}

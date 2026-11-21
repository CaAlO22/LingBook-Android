package com.lingji.app.domain.provider

import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.bailian.BailianConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型配置的默认思考档位策略：
 * 默认开启思考；有强度档位时优先 HIGH，没有 HIGH 取第二高；只支持开关的取 ON；
 * 不支持思考（空档位）才回落到 OFF。
 */
class ThinkingLevelDefaultsTest {

    @Test
    fun `provider defaults turn thinking on at high`() {
        val expected = mapOf(
            APIProvider.OPENAI to ThinkingLevel.HIGH,
            APIProvider.DEEPSEEK to ThinkingLevel.HIGH,
            APIProvider.DOUBAO to ThinkingLevel.HIGH,
            APIProvider.KIMI to ThinkingLevel.HIGH,
            // 这两家供应商级只声明 OFF/ON（4.x 系列），取 ON
            APIProvider.ZHIPU to ThinkingLevel.ON,
            APIProvider.BAILIAN to ThinkingLevel.ON,
            APIProvider.HUNYUAN to ThinkingLevel.ON,
            APIProvider.XIAOMI to ThinkingLevel.ON
        )
        for ((provider, level) in expected) {
            assertEquals("$provider 默认档位", level, ProviderRegistry.defaultThinkingLevel(provider))
        }
    }

    @Test
    fun `no provider defaults to thinking off`() {
        for (provider in APIProvider.entries) {
            assertTrue(
                "$provider 不应默认关闭思考",
                ProviderRegistry.defaultThinkingLevel(provider) != ThinkingLevel.OFF
            )
        }
    }

    @Test
    fun `new config uses model level default`() {
        // 新建配置时按 defaultModelId 推导：同供应商下模型档位可能不同
        assertEquals(ThinkingLevel.HIGH, ProviderRegistry.defaultThinkingLevel(APIProvider.ZHIPU, "glm-5.3"))
        assertEquals(ThinkingLevel.HIGH, ProviderRegistry.defaultThinkingLevel(APIProvider.BAILIAN, "qwen4-max"))
        assertEquals(ThinkingLevel.ON, ProviderRegistry.defaultThinkingLevel(APIProvider.HUNYUAN, "hy4"))
        assertEquals(ThinkingLevel.ON, ProviderRegistry.defaultThinkingLevel(APIProvider.XIAOMI, "mimo-v2.6-pro"))
        assertEquals(ThinkingLevel.HIGH, ProviderRegistry.defaultThinkingLevel(APIProvider.KIMI, "kimi-k3.1-pro"))
    }

    @Test
    fun `model without HIGH falls back to second highest`() {
        // qwen3.8-max 档位 [OFF, LOW, MEDIUM, MAX]：没有 HIGH，第二高是 MEDIUM
        assertEquals(
            ThinkingLevel.MEDIUM,
            ProviderRegistry.defaultThinkingLevel(APIProvider.BAILIAN, "qwen3.8-max")
        )
    }

    @Test
    fun `always thinking models pick HIGH`() {
        // GLM-5.3 不可关闭思考，档位 LOW/HIGH/MAX
        assertEquals(ThinkingLevel.HIGH, ProviderRegistry.defaultThinkingLevel(APIProvider.ZHIPU, "glm-5.3"))
        // Hy3 支持 low/high
        assertEquals(ThinkingLevel.HIGH, ProviderRegistry.defaultThinkingLevel(APIProvider.HUNYUAN, "hy3"))
    }

    @Test
    fun `models without thinking support stay OFF`() {
        assertEquals(
            ThinkingLevel.OFF,
            ProviderRegistry.defaultThinkingLevel(APIProvider.BAILIAN, "qwen-long")
        )
        assertEquals(
            emptyList<ThinkingLevel>(),
            BailianConfig.thinkingLevelsFor("qwen-long")
        )
    }

    @Test
    fun `selection rule prefers HIGH then second highest`() {
        assertEquals(
            ThinkingLevel.HIGH,
            listOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH, ThinkingLevel.MAX)
                .defaultThinkingSelection()
        )
        assertEquals(
            ThinkingLevel.MEDIUM,
            listOf(ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.MAX).defaultThinkingSelection()
        )
        assertEquals(
            ThinkingLevel.ON,
            listOf(ThinkingLevel.OFF, ThinkingLevel.ON).defaultThinkingSelection()
        )
        assertEquals(ThinkingLevel.MAX, listOf(ThinkingLevel.MAX).defaultThinkingSelection())
        assertEquals(ThinkingLevel.OFF, listOf(ThinkingLevel.OFF).defaultThinkingSelection())
        assertEquals(ThinkingLevel.OFF, emptyList<ThinkingLevel>().defaultThinkingSelection())
        // 列表顺序不影响结果
        assertEquals(
            ThinkingLevel.HIGH,
            listOf(ThinkingLevel.HIGH, ThinkingLevel.LOW, ThinkingLevel.MAX).defaultThinkingSelection()
        )
    }

    @Test
    fun `every model default is a supported level`() {
        for (provider in APIProvider.entries) {
            val cfg = ProviderRegistry.config(provider)
            for (model in cfg.models) {
                val levels = cfg.thinkingLevelsFor(model.id)
                if (levels.isEmpty()) continue
                val chosen = ProviderRegistry.defaultThinkingLevel(provider, model.id)
                assertTrue("$provider/${model.id} 默认档位 $chosen 不在 $levels 内", chosen in levels)
            }
        }
    }
}

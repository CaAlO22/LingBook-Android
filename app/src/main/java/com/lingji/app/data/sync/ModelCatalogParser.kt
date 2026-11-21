package com.lingji.app.data.sync

import com.lingji.app.data.remote.sync.ModelCatalogResponse
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderModel

/**
 * 云端模型目录的解析规则（纯函数，便于单测）。
 *
 * 服务端只负责存储与下发，宽松校验放在这里：
 * 单条记录缺 id / name 时跳过该条，不影响同一供应商的其它模型；
 * 供应商列表为空时整条丢弃，让调用方回落到内置列表。
 */
object ModelCatalogParser {

    fun parse(response: ModelCatalogResponse): Map<String, List<ProviderModel>> =
        response.providers.orEmpty().mapValues { (_, models) ->
            models.mapNotNull { model ->
                val id = model.id?.trim().orEmpty()
                val name = model.name?.trim().orEmpty()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                ProviderModel(
                    id = id,
                    name = name,
                    description = model.description.orEmpty(),
                    supportsVision = model.supportsVision == true,
                    thinkingLevels = parseThinkingLevels(model.thinkingLevels)
                )
            }
        }.filterValues { it.isNotEmpty() }

    /**
     * 解析思考档位。
     *
     * - 字段缺失（null）：沿用供应商默认档位；
     * - 空数组：该模型不支持思考；
     * - 全是无法识别的字符串：当成缺失处理，避免把思考能力误关掉。
     */
    fun parseThinkingLevels(raw: List<String>?): List<ThinkingLevel>? {
        if (raw == null) return null
        val parsed = raw.mapNotNull { name ->
            ThinkingLevel.entries.find { it.name.equals(name.trim(), ignoreCase = true) }
        }
        return if (raw.isNotEmpty() && parsed.isEmpty()) null else parsed
    }
}

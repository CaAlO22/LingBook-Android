package com.lingji.app.domain.provider

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 云端下发的模型目录快照（进程内）。
 *
 * 模型列表不再写死在各自的 `XxxConfig` 里：服务器 `model_catalog` 表是唯一可维护来源，
 * 客户端同步拉取后写进这里，[ProviderRegistry] 优先使用这里的列表，取不到再回落到内置列表。
 *
 * 用 Compose 的 `mutableStateOf` 持有：设置页的模型下拉直接读它，目录一更新就会重组，
 * 不需要额外的通知机制；非 Compose 调用方（请求策略、视觉能力判断）读到的只是当前值。
 */
object RemoteModelCatalog {

    /** 当前生效的目录。 */
    data class Snapshot(
        val version: Int = 0,
        val updatedAt: Long = 0L,
        /** key 为 `APIProvider` 枚举名（OPENAI / DOUBAO / ...）。 */
        val models: Map<String, List<ProviderModel>> = emptyMap()
    )

    var snapshot: Snapshot by mutableStateOf(Snapshot())
        private set

    /** 某个供应商的云端模型列表；没下发过（或下发为空）时返回 null，由调用方回落到内置列表。 */
    fun modelsFor(providerId: String): List<ProviderModel>? =
        snapshot.models[providerId]?.takeIf { it.isNotEmpty() }

    fun apply(models: Map<String, List<ProviderModel>>, version: Int, updatedAt: Long) {
        snapshot = Snapshot(
            version = version,
            updatedAt = updatedAt,
            // 空列表等同于「未下发」，留着会让下拉框变空
            models = models.filterValues { it.isNotEmpty() }
        )
    }

    /** 清空（目前只在测试与「重置目录」时使用）。 */
    fun clear() {
        snapshot = Snapshot()
    }
}

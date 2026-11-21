package com.lingji.app.data.remote.sync

/**
 * `/api/model_catalog` 的响应。
 *
 * 字段一律可空：Gson 不执行 Kotlin 默认参数，服务器少给某个字段时得到的是 null 而不是默认值，
 * 由调用方（[com.lingji.app.data.sync.ModelCatalogRepository]）统一做兜底。
 */
data class ModelCatalogResponse(
    /** 版本与请求里带的一致，服务端没有重发目录。 */
    val unchanged: Boolean? = null,
    val catalogVersion: Int? = null,
    /** 服务端最后一次修改目录的时间（毫秒）。 */
    val updatedAt: Long? = null,
    /** key 为供应商枚举名（OPENAI / DOUBAO / ...）。 */
    val providers: Map<String, List<RemoteModel>>? = null
)

/** 目录里的一个模型。 */
data class RemoteModel(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val supportsVision: Boolean? = null,
    /**
     * 该模型可选的思考档位（`OFF` / `ON` / `LOW` / `MEDIUM` / `HIGH` / `MAX`）。
     *
     * - `null`（服务器不下发该字段）：沿用供应商默认档位
     * - 空列表：该模型不支持思考
     */
    val thinkingLevels: List<String>? = null
)

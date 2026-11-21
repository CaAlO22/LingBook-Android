package com.lingji.app.data.sync

/**
 * 云同步协议版本。
 *
 * 服务器对同步表维护一份**硬编码白名单**（`TABLES`）：App 新增一张表或改变字段语义后，
 * 旧服务器会把新数据静默丢弃 —— 同步显示成功，换设备却发现数据没了。
 * 因此两端各有一个版本号，同步前先比对：
 *
 *  - `server < client`：**客户端拒绝本轮同步**，一个字节都不上传，设置页提示升级服务器；
 *  - `server >= client`：正常同步（旧客户端不认识的新字段本来就会忽略）。
 *
 * 何时 +1：只在**同步载荷结构变化**时（新增/删除同步表、改字段含义、改合并规则）。
 * 纯 UI 改动、客户端内部重构、新增可选接口（如模型目录）都不动它。
 */
object SyncProtocol {

    /** 本端同步数据结构版本。 */
    const val SCHEMA_VERSION = 1

    /**
     * 加版本号之前的协议按 1 处理。
     *
     * 这样本次升级不会让已在运行的旧服务器立刻失联；只有将来真正改了数据结构、
     * 客户端把 [SCHEMA_VERSION] 提到 2 时，才会拒绝这些旧服务器。
     */
    const val LEGACY_SCHEMA_VERSION = 1

    /** 服务端上报的版本号归一化：缺失 / 非法值按 [LEGACY_SCHEMA_VERSION] 处理。 */
    fun normalizeServerVersion(raw: Int?): Int =
        raw?.takeIf { it > 0 } ?: LEGACY_SCHEMA_VERSION

    /** 服务端版本是否满足本端要求。 */
    fun isServerCompatible(serverVersion: Int): Boolean = serverVersion >= SCHEMA_VERSION
}

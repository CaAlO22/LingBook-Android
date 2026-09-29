package com.lingji.app.data.db.entities

import androidx.room.Entity

/**
 * 云同步删除墓碑：记录本地被删除实体的 (表名, id, 删除时间)，
 * 用于把删除操作传播到服务器与其他设备。
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["tbl", "id"])
data class SyncTombstoneEntity(
    val tbl: String,
    val id: String,
    val deletedAt: Long
)

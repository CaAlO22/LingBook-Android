package com.lingji.app.data.edit

import kotlin.coroutines.CoroutineContext

/**
 * 协程上下文元素：标记当前协程处于一次 agent 编辑会话中。
 *
 * [com.lingji.app.data.repository.SubjectRepository] 的 update 方法会读取此元素，
 * 将修改归入同一 batch，支持按会话整体撤销。
 * 用法：`withContext(EditBatch(UUID.randomUUID().toString())) { ... }`
 */
class EditBatch(val batchId: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<EditBatch>
    override val key: CoroutineContext.Key<*> = Key
}

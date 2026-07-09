package com.lingji.app.domain.tool.image

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话级图片存储：将用户在当前对话中发送的图片按累积顺序编号（1, 2, 3...）。
 * 单用户 App，同时只有一个活跃对话，用单例即可。
 * 新对话或切换对话时调用 clear() 重置编号。
 */
@Singleton
class ConversationImageStore @Inject constructor() {
    private val images = mutableMapOf<Int, String>()
    private var nextNumber = 1

    /**
     * 添加图片，返回分配的编号列表。
     * 编号在对话内累积递增，不会因新消息而重置。
     */
    @Synchronized
    fun addImages(uris: List<String>): List<Int> {
        if (uris.isEmpty()) return emptyList()
        val numbers = mutableListOf<Int>()
        for (uri in uris) {
            images[nextNumber] = uri
            numbers.add(nextNumber)
            nextNumber++
        }
        return numbers
    }

    /** 根据编号获取图片的 base64 data URI。 */
    @Synchronized
    fun getImage(number: Int): String? = images[number]

    /** 当前对话中已存储的图片数量。 */
    @Synchronized
    fun getImageCount(): Int = images.size

    /** 清空所有图片，重置编号。在新建/切换对话时调用。 */
    @Synchronized
    fun clear() {
        images.clear()
        nextNumber = 1
    }
}

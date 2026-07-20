package com.lingji.app.ui.viewmodel

import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.HomeItem
import com.lingji.app.domain.model.Subject
import com.lingji.app.ui.components.ChatMode

data class HomeChatMessage(
    val role: String,
    val content: String,
    val toolCallsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class HomeFragmentData(
    val content: String,
    val timestamp: Long? = null
)

data class SubjectUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: String? = null,
    val settings: AISettings = AISettings(),
    val isProcessing: Boolean = false,
    val processingMessage: String? = null,
    val processingStreamLine: String = "",
    val isSettingsOpen: Boolean = false,
    val processingLastUpdate: Long? = null,
    val aiErrorMessage: String? = null,
    val aiWarningMessage: String? = null,
    /** 模型思考内容（仅用于 AI 运行岛展示，不写入最终生成内容）。 */
    val aiIslandReasoning: String = "",
    /** 供 AI 运行岛展示的最新文本行（按换行拆分），包含普通输出与思考内容。 */
    val aiIslandLines: List<AiIslandLine> = emptyList(),
    /** 各碎片笔记与 AI 的对话历史，按 subjectId 索引；退出笔记后仍保留。 */
    val noteChatHistories: Map<String, List<Pair<String, String>>> = emptyMap(),
    // Home chat state
    val homeChatExpanded: Boolean = false,
    val homeChatMode: ChatMode = ChatMode.FRAGMENT,
    val homeCurrentConversationId: String? = null,
    val homeConversations: List<HomeConversationEntity> = emptyList(),
    val homeMessages: List<HomeChatMessage> = emptyList(),
    val homeStreamLine: String = "",
    val homeIsLoading: Boolean = false,
    /** 碎片输入模式下收集的碎片列表。 */
    val homeFragments: List<HomeFragmentData> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val currentFolderId: String? = null,
) {
    val currentSubject: Subject? get() = subjects.find { it.id == currentSubjectId }

    /**
     * 首页展示项：合并文件夹与无文件夹归属的碎片笔记，按 orderIndex 降序排列。
     */
    val homeItems: List<HomeItem>
        get() {
            val folderItems = folders.map { folder ->
                HomeItem.FolderItem(folder, subjects.count { it.folderId == folder.id })
            }
            val noteItems = subjects.filter { it.folderId == null }.map { HomeItem.NoteItem(it) }
            return (folderItems + noteItems).sortedByDescending { item ->
                when (item) {
                    is HomeItem.FolderItem -> item.folder.orderIndex
                    is HomeItem.NoteItem -> item.subject.orderIndex
                }
            }
        }
}

/**
 * AI 运行岛展示的一行文本。
 *
 * @param text 行文本。
 * @param isReasoning 是否为模型思考内容；思考内容只在岛上展示，不会写入最终生成结果。
 */
data class AiIslandLine(
    val text: String,
    val isReasoning: Boolean = false
)

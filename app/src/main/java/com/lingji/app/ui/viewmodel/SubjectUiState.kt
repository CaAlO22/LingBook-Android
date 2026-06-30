package com.lingji.app.ui.viewmodel

import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.Subject

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
    val noteChatHistories: Map<String, List<Pair<String, String>>> = emptyMap()
) {
    val currentSubject: Subject? get() = subjects.find { it.id == currentSubjectId }
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

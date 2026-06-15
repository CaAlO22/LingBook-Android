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
    val processingLastUpdate: Long? = null
) {
    val currentSubject: Subject? get() = subjects.find { it.id == currentSubjectId }
}

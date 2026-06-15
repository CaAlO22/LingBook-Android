package com.lingji.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingji.app.data.file.FileManager
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndex
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.SearchResult
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.generateId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SubjectViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val llmService: LLMService,
    private val indexService: IndexService,
    private val fileManager: FileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectUiState())
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    init {
        combine(
            subjectRepository.getAllSubjects(),
            settingsRepository.getSettings()
        ) { subjects, settings ->
            _uiState.update {
                it.copy(
                    subjects = subjects,
                    settings = settings,
                    isSettingsOpen = it.isSettingsOpen || !settings.isConfigured()
                )
            }
        }.launchIn(viewModelScope)
    }

    fun openSettings() = _uiState.update { it.copy(isSettingsOpen = true) }
    fun closeSettings() = _uiState.update { it.copy(isSettingsOpen = false) }

    fun setCurrentSubject(id: String?) = _uiState.update { it.copy(currentSubjectId = id) }

    fun addSubject(title: String, type: SubjectType) {
        viewModelScope.launch {
            val subject = Subject.create(title, type)
            subjectRepository.insert(subject)
            _uiState.update { it.copy(currentSubjectId = subject.id) }
        }
    }

    fun importSubject(subject: Subject) {
        viewModelScope.launch {
            val imported = subject.copy(id = generateId())
            subjectRepository.insert(imported)
            _uiState.update { it.copy(currentSubjectId = imported.id) }
        }
    }

    fun deleteSubject(id: String) {
        viewModelScope.launch {
            subjectRepository.delete(id)
            if (_uiState.value.currentSubjectId == id) {
                _uiState.update { it.copy(currentSubjectId = null) }
            }
        }
    }

    fun moveSubjectToTop(id: String) {
        val subjects = _uiState.value.subjects
        if (subjects.size <= 1) return
        val maxOrder = subjects.maxOf { it.orderIndex }
        val target = subjects.find { it.id == id } ?: return
        if (target.orderIndex == maxOrder) return
        viewModelScope.launch { subjectRepository.moveSubject(id, maxOrder + 1) }
    }

    fun moveSubjectUp(id: String) {
        val subjects = _uiState.value.subjects
        val index = subjects.indexOfFirst { it.id == id }
        if (index <= 0) return
        val previous = subjects[index - 1]
        viewModelScope.launch { subjectRepository.moveSubject(id, previous.orderIndex + 1) }
    }

    fun moveSubjectDown(id: String) {
        val subjects = _uiState.value.subjects
        val index = subjects.indexOfFirst { it.id == id }
        if (index < 0 || index >= subjects.lastIndex) return
        val next = subjects[index + 1]
        viewModelScope.launch { subjectRepository.moveSubject(id, next.orderIndex - 1) }
    }

    fun moveSubjectToBottom(id: String) {
        val subjects = _uiState.value.subjects
        if (subjects.size <= 1) return
        val minOrder = subjects.minOf { it.orderIndex }
        val target = subjects.find { it.id == id } ?: return
        if (target.orderIndex == minOrder) return
        viewModelScope.launch { subjectRepository.moveSubject(id, minOrder - 1) }
    }

    fun renameSubject(id: String, title: String) {
        viewModelScope.launch { subjectRepository.rename(id, title) }
    }

    fun addFragment(content: String) {
        val subjectId = _uiState.value.currentSubjectId ?: return
        viewModelScope.launch {
            subjectRepository.addFragment(subjectId, Fragment(content = content))
        }
    }

    fun updateFragment(subjectId: String, fragmentId: String, content: String) {
        viewModelScope.launch { subjectRepository.updateFragment(subjectId, fragmentId, content) }
    }

    fun deleteFragment(subjectId: String, fragmentId: String) {
        viewModelScope.launch { subjectRepository.deleteFragment(subjectId, fragmentId) }
    }

    fun addPage(
        subjectId: String,
        title: String = "",
        content: String = "",
        onAdded: ((NotebookPage) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val page = NotebookPage(title = title, content = content)
            subjectRepository.addPage(subjectId, page)
            onAdded?.invoke(page)
        }
    }

    fun updatePage(subjectId: String, page: NotebookPage) {
        viewModelScope.launch {
            subjectRepository.updatePage(subjectId, page.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deletePage(subjectId: String, pageId: String) {
        viewModelScope.launch { subjectRepository.deletePage(subjectId, pageId) }
    }

    fun updatePageIndex(subjectId: String, pageIndex: List<PageIndex>) {
        // Reorder handled by updating order in page entities; keep simple here.
    }

    fun buildPageIndexes(
        subject: Subject,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
        onComplete: (List<PageIndexEntry>, List<String>) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        if (_uiState.value.isProcessing) return
        val pages = subject.pages ?: emptyList()
        if (pages.isEmpty()) return
        viewModelScope.launch {
            setProcessing(true, "正在构建页面索引…")
            try {
                val (entries, indexedIds) = indexService.batchBuildIndexesForDirtyPages(
                    pages,
                    _uiState.value.settings,
                    onProgress
                )
                subjectRepository.markPagesIndexed(subject.id, indexedIds, System.currentTimeMillis())
                subjectRepository.savePageIndexEntries(subject.id, entries)
                onComplete(entries, indexedIds)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.message ?: "构建索引失败")
            } finally {
                setProcessing(false)
            }
        }
    }

    fun chatWithPage(
        page: NotebookPage,
        question: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        chatWithContent(
            content = page.content,
            question = question,
            onToken = onToken,
            onComplete = onComplete,
            onError = onError
        )
    }

    fun chatWithNote(
        subject: Subject,
        question: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        chatWithContent(
            content = subject.aggregatedNote,
            question = question,
            onToken = onToken,
            onComplete = onComplete,
            onError = onError
        )
    }

    private fun chatWithContent(
        content: String,
        question: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            setProcessing(true, "正在思考…")
            try {
                val images = IndexService.extractImagesFromContent(content)
                val answer = llmService.chatWithPage(
                    content,
                    question,
                    _uiState.value.settings,
                    onToken,
                    images
                )
                onComplete(answer)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.message ?: "请求失败")
            } finally {
                setProcessing(false)
            }
        }
    }

    fun searchPages(query: String, subject: Subject): List<SearchResult> {
        val pages = subject.pages ?: emptyList()
        val indexes = subject.pageIndexEntries ?: emptyList()
        return indexService.searchPages(query, pages, indexes)
    }

    fun updateAggregatedNote(content: String) {
        val subjectId = _uiState.value.currentSubjectId ?: return
        viewModelScope.launch { subjectRepository.updateAggregatedNote(subjectId, content) }
    }

    fun rollbackAggregatedNote() {
        val subjectId = _uiState.value.currentSubjectId ?: return
        viewModelScope.launch { subjectRepository.rollbackAggregatedNote(subjectId) }
    }

    fun updateStudyPlan(content: String) {
        val subjectId = _uiState.value.currentSubjectId ?: return
        viewModelScope.launch { subjectRepository.updateStudyPlan(subjectId, content) }
    }

    fun organize(subject: Subject, hint: String? = null) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            setProcessing(true, if (subject.unmergedFragments.isNotEmpty()) "正在整理并合并新碎片" else "正在基于全量碎片重构笔记")
            try {
                val updated = if (subject.unmergedFragments.isNotEmpty()) {
                    val fragmentsToMerge = subject.unmergedFragments
                    val note = llmService.mergeFragment(
                        subject.aggregatedNote,
                        fragmentsToMerge,
                        _uiState.value.settings,
                        hint
                    ) { token -> appendStream(token) }
                    subjectRepository.updateAggregatedNote(subject.id, note)
                    subjectRepository.completeBatchMerge(subject.id, fragmentsToMerge.map { it.id })
                    note
                } else {
                    llmService.refineNote(
                        subject.fragments,
                        subject.aggregatedNote,
                        _uiState.value.settings,
                        hint
                    ) { token -> appendStream(token) }
                }
                subjectRepository.updateAggregatedNote(subject.id, updated)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                setProcessing(false)
            }
        }
    }

    fun refine(subject: Subject, hint: String? = null) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            setProcessing(true, "正在基于全量碎片重构笔记")
            try {
                val note = llmService.refineNote(
                    subject.fragments,
                    subject.aggregatedNote,
                    _uiState.value.settings,
                    hint
                ) { token -> appendStream(token) }
                subjectRepository.updateAggregatedNote(subject.id, note)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                setProcessing(false)
            }
        }
    }

    fun generatePlan(subject: Subject, deadline: String? = null) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            setProcessing(true, "正在为您生成专属学习计划")
            try {
                val plan = llmService.generateStudyPlan(
                    subject.aggregatedNote,
                    _uiState.value.settings,
                    deadline
                ) { token -> appendStream(token) }
                subjectRepository.updateStudyPlan(subject.id, plan)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                setProcessing(false)
            }
        }
    }

    fun saveSettings(settings: AISettings) {
        viewModelScope.launch { settingsRepository.save(settings) }
    }

    fun testConnection(onResult: (Pair<Boolean, String>) -> Unit) {
        viewModelScope.launch {
            val result = llmService.testConnection(_uiState.value.settings)
            onResult(result)
        }
    }

    fun testMultimodalConnection(imageBase64: String, onResult: (Pair<Boolean, String>) -> Unit) {
        viewModelScope.launch {
            val result = llmService.testMultimodalConnection(_uiState.value.settings, imageBase64)
            onResult(result)
        }
    }

    suspend fun importSubject(uri: Uri): Boolean {
        val imported = fileManager.importSubject(uri) ?: return false
        importSubject(imported)
        return true
    }

    suspend fun importSubject(text: String): Boolean {
        val imported = fileManager.importSubjectFromText(text) ?: return false
        importSubject(imported)
        return true
    }

    suspend fun exportSubject(subject: Subject, uri: Uri) {
        fileManager.exportSubject(subject, uri)
    }

    suspend fun exportSubjectToText(subject: Subject): String = withContext(Dispatchers.IO) {
        fileManager.encodeSubject(subject)
    }

    private fun setProcessing(loading: Boolean, message: String? = null) {
        _uiState.update {
            it.copy(
                isProcessing = loading,
                processingMessage = if (loading) message else null,
                processingStreamLine = if (loading) "" else it.processingStreamLine,
                processingLastUpdate = if (loading) System.currentTimeMillis() else null
            )
        }
    }

    private fun appendStream(token: String) {
        _uiState.update {
            it.copy(
                processingStreamLine = it.processingStreamLine + token,
                processingLastUpdate = System.currentTimeMillis()
            )
        }
    }

    private fun AISettings.isConfigured(): Boolean {
        return apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()
    }
}

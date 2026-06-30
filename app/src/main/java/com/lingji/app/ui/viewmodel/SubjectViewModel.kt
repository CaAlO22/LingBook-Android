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
import com.lingji.app.ui.viewmodel.AiIslandLine
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
    fun clearAiError() = _uiState.update { it.copy(aiErrorMessage = null) }
    fun clearAiWarning() = _uiState.update { it.copy(aiWarningMessage = null) }

    private fun ensureAiConfigured(): Boolean {
        if (_uiState.value.settings.isConfigured()) return true
        _uiState.update {
            it.copy(aiErrorMessage = "AI 未配置：请先填写 Base URL、API Key 和模型名称")
        }
        return false
    }

    fun setCurrentSubject(id: String?) = _uiState.update { it.copy(currentSubjectId = id) }

    fun saveLastOpenedPageId(subjectId: String, pageId: String?) {
        viewModelScope.launch {
            subjectRepository.updateLastOpenedPageId(subjectId, pageId)
        }
    }

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

    fun movePage(subjectId: String, pageId: String, newIndex: Int) {
        viewModelScope.launch { subjectRepository.movePage(subjectId, pageId, newIndex) }
    }

    fun updatePageIndex(subjectId: String, pageIndex: List<PageIndex>) {
        // Reorder handled by updating order in page entities; keep simple here.
    }

    fun updatePageIndexEntry(subjectId: String, pageId: String, entry: PageIndexEntry) {
        viewModelScope.launch {
            subjectRepository.updatePageIndexEntry(subjectId, pageId, entry)
        }
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
                    onProgress = { done, total, page ->
                        val title = page.title.takeIf { it.isNotBlank() } ?: "未命名页面"
                        _uiState.update {
                            it.copy(
                                processingStreamLine = "",
                                aiIslandReasoning = "正在分析页面 $done / $total：$title",
                                aiIslandLines = buildIslandLines(
                                    reasoning = "正在分析页面 $done / $total：$title",
                                    content = ""
                                ),
                                processingLastUpdate = System.currentTimeMillis()
                            )
                        }
                        onProgress?.invoke(done, total)
                    },
                    onToken = { token -> appendStream(token) },
                    onReasoning = { token -> appendReasoning(token) },
                    onWarning = { warning ->
                        _uiState.update { state ->
                            if (state.aiWarningMessage == null) state.copy(aiWarningMessage = warning) else state
                        }
                    }
                )
                subjectRepository.markPagesIndexed(subject.id, indexedIds, System.currentTimeMillis())
                subjectRepository.savePageIndexEntries(subject.id, entries)
                onComplete(entries, indexedIds)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "构建索引失败") }
                onError(e.message ?: "构建索引失败")
            } finally {
                setProcessing(false)
            }
        }
    }

    fun buildDirectory(
        subject: Subject,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_uiState.value.isProcessing) return
        val pages = subject.pages ?: emptyList()
        if (pages.isEmpty()) return
        viewModelScope.launch {
            setProcessing(true, "正在构建目录…")
            try {
                // Step 1: Build indexes for all dirty pages
                _uiState.update {
                    it.copy(
                        processingStreamLine = "",
                        aiIslandReasoning = "正在构建页面索引…",
                        aiIslandLines = buildIslandLines(reasoning = "正在构建页面索引…", content = ""),
                        processingLastUpdate = System.currentTimeMillis()
                    )
                }
                val (newEntries, indexedIds) = indexService.batchBuildIndexesForDirtyPages(
                    pages,
                    _uiState.value.settings,
                    onProgress = { done, total, page ->
                        val title = page.title.takeIf { it.isNotBlank() } ?: "未命名页面"
                        _uiState.update {
                            it.copy(
                                processingStreamLine = "",
                                aiIslandReasoning = "正在分析页面 $done / $total：$title",
                                aiIslandLines = buildIslandLines(
                                    reasoning = "正在分析页面 $done / $total：$title",
                                    content = ""
                                ),
                                processingLastUpdate = System.currentTimeMillis()
                            )
                        }
                    },
                    onToken = { token -> appendStream(token) },
                    onReasoning = { token -> appendReasoning(token) },
                    onWarning = { warning ->
                        _uiState.update { state ->
                            if (state.aiWarningMessage == null) state.copy(aiWarningMessage = warning) else state
                        }
                    }
                )
                subjectRepository.markPagesIndexed(subject.id, indexedIds, System.currentTimeMillis())
                subjectRepository.savePageIndexEntries(subject.id, newEntries)

                // Step 2: Merge all index entries (existing + newly built)
                val allEntries = (subject.pageIndexEntries ?: emptyList()) + newEntries

                // Step 3: Generate directory
                _uiState.update {
                    it.copy(
                        processingStreamLine = "",
                        aiIslandReasoning = "正在生成目录…",
                        aiIslandLines = buildIslandLines(reasoning = "正在生成目录…", content = ""),
                        processingLastUpdate = System.currentTimeMillis()
                    )
                }
                val directory = indexService.generateDirectory(
                    entries = allEntries,
                    pages = pages,
                    settings = _uiState.value.settings,
                    onToken = { token -> appendStream(token) },
                    onReasoning = { token -> appendReasoning(token) },
                    onWarning = { warning ->
                        _uiState.update { state ->
                            if (state.aiWarningMessage == null) state.copy(aiWarningMessage = warning) else state
                        }
                    }
                )

                // Step 4: Insert directory page as first page
                val directoryPage = NotebookPage(
                    title = "目录",
                    content = directory
                )
                subjectRepository.insertPageAt(subject.id, directoryPage, 0)

                onComplete(directory)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "构建目录失败") }
                onError(e.message ?: "构建目录失败")
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
        onError: (String) -> Unit = {},
        conversationHistory: List<Pair<String, String>> = emptyList()
    ) {
        chatWithContent(
            content = page.content,
            question = question,
            pageTitle = page.title,
            onToken = onToken,
            onComplete = onComplete,
            onError = onError,
            conversationHistory = conversationHistory
        )
    }

    fun chatWithNote(
        subject: Subject,
        question: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        conversationHistory: List<Pair<String, String>> = emptyList()
    ) {
        chatWithContent(
            content = subject.aggregatedNote,
            question = question,
            pageTitle = subject.title,
            onToken = onToken,
            onComplete = onComplete,
            onError = onError,
            conversationHistory = conversationHistory
        )
    }

    private fun chatWithContent(
        content: String,
        question: String,
        pageTitle: String = "",
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        conversationHistory: List<Pair<String, String>> = emptyList()
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
                    onToken = { token ->
                        appendStream(token)
                        onToken(token)
                    },
                    onReasoning = { token -> appendReasoning(token) },
                    images = images,
                    onWarning = { warning ->
                        _uiState.update { state ->
                            if (state.aiWarningMessage == null) state.copy(aiWarningMessage = warning) else state
                        }
                    },
                    conversationHistory = conversationHistory,
                    pageTitle = pageTitle
                )
                onComplete(answer)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "请求失败") }
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

    fun updateNoteChatHistory(subjectId: String, history: List<Pair<String, String>>) {
        _uiState.update { it.copy(noteChatHistories = it.noteChatHistories + (subjectId to history)) }
    }

    fun clearNoteChatHistory(subjectId: String) {
        _uiState.update { it.copy(noteChatHistories = it.noteChatHistories - subjectId) }
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
        if (!ensureAiConfigured()) return
        viewModelScope.launch {
            setProcessing(true, if (subject.unmergedFragments.isNotEmpty()) "正在整理并合并新碎片" else "正在基于全量碎片重构笔记")
            try {
                val updated = if (subject.unmergedFragments.isNotEmpty()) {
                    val fragmentsToMerge = subject.unmergedFragments
                    val note = llmService.mergeFragment(
                        subject.aggregatedNote,
                        fragmentsToMerge,
                        _uiState.value.settings,
                        hint,
                        onToken = { token -> appendStream(token) },
                        onReasoning = { token -> appendReasoning(token) }
                    )
                    subjectRepository.updateAggregatedNote(subject.id, note)
                    subjectRepository.completeBatchMerge(subject.id, fragmentsToMerge.map { it.id })
                    note
                } else {
                    llmService.refineNote(
                        subject.fragments,
                        subject.aggregatedNote,
                        _uiState.value.settings,
                        hint,
                        onToken = { token -> appendStream(token) },
                        onReasoning = { token -> appendReasoning(token) }
                    )
                }
                subjectRepository.updateAggregatedNote(subject.id, updated)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "整理失败") }
            } finally {
                setProcessing(false)
            }
        }
    }

    fun refine(subject: Subject, hint: String? = null) {
        if (_uiState.value.isProcessing) return
        if (!ensureAiConfigured()) return
        viewModelScope.launch {
            setProcessing(true, "正在基于全量碎片重构笔记")
            try {
                val note = llmService.refineNote(
                    subject.fragments,
                    subject.aggregatedNote,
                    _uiState.value.settings,
                    hint,
                    onToken = { token -> appendStream(token) },
                    onReasoning = { token -> appendReasoning(token) }
                )
                subjectRepository.updateAggregatedNote(subject.id, note)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "重构失败") }
            } finally {
                setProcessing(false)
            }
        }
    }

    fun generatePlan(subject: Subject, deadline: String? = null) {
        if (_uiState.value.isProcessing) return
        if (!ensureAiConfigured()) return
        viewModelScope.launch {
            setProcessing(true, "正在为您生成专属学习计划")
            try {
                val plan = llmService.generateStudyPlan(
                    subject.aggregatedNote,
                    _uiState.value.settings,
                    deadline,
                    onToken = { token -> appendStream(token) },
                    onReasoning = { token -> appendReasoning(token) }
                )
                subjectRepository.updateStudyPlan(subject.id, plan)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "生成计划失败") }
            } finally {
                setProcessing(false)
            }
        }
    }

    fun saveSettings(settings: AISettings) {
        viewModelScope.launch { settingsRepository.save(settings) }
    }

    fun testConnection(onResult: (Pair<Boolean, String>) -> Unit) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            setProcessing(true, "正在测试连接…")
            try {
                val result = llmService.testConnection(_uiState.value.settings)
                onResult(result)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "测试失败") }
                onResult(false to (e.message ?: "测试失败"))
            } finally {
                setProcessing(false)
            }
        }
    }

    fun testMultimodalConnection(imageBase64: String, onResult: (Pair<Boolean, String>) -> Unit) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            setProcessing(true, "正在测试多模态连接…")
            try {
                val result = llmService.testMultimodalConnection(_uiState.value.settings, imageBase64)
                onResult(result)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "多模态测试失败") }
                onResult(false to (e.message ?: "多模态测试失败"))
            } finally {
                setProcessing(false)
            }
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

    fun buildExportFileName(title: String): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").takeIf { it.isNotBlank() } ?: "notebook"
        val time = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        return "${safe}_$time.ling"
    }

    private fun setProcessing(loading: Boolean, message: String? = null) {
        _uiState.update {
            it.copy(
                isProcessing = loading,
                processingMessage = if (loading) message else null,
                processingStreamLine = if (loading) "" else it.processingStreamLine,
                aiIslandReasoning = if (loading) "" else it.aiIslandReasoning,
                aiIslandLines = if (loading) emptyList() else it.aiIslandLines,
                processingLastUpdate = if (loading) System.currentTimeMillis() else null
            )
        }
    }

    private fun appendStream(token: String) {
        _uiState.update {
            val newText = it.processingStreamLine + token
            it.copy(
                processingStreamLine = newText,
                aiIslandLines = buildIslandLines(
                    reasoning = it.aiIslandReasoning,
                    content = newText
                ),
                processingLastUpdate = System.currentTimeMillis()
            )
        }
    }

    private fun appendReasoning(token: String) {
        _uiState.update {
            val newText = it.aiIslandReasoning + token
            it.copy(
                aiIslandReasoning = newText,
                aiIslandLines = buildIslandLines(
                    reasoning = newText,
                    content = it.processingStreamLine
                ),
                processingLastUpdate = System.currentTimeMillis()
            )
        }
    }

    private fun updateIslandProgress(message: String) {
        _uiState.update {
            it.copy(
                processingStreamLine = message,
                aiIslandLines = buildIslandLines(
                    reasoning = it.aiIslandReasoning,
                    content = message
                ),
                processingLastUpdate = System.currentTimeMillis()
            )
        }
    }

    private fun buildIslandLines(reasoning: String, content: String): List<AiIslandLine> {
        val reasoningLines = reasoning.split('\n').map { line -> AiIslandLine(line, isReasoning = true) }
        val contentLines = content.split('\n').map { line -> AiIslandLine(line, isReasoning = false) }
        return reasoningLines + contentLines
    }

    private fun AISettings.isConfigured(): Boolean {
        return apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()
    }
}

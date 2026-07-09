package com.lingji.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.HomeChatDao
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeFragmentEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import com.lingji.app.data.file.FileManager
import com.lingji.app.data.remote.AgentService
import com.lingji.app.data.remote.HomeAgentService
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ToolCall
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.AISettings
import com.lingji.app.ui.viewmodel.AiIslandLine
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.model.HomeItem
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndex
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.SearchResult
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.fullNoteContent
import com.lingji.app.domain.model.generateId
import com.lingji.app.ui.components.ChatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SubjectViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val llmService: LLMService,
    private val agentService: AgentService,
    private val homeAgentService: HomeAgentService,
    private val indexService: IndexService,
    private val fileManager: FileManager,
    private val homeChatDao: HomeChatDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectUiState())
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val homeMessageCache = mutableMapOf<String, List<HomeChatMessage>>()
    private val homeAgentMessageCache = mutableMapOf<String, List<ChatMessage>>()
    private var messagesCollectJob: Job? = null
    private var conversationsCollectJob: Job? = null
    private var fragmentsCollectJob: Job? = null
    private var processingJob: Job? = null

    init {
        combine(
            subjectRepository.getAllSubjects(),
            subjectRepository.getAllFolders(),
            settingsRepository.getSettings()
        ) { subjects, folders, settings ->
            _uiState.update {
                it.copy(
                    subjects = subjects,
                    folders = folders,
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

    fun stopAiProcessing() {
        processingJob?.cancel()
        processingJob = null
        setProcessing(false)
        _uiState.update { it.copy(homeIsLoading = false, homeStreamLine = "") }
    }

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

    fun addSubject(title: String, type: SubjectType, folderId: String? = null) {
        viewModelScope.launch {
            val subject = Subject.create(title, type).copy(folderId = folderId)
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

    fun createFolder(name: String) {
        viewModelScope.launch { subjectRepository.createFolder(name) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch { subjectRepository.deleteFolder(id) }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch { subjectRepository.renameFolder(id, name) }
    }

    fun moveSubjectToFolder(subjectId: String, folderId: String) {
        viewModelScope.launch { subjectRepository.moveSubjectToFolder(subjectId, folderId) }
    }

    fun removeSubjectFromFolder(subjectId: String) {
        viewModelScope.launch { subjectRepository.removeSubjectFromFolder(subjectId) }
    }

    fun reorderHomeItems(orderedItems: List<HomeItem>) {
        viewModelScope.launch { subjectRepository.reorderHomeItems(orderedItems) }
    }

    fun reorderFolderItems(folderId: String, orderedSubjectIds: List<String>) {
        viewModelScope.launch { subjectRepository.reorderFolderItems(folderId, orderedSubjectIds) }
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
            content = subject.fullNoteContent(),
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

    fun chatWithAgent(
        subjectId: String,
        question: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        conversationHistory: List<Pair<String, String>> = emptyList()
    ) {
        if (_uiState.value.isProcessing) return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            setProcessing(true, "Agent 思考中…")
            try {
                agentService.runAgentLoop(
                    subjectId = subjectId,
                    question = question,
                    priorMessages = conversationHistory.flatMap { (q, a) ->
                        listOf(ChatMessage(role = "user", content = q), ChatMessage(role = "assistant", content = a))
                    },
                    onReasoning = { reasoning -> appendReasoning(reasoning) },
            onToolCall = { toolName, args, result ->
                        val display = buildString {
                            append("🔧 调用工具: $toolName\n")
                            if (args.isNotBlank() && args != "{}") append("  参数: $args\n")
                            append("  结果: ${result.take(500)}")
                            if (result.length > 500) append("…")
                        }
                        appendStream(display + "\n\n")
                    },
                    onToken = { token ->
                        appendStream(token)
                        onToken(token)
                    },
                    onComplete = { answer -> onComplete(answer) },
                    onError = { msg ->
                        _uiState.update { it.copy(aiErrorMessage = msg) }
                        onError(msg)
                    },
                    onAssessment = { decision, reason, extraSteps ->
                        val display = if (decision == "CONTINUE") {
                            "🔄 监督者判断任务未完成，追加 $extraSteps 轮工具调用（$reason）\n\n"
                        } else {
                            "⏹️ 监督者判断应终止：$reason\n\n"
                        }
                        appendStream(display)
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(aiErrorMessage = e.message ?: "Agent 请求失败") }
                onError(e.message ?: "Agent 请求失败")
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

    // ───────────────────────── Home Chat ─────────────────────────

    fun toggleHomeChat() {
        val expanding = !_uiState.value.homeChatExpanded
        Log.d("Fragment", "========== toggleHomeChat | expanding=$expanding ==========")
        _uiState.update { it.copy(homeChatExpanded = expanding, homeStreamLine = "") }
        if (expanding) {
            loadHomeFragments()
        }
    }

    private fun loadHomeFragments() {
        Log.d("Fragment", "loadHomeFragments | starting Flow collection")
        fragmentsCollectJob?.cancel()
        fragmentsCollectJob = viewModelScope.launch {
            homeChatDao.getFragments().collect { entities ->
                val contents = entities.map { e -> e.content }
                Log.d("Fragment", "loadHomeFragments -> Flow EMIT | count=${contents.size} contents=$contents")
                _uiState.update { it.copy(homeFragments = contents) }
            }
        }
    }

    fun setHomeChatMode(mode: ChatMode) {
        _uiState.update { it.copy(homeChatMode = mode) }
    }

    fun loadHomeConversations() {
        conversationsCollectJob?.cancel()
        conversationsCollectJob = viewModelScope.launch {
            homeChatDao.getConversations().collect { conversations ->
                _uiState.update { it.copy(homeConversations = conversations) }
            }
        }
    }

    fun startNewConversation() {
        setProcessing(false)
        messagesCollectJob?.cancel()
        val currentId = _uiState.value.homeCurrentConversationId
        val currentMessages = _uiState.value.homeMessages
        if (currentId != null && currentMessages.isNotEmpty()) {
            homeMessageCache[currentId] = currentMessages
        }
        _uiState.update { it.copy(
            homeCurrentConversationId = null,
            homeMessages = emptyList(),
            homeStreamLine = "",
            homeFragments = emptyList(),
            homeIsLoading = false
        ) }
        viewModelScope.launch { homeChatDao.clearAllFragments() }
        homeAgentMessageCache.remove("_current")
    }

    fun loadConversation(id: String) {
        setProcessing(false)
        val currentId = _uiState.value.homeCurrentConversationId
        val currentMessages = _uiState.value.homeMessages
        if (currentId != null && currentMessages.isNotEmpty()) {
            homeMessageCache[currentId] = currentMessages
        }
        homeMessageCache[id]?.let { cached ->
            _uiState.update { it.copy(homeCurrentConversationId = id, homeMessages = cached) }
            return
        }
        messagesCollectJob?.cancel()
        _uiState.update { it.copy(homeCurrentConversationId = id, homeMessages = emptyList()) }
        messagesCollectJob = viewModelScope.launch {
            homeChatDao.getMessages(id).collect { entities ->
                val messages = entities.map { e ->
                    HomeChatMessage(role = e.role, content = e.content, toolCallsJson = e.tool_calls_json, timestamp = e.timestamp)
                }
                homeMessageCache[id] = messages
                _uiState.update { it.copy(homeMessages = messages) }
                homeAgentMessageCache["_current"] = entities.map { e ->
                    if (e.tool_calls_json != null) {
                        val tc = Gson().fromJson(e.tool_calls_json, Array<ToolCall>::class.java)
                        ChatMessage(role = e.role, content = e.content, tool_calls = tc?.toList())
                    } else {
                        ChatMessage(role = e.role, content = e.content)
                    }
                }
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            homeChatDao.deleteConversation(id)
            if (_uiState.value.homeCurrentConversationId == id) {
                startNewConversation()
            }
        }
    }

    fun addHomeFragment(text: String) {
        val current = _uiState.value.homeFragments
        val updated = current + text
        Log.d("Fragment", "addHomeFragment | BEFORE count=${current.size} | ADDING '$text' | AFTER count=${updated.size}")
        viewModelScope.launch { persistHomeFragments(updated) }
    }

    fun removeHomeFragment(index: Int) {
        val current = _uiState.value.homeFragments
        val updated = current.filterIndexed { i, _ -> i != index }
        Log.d("Fragment", "removeHomeFragment | index=$index | BEFORE count=${current.size} | AFTER count=${updated.size}")
        viewModelScope.launch { persistHomeFragments(updated) }
    }

    fun organizeHomeFragments() {
        val fragments = _uiState.value.homeFragments
        Log.d("Fragment", "========== organizeHomeFragments | count=${fragments.size} ==========")
        Log.d("Fragment", "organizeHomeFragments | fragments=$fragments")
        if (fragments.isEmpty() || !ensureAiConfigured()) {
            Log.d("Fragment", "organizeHomeFragments | ABORT empty=${fragments.isEmpty()}")
            return
        }

        val prompt = buildString {
            append("请阅读以下碎片知识，先提出一个整理方案（说明每条碎片应归入哪个已有主题或创建新主题），")
            append("等待用户确认后再执行。不要直接修改笔记。\n\n")
            append("步骤：\n")
            append("1. 先用 summarize_all_notes 查看所有笔记的标题和摘要，了解现有笔记内容\n")
            append("2. 根据碎片内容与笔记摘要的匹配程度，制定整理方案\n")
            append("3. 向用户展示方案，等待确认\n\n")
            append("碎片列表：\n")
            fragments.forEachIndexed { i, f ->
                append("[碎片${i + 1}] $f\n")
            }
        }

        Log.d("Fragment", "organizeHomeFragments | switching to AGENT mode")
        _uiState.update { it.copy(homeChatMode = ChatMode.AGENT) }
        Log.d("Fragment", "organizeHomeFragments | clearing DB fragments")
        viewModelScope.launch { homeChatDao.clearAllFragments() }
        Log.d("Fragment", "organizeHomeFragments | sending organize prompt to Agent")
        sendHomeMessage(prompt)
    }

    private suspend fun persistHomeFragments(fragments: List<String>) {
        Log.d("Fragment", "persistHomeFragments -> CLEAR_ALL count=${fragments.size}")
        homeChatDao.clearAllFragments()
        fragments.forEachIndexed { i, content ->
            Log.d("Fragment", "persistHomeFragments -> INSERT pos=$i content='${content.take(40)}'")
            homeChatDao.insertFragment(HomeFragmentEntity(position = i, content = content))
        }
        Log.d("Fragment", "persistHomeFragments -> DONE count=${fragments.size}")
    }

    fun sendHomeMessage(text: String) {
        val mode = _uiState.value.homeChatMode
        Log.d("Fragment", "sendHomeMessage | mode=$mode text='${text.take(50)}'")
        if (text.isBlank()) return

        if (mode == ChatMode.FRAGMENT) {
            addHomeFragment(text)
            return
        }

        if (_uiState.value.homeIsLoading) return
        if (!ensureAiConfigured()) return

        val timestamp = System.currentTimeMillis()
        val userMessage = HomeChatMessage(role = "user", content = text, timestamp = timestamp)

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            var convId = _uiState.value.homeCurrentConversationId
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                val title = text.take(50).replace("\n", " ")
                try {
                    homeChatDao.insertConversation(HomeConversationEntity(id = convId, title = title, created_at = timestamp, updated_at = timestamp))
                } catch (e: Exception) {
                    Log.e("HomeChat", "insertConversation FAILED: ${e.message}", e)
                }
                _uiState.update { it.copy(homeCurrentConversationId = convId) }
            }

            try {
                homeChatDao.insertMessageRaw(id = UUID.randomUUID().toString(), conversationId = convId, role = "user", content = text, toolCallsJson = null, timestamp = timestamp)
            } catch (e: Exception) {
                Log.e("HomeChat", "insertMessageRaw FAILED: ${e.message}", e)
            }

            setProcessing(true, "AI 回答中…")
            _uiState.update { it.copy(homeMessages = it.homeMessages + userMessage, homeStreamLine = "", homeIsLoading = true, aiErrorMessage = null) }
            homeMessageCache[convId] = _uiState.value.homeMessages
            runHomeAgent(text, convId)
        }
    }

    private suspend fun runHomeAgent(question: String, convId: String) {
        val priorMessages = homeAgentMessageCache["_current"] ?: emptyList()
        val collectedMessages = mutableListOf<ChatMessage>()
        val toolCallDescriptions = mutableListOf<HomeChatMessage>()
        homeAgentService.runHomeAgentLoop(
            question = question,
            priorMessages = priorMessages,
            onReasoning = { appendReasoning(it) },
            onToolCall = { toolName, args, result ->
                // AI 岛显示原始工具调用信息（工具名 + 参数 + 结果）
                val islandDisplay = buildString {
                    append("🔧 调用工具: $toolName\n")
                    if (args.isNotBlank() && args != "{}") append("  参数: $args\n")
                    append("  结果: ${result.take(500)}")
                    if (result.length > 500) append("…")
                }
                appendStream(islandDisplay + "\n\n")
                // 聊天列表保持简洁显示
                val chatDisplay = formatToolCall(toolName, args)
                val toolMsg = HomeChatMessage(role = "tool", content = chatDisplay)
                toolCallDescriptions.add(toolMsg)
                _uiState.update { it.copy(homeMessages = it.homeMessages + toolMsg) }
            },
            onToken = { token ->
                appendStream(token)
                _uiState.update { it.copy(homeStreamLine = it.homeStreamLine + token) }
            },
            onAgentMessages = { messages ->
                collectedMessages.clear(); collectedMessages.addAll(messages)
                homeAgentMessageCache["_current"] = messages
            },
            onComplete = { answer ->
                setProcessing(false)
                val assistantMsg = HomeChatMessage(role = "assistant", content = answer)
                _uiState.update { it.copy(homeMessages = it.homeMessages + assistantMsg, homeStreamLine = "", homeIsLoading = false) }
                homeMessageCache[convId] = _uiState.value.homeMessages
                // 保存用户可见的消息：工具调用描述 + 最终回答（跳过原始 tool 结果和空内容消息）
                val visibleEntities = toolCallDescriptions.map { msg ->
                    HomeMessageEntity(id = UUID.randomUUID().toString(), conversation_id = convId, role = msg.role, content = msg.content, tool_calls_json = null, timestamp = msg.timestamp)
                } + listOf(HomeMessageEntity(id = UUID.randomUUID().toString(), conversation_id = convId, role = "assistant", content = answer, tool_calls_json = null, timestamp = System.currentTimeMillis()))
                viewModelScope.launch { homeChatDao.insertMessages(visibleEntities); homeChatDao.updateConversationTimestamp(id = convId, title = question.take(50).replace("\n", " "), updatedAt = System.currentTimeMillis()) }
            },
            onError = { msg -> setProcessing(false); _uiState.update { it.copy(aiErrorMessage = msg, homeIsLoading = false) } },
            onAssessment = { decision, reason, extraSteps ->
                val display = if (decision == "CONTINUE") {
                    "🔄 监督者判断任务未完成，追加 $extraSteps 轮工具调用（$reason）\n"
                } else {
                    "⏹️ 监督者判断应终止：$reason\n"
                }
                appendStream(display)
                val assessMsg = HomeChatMessage(role = "system", content = display.trim())
                _uiState.update { it.copy(homeMessages = it.homeMessages + assessMsg) }
            }
        )
    }

    /**
     * 将工具调用转换为用户可读的中文描述。
     * 例如：create_subject + {"title":"日记"} → "创建笔记：日记"
     */
    private fun formatToolCall(toolName: String, args: String): String {
        val params = try {
            gson.fromJson(args, JsonObject::class.java) ?: JsonObject()
        } catch (e: Exception) {
            JsonObject()
        }
        fun p(key: String): String? = params.get(key)?.takeIf { !it.isJsonNull }?.asString
        return when (toolName) {
            "list_subjects" -> "查询笔记列表"
            "get_subject" -> "查询笔记信息"
            "create_subject" -> "创建笔记：${p("title") ?: ""}"
            "delete_subject" -> "删除笔记"
            "rename_subject" -> "重命名笔记为：${p("new_title") ?: ""}"
            "list_pages" -> "查询页面列表"
            "get_page" -> "查询页面内容"
            "create_page" -> "创建页面：${p("title") ?: ""}"
            "update_page" -> "更新页面内容"
            "delete_page" -> "删除页面"
            "list_fragments" -> "查询碎片列表"
            "add_fragment" -> "添加碎片：${(p("content") ?: "").take(30)}"
            "update_fragment" -> "修改碎片内容"
            "delete_fragment" -> "删除碎片"
            "search_fragments" -> "搜索碎片：${p("query") ?: ""}"
            "get_aggregated_note" -> "查询聚合笔记"
            "update_aggregated_note" -> "更新聚合笔记"
            "get_study_plan" -> "查询学习计划"
            "update_study_plan" -> "更新学习计划"
            "get_page_index" -> "查询页面索引"
            "search_pages" -> "搜索页面：${p("query") ?: ""}"
            "summarize_all_notes" -> "生成笔记摘要"
            else -> "调用工具：$toolName"
        }
    }
}

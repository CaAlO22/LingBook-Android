# 首页 Agent 图片上传 + insert_img 工具 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为首页 agent 模式添加图片上传提问功能，并创建 `insert_img(note_id, page, image_number)` 工具，让 agent 能将对话中的图片插入到笔记本笔记的指定页面。

**Architecture:** 新建 `ConversationImageStore` 单例管理对话级图片编号存储，新建 `ImageTools` 提供 `insert_img` 工具。修改 HomeChatSheet 添加图片选择/预览 UI，通过 SubjectViewModel -> HomeAgentService 链路将图片以多模态消息发送给 LLM。非视觉模型时隐藏图片上传按钮。

**Tech Stack:** Kotlin + Jetpack Compose + Hilt + Gson + OpenAI-compatible function calling

## Global Constraints

- 项目语言：Kotlin + Jetpack Compose
- 依赖注入：Hilt（所有新类用 `@Singleton` + `@Inject constructor`）
- 工具系统：实现 `Tool` 接口，用 `buildJsonObject`/`buildJsonArray` DSL 构建 JSON Schema
- 验证命令：`./gradlew :app:compileDebugKotlin`
- 模拟器同步：`./gradlew :app:installDebug`
- 图片格式：base64 data URI（`data:image/png;base64,...`），PNG 无损压缩，max 960px
- 图片编号：对话级累积编号（1, 2, 3...），新对话/切换对话时清零
- page 参数：1-indexed（第1页、第2页…）
- insert_img 仅注册到 ToolRegistry（首页 agent），不加入 ScopedToolRegistry.SINGLE_NOTE_TOOLS

---

### Task 1: 创建 ConversationImageStore

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/image/ConversationImageStore.kt`

**Interfaces:**
- Produces: `ConversationImageStore` 单例，方法 `addImages(uris: List<String>): List<Int>`、`getImage(number: Int): String?`、`getImageCount(): Int`、`clear()`

- [ ] **Step 1: 创建文件**

```kotlin
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
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: 创建 ImageTools（insert_img 工具）

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/image/ImageTools.kt`

**Interfaces:**
- Consumes: `SubjectRepository`（来自 data 层）、`ConversationImageStore`（来自 Task 1）
- Produces: `ImageTools.create(repo, imageStore): List<Tool>` 返回包含 `insert_img` 工具的列表

- [ ] **Step 1: 创建文件**

```kotlin
package com.lingji.app.domain.tool.image

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object ImageTools {

    fun create(repo: SubjectRepository, imageStore: ConversationImageStore): List<Tool> = listOf(
        InsertImage(repo, imageStore)
    )

    private class InsertImage(
        private val repo: SubjectRepository,
        private val imageStore: ConversationImageStore
    ) : Tool {
        override val name = "insert_img"
        override val description = "将当前对话中指定编号的图片插入到笔记本笔记的指定页面。只能操作 notebook 类型笔记。" +
            "图片编号是用户在对话中发送图片时自动分配的（从1开始递增）。页码从1开始（第1页、第2页…），" +
            "可通过 list_pages 查看页面列表（返回的 order 字段 + 1 即为页码）。图片会追加到页面内容末尾。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "note_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记本笔记的 ID（必须为 notebook 类型）"
                }
                "page" to buildJsonObject {
                    "type" to "integer"
                    "description" to "目标页码，从1开始（第1页、第2页…）"
                }
                "image_number" to buildJsonObject {
                    "type" to "integer"
                    "description" to "对话中图片的编号（用户发送图片时分配的编号）"
                }
            }
            "required" to buildJsonArray { +"note_id"; +"page"; +"image_number" }
        }

        override suspend fun execute(params: JsonObject): String {
            val noteId = params.get("note_id")?.asString
                ?: return "Error: Missing required parameter: note_id"
            val pageNumber = params.get("page")?.asInt
                ?: return "Error: Missing required parameter: page"
            val imageNumber = params.get("image_number")?.asInt
                ?: return "Error: Missing required parameter: image_number"

            if (pageNumber < 1) return "Error: page 必须 >= 1"

            // 获取图片
            val imageData = imageStore.getImage(imageNumber)
                ?: return "Error: 找不到编号为 $imageNumber 的图片。当前对话共有 ${imageStore.getImageCount()} 张图片。"

            // 获取笔记
            val subject = repo.getSubjectByIdOnce(noteId)
                ?: return "Error: 找不到笔记: $noteId"

            if (subject.type != SubjectType.NOTEBOOK) {
                return "Error: 只能对 notebook 类型笔记操作。「${subject.title}」是 ${subject.type.name} 类型，没有页面功能。"
            }

            val pages = subject.pages
            if (pages.isNullOrEmpty()) return "Error: 笔记「${subject.title}」没有任何页面。"

            if (pageNumber > pages.size) {
                return "Error: 页码 $pageNumber 超出范围。笔记「${subject.title}」共有 ${pages.size} 页。"
            }

            val targetPage = pages[pageNumber - 1] // 1-indexed -> 0-indexed

            // 在页面内容末尾追加图片（Markdown 图片语法）
            val newContent = if (targetPage.content.isBlank()) {
                "![图片]($imageData)"
            } else {
                "${targetPage.content}\n\n![图片]($imageData)"
            }

            val updatedPage = targetPage.copy(
                content = newContent,
                updatedAt = System.currentTimeMillis()
            )
            repo.updatePage(noteId, updatedPage)

            return buildJsonObject {
                "success" to true
                "page_title" to targetPage.title
                "page_number" to pageNumber
                "image_number" to imageNumber
            }.toString()
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 3: 注册 ImageTools 到 ToolRegistry

**Files:**
- Modify: `app/src/main/java/com/lingji/app/domain/tool/ToolRegistry.kt`

**Interfaces:**
- Consumes: `ConversationImageStore`（Task 1）、`ImageTools`（Task 2）
- Produces: ToolRegistry 现在包含 23 个工具（原 22 + insert_img）

- [ ] **Step 1: 添加 import 和构造函数参数**

在 `ToolRegistry.kt` 中：
1. 添加 import: `import com.lingji.app.domain.tool.image.ConversationImageStore` 和 `import com.lingji.app.domain.tool.image.ImageTools`
2. 构造函数添加参数: `conversationImageStore: ConversationImageStore`

修改后的构造函数（行 19-25）:
```kotlin
@Singleton
class ToolRegistry @Inject constructor(
    subjectRepository: SubjectRepository,
    llmService: LLMService,
    settingsRepository: SettingsRepository,
    subjectSummaryDao: SubjectSummaryDao,
    indexService: IndexService,
    conversationImageStore: ConversationImageStore
) {
```

- [ ] **Step 2: 在 buildList 中注册 ImageTools**

在 `buildList` 块中（行 26-32），在 `SearchTools.create(...)` 之后添加一行:

```kotlin
    private val tools: Map<String, Tool> = buildList {
        addAll(SubjectTools.create(subjectRepository, subjectSummaryDao))
        addAll(PageTools.create(subjectRepository))
        addAll(FragmentTools.create(subjectRepository))
        addAll(NoteTools.create(subjectRepository))
        addAll(SearchTools.create(subjectRepository, llmService, settingsRepository, subjectSummaryDao, indexService))
        addAll(ImageTools.create(subjectRepository, conversationImageStore))
    }.associateBy { it.name }
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 4: 更新 HomeAgentService 支持多模态消息

**Files:**
- Modify: `app/src/main/java/com/lingji/app/data/remote/HomeAgentService.kt`

**Interfaces:**
- Consumes: `ConversationImageStore`（Task 1）、`ProviderRegistry.supportsVision()`
- Produces: `runHomeAgentLoop` 新增 `images: List<String> = emptyList()` 参数

- [ ] **Step 1: 添加 import 和构造函数参数**

在文件顶部添加 imports:
```kotlin
import com.lingji.app.data.remote.models.ContentPart
import com.lingji.app.data.remote.models.ImageContentPart
import com.lingji.app.data.remote.models.ImageUrl
import com.lingji.app.data.remote.models.TextContentPart
import com.lingji.app.domain.provider.ProviderRegistry
import com.lingji.app.domain.tool.image.ConversationImageStore
```

构造函数添加 `conversationImageStore` 参数（行 15-18）:
```kotlin
@Singleton
class HomeAgentService @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val llmService: LLMService,
    private val settingsRepository: SettingsRepository,
    private val conversationImageStore: ConversationImageStore
) {
```

- [ ] **Step 2: 修改 runHomeAgentLoop 签名，添加 images 参数**

在方法签名中（行 22-31）添加 `images` 参数:
```kotlin
    suspend fun runHomeAgentLoop(
        question: String,
        priorMessages: List<ChatMessage>,
        images: List<String> = emptyList(),
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onToolCall: (toolName: String, args: String, result: String) -> Unit = { _, _, _ },
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onAgentMessages: (List<ChatMessage>) -> Unit = {},
        onAssessment: (decision: String, reason: String, extraSteps: Int) -> Unit = { _, _, _ }
    ) = withContext(Dispatchers.IO) {
```

- [ ] **Step 3: 存储图片到 ConversationImageStore 并更新系统提示词**

在 `try {` 块内（行 34 之后），获取 settings 之后，添加图片存储和系统提示词更新:

```kotlin
        try {
            val settings = settingsRepository.getSettingsOnce()
            val tools = toolRegistry.toOpenAITools()

            // 存储图片到 ConversationImageStore，获取编号
            val imageNumbers = if (images.isNotEmpty()) {
                conversationImageStore.addImages(images)
            } else emptyList()

            val supportsVision = ProviderRegistry.supportsVision(settings.provider, settings.modelName)

            val systemPrompt = """你是灵记的首页助手，兼具「知识问答」与「笔记管理」两种能力，请根据用户意图选择响应方式。

【知识性问题】当用户询问概念、事实、解释、建议、做法等知识性问题时，直接用你的知识回答，不要调用笔记工具去创建笔记。回答应清晰、准确、简洁。回答结束后，主动询问用户是否需要把这部分内容整理保存为一篇笔记。

【笔记管理任务】当用户明确要求创建、查询、修改、搜索或删除笔记时，使用工具完成。笔记有两种类型：notebook（笔记本，有页面 page 功能）和 fragment（碎片，只有碎片 fragment 功能，没有页面）。你可以通过工具读取、搜索、创建、修改和删除用户的笔记（包括主题、页面、碎片、聚合笔记和学习计划）。页面操作（create_page/update_page/delete_page/list_pages/get_page）仅适用于 notebook 类型笔记；fragment 类型笔记没有页面，请改用 list_fragments/search_fragments/add_fragment 等碎片工具读取和管理其内容。当前对话可以访问用户的所有笔记，subject_id 需根据上下文或通过 list_subjects / summarize_all_notes 获取。操作完成后用简洁的中文总结你做了什么。

【图片插入能力】当用户在对话中发送图片时，每张图片会被自动编号（从1开始递增）。你可以使用 insert_img 工具将指定编号的图片插入到笔记本笔记的指定页面。insert_img 参数说明：note_id（笔记本 ID）、page（页码，从1开始）、image_number（图片编号）。仅支持 notebook 类型笔记。

判断原则：优先判断用户是否在「求知」。只要用户想得到一个答案、而非想「动笔记」，就直接回答并在末尾询问是否写入笔记；只有用户明确表达要管理或记录笔记时，才调用笔记工具。"""
```

- [ ] **Step 4: 构建多模态用户消息**

将原来行 47 的 `messages.add(ChatMessage("user", question))` 替换为多模态消息构建逻辑:

```kotlin
            val messages = mutableListOf(ChatMessage("system", systemPrompt))
            messages.addAll(priorMessages)

            // 构建用户消息：有图片时为多模态，无图片时为纯文本
            if (images.isNotEmpty() && supportsVision) {
                val parts = mutableListOf<ContentPart>(TextContentPart(text = question))
                images.forEach { uri ->
                    parts.add(ImageContentPart(imageUrl = ImageUrl(uri)))
                }
                messages.add(ChatMessage("user", parts))
            } else if (images.isNotEmpty() && !supportsVision) {
                // 模型不支持视觉：文字提示图片存在
                val imageInfo = "编号 ${imageNumbers.first()}-${imageNumbers.last()}"
                messages.add(ChatMessage("user", "$question\n\n[附图片${images.size}张，$imageInfo，当前模型不支持图片识别]"))
            } else {
                messages.add(ChatMessage("user", question))
            }
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 5: 更新 SubjectViewModel 传递图片

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt`

**Interfaces:**
- Consumes: `ConversationImageStore`（Task 1）、`HomeAgentService.runHomeAgentLoop`（Task 4，新 images 参数）
- Produces: `sendHomeMessage(text: String, images: List<String>)` 新签名

- [ ] **Step 1: 添加 ConversationImageStore 依赖**

在构造函数（行 52-61）中添加参数:
```kotlin
@HiltViewModel
class SubjectViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val llmService: LLMService,
    private val agentService: AgentService,
    private val homeAgentService: HomeAgentService,
    private val indexService: IndexService,
    private val fileManager: FileManager,
    private val homeChatDao: HomeChatDao,
    private val conversationImageStore: ConversationImageStore
) : ViewModel() {
```

添加 import: `import com.lingji.app.domain.tool.image.ConversationImageStore`

- [ ] **Step 2: 修改 sendHomeMessage 签名和逻辑**

将 `sendHomeMessage`（行 939）签名改为接受 images 参数，并在 AGENT 模式下传递:

```kotlin
    fun sendHomeMessage(text: String, images: List<String> = emptyList()) {
        val mode = _uiState.value.homeChatMode
        Log.d("Fragment", "sendHomeMessage | mode=$mode text='${text.take(50)}' images=${images.size}")
        if (text.isBlank() && images.isEmpty()) return

        if (mode == ChatMode.FRAGMENT) {
            addHomeFragment(text)
            return
        }

        if (_uiState.value.homeIsLoading) return
        if (!ensureAiConfigured()) return

        val timestamp = System.currentTimeMillis()
        val displayContent = if (images.isNotEmpty()) {
            "$text${if (text.isNotBlank()) "\n" else ""}[附${images.size}张图片]"
        } else text
        val userMessage = HomeChatMessage(role = "user", content = displayContent, timestamp = timestamp)

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
                homeChatDao.insertMessageRaw(id = UUID.randomUUID().toString(), conversationId = convId, role = "user", content = displayContent, toolCallsJson = null, timestamp = timestamp)
            } catch (e: Exception) {
                Log.e("HomeChat", "insertMessageRaw FAILED: ${e.message}", e)
            }

            setProcessing(true, "AI 回答中…")
            _uiState.update { it.copy(homeMessages = it.homeMessages + userMessage, homeStreamLine = "", homeIsLoading = true, aiErrorMessage = null) }
            homeMessageCache[convId] = _uiState.value.homeMessages
            runHomeAgent(text, convId, images)
        }
    }
```

- [ ] **Step 3: 修改 runHomeAgent 传递 images**

将 `runHomeAgent`（行 982）签名和调用更新:

```kotlin
    private suspend fun runHomeAgent(question: String, convId: String, images: List<String> = emptyList()) {
        val priorMessages = homeAgentMessageCache["_current"] ?: emptyList()
        val collectedMessages = mutableListOf<ChatMessage>()
        val toolCallDescriptions = mutableListOf<HomeChatMessage>()
        homeAgentService.runHomeAgentLoop(
            question = question,
            priorMessages = priorMessages,
            images = images,
            onReasoning = { appendReasoning(it) },
            onToolCall = { toolName, args, result ->
                // ... 保持原有逻辑不变 ...
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
                // ... 保持原有逻辑不变 ...
            },
            onError = { msg -> setProcessing(false); _uiState.update { it.copy(aiErrorMessage = msg, homeIsLoading = false) } },
            onAssessment = { decision, reason, extraSteps ->
                // ... 保持原有逻辑不变 ...
            }
        )
    }
```

注意：onToolCall、onComplete、onAssessment 回调的内部逻辑保持原样不变，只是在外层添加了 `images = images` 参数。

- [ ] **Step 4: 在 startNewConversation 和 loadConversation 中清空图片存储**

在 `startNewConversation()`（行 821）中添加 `conversationImageStore.clear()`:
```kotlin
    fun startNewConversation() {
        setProcessing(false)
        messagesCollectJob?.cancel()
        conversationImageStore.clear()  // 新增：清空图片存储
        val currentId = _uiState.value.homeCurrentConversationId
        // ... 后续不变 ...
    }
```

在 `loadConversation(id: String)`（行 840）中添加 `conversationImageStore.clear()`:
```kotlin
    fun loadConversation(id: String) {
        setProcessing(false)
        conversationImageStore.clear()  // 新增：清空图片存储
        // ... 后续不变 ...
    }
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 6: 更新 HomeChatSheet UI 添加图片选择和预览

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/chat/HomeChatSheet.kt`
- Modify: `app/src/main/java/com/lingji/app/ui/components/PageImagePicker.kt`（将 uriToBase64 改为 internal）

**Interfaces:**
- Consumes: `supportsVision: Boolean`（决定是否显示图片按钮）、`onSend: (String, List<String>) -> Unit`
- Produces: 图片选择 UI、缩略图预览、发送时附带图片 base64 列表

- [ ] **Step 1: 将 PageImagePicker.kt 中的 uriToBase64 改为 internal**

在 `PageImagePicker.kt` 行 223，将 `private fun uriToBase64` 改为 `internal fun uriToBase64`:

```kotlin
internal fun uriToBase64(context: Context, uri: Uri): String? {
```

- [ ] **Step 2: 在 HomeChatSheet 添加 imports**

在 `HomeChatSheet.kt` 顶部添加以下 imports:

```kotlin
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.lingji.app.ui.components.uriToBase64
import androidx.compose.foundation.border
```

- [ ] **Step 3: 修改 HomeChatSheet 参数签名**

在行 78，修改 `onSend` 签名并添加 `supportsVision` 参数:

```kotlin
@Composable
fun HomeChatSheet(
    messages: List<HomeChatMessage>,
    streamLine: String,
    isLoading: Boolean,
    currentMode: ChatMode,
    conversations: List<HomeConversationEntity>,
    currentConversationId: String?,
    fragments: List<String>,
    onSend: (String, List<String>) -> Unit,
    onModeChange: (ChatMode) -> Unit,
    onNewConversation: () -> Unit,
    onLoadConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteFragment: (Int) -> Unit,
    onOrganizeFragments: () -> Unit,
    onDismiss: () -> Unit,
    supportsVision: Boolean = false
) {
```

- [ ] **Step 4: 添加图片选择状态和 launcher**

在行 96 附近（`var inputText` 声明之后），添加图片相关状态:

```kotlin
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    val selectedImages = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = uriToBase64(context, uri)
            if (base64 != null) {
                selectedImages.add(base64)
            }
        }
    }
```

- [ ] **Step 5: 修改 submitInput 逻辑**

修改 `submitInput` lambda（行 105-111），发送时包含图片并清空:

```kotlin
    val submitInput: () -> Unit = {
        val text = inputText.text.trim()
        if ((text.isNotBlank() || selectedImages.isNotEmpty()) && !isLoading) {
            onSend(text, selectedImages.toList())
            inputText = TextFieldValue("")
            selectedImages.clear()
        }
    }
```

- [ ] **Step 6: 添加图片预览和选择按钮 UI**

在输入区域 `Row`（行 507）之前，添加图片预览区域。并在 `GlassSurface` 之前添加图片选择按钮（仅在 AGENT 模式 + supportsVision 时显示）。

将输入区域（行 506-562）替换为:

```kotlin
            // --- 图片预览区 ---
            if (selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedImages.forEachIndexed { index, base64 ->
                        val bitmap = remember(base64) {
                            try {
                                val bytes = Base64.decode(base64.substringAfter(","), Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) { null }
                        }
                        if (bitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "图片${index + 1}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                // 删除按钮
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                        .clickable { selectedImages.removeAt(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "×",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Input Area ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 图片选择按钮（仅 AGENT 模式 + 支持视觉时显示）
                if (currentMode == ChatMode.AGENT && supportsVision && !isLoading) {
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "添加图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .enterSendBehavior(inputText, { inputText = it }, submitInput),
                        enabled = !isLoading || currentMode == ChatMode.FRAGMENT,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = if (!isLoading) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (inputText.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.home_chat_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val isFragmentMode = currentMode == ChatMode.FRAGMENT
                val canSend = (inputText.text.isNotBlank() || selectedImages.isNotEmpty()) && (isFragmentMode || !isLoading)
                TextButton(
                    onClick = submitInput,
                    enabled = canSend,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = if (isFragmentMode) "记录" else "发送",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSend) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 7: 更新 SubjectGalleryScreen 连接 onSend

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt`

- [ ] **Step 1: 添加 import**

在文件顶部添加:
```kotlin
import com.lingji.app.domain.provider.ProviderRegistry
```

- [ ] **Step 2: 修改 HomeChatSheet 调用**

在行 646-662，修改 `HomeChatSheet` 调用，更新 `onSend` 并添加 `supportsVision`:

```kotlin
        HomeChatSheet(
            messages = uiState.homeMessages,
            streamLine = uiState.homeStreamLine,
            isLoading = uiState.homeIsLoading,
            currentMode = uiState.homeChatMode,
            conversations = uiState.homeConversations,
            currentConversationId = uiState.homeCurrentConversationId,
            fragments = uiState.homeFragments,
            onSend = { text, images -> viewModel.sendHomeMessage(text, images) },
            onModeChange = { viewModel.setHomeChatMode(it) },
            onNewConversation = { viewModel.startNewConversation() },
            onLoadConversation = { viewModel.loadConversation(it) },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onDeleteFragment = { viewModel.removeHomeFragment(it) },
            onOrganizeFragments = { viewModel.organizeHomeFragments() },
            onDismiss = { viewModel.toggleHomeChat() },
            supportsVision = ProviderRegistry.supportsVision(uiState.settings.provider, uiState.settings.modelName)
        )
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 8: 最终验证与模拟器同步

- [ ] **Step 1: 完整编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 安装到模拟器**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL（若无可用的模拟器/设备，记录原因并跳过）

- [ ] **Step 3: 提交代码**

```bash
git add -A
git commit -m "feat: 首页Agent模式添加图片上传提问功能及insert_img工具"
```

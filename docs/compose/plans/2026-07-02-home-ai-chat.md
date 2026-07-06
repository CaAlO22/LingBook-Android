# 首页 AI 对话框 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a global AI chat dialog on the home page with ASK (cross-note Q&A) and AGENT (full 20-tool function calling) modes, persistent chat history via Room.

**Architecture:** Extend existing patterns — new Room entities (home_conversations, home_messages), new HomeAgentService using full ToolRegistry, new UI components (HomeChatBar + HomeChatSheet) composited into the gallery screen's Scaffold. All state flows through SubjectViewModel.

**Tech Stack:** Kotlin + Jetpack Compose + Hilt + Room (v7→v8) + OkHttp + Gson

## Global Constraints

- Room version bump: 7 → 8 with addTable migration (no data loss)
- Follow existing provider plugin architecture — no provider-specific logic in new components
- Use `snake_case` for Room column names
- No `try/catch` where `runCatching` suffices; no `any` type
- Prefer `const` / `val`; ternaries or early returns; no `else` after `return`

---

### Task 1: Database Layer (Entities + DAO + Migration)

**Covers:** [S7]

**Files:**
- Create: `app/src/main/java/com/lingji/app/data/db/entities/HomeConversationEntity.kt`
- Create: `app/src/main/java/com/lingji/app/data/db/entities/HomeMessageEntity.kt`
- Create: `app/src/main/java/com/lingji/app/data/db/dao/HomeChatDao.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt`

**Interfaces:**
- Produces: `HomeConversationEntity(id, title, created_at, updated_at)`, `HomeMessageEntity(id, conversation_id, role, content, tool_calls_json, timestamp)`, `HomeChatDao` with Flow-based queries, `LingjiDatabase.homeChatDao()`, `MIGRATION_7_8`

- [ ] **Step 1: Create HomeConversationEntity**

```kotlin
// app/src/main/java/com/lingji/app/data/db/entities/HomeConversationEntity.kt
package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_conversations")
data class HomeConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val created_at: Long,
    val updated_at: Long
)
```

- [ ] **Step 2: Create HomeMessageEntity**

```kotlin
// app/src/main/java/com/lingji/app/data/db/entities/HomeMessageEntity.kt
package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "home_messages",
    foreignKeys = [ForeignKey(
        entity = HomeConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversation_id")]
)
data class HomeMessageEntity(
    @PrimaryKey val id: String,
    val conversation_id: String,
    val role: String,
    val content: String,
    val tool_calls_json: String?,
    val timestamp: Long
)
```

- [ ] **Step 3: Create HomeChatDao**

```kotlin
// app/src/main/java/com/lingji/app/data/db/dao/HomeChatDao.kt
package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeChatDao {

    @Query("SELECT * FROM home_conversations ORDER BY updated_at DESC")
    fun getConversations(): Flow<List<HomeConversationEntity>>

    @Query("SELECT * FROM home_conversations WHERE id = :id")
    suspend fun getConversationById(id: String): HomeConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: HomeConversationEntity)

    @Query("DELETE FROM home_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM home_messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<HomeMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: HomeMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<HomeMessageEntity>)

    @Query("DELETE FROM home_messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM home_messages WHERE conversation_id = :id LIMIT 1)")
    suspend fun conversationHasMessages(id: String): Boolean
}
```

- [ ] **Step 4: Add MIGRATION_7_8 to LingjiDatabase**

```kotlin
// In app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt

// Add import
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import com.lingji.app.data.db.dao.HomeChatDao

// Inside companion object — add new migration
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS home_conversations (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "title TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS home_messages (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "conversation_id TEXT NOT NULL, " +
                "role TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "tool_calls_json TEXT, " +
                "timestamp INTEGER NOT NULL, " +
                "FOREIGN KEY(conversation_id) REFERENCES home_conversations(id) ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_home_messages_conversation_id ON home_messages(conversation_id)")
    }
}

// Update @Database annotation:
//   entities = [..., HomeConversationEntity::class, HomeMessageEntity::class]
//   version = 8

// Add abstract method:
abstract fun homeChatDao(): HomeChatDao
```

- [ ] **Step 5: Add MIGRATION_7_8 to AppModule and register HomeChatDao**

```kotlin
// In app/src/main/java/com/lingji/app/di/AppModule.kt
// Add to .addMigrations() chain:
//   LingjiDatabase.MIGRATION_7_8

// Add provider:
@Provides
fun provideHomeChatDao(database: LingjiDatabase) = database.homeChatDao()
```

- [ ] **Step 6: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 2: Home Agent Service

**Covers:** [S5]

**Files:**
- Create: `app/src/main/java/com/lingji/app/data/remote/HomeAgentService.kt`

**Interfaces:**
- Consumes: `ToolRegistry`, `LLMService`, `SettingsRepository`, `ChatMessage`, `ChatResponse`
- Produces: `HomeAgentService.runHomeAgentLoop(question, priorMessages, onToken, onReasoning, onToolCall, onComplete, onError, onAgentMessages)`

- [ ] **Step 1: Create HomeAgentService**

```kotlin
// app/src/main/java/com/lingji/app/data/remote/HomeAgentService.kt
package com.lingji.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatResponse
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.domain.tool.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAgentService @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val llmService: LLMService,
    private val settingsRepository: SettingsRepository
) {
    private val gson = Gson()

    suspend fun runHomeAgentLoop(
        question: String,
        priorMessages: List<ChatMessage>,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onToolCall: (toolName: String, args: String, result: String) -> Unit = { _, _, _ -> },
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onAgentMessages: (List<ChatMessage>) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.getSettingsOnce()
            val tools = toolRegistry.toOpenAITools()

            val systemPrompt = """你是一个灵记笔记管理助手，可以帮用户管理所有笔记。
你可以通过工具读取、搜索、创建、修改和删除用户的笔记（包括主题、页面、碎片、聚合笔记和学习计划）。
当前对话可以访问用户的所有笔记，subject_id 需要你根据上下文或通过 list_subjects / summarize_all_notes 获取。
请根据用户需求选择合适的工具。操作完成后用简洁的中文总结你做了什么。"""

            val messages = mutableListOf(ChatMessage("system", systemPrompt))
            messages.addAll(priorMessages)
            messages.add(ChatMessage("user", question))

            repeat(MAX_ITERATIONS) {
                val response: ChatResponse = llmService.chatWithTools(messages, tools, settings)
                val message = response.choices?.firstOrNull()?.message

                val reasoning = message?.reasoning_content
                if (!reasoning.isNullOrBlank()) {
                    onReasoning(reasoning)
                }

                val toolCalls = message?.tool_calls
                if (toolCalls == null || toolCalls.isEmpty()) {
                    val content = message?.content?.let { if (it is String) it else "" } ?: ""
                    val cleaned = LLMService.sanitizeOutput(content)
                    messages.add(ChatMessage("assistant", cleaned))
                    onAgentMessages(messages.toList())
                    onToken(cleaned)
                    onComplete(cleaned)
                    return@withContext
                }

                messages.add(ChatMessage(
                    role = "assistant",
                    content = message?.content,
                    tool_calls = toolCalls
                ))

                for (tc in toolCalls) {
                    val toolName = tc.function.name
                    val argsString = tc.function.arguments
                    val params = try {
                        gson.fromJson(argsString, JsonObject::class.java) ?: JsonObject()
                    } catch (e: Exception) {
                        JsonObject()
                    }
                    val result = toolRegistry.executeTool(toolName, params)
                    onToolCall(toolName, argsString, result)
                    messages.add(ChatMessage(
                        role = "tool",
                        content = result,
                        toolCallId = tc.id
                    ))
                }
            }

            val fallback = "已达到最大工具调用次数限制，请尝试简化请求。"
            onAgentMessages(messages.toList())
            onToken(fallback)
            onComplete(fallback)
        } catch (e: Exception) {
            onError(e.message ?: "Agent 执行失败")
        }
    }

    companion object {
        private const val MAX_ITERATIONS = 10
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 3: Add message type for home chat UI state

**Covers:** [S4]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectUiState.kt`

**Interfaces:**
- Produces: `HomeChatMessage(role, content, toolCallsJson, timestamp)` data class, new fields in `SubjectUiState`

- [ ] **Step 1: Add HomeChatMessage and new UI state fields**

```kotlin
// app/src/main/java/com/lingji/app/ui/viewmodel/SubjectUiState.kt

// Add at top of file after existing imports:
import com.lingji.app.ui.components.ChatMode

// Add data class before SubjectUiState:
data class HomeChatMessage(
    val role: String,           // "user", "assistant", "tool"
    val content: String,
    val toolCallsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// Add to SubjectUiState constructor (at end, next to noteChatHistories):
data class SubjectUiState(
    // ... existing fields unchanged ...
    val noteChatHistories: Map<String, List<Pair<String, String>>> = emptyMap(),
    // NEW:
    val homeChatExpanded: Boolean = false,
    val homeChatMode: ChatMode = com.lingji.app.ui.components.ChatMode.ASK,
    val homeCurrentConversationId: String? = null,
    val homeConversations: List<com.lingji.app.data.db.entities.HomeConversationEntity> = emptyList(),
    val homeMessages: List<HomeChatMessage> = emptyList(),
    val homeStreamLine: String = "",
    val homeIsLoading: Boolean = false
)
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 4: ViewModel — Home Chat Methods

**Covers:** [S4, S5]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt`

**Interfaces:**
- Consumes: `HomeAgentService` (new), `HomeChatDao` (new), `HomeConversationEntity`, `HomeMessageEntity`, `HomeChatMessage`
- Produces: `toggleHomeChat()`, `setHomeChatMode()`, `sendHomeMessage(text)`, `startNewConversation()`, `loadConversation(id)`, `deleteConversation(id)`, `deleteAllConversations()`, `getHomeConversations()`

- [ ] **Step 1: Add new dependencies, state helpers, and home chat methods to SubjectViewModel**

In `SubjectViewModel`, inject `HomeAgentService` and `HomeChatDao`:

```kotlin
// Add imports at top
import com.google.gson.Gson
import com.lingji.app.data.db.dao.HomeChatDao
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import com.lingji.app.data.remote.HomeAgentService
import com.lingji.app.data.remote.models.ToolCall
import com.lingji.app.ui.components.ChatMode
import java.util.UUID

// Add to constructor injection:
class SubjectViewModel @Inject constructor(
    // ... existing deps ...
    private val homeAgentService: HomeAgentService,  // NEW
    private val homeChatDao: HomeChatDao              // NEW
) : ViewModel() {

    // Add after existing agentMessageCache:
    private val homeAgentMessageCache = mutableMapOf<String, List<ChatMessage>>()

    // Add methods after clearAgentMessages:

    fun toggleHomeChat() {
        _uiState.update {
            it.copy(homeChatExpanded = !it.homeChatExpanded, homeStreamLine = "")
        }
    }

    fun setHomeChatMode(mode: ChatMode) {
        _uiState.update { it.copy(homeChatMode = mode) }
    }

    fun loadHomeConversations() {
        viewModelScope.launch {
            homeChatDao.getConversations().collect { conversations ->
                _uiState.update { it.copy(homeConversations = conversations) }
            }
        }
    }

    fun startNewConversation() {
        _uiState.update { it.copy(
            homeCurrentConversationId = null,
            homeMessages = emptyList(),
            homeStreamLine = "",
            homeIsLoading = false
        ) }
        homeAgentMessageCache.remove("_current")
    }

    fun loadConversation(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(homeCurrentConversationId = id) }
            homeChatDao.getMessages(id).collect { entities ->
                val messages = entities.map { e ->
                    HomeChatMessage(
                        role = e.role,
                        content = e.content,
                        toolCallsJson = e.tool_calls_json,
                        timestamp = e.timestamp
                    )
                }
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

    fun sendHomeMessage(text: String) {
        val mode = _uiState.value.homeChatMode
        if (_uiState.value.homeIsLoading || text.isBlank()) return
        if (!ensureAiConfigured()) return

        val timestamp = System.currentTimeMillis()
        val userMessage = HomeChatMessage(role = "user", content = text, timestamp = timestamp)

        viewModelScope.launch {
            // Ensure conversation exists
            var convId = _uiState.value.homeCurrentConversationId
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                val title = text.take(50).replace("\n", " ")
                homeChatDao.insertConversation(HomeConversationEntity(
                    id = convId,
                    title = title,
                    created_at = timestamp,
                    updated_at = timestamp
                ))
                _uiState.update { it.copy(homeCurrentConversationId = convId) }
            }

            // Persist user message
            homeChatDao.insertMessage(HomeMessageEntity(
                id = UUID.randomUUID().toString(),
                conversation_id = convId!!,
                role = "user",
                content = text,
                tool_calls_json = null,
                timestamp = timestamp
            ))

            _uiState.update { it.copy(
                homeMessages = it.homeMessages + userMessage,
                homeStreamLine = "",
                homeIsLoading = true,
                aiErrorMessage = null
            ) }

            if (mode == ChatMode.ASK) {
                runHomeAsk(text, convId!!)
            } else {
                runHomeAgent(text, convId!!)
            }
        }
    }

    private suspend fun runHomeAsk(question: String, convId: String) {
        try {
            val settings = _uiState.value.settings
            val systemPrompt = buildGlobalContextPrompt()
            var fullAnswer = ""
            llmService.streamGenerate(
                prompt = question,
                settings = settings,
                systemPrompt = systemPrompt,
                onToken = { token ->
                    fullAnswer += token
                    _uiState.update { it.copy(homeStreamLine = it.homeStreamLine + token) }
                },
                onReasoning = { reasoning ->
                    _uiState.update { it.copy(aiIslandReasoning = reasoning) }
                }
            )
            val assistantMsg = HomeChatMessage(role = "assistant", content = fullAnswer)
            _uiState.update { it.copy(
                homeMessages = it.homeMessages + assistantMsg,
                homeStreamLine = "",
                homeIsLoading = false
            ) }
            homeChatDao.insertMessage(HomeMessageEntity(
                id = UUID.randomUUID().toString(),
                conversation_id = convId,
                role = "assistant",
                content = fullAnswer,
                tool_calls_json = null,
                timestamp = System.currentTimeMillis()
            ))
            homeChatDao.insertConversation(HomeConversationEntity(
                id = convId,
                title = question.take(50).replace("\n", " "),
                created_at = _uiState.value.homeMessages.firstOrNull()?.timestamp
                    ?: System.currentTimeMillis(),
                updated_at = System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update {
                it.copy(aiErrorMessage = e.message ?: "请求失败", homeIsLoading = false)
            }
        }
    }

    private suspend fun runHomeAgent(question: String, convId: String) {
        val priorMessages = homeAgentMessageCache["_current"] ?: emptyList()
        val collectedMessages = mutableListOf<ChatMessage>()

        homeAgentService.runHomeAgentLoop(
            question = question,
            priorMessages = priorMessages,
            onReasoning = { reasoning ->
                _uiState.update { it.copy(aiIslandReasoning = reasoning) }
            },
            onToolCall = { toolName, args, result ->
                val display = buildString {
                    append("🔧 调用工具: $toolName\n")
                    if (args.isNotBlank() && args != "{}") append("  参数: $args\n")
                    append("  结果: ${result.take(500)}")
                    if (result.length > 500) append("…")
                }
                _uiState.update { it.copy(
                    homeStreamLine = it.homeStreamLine + display + "\n\n"
                ) }
            },
            onToken = { token ->
                _uiState.update { it.copy(homeStreamLine = it.homeStreamLine + token) }
            },
            onAgentMessages = { messages ->
                collectedMessages.clear()
                collectedMessages.addAll(messages)
                homeAgentMessageCache["_current"] = messages
            },
            onComplete = { answer ->
                val assistantMsg = HomeChatMessage(role = "assistant", content = answer)
                _uiState.update { it.copy(
                    homeMessages = it.homeMessages + assistantMsg,
                    homeStreamLine = "",
                    homeIsLoading = false
                ) }
                // Persist messages
                val entities = collectedMessages.filter { m ->
                    m.content != null && (m.content is String && (m.content as String).isNotBlank())
                }.map { m ->
                    val tcJson = if (!m.tool_calls.isNullOrEmpty()) Gson().toJson(m.tool_calls) else null
                    HomeMessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversation_id = convId,
                        role = m.role,
                        content = (m.content as? String) ?: "",
                        tool_calls_json = tcJson,
                        timestamp = System.currentTimeMillis()
                    )
                }
                homeChatDao.insertMessages(entities)
                homeChatDao.insertConversation(HomeConversationEntity(
                    id = convId,
                    title = question.take(50).replace("\n", " "),
                    created_at = _uiState.value.homeMessages.firstOrNull()?.timestamp
                        ?: System.currentTimeMillis(),
                    updated_at = System.currentTimeMillis()
                ))
            },
            onError = { msg ->
                _uiState.update {
                    it.copy(aiErrorMessage = msg, homeIsLoading = false)
                }
            }
        )
    }

    private suspend fun buildGlobalContextPrompt(): String {
        val subjects = _uiState.value.subjects
        return buildString {
            append("你是灵记的笔记助手，可以回答用户关于笔记的问题。\n\n")
            append("用户共有 ${subjects.size} 个主题：\n")
            for (s in subjects) {
                append("- [${s.id}] ${s.title} (${s.type.name})\n")
            }
            append("\n请根据以上信息回答用户问题。如果需要更详细的笔记内容，请让用户切换到 Agent 模式使用工具查询。")
        }
    }
}

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 5: UI — HomeChatBar (capsule input bar)

**Covers:** [S6]

**Files:**
- Create: `app/src/main/java/com/lingji/app/ui/chat/HomeChatBar.kt`

**Interfaces:**
- Consumes: `ChatMode` (existing enum in PageChatBar.kt)
- Produces: `HomeChatBar(onClick, onModeToggle, currentMode, modifier)` composable

- [ ] **Step 1: Create HomeChatBar composable**

```kotlin
// app/src/main/java/com/lingji/app/ui/chat/HomeChatBar.kt
package com.lingji.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.ui.components.ChatMode

@Composable
fun HomeChatBar(
    currentMode: ChatMode,
    onModeToggle: (ChatMode) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💬",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.home_chat_placeholder),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val modeLabel = if (currentMode == ChatMode.ASK) "ASK" else "AGENT"
            Surface(
                modifier = Modifier.clickable {
                    onModeToggle(if (currentMode == ChatMode.ASK) ChatMode.AGENT else ChatMode.ASK)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (currentMode == ChatMode.AGENT)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = modeLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (currentMode == ChatMode.AGENT)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 6: UI — HomeChatSheet (bottom sheet dialog)

**Covers:** [S6]

**Files:**
- Create: `app/src/main/java/com/lingji/app/ui/chat/HomeChatSheet.kt`

**Interfaces:**
- Consumes: `HomeChatMessage`, `ChatMode`, `HomeConversationEntity`
- Produces: `HomeChatSheet(messages, streamLine, isLoading, mode, conversations, currentConversationId, onSend, onModeChange, onNewConversation, onLoadConversation, onDeleteConversation, onDismiss)` composable

- [ ] **Step 1: Create HomeChatSheet composable**

```kotlin
// app/src/main/java/com/lingji/app/ui/chat/HomeChatSheet.kt
package com.lingji.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.ui.components.ChatMode
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.MarkdownView
import com.lingji.app.ui.viewmodel.HomeChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChatSheet(
    messages: List<HomeChatMessage>,
    streamLine: String,
    isLoading: Boolean,
    currentMode: ChatMode,
    conversations: List<HomeConversationEntity>,
    currentConversationId: String?,
    onSend: (String) -> Unit,
    onModeChange: (ChatMode) -> Unit,
    onNewConversation: () -> Unit,
    onLoadConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, streamLine) {
        if (messages.isNotEmpty() || streamLine.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Top Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_chat_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // History button
                Box {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.home_chat_history)
                        )
                    }
                    DropdownMenu(
                        expanded = showHistory,
                        onDismissRequest = { showHistory = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_chat_new_conversation)) },
                            onClick = {
                                onNewConversation()
                                showHistory = false
                            },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                        )
                        if (conversations.isNotEmpty()) {
                            conversations.take(20).forEach { conv ->
                                val isActive = conv.id == currentConversationId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = conv.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = dateFormat.format(Date(conv.updated_at)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onLoadConversation(conv.id)
                                        showHistory = false
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            onDeleteConversation(conv.id)
                                            showHistory = false
                                        }) {
                                            Icon(
                                                Icons.Filled.DeleteSweep,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // --- Mode Switch ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                TextButton(
                    onClick = { onModeChange(ChatMode.ASK) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "问答",
                        color = if (currentMode == ChatMode.ASK)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onModeChange(ChatMode.AGENT) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Agent",
                        color = if (currentMode == ChatMode.AGENT)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Messages ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && streamLine.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_chat_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(messages) { msg ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (msg.role == "user") "👤" else "🤖",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (msg.role == "user") "你" else "灵记",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            MarkdownView(
                                content = msg.content,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (msg.role == "user")
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .padding(12.dp)
                            )
                        }
                    }

                    // Streaming content
                    if (streamLine.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🤖", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "灵记",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                MarkdownView(
                                    content = streamLine,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .padding(12.dp)
                                )
                            }
                        }
                    }

                    // Loading indicator
                    if (isLoading && streamLine.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // --- Input Area ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                GlassOutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text(stringResource(R.string.home_chat_placeholder)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 120.dp),
                    singleLine = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            onSend(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isLoading)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 7: Integrate into SubjectGalleryScreen

**Covers:** [S2, S6]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt`

**Interfaces:**
- Consumes: `HomeChatBar`, `HomeChatSheet`, new ViewModel methods
- Produces: Home chat integrated into gallery Scaffold

- [ ] **Step 1: Add HomeChatBar to the bottom of SubjectGalleryScreen**

In `SubjectGalleryScreen`, wrap the existing `Scaffold` content or use a `Box` overlay. Add:

```kotlin
// In SubjectGalleryScreen, at the bottom of the layout, inside a Box wrapper:

// If sheet is not expanded, show the capsule bar at the bottom
if (!uiState.homeChatExpanded) {
    HomeChatBar(
        currentMode = uiState.homeChatMode,
        onModeToggle = { viewModel.setHomeChatMode(it) },
        onClick = {
            viewModel.toggleHomeChat()
            if (uiState.homeConversations.isEmpty()) {
                viewModel.loadHomeConversations()
            }
        },
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}

// Show the bottom sheet when expanded
if (uiState.homeChatExpanded) {
    HomeChatSheet(
        messages = uiState.homeMessages,
        streamLine = uiState.homeStreamLine,
        isLoading = uiState.homeIsLoading,
        currentMode = uiState.homeChatMode,
        conversations = uiState.homeConversations,
        currentConversationId = uiState.homeCurrentConversationId,
        onSend = { text -> viewModel.sendHomeMessage(text) },
        onModeChange = { viewModel.setHomeChatMode(it) },
        onNewConversation = { viewModel.startNewConversation() },
        onLoadConversation = { viewModel.loadConversation(it) },
        onDeleteConversation = { viewModel.deleteConversation(it) },
        onDismiss = { viewModel.toggleHomeChat() }
    )
}
```

The existing `Scaffold` with `TopAppBar` stays — the chat bar and sheet are overlays on top.

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 8: String Resources

**Covers:** [S6]

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add home chat strings**

Add to strings.xml:

```xml
<!-- Home Chat -->
<string name="home_chat_title">灵记对话</string>
<string name="home_chat_placeholder">向灵记提问...</string>
<string name="home_chat_new_conversation">新对话</string>
<string name="home_chat_history">历史对话</string>
<string name="home_chat_delete_confirm">确定删除此对话？</string>
<string name="home_chat_empty">开始与灵记对话吧</string>
<string name="home_chat_mode_ask">问答</string>
<string name="home_chat_mode_agent">Agent</string>
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`

---

### Task 9: Verify — Compile + Install

- [ ] **Step 1: Full compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install to emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, INSTALL SUCCESS

- [ ] **Step 3: Integration check**

Verify in emulator:
1. Home page shows "向灵记提问..." capsule bar at bottom
2. Tapping it opens bottom sheet
3. ASK mode: type a question → streaming response
4. AGENT mode: type a command → tool calls visible
5. Close and reopen → conversation history persists

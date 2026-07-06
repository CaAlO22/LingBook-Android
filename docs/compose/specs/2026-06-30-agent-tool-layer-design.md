# Agent 笔记工具层设计

## [S1] 背景与目标

灵记是一款基于 Kotlin + Jetpack Compose + Hilt + Room 的笔记应用。应用内的 AI 功能（碎片合并、页面问答、笔记重构等）当前以硬编码 prompt 形式集成在 `LLMService.kt` 中，LLM 无法主动操作笔记数据。

本设计为未来 agent 升级打基座：构建一个**独立的工具层**，将笔记操作封装为 OpenAI function calling 格式的工具定义。该层作为独立组件存在，不改动 `LLMService.kt`、`SettingsScreen.kt`、`Models.kt` 等现有文件的核心逻辑。未来 agent 升级后可直接接入此工具层，通过 tool calling 自主操作笔记。

### 约束

- **不改动现有功能**：工具层是纯增量，现有 LLM 调用路径不受影响
- **遵循项目开闭原则**（见 AGENTS.md）：新增工具 = 新建文件 + 注册一行
- **OpenAI function calling 格式**：工具定义采用 `{type: "function", function: {name, description, parameters}}` 结构

## [S2] 核心抽象

### Tool 接口

```kotlin
package com.lingji.app.domain.tool

interface Tool {
    /** OpenAI function name，蛇形命名，如 "create_subject" */
    val name: String

    /** 给 LLM 看的工具说明 */
    val description: String

    /** JSON Schema 参数定义，使用 Gson JsonObject */
    val parameters: JsonObject

    /** 执行工具，返回结果文本（通常是 JSON 字符串供 LLM 解析） */
    suspend fun execute(params: JsonObject): String
}
```

### toOpenAITool 扩展函数

```kotlin
fun Tool.toOpenAITool(): JsonObject = buildJsonObject {
    "type" to "function"
    "function" to buildJsonObject {
        "name" to name
        "description" to description
        "parameters" to parameters
    }
}
```

### JsonSchemaBuilder 辅助

提供 `buildJsonObject` 和 `buildJsonArray` 辅助函数，封装 Gson `JsonObject`/`JsonArray` 的构造，避免裸 Gson API 的冗长链式调用。这是工具层内部使用的便利函数，不对外暴露。

## [S3] 注册表与依赖注入

### ToolRegistry

```kotlin
@Singleton
class ToolRegistry @Inject constructor(
    subjectRepository: SubjectRepository,
    llmService: LLMService,
    settingsRepository: SettingsRepository,
    subjectSummaryDao: SubjectSummaryDao
) {
    private val tools: Map<String, Tool> = buildList {
        addAll(SubjectTools.create(subjectRepository))
        addAll(PageTools.create(subjectRepository))
        addAll(FragmentTools.create(subjectRepository))
        addAll(NoteTools.create(subjectRepository))
        addAll(SearchTools.create(subjectRepository, llmService, settingsRepository, subjectSummaryDao))
    }.associateBy { it.name }

    fun getTool(name: String): Tool?
    fun getAllTools(): List<Tool>
    fun toOpenAITools(): JsonArray  // 所有工具的 OpenAI 格式数组

    suspend fun executeTool(name: String, params: JsonObject): String {
        // 1. 查找工具，未知工具返回 "Error: Unknown tool '$name'"
        // 2. runCatching 执行，异常包装为 "Error: ${it.message}"
        // 3. 返回工具结果字符串
    }
}
```

### 依赖注入策略

- 工具类是普通 class，不标注 `@Inject`，不被 Hilt 直接感知
- 每个工具包提供工厂方法（如 `SubjectTools.create(repo): List<Tool>`）批量创建
- `ToolRegistry` 通过 Hilt `@Inject` 注入 Repository/Service/Dao，再传给各工厂方法
- 新增工具只需修改它所在工具包的工厂方法，不影响 `ToolRegistry` 构造函数

### DI 注册变更

在 `AppModule.kt` 中新增：

```kotlin
@Provides
fun provideSubjectSummaryDao(database: LingjiDatabase) = database.subjectSummaryDao()
```

不改动现有绑定。`ToolRegistry` 通过 Hilt 自动构造注入。

## [S4] 数据层变更 — SubjectSummaryEntity

### 新建实体

```kotlin
@Entity(tableName = "subject_summaries")
data class SubjectSummaryEntity(
    @PrimaryKey val subjectId: String,
    val summary: String,
    val summarizedAt: Long
)
```

### 新建 DAO

```kotlin
@Dao
interface SubjectSummaryDao {
    @Query("SELECT * FROM subject_summaries")
    suspend fun getAll(): List<SubjectSummaryEntity>

    @Query("SELECT * FROM subject_summaries WHERE subjectId = :subjectId")
    suspend fun getBySubjectId(subjectId: String): SubjectSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubjectSummaryEntity)

    @Query("DELETE FROM subject_summaries WHERE subjectId = :subjectId")
    suspend fun deleteBySubjectId(subjectId: String)
}
```

### 数据库迁移

- `LingjiDatabase` 版本从 5 升至 6
- 新增 `MIGRATION_5_6`：`CREATE TABLE IF NOT EXISTS subject_summaries (subjectId TEXT NOT NULL PRIMARY KEY, summary TEXT NOT NULL, summarizedAt INTEGER NOT NULL)`
- 在 `@Database` entities 中添加 `SubjectSummaryEntity::class`
- 在 `AppModule.provideDatabase` 中添加 `MIGRATION_5_6`
- 新增 `abstract fun subjectSummaryDao(): SubjectSummaryDao`

## [S5] 工具清单

共 21 个工具，按实体分组。name 采用蛇形命名。

### Subject 管理（5 个）

| name | 参数 | 返回 | 说明 |
|---|---|---|---|
| `list_subjects` | 无 | `[{id, title, type}]` | 列出所有笔记 |
| `get_subject` | `subject_id` | `{id, title, type, page_count, fragment_count}` | 获取笔记概览 |
| `create_subject` | `title`, `type?`(默认"notebook") | `{id, title, type}` | 创建笔记 |
| `delete_subject` | `subject_id` | `{success: true}` | 删除笔记及其所有页面/碎片/摘要 |
| `rename_subject` | `subject_id`, `new_title` | `{success: true}` | 重命名 |

### NotebookPage 管理（5 个）

| name | 参数 | 返回 | 说明 |
|---|---|---|---|
| `list_pages` | `subject_id` | `[{id, title, order}]` | 列出笔记的页面 |
| `get_page` | `subject_id`, `page_id` | `{id, title, content, updated_at}` | 读取页面完整内容 |
| `create_page` | `subject_id`, `title?`, `content?` | `{id, title}` | 在末尾新增页面 |
| `update_page` | `subject_id`, `page_id`, `title?`, `content?` | `{success: true}` | 更新页面标题/内容 |
| `delete_page` | `subject_id`, `page_id` | `{success: true}` | 删除页面 |

### Fragment 管理（4 个）

| name | 参数 | 返回 | 说明 |
|---|---|---|---|
| `list_fragments` | `subject_id` | `[{id, content, timestamp}]` | 列出碎片（含未合并） |
| `add_fragment` | `subject_id`, `content` | `{id}` | 添加碎片 |
| `update_fragment` | `subject_id`, `fragment_id`, `content` | `{success: true}` | 修改碎片内容 |
| `delete_fragment` | `subject_id`, `fragment_id` | `{success: true}` | 删除碎片 |

### 聚合笔记与学习计划（4 个）

| name | 参数 | 返回 | 说明 |
|---|---|---|---|
| `get_aggregated_note` | `subject_id` | `{content}` | 读取聚合笔记 |
| `update_aggregated_note` | `subject_id`, `content` | `{success: true}` | 更新聚合笔记 |
| `get_study_plan` | `subject_id` | `{content}` | 读取学习计划 |
| `update_study_plan` | `subject_id`, `content` | `{success: true}` | 更新学习计划 |

### 索引与搜索（2 个）

| name | 参数 | 返回 | 说明 |
|---|---|---|---|
| `get_page_index` | `subject_id` | `[{page_id, title, keywords, summary}]` | 读取页面索引 |
| `search_pages` | `query`, `subject_id?` | `[{subject_id, page_id, title, snippet, score}]` | 按关键词搜索页面。复用 `IndexService.searchPages` 逻辑。subject_id 可选，不传则搜索全部笔记 |

### 全局摘要（1 个）

| name | 参数 | 返回 | 说明 |
|---|---|---|---|
| `summarize_all_notes` | 无 | `[{subject_id, subject_title, summary}]` | 见 [S6] |

## [S6] summarize_all_notes 工具详细设计

### 用途

为 agent 提供全局笔记概览。当 agent 需要了解用户有哪些笔记、各自讲了什么时调用此工具，获取所有笔记的标题+摘要，从而决定进一步阅读哪个笔记。

### 执行流程

1. 调用 `SubjectRepository.getAllSubjects()` 的 suspend 一次读取（非 Flow），获取全部笔记
2. 对每个 Subject：
   a. 查询 `SubjectSummaryDao.getBySubjectId(subjectId)` 获取缓存摘要
   b. 判断是否需要重新摘要：**无缓存** 或 **摘要后笔记有修改**
      - NOTEBOOK 类型：比较 `summarizedAt` 与该 subject 下所有 page 的 `updatedAt` 最大值
      - FRAGMENT 类型：比较 `summarizedAt` 与该 subject 下所有 fragment 的 `timestamp` 最大值
   c. 如需重新摘要：
      - NOTEBOOK：拼接所有 page 的 title+content 作为输入
      - FRAGMENT：使用 `aggregatedNote`（若为空则拼接所有 fragment content）作为输入
      - 调用 `LLMService.generate()` 生成摘要（system prompt 指示生成 100-200 字摘要）
      - 调用 `SubjectSummaryDao.upsert()` 缓存
3. 收集所有 subject 的摘要，返回 JSON 数组

### LLM 调用

- 通过 `SettingsRepository.getSettingsOnce()` 获取当前 AI 配置
- system prompt: `"你是一个摘要专家。请用100-200字概括以下笔记的核心内容，突出主题和关键知识点。只输出摘要文本，不要添加任何额外格式或说明。"`
- prompt: `"笔记标题：{title}\n\n笔记内容：\n{content}"`
- 使用 `LLMService.generate()`（非流式），关闭 thinking

### 异常处理

- 单个 subject 摘要失败时，该 subject 的 summary 字段返回 `"摘要生成失败"`，不影响其他 subject
- LLM 未配置时，所有 summary 返回 `"AI未配置"`

## [S7] 目录结构

```
app/src/main/java/com/lingji/app/
├── data/
│   ├── db/
│   │   ├── dao/
│   │   │   └── SubjectSummaryDao.kt          # 新增
│   │   ├── entities/
│   │   │   └── SubjectSummaryEntity.kt       # 新增
│   │   └── LingjiDatabase.kt                 # 修改：版本 5→6，加 entity + dao + migration
│   └── di/
│       └── AppModule.kt                       # 修改：加一行 provideSubjectSummaryDao
├── domain/
│   └── tool/                                  # 新增整个目录
│       ├── Tool.kt                            # 接口 + toOpenAITool 扩展
│       ├── ToolRegistry.kt                    # 注册表
│       ├── JsonSchemaBuilder.kt               # JSON Schema 构造辅助
│       ├── subject/
│       │   └── SubjectTools.kt                # 5 个 Subject 工具
│       ├── page/
│       │   └── PageTools.kt                   # 5 个 Page 工具
│       ├── fragment/
│       │   └── FragmentTools.kt               # 4 个 Fragment 工具
│       ├── note/
│       │   └── NoteTools.kt                   # 4 个笔记/学习计划工具
│       └── search/
│           └── SearchTools.kt                 # 2 个索引/搜索 + 1 个全局摘要工具
```

### 修改的现有文件

仅 2 个现有文件被修改，改动均为纯增量：

1. **`LingjiDatabase.kt`**：版本号 5→6，entities 列表加 `SubjectSummaryEntity`，加 `MIGRATION_5_6`，加 `subjectSummaryDao()` 抽象方法
2. **`AppModule.kt`**：加 `provideSubjectSummaryDao` 方法

不改动 `LLMService.kt`、`SettingsScreen.kt`、`Models.kt`、`SubjectRepository.kt`、`IndexService.kt` 等文件。

## [S8] 错误处理与返回格式

### 返回格式

- 成功：工具返回 JSON 字符串，便于 LLM 解析（如 `{"id": "abc123", "title": "数学笔记"}`）
- 失败：`ToolRegistry.executeTool` 捕获异常，返回 `"Error: <message>"`
- 未知工具：返回 `"Error: Unknown tool '$name'"`

### 参数校验

- `ToolRegistry` 不做参数校验，由各工具自行从 JsonObject 中读取参数
- 必填参数缺失时，工具抛出 `IllegalArgumentException`，被 Registry 捕获为 `"Error: Missing required parameter: xxx"`
- 类型不匹配时同理

### 删除操作

- 删除不存在的资源返回 `"Error: Subject not found: xxx"` 而非静默成功
- 这让 LLM 能区分"删除成功"与"目标不存在"

## [S9] 测试策略

### 单元测试

每个工具包配一个测试类，mock `SubjectRepository`：

- **SubjectToolsTest**：验证 create/delete/rename/list/get 的参数解析与返回 JSON 格式
- **PageToolsTest**：验证页面 CRUD 与 orderIndex 处理
- **FragmentToolsTest**：验证碎片 CRUD
- **NoteToolsTest**：验证聚合笔记/学习计划读写
- **SearchToolsTest**：验证搜索结果格式；mock LLMService 验证 `summarize_all_notes` 缓存逻辑（已摘要不重复调用 LLM；摘要后修改笔记会触发重新摘要）
- **ToolRegistryTest**：注册完整性（21 个工具）、未知工具处理、异常包装

### 集成验证

- `./gradlew :app:compileDebugKotlin` 编译通过
- `./gradlew :app:installDebug` 同步到模拟器
- 手动验证数据库迁移（升级后旧数据不丢失）

## [S10] 与现有代码的关系

### 复用的现有组件

| 组件 | 用途 |
|---|---|
| `SubjectRepository` | 所有笔记/页面/碎片/聚合笔记/学习计划的数据操作 |
| `LLMService.generate()` | `summarize_all_notes` 工具调用 LLM 生成摘要 |
| `SettingsRepository.getSettingsOnce()` | 获取 AI 配置供摘要工具使用 |
| `IndexService.searchPages()` | `search_pages` 工具复用搜索逻辑 |
| `generateId()` | 工具创建资源时生成 ID |

### 不触碰的现有组件

| 组件 | 原因 |
|---|---|
| `LLMService.kt` | 工具层是数据消费者，不是 LLM 调用者（摘要工具仅复用 `generate()`） |
| `SettingsScreen.kt` | 无 UI 变更 |
| `Models.kt` | 不新增领域模型（SubjectSummary 是数据层实体，不暴露到 domain model） |
| `SubjectRepository.kt` | 工具直接调用现有方法，无需扩展 |
| `IndexService.kt` | `search_pages` 复用现有搜索方法，不修改 |

### 未来 agent 接入方式

当 agent 升级时：
1. `ToolRegistry.toOpenAITools()` 输出工具定义 → 放入 LLM 请求的 `tools` 参数
2. LLM 返回 `tool_calls` → 调用 `ToolRegistry.executeTool(name, params)` 执行
3. 执行结果作为 `tool` role 消息回传 LLM
4. 循环直到 LLM 不再请求工具调用

此循环逻辑属于未来 agent 实现，不在本工具层范围内。

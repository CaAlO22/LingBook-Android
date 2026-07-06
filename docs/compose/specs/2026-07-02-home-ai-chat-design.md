# 首页 AI 对话框 — 设计文档

## [S1] 问题

当前灵记的 AI 对话仅限于单个主题内部（Notebook/Fragment 详情页），用户无法跨主题提问或让 Agent 对全部笔记进行操作。需要在首页新增一个全局 AI 对话入口，支持跨笔记问答和 Agent 模式。

## [S2] 方案概述

采用**扩展架构方案**：在首页底部新增胶囊形输入条（类似现有 PageChatBar），点击展开为 Material3 ModalBottomSheet 全屏对话面板。支持 ASK（跨笔记问答）和 AGENT（全工具调用）两种模式。对话历史持久化到 Room。

### 技术栈

- 与现有架构一致：Jetpack Compose + Hilt + Room + OkHttp
- 复用现有 `MarkdownView`、`FilterChip`、流式渲染模式

## [S3] 架构与文件规划

### 新增文件

| 文件 | 作用 |
|------|------|
| `data/db/entity/HomeConversationEntity.kt` | Room 实体：对话 ID、标题、时间戳 |
| `data/db/entity/HomeMessageEntity.kt` | Room 实体：单条消息（role、content、tool_calls_json、timestamp） |
| `data/db/dao/HomeChatDao.kt` | DAO：CRUD 对话和消息 |
| `data/remote/HomeAgentService.kt` | Agent 循环服务，使用完整 ToolRegistry（20个工具） |
| `ui/chat/HomeChatBar.kt` | 底部胶囊输入条组件 |
| `ui/chat/HomeChatSheet.kt` | Bottom Sheet 对话面板（消息列表 + 输入区 + 模式切换） |

### 修改文件

| 文件 | 改动 |
|------|------|
| `data/db/LingjiDatabase.kt` | 新增 HomeConversationEntity、HomeMessageEntity、HomeChatDao，版本升级到 8 |
| `di/AppModule.kt` | 注入 HomeChatDao |
| `ui/viewmodel/SubjectViewModel.kt` | 新增 home chat 相关 State 和方法 |
| `ui/screens/SubjectGalleryScreen.kt` | 底部叠加 HomeChatBar + HomeChatSheet |

## [S4] 数据流与状态

### 消息流转

```
用户输入 → viewModel.chatWithHome(text, mode)
  ├─ mode == ASK → llmService.streamGenerate(...)
  │   注入全局上下文（所有主题摘要 + 页面索引）→ 流式输出
  └─ mode == AGENT → homeAgentService.runLoop(...)
      使用完整 ToolRegistry → 循环调用工具 → 流式输出

结果 → homeMessages 更新 → Room 持久化
```

### ViewModel State 新增

```kotlin
val homeChatExpanded: Boolean       // Sheet 展开/收起
val homeChatMode: ChatMode          // ASK / AGENT
val homeMessages: List<ChatMessage> // 当前对话消息
val homeStreamLine: String          // 流式输出当前行
val homeConversations: List<HomeConversation> // 历史对话列表
```

### ASK 模式上下文注入

System prompt 包含：
- 所有主题名称 + 摘要（通过 `SubjectSummaryDao`）
- Notebook 主题的页面索引关键词

## [S5] Agent 模式与工具

### 与现有 Agent 对比

| | 主题内 Agent（现有） | 首页 Agent（新增） |
|---|---|---|
| 工具集 | ScopedToolRegistry（17个） | 完整 ToolRegistry（20个） |
| 实现 | AgentService（112行） | HomeAgentService |
| subject_id | 自动注入 | 需显式传入 |

### 工具行为

| 工具类 | 工具 | 首页 Agent 行为 |
|--------|------|----------------|
| Subject | list/get/create/delete/rename | 完全可用 |
| Page | list/get/create/update/delete | 需 subject_id |
| Fragment | list/add/update/delete | 需 subject_id |
| Note | get/update_note, get/update_study_plan | 需 subject_id |
| Search | get_page_index/search_pages | 需 subject_id |
| Search | summarize_all | 无需参数 |
| Search | search_all_subjects（新增） | 跨主题混合搜索 |

### search_all_subjects 搜索策略

1. 查 SubjectSummaryDao 获取所有主题摘要
2. 查 NotebookPageDao 获取 notebook 主题的页面索引关键词
3. 碎片类：匹配主题名 + 摘要文本
4. Notebook 类：匹配主题名 + 摘要 + 页面索引关键词
5. 返回匹配列表（含匹配合集和摘要片段）

## [S6] UI 设计

### 胶囊输入条（收起态）

位于首页 Gallery 底部，始终悬浮不遮挡滑动：
- 左侧：💬 图标 + "向灵记提问..." 占位文字
- 右侧：模式标签 ASK / AGENT，可点击切换
- 点击整条 → 展开 Bottom Sheet

### Bottom Sheet（展开态）

- 标题栏："灵记对话" + 关闭按钮 + 历史对话入口
- 模式切换：ASK / AGENT FilterChip
- 消息列表：复用 MarkdownView + 流式渲染
- 工具调用卡片：可折叠，显示执行状态
- 输入区：底部固定，发送按钮

### 复用组件

- `MarkdownView` — 消息内容渲染
- `FilterChip` — 模式切换
- 现有流式渲染模式 — `processingStreamLine`

## [S7] 数据库

Room 版本 7 → 8，迁移策略：直接 addTable（无数据迁移）。

### home_conversations

| 字段 | 类型 | 说明 |
|------|------|------|
| id | TEXT PK | UUID |
| title | TEXT | 对话标题（首条消息摘要） |
| created_at | INTEGER | 创建时间戳 |
| updated_at | INTEGER | 最后活跃时间 |

### home_messages

| 字段 | 类型 | 说明 |
|------|------|------|
| id | TEXT PK | UUID |
| conversation_id | TEXT FK | 关联对话，级联删除 |
| role | TEXT | user / assistant / tool |
| content | TEXT | 消息文本 |
| tool_calls_json | TEXT? | 工具调用 JSON |
| timestamp | INTEGER | 消息时间戳 |

## [S8] 验证计划

1. `./gradlew :app:compileDebugKotlin` — 编译通过
2. `./gradlew :app:installDebug` — 安装到模拟器
3. 手动测试：ASK 模式跨笔记提问 → 验证回答引用多个主题
4. 手动测试：AGENT 模式 → 验证工具调用（列出主题、搜索笔记、创建笔记等）
5. 手动测试：对话持久化 → 关闭再打开，历史对话仍存在

# Agent 笔记修改撤销功能设计

> [!NOTE]
> This document may not reflect the current implementation.
> See the final report for up-to-date state:
> [Final Report](../reports/agent-note-undo.md)

- 日期：2026-07-20
- 状态：已批准，待实现
- 决策来源：多步撤销 + 按 agent 会话整体撤销（用户确认）

## [S1] 问题与目标

当前 FRAGMENT 类型笔记被 agent 修改后有单步回滚（`prevAggregatedNote` 单字段，顶部菜单"回滚"），但存在两个问题：

1. **NOTEBOOK 类型单页被 agent 修改后完全无法撤销**：`NotebookPageEntity` 无 prev 字段，`NotebookSubjectScreen` 无回滚入口。
2. **FRAGMENT 仅单步撤销**：agent 连续多次修改无法逐步回退。

**目标**：两种类型统一支持多步撤销；agent 一轮会话里对多页的修改作为一个整体撤销点（点一次撤销，回退整轮）。

## [S2] 数据模型：`note_revisions` 表

新建 Room 表，记录每次内容修改**前**的快照：

```
note_revisions(
  id          TEXT PK,          -- UUID
  subjectId   TEXT,             -- 所属笔记
  pageId      TEXT NULL,        -- NULL=聚合笔记(FRAGMENT)；非NULL=具体页(NOTEBOOK)
  field       TEXT,             -- "aggregated" | "page"
  prevContent TEXT,             -- 修改前的内容（全文快照）
  prevTitle   TEXT NULL,        -- page 类型时存修改前标题
  batchId     TEXT NULL,        -- 非NULL=agent 会话批次；NULL=用户手动修改
  createdAt   INTEGER           -- 时间戳，用于排序
)
```

设计要点：
- 存全文快照而非 diff——笔记含 base64 图片，diff 无意义且实现复杂。
- 每个 subject 保留最近 **20** 条，超出删最旧；同一 batch 的 revision 整体删，不拆散。
- 旧 `prevAggregatedNote` 字段保留不动，向后兼容，不破坏现有数据。

## [S3] batch 标记机制：协程上下文 `EditBatch`

新建协程上下文元素 `EditBatch(val batchId: String)`。

**注入点（ViewModel 层 4 处）**：在 `chatWithAgent`、`organize`、`refine`、`runHomeAgent` 的协程最外层包 `withContext(EditBatch(UUID)) { ... }`。内层 `AgentService` -> `ScopedToolRegistry` -> `NoteTools` -> `repo.update*` 全程自动继承协程上下文，**无需改任何中间层方法签名**。

**读取点（Repository 层）**：`SubjectRepository.updateAggregatedNote` / `updatePage` 是 suspend 函数，直接 `coroutineContext[EditBatch]?.batchId` 取 batchId 存进 revision。用户手动保存不包 `EditBatch`，batchId 为 null。

**优势**：对现有 agent 调用链（AgentService、ScopedToolRegistry、NoteTools、ToolRegistry）零侵入。这是该方案的核心。

**恢复操作防循环**：`rollbackLast` 恢复内容时用 `withContext(NoRevision)` 或内部 flag 跳过 revision 记录，避免撤销产生新历史。

## [S4] 写入时机与撤销逻辑

### 写入（更新数据库之前存快照）

- `updateAggregatedNote(id, content)`：先读当前 `aggregatedNote`，存 revision(field=aggregated, prevContent=当前值, batchId)，再更新。
- `updatePage(subjectId, page)`：先读当前 page，存 revision(field=page, pageId=page.id, prevContent+prevTitle=旧值, batchId)，再更新。

### 撤销 `rollbackLast(subjectId)`

1. 查该 subject 最近一条 revision（createdAt 降序）。无则返回"无可撤销"。
2. 取其 batchId：
   - **非 null**：找同 batchId 下该 subject 的所有 revision。按 pageId 分组（聚合笔记视作一组），每组取**最早**一条的 prevContent/prevTitle 恢复（= batch 开始前的状态）。删除该 batch 全部 revision。
   - **null**：单条恢复，删除该条。
3. 恢复时：
   - field=aggregated -> `subjectDao.updateAggregatedNote(id, prevContent, prev)`
   - field=page -> `pageDao.update(pageId, prevTitle, prevContent, updatedAt)`

### 多步撤销

每次撤销删掉最近一个 batch 的 revision，次新 batch 自动成为新的"最近"。revision 表天然支持多步。点一次"撤销"退一个撤销点（一个 batch = 一轮 agent 会话的全部改动，或一条用户手动修改）。

## [S5] 覆盖范围与边界

### 覆盖

- `edit_replace` 改 page content（NOTEBOOK）/ aggregatedNote（FRAGMENT）
- `update_page` 改标题（顺带覆盖，同一 `repo.updatePage` 方法）
- `organize` / `refine` 整体重写聚合笔记（ViewModel 直接调 `repo.updateAggregatedNote`）
- 用户手动保存（batchId=null，作为独立撤销点）
- 首页 agent（HomeAgentService）跨 subject 修改——在某个 subject 界面只撤该 subject 的部分

### 不覆盖（第一版，YAGNI）

- `create_page` / `delete_page` 结构操作
- `add_fragment` / `delete_fragment` 碎片增删
- `update_study_plan` 学习计划
- `organize` 的 `completeBatchMerge` 碎片合并状态

## [S6] UI 入口

- **FRAGMENT**：现有顶部 MoreVert 菜单"回滚"改为"撤销"，调用新的 `undoLastEdit()`。无可撤销时禁用或 Toast 提示。
- **NOTEBOOK**：顶部 MoreVert 菜单新增"撤销"项，同样调用 `undoLastEdit()`。
- 撤销后 Toast 提示"已撤销最近一次修改"。
- 不做历史列表选择 UI（点一次撤一步，保持简单）。

## [S7] 影响文件清单

| 文件 | 改动 |
|------|------|
| `NoteRevisionEntity.kt` | **新建**：Room 实体 |
| `NoteRevisionDao.kt` | **新建**：insert / 查最近 / 按 batch 查 / 删除 / 清理超额 |
| `AppDatabase.kt` | 加入新实体（version +1） |
| `EditBatch.kt` | **新建**：协程上下文元素 |
| `SubjectRepository.kt` | update 方法加 revision 存档；新增 `rollbackLast`；注入 NoteRevisionDao |
| `SubjectViewModel.kt` | chatWithAgent/organize/refine/runHomeAgent 包 withContext(EditBatch)；新增 `undoLastEdit`；废弃 `rollbackAggregatedNote` |
| `FragmentSubjectScreen.kt` | "回滚"->"撤销"，调新方法 |
| `NotebookSubjectScreen.kt` | MoreVert 菜单加"撤销"项 |
| `strings.xml` | 新增撤销相关字符串 |
| `NoteToolsTest.kt` 等 | 更新受影响测试 |

## 验证

实现完成后执行 `./gradlew :app:compileDebugKotlin`，并同步模拟器 `./gradlew :app:installDebug`（依 AGENTS.md 规范）。

---
feature: agent-note-undo
status: delivered
specs:
  - docs/compose/specs/2026-07-20-agent-note-undo-design.md
plans:
  - docs/compose/plans/2026-07-20-agent-note-undo.md
branch: main
commits: uncommitted (pending user approval)
---

# Agent 笔记修改撤销功能 - Final Report

## What Was Built

为灵记 LingBook 的 FRAGMENT 和 NOTEBOOK 两种笔记类型实现了多步撤销功能。agent（AI 助手）一轮会话中对笔记的修改作为一个整体撤销点--点一次"撤销修改"，回退整轮 agent 操作（即使改了多个页）。用户手动保存也作为独立撤销点，可逐步回退。

之前 FRAGMENT 类型仅有单步回滚（`prevAggregatedNote` 单字段），NOTEBOOK 类型单页被 agent 修改后完全无法撤销。现在两者统一支持多步撤销，共享同一套 `note_revisions` 历史表。

## Architecture

**数据层**：新建 `note_revisions` Room 表（`NoteRevisionEntity` + `NoteRevisionDao`），记录每次内容修改前的全文快照：`subjectId, pageId?(null=聚合笔记), field(aggregated|page), prevContent, prevTitle?, batchId?(null=用户手动), createdAt`。数据库 version 12 -> 13，含 `MIGRATION_12_13`。每 subject 保留最近 20 条。

**batch 标记**：通过 Kotlin 协程上下文元素 `EditBatch(batchId)` 在 ViewModel 层注入。`chatWithAgent`/`organize`/`refine`/`runHomeAgent` 的协程最外层用 `withContext(EditBatch(UUID.randomUUID().toString()))` 包裹。`SubjectRepository` 的 suspend update 方法从 `coroutineContext[EditBatch]?.batchId` 读取 batchId 存入 revision。用户手动保存不包 `EditBatch`，batchId 为 null。

这是核心设计优势：对现有 agent 调用链（`AgentService`、`ScopedToolRegistry`、`NoteTools`、`ToolRegistry`）**零侵入**--无需改任何中间层方法签名。

**撤销逻辑**：`SubjectRepository.rollbackLast(subjectId)` 取最近一条 revision。若 batchId 非 null，找同 batch 所有 revision，按 pageId 分组取每组最早一条的 prevContent/prevTitle 恢复（= batch 开始前状态），删除该 batch 全部 revision；若 batchId null，单条恢复。恢复时直接调 DAO（绕过 update 方法，避免产生新 revision）。

**写入时机**：`updateAggregatedNote` / `updatePage` 在更新数据库前，先读当前值存一条 revision（含 batchId），再更新。多步撤销天然支持--每次 rollbackLast 删掉最近一个 batch 的 revision，次新 batch 成为新的"最近"。

## Design Decisions

- **协程上下文注入 batchId（vs 显式参数透传）**：选择协程上下文因为它对 AgentService/ScopedToolRegistry/NoteTools/ToolRegistry 零侵入，只改 ViewModel（包裹 withContext）和 Repository（读 coroutineContext）。显式参数需改 4-5 层签名。
- **全文快照（vs diff）**：笔记含 base64 图片，diff 无意义且实现复杂。全文快照简单可靠，配合每 subject 20 条上限控制空间。
- **按 batch 整体撤销（vs 逐条/逐页）**：agent 一轮改多页时，逐页撤销体验差（要撤多次）。batch 整体撤销一次回退整轮。
- **不覆盖结构操作（create/delete page）**：YAGNI，第一版聚焦内容修改。恢复被删页需存整页快照，复杂度高且发生频率低。
- **删除旧 rollbackAggregatedNote**：新 `rollbackLast` 完全替代，保留旧 `prevAggregatedNote` 字段向后兼容（不破坏现有数据）。

## Usage

- **FRAGMENT 笔记**：顶部 MoreVert 菜单 -> "撤销修改"。
- **NOTEBOOK 笔记**：顶部 MoreVert 菜单 -> "撤销修改"。
- 点一次撤一个撤销点（一轮 agent 会话的全部改动，或一次用户手动保存）。
- 无可撤销时：Toast "没有可撤销的修改"。
- 撤销成功：Toast "已撤销最近一次修改"。

## Verification

- **单元测试**：10 个新测试全通过（`EditBatchTest` 3 + `SubjectRepositoryUndoTest` 7）。覆盖：EditBatch 协程上下文传播/嵌套、revision 存档（带/不带 batchId）、rollbackLast（无历史/单条/batch 多页/batch 同对象多次取最早）。
- **全量测试**：72/73 通过。唯一失败的 `Codec82Test > decode user provided web ling82 file` 是预先存在的环境依赖（缺 `tmp/web_lingcode.txt` 文件），与本次改动无关。
- **编译**：`./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL。
- **模拟器**：`./gradlew :app:installDebug` 成功，Installed on 1 device。
- **手动验证**：待用户在模拟器上验证 6 项清单（FRAGMENT/NOTEBOOK 各类撤销场景，见 plan Task 6 Step 4）。

## Journey Log

> 简要记录影响最终设计的经验。

- [lesson] 计划 Task 3 删除了 `SubjectRepository.rollbackAggregatedNote`，但引用方（ViewModel、FragmentSubjectScreen）在 Task 4/5 才改，导致中间编译失败。删除公共 API 和更新所有引用方应在同一 task。
- [pivot] 原设计用 `NoRevision` 上下文标记防止撤销产生新历史，实际改为 `rollbackLast` 直接调 DAO 绕过 update 方法，更简单。

## Source Materials

| File | Role | Notes |
|------|------|-------|
| `docs/compose/specs/2026-07-20-agent-note-undo-design.md` | 设计文档 | [S1]-[S7] 章节锚 |
| `docs/compose/plans/2026-07-20-agent-note-undo.md` | 实现计划 | 6 个 task，已全部完成 |

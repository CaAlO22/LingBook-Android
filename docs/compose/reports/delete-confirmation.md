---
feature: delete-confirmation
status: delivered
specs:
  - docs/compose/specs/2026-08-16-delete-confirmation-design.md
plans:
  - docs/compose/plans/2026-08-16-delete-confirmation.md
branch: main
commits: 4f8938d..75b1c90
---

# 删除操作统一二次确认 - Final Report

## What Was Built

App 内所有删除与清空类操作现在都有统一的二次确认弹窗，防止误删。新增通用组件 `DeleteConfirmDialog`（红色破坏性确认按钮 + 取消按钮），共覆盖 10 处操作：

**替换原有 3 处手写确认**（行为不变，仅统一为通用组件）：删除笔记（SubjectCard）、删除文件夹（SubjectGalleryScreen）、笔记本删除页面（原 DeletePageDialog 已删除）。

**新增 7 处确认**：
- 删除碎片（碎片学科页 FragmentList 尾部 Delete 图标）
- 删除首页 AI 会话（含级联消息删除，HomeChatSheet 历史菜单 DeleteSweep）
- 删除首页单条碎片（HomeChatSheet 碎片尾部 Close 图标）
- 清空笔记聊天历史（持久化，FragmentSubjectScreen 的 PageChatBar）
- 清空页面聊天历史（内存态，NotebookSubjectScreen 的同一 PageChatBar 图标）
- 「新对话」隐式清空碎片：仅当存在未整理碎片时弹确认（文案含碎片数量），无碎片时直接执行
- 「整理碎片」隐式清空碎片列表：点击后先弹确认

**明确不加确认**（用户决策）：Agent 聊天工具删除（delete_subject/delete_page/delete_fragment）、页面内图片段删除（已有 undo）、从文件夹移出笔记（数据保留）、草稿类操作（待发送图片、索引关键词）。

## Architecture

纯 UI 层改动，ViewModel/Repository/DAO 零改动。所有确认采用同一模式：点击删除入口 -> 屏幕层/组件内置 `pendingXxx` 状态 -> 渲染 `DeleteConfirmDialog` -> 确认后才调用 ViewModel 删除函数。

### 组件

`app/src/main/java/com/lingji/app/ui/components/LingjiDialog.kt` 新增：

```kotlin
@Composable
fun DeleteConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.delete)  // 清空类操作传「清空」「整理」等
)
```

内部复用 `LingjiDialog` 容器 + `LingjiDialogConfirmButton(isDestructive = true)`；`onDismiss` 同时承担取消按钮与点外部/返回键关闭两种语义。

### 弹窗状态持有位置

| 调用点 | 状态位置 | 状态 |
|---|---|---|
| SubjectCard / SubjectGalleryScreen（笔记/文件夹） | 组件内/屏幕层（沿袭原状） | `showDeleteDialog` / `deleteFolderId` |
| NotebookSubjectScreen（删页面/清空聊天） | 屏幕层 | `deleteConfirmPage` / `showClearChatConfirm` |
| HomeChatSheet（会话/碎片/新对话/整理） | 组件内（回调签名不变，屏幕层无感知） | `pendingDeleteConversation` / `pendingDeleteFragmentIndex` / `showNewConversationConfirm` / `showOrganizeConfirm` |
| FragmentSubjectScreen（碎片/聊天历史） | 屏幕层 | `pendingDeleteFragment` / `showClearChatConfirm` |

### 字符串资源

`strings.xml` 新增 `<!-- 删除确认 -->` 分组：7 条文案 + 复用既有的 `organize`（注意：strings.xml 已有同名定义，不可重复添加）。

### Design Decisions

- **组件加 `confirmText` 默认参数而非固定「删除」**：清空类操作（清空聊天历史）和继续类操作（清空并开始、整理）需要不同按钮文案，保持单一组件覆盖全部场景。
- **HomeChatSheet 弹窗状态放组件内**：4 个入口都在组件内部（下拉菜单/图标），组件内闭环避免向 SubjectGalleryScreen 泄漏 pending 状态；回调签名不变。
- **「新对话」仅在有碎片时弹确认**：无碎片的「新对话」没有破坏性副作用，弹窗反而打断正常流程。
- **页面聊天历史（内存态）也加确认**：与持久化的笔记聊天历史共用同一个 PageChatBar 删除图标，只加一处会造成交互不一致，且内存历史清了同样找不回。
- **DeletePageDialog 直接删除函数、调用处内联**：仅一处调用，其职责已被通用组件完全覆盖，保留空壳包装无意义。

## Usage

无配置项。开发者新增删除类操作时，直接复用：

```kotlin
pendingDeleteXxx?.let { item ->
    DeleteConfirmDialog(
        title = stringResource(R.string.delete),
        text = stringResource(R.string.xxx_confirm),
        onConfirm = { viewModel.deleteXxx(item.id); pendingDeleteXxx = null },
        onDismiss = { pendingDeleteXxx = null }
    )
}
```

## Verification

- `./gradlew :app:compileDebugKotlin`：BUILD SUCCESSFUL（每个 task 单独验证通过）
- `./gradlew :app:testDebugUnitTest`：73 个测试 72 通过；唯一失败 `Codec82Test > decode user provided web ling82 file` 系存量环境问题（依赖未入库的本地 fixture 文件 `tmp/web_lingcode.txt`，文件缺失），与本次改动无关（本次未触碰 util/ 层，git diff 确认）
- `./gradlew :app:installDebug`：APK 构建成功，但无可用模拟器/设备（AVD 列表为空、adb 无连接设备），按 AGENTS.md 规范跳过同步，手动点检清单（9 项）待有设备后执行

## Journey Log

- [lesson] strings.xml 已有 `organize`（整理）定义，新增同名字符串导致 `mergeDebugResources` 失败；给通用组件加文案前应先 grep 既有资源名。
- [lesson] `Codec82Test` 中有依赖本地 fixture 文件（`tmp/web_lingcode.txt`）的测试，跑全量单测时需区分存量环境失败与真实回归。
- [pivot] 用户中断 subagent 派发后选择会话内联执行剩余 task（Task 1 已完成部分由控制器收尾），流程从 compose:subagent 切到 compose:execute。
- [decision] 探索阶段发现「新对话/整理碎片」会隐式清空全部碎片（最易误删大量数据的入口），遂将其纳入确认范围并经用户确认。

## Source Materials

| File | Role | Notes |
|------|------|-------|
| `docs/compose/specs/2026-08-16-delete-confirmation-design.md` | 设计文档 | 覆盖范围与 7+3 处调用点清单 |
| `docs/compose/plans/2026-08-16-delete-confirmation.md` | 实现计划 | 6 task 分步与精确代码 |

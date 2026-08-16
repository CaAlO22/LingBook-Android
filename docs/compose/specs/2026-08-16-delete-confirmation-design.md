# 设计：删除操作统一二次确认

> [!NOTE]
> This document may not reflect the current implementation.
> See the final report for up-to-date state:
> [Final Report](../reports/delete-confirmation.md)

日期：2026-08-16
状态：已获用户批准

## [S1] 问题

App 内多数删除操作（删除碎片、删除会话、清空聊天历史等）点击即执行，无二次确认，存在误删风险。仅删除笔记、删除文件夹、删除页面 3 处已有确认弹窗，且为各自手写的内联 `LingjiDialog`，无统一封装。

## [S2] 方案概述

新建通用确认弹窗组件 `DeleteConfirmDialog`（基于现有 `LingjiDialog` 封装，红色危险确认按钮），统一用于：

1. 替换现有 3 处手写确认（体验统一）
2. 为 7 处无确认的删除/清空操作新增二次确认

ViewModel / Repository / DAO 层完全不动，所有确认均为 UI 层状态门控：点击删除入口 -> 置 `showXxxConfirmDialog = true` -> 弹窗 -> 确认后才调用 ViewModel 删除函数。

## [S3] 通用组件 DeleteConfirmDialog

位置：`app/src/main/java/com/lingji/app/ui/components/LingjiDialog.kt`（与现有弹窗组件同文件）。

```kotlin
@Composable
fun DeleteConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```

- 内部复用 `LingjiDialog` 容器
- 确认按钮：`LingjiDialogConfirmButton(isDestructive = true)`，文案统一「删除」或「清空」（按操作语义）
- 取消按钮：`LingjiDialogDismissButton`，文案「取消」

## [S4] 替换现有 3 处手写确认

| 位置 | 操作 |
|---|---|
| `ui/screens/SubjectCard.kt:243-272` | 删除笔记确认 -> DeleteConfirmDialog |
| `ui/screens/SubjectGalleryScreen.kt:538-563` | 删除文件夹确认 -> DeleteConfirmDialog |
| `ui/components/NotebookDialogs.kt:109-143` | DeletePageDialog -> DeleteConfirmDialog |

## [S5] 新增 7 处确认

| # | 操作 | 入口位置 | 弹窗文案要点 |
|---|---|---|---|
| a | 删除碎片 | `FragmentSubjectScreen.kt:402`（FragmentList 尾部 Delete 图标） | 「删除这条碎片？」 |
| b | 删除首页会话（级联删消息） | `SubjectGalleryScreen.kt:659`（HomeChatSheet 会话项 DeleteSweep） | 「删除会话及其全部消息？」 |
| c | 删除首页单条碎片 | `SubjectGalleryScreen.kt:660`（HomeChatSheet 碎片尾部 Close） | 「删除这条碎片？」 |
| d | 清空笔记聊天历史（持久化） | `FragmentSubjectScreen.kt:374`（PageChatBar DeleteSweep） | 「清空当前笔记的聊天历史？」 |
| e | 「新对话」隐式清空全部碎片 | `HomeChatSheet.kt:218-226` 历史菜单 | 有碎片时弹：「开始新对话将清空 N 条未整理碎片」；无碎片时不弹窗直接执行 |
| f | 「整理碎片」隐式清空 | `HomeChatSheet.kt:407-423` 整理按钮 | 「整理完成后将清空碎片列表」 |
| g | 清空页面聊天历史（内存态） | `NotebookSubjectScreen.kt:522`（同一 PageChatBar 图标） | 同 d（同一图标两入口一致加确认） |

弹窗状态（`showXxxConfirmDialog` / `pendingDeleteXxx`）持有位置跟随现有代码模式：屏幕层（SubjectGalleryScreen / FragmentSubjectScreen / NotebookSubjectScreen）或组件内（SubjectCard 模式），计划阶段按各文件现状定。

## [S6] 明确不改动

- Agent 聊天工具删除（`SubjectTools` / `PageTools` / `FragmentTools`）-- 用户选择不加
- 页面内图片段删除（`NotebookPageEditor`）-- 已有 undo 可撤销
- 「从文件夹移除」笔记 -- 数据保留，非删除
- 草稿性质操作（待发送图片移除、索引关键词草稿移除）

## [S7] 字符串资源

所有新增文案统一放 `app/src/main/res/values/strings.xml`，分组注释 `<!-- 删除确认 -->`，中文。

## [S8] 验证

- `./gradlew :app:compileDebugKotlin` 编译通过
- `./gradlew :app:installDebug` 同步模拟器（按 AGENTS.md 规范）

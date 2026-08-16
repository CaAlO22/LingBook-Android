# 删除操作统一二次确认 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 app 内所有删除/清空操作统一添加二次确认弹窗，防止误删。

**Architecture:** 新建通用确认组件 `DeleteConfirmDialog`（基于现有 `LingjiDialog` 封装，红色破坏性确认按钮），替换 3 处现有手写确认、为 7 处无确认的删除/清空操作新增确认门控。所有改动纯 UI 层（弹窗状态门控），ViewModel/Repository/DAO 完全不动。

**Tech Stack:** Kotlin + Jetpack Compose（Material3），构建工具 Gradle，Windows/PowerShell 环境。

**Spec:** `docs/compose/specs/2026-08-16-delete-confirmation-design.md`

## Global Constraints

- 编译验证命令：`./gradlew :app:compileDebugKotlin`（AGENTS.md 规定，每个 task 必须执行且通过）
- 字符串资源统一放 `app/src/main/res/values/strings.xml`，新增字符串放在 `<!-- 删除确认 -->` 分组注释下（AGENTS.md 规定）
- 中文文案；确认弹窗一律用 `LingjiDialogConfirmButton(isDestructive = true)` 红色按钮风格
- 禁止改动 ViewModel / Repository / DAO 层代码
- 每个 task 结束执行一次 git commit
- 项目为 Windows 环境，gradle 命令用 `./gradlew`（PowerShell 兼容）

---

### Task 1: 新增 DeleteConfirmDialog 组件与字符串资源

**Covers:** [S3, S7]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/components/LingjiDialog.kt`（文件末尾追加组件）
- Modify: `app/src/main/res/values/strings.xml`（`</resources>` 前追加分组）

**Interfaces:**
- Consumes: 现有 `LingjiDialog(onDismissRequest, modifier, title, text, confirmButton, dismissButton, properties)`、`LingjiDialogConfirmButton(text, onClick, enabled, isDestructive)`、`LingjiDialogDismissButton(text, onClick)`（同文件）
- Produces: `@Composable fun DeleteConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmText: String = stringResource(R.string.delete))` -- 后续所有 task 使用此签名。`onDismiss` 同时承担「取消按钮」与「点弹窗外/返回键关闭」两种语义。

- [ ] **Step 1: 在 strings.xml 追加字符串资源**

在 `app/src/main/res/values/strings.xml` 的 `</resources>` 标签之前追加：

```xml
    <!-- 删除确认 -->
    <string name="delete_fragment_confirm">确定要删除这条碎片吗？此操作不可撤销。</string>
    <string name="delete_conversation_confirm">确定要删除会话“%1$s”吗？会话的全部消息将一并删除，此操作不可撤销。</string>
    <string name="clear_chat_history_confirm">确定要清空聊天历史吗？此操作不可撤销。</string>
    <string name="new_conversation_clear_confirm">开始新对话将清空 %1$d 条未整理碎片，此操作不可撤销。</string>
    <string name="organize_clear_confirm">整理完成后碎片列表将被清空，确定继续吗？</string>
    <string name="clear">清空</string>
    <string name="clear_and_start">清空并开始</string>
    <string name="organize">整理</string>
```

- [ ] **Step 2: 在 LingjiDialog.kt 追加 DeleteConfirmDialog**

在 `app/src/main/java/com/lingji/app/ui/components/LingjiDialog.kt` 文件顶部 import 区追加（该文件目前没有这两个 import）：

```kotlin
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
```

在文件末尾（`LingjiDialogDismissButton` 函数之后）追加：

```kotlin
/**
 * 通用删除/清空二次确认弹窗。
 *
 * 基于 [LingjiDialog] 封装，确认按钮固定使用破坏性（红色）样式，
 * 用于所有删除、清空类操作的二次确认，统一交互体验。
 *
 * @param title 弹窗标题。
 * @param text 确认提示正文。
 * @param onConfirm 点击确认按钮回调（执行删除后由调用方清除 pending 状态）。
 * @param onDismiss 取消/点外部关闭回调（由调用方清除 pending 状态）。
 * @param confirmText 确认按钮文案，默认「删除」；清空类操作可传「清空」等。
 */
@Composable
fun DeleteConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.delete)
) {
    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = confirmText,
                onClick = onConfirm,
                isDestructive = true
            )
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/components/LingjiDialog.kt app/src/main/res/values/strings.xml
git commit -m "feat: 新增 DeleteConfirmDialog 通用删除确认组件与字符串资源"
```

---

### Task 2: 替换 SubjectCard 与 SubjectGalleryScreen 的手写确认

**Covers:** [S4]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/components/SubjectCard.kt:243-272`
- Modify: `app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt:538-563`

**Interfaces:**
- Consumes: `DeleteConfirmDialog(title, text, onConfirm, onDismiss, confirmText?)`（Task 1 产出；默认 confirmText 为「删除」，删除类无需传）

- [ ] **Step 1: 替换 SubjectCard 内的删除确认弹窗**

在 `SubjectCard.kt` 中找到 `if (showDeleteDialog) {` 开始、到对应闭合 `}` 的整块（约 243-272 行，内容为 `LingjiDialog(...)`)，整块替换为：

```kotlin
            if (showDeleteDialog) {
                DeleteConfirmDialog(
                    title = stringResource(R.string.delete),
                    text = stringResource(
                        R.string.delete_subject_confirm,
                        subject.title.takeIf { it.isNotBlank() } ?: ""
                    ),
                    onConfirm = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }
```

注意保持原缩进层级（12 空格，该块位于 Column 内）。替换后若 `LingjiDialog`、`LingjiDialogConfirmButton`、`LingjiDialogDismissButton` 的 import 在本文件不再被使用（用 grep 确认文件内无其他使用处），将这三个 import 替换为：

```kotlin
import com.lingji.app.ui.components.DeleteConfirmDialog
```

- [ ] **Step 2: 替换 SubjectGalleryScreen 内的删除文件夹确认**

在 `SubjectGalleryScreen.kt` 中找到 `deleteFolderId?.let { id ->` 开始到闭合 `}` 的整块（约 538-563 行），整块替换为：

```kotlin
    deleteFolderId?.let { id ->
        val folder = uiState.folders.find { it.id == id }
        DeleteConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_folder_confirm, folder?.name ?: ""),
            onConfirm = {
                viewModel.deleteFolder(id)
                deleteFolderId = null
            },
            onDismiss = { deleteFolderId = null }
        )
    }
```

注意保持 4 空格缩进（该块在组合函数根层级）。该文件中 `LingjiDialog` 等仍被重命名文件夹、剪贴板导入等其他弹窗使用，import 保持不动；仅添加：

```kotlin
import com.lingji.app.ui.components.DeleteConfirmDialog
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/components/SubjectCard.kt app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt
git commit -m "refactor: 删除笔记/文件夹确认改用 DeleteConfirmDialog 统一组件"
```

---

### Task 3: NotebookSubjectScreen 替换 DeletePageDialog 并新增清空聊天历史确认

**Covers:** [S4, S5g]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/screens/NotebookSubjectScreen.kt:522-525, 678-690`
- Modify: `app/src/main/java/com/lingji/app/ui/screens/notebook/NotebookDialogs.kt:109-143`（删除 DeletePageDialog 函数）

**Interfaces:**
- Consumes: `DeleteConfirmDialog(title, text, onConfirm, onDismiss, confirmText?)`（Task 1 产出）；已存在字符串 `R.string.delete_page`、`R.string.delete_page_confirm`、`R.string.unnamed_page`、`R.string.cd_clear_history`、`R.string.clear_chat_history_confirm`、`R.string.clear`

- [ ] **Step 1: 替换删除页面确认弹窗**

在 `NotebookSubjectScreen.kt` 中找到 `deleteConfirmPage?.let { page ->` 到闭合 `}` 的整块（约 678-690 行），整块替换为：

```kotlin
    deleteConfirmPage?.let { page ->
        DeleteConfirmDialog(
            title = stringResource(R.string.delete_page),
            text = stringResource(
                R.string.delete_page_confirm,
                page.title.ifBlank { stringResource(R.string.unnamed_page) }
            ),
            onConfirm = {
                val deletedIndex = pages.indexOfFirst { it.id == page.id }
                val nextPage = pages.getOrNull(deletedIndex + 1)
                    ?: pages.getOrNull(deletedIndex - 1)
                currentPageId = nextPage?.id
                viewModel.deletePage(liveSubject.id, page.id)
                deleteConfirmPage = null
            },
            onDismiss = { deleteConfirmPage = null }
        )
    }
```

（与原 `DeletePageDialog` 行为一致：确认后先切换当前页引用再执行删除；新增 `deleteConfirmPage = null` 显式关闭。）

- [ ] **Step 2: 新增清空页面聊天历史确认**

在 `NotebookSubjectScreen.kt` 中：

1. 找到组合函数内与其他 `var showXxx by remember` 状态声明同级的位置，追加状态：

```kotlin
    var showClearChatConfirm by remember { mutableStateOf(false) }
```

2. 找到 `onClearHistory = {` 回调（约 522-525 行，原内容为 `chatHistory = emptyList(); chatAnswer = ""`），替换为：

```kotlin
                        onClearHistory = {
                            showClearChatConfirm = true
                        },
```

3. 在 `deleteConfirmPage?.let { ... }` 弹窗块之后追加弹窗：

```kotlin
    if (showClearChatConfirm) {
        DeleteConfirmDialog(
            title = stringResource(R.string.cd_clear_history),
            text = stringResource(R.string.clear_chat_history_confirm),
            confirmText = stringResource(R.string.clear),
            onConfirm = {
                chatHistory = emptyList()
                chatAnswer = ""
                showClearChatConfirm = false
            },
            onDismiss = { showClearChatConfirm = false }
        )
    }
```

4. import 更新：删除 `import com.lingji.app.ui.screens.notebook.DeletePageDialog`，添加 `import com.lingji.app.ui.components.DeleteConfirmDialog`。

- [ ] **Step 3: 删除 NotebookDialogs.kt 中的 DeletePageDialog 函数**

在 `NotebookDialogs.kt` 中删除整个 `DeletePageDialog` 函数（约 109-143 行，从 `@Composable` 注解到闭合 `}`）。其余两个弹窗函数（JumpPageDialog、MovePageDialog）保留。该文件的 `LingjiDialog` 等 import 仍被保留的函数使用，不动。

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`（若报 DeletePageDialog 未找到，说明 Step 1 未完成或还有其他调用处--用 grep 全局搜 `DeletePageDialog` 确认仅 NotebookSubjectScreen 一处引用）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/screens/NotebookSubjectScreen.kt app/src/main/java/com/lingji/app/ui/screens/notebook/NotebookDialogs.kt
git commit -m "refactor: 删除页面确认改用通用组件；新增清空页面聊天历史二次确认"
```

---

### Task 4: HomeChatSheet 新增 4 处确认（会话/碎片删除、新对话、整理）

**Covers:** [S5b, S5c, S5e, S5f]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/chat/HomeChatSheet.kt`

**Interfaces:**
- Consumes: `DeleteConfirmDialog(title, text, onConfirm, onDismiss, confirmText?)`（Task 1 产出）；已存在字符串 `R.string.home_chat_new_conversation`；Task 1 新增的 `R.string.delete_conversation_confirm`、`R.string.delete_fragment_confirm`、`R.string.new_conversation_clear_confirm`、`R.string.organize_clear_confirm`、`R.string.clear_and_start`、`R.string.organize`
- Produces: 无（组件签名与回调均不变，SubjectGalleryScreen 无需改动）

- [ ] **Step 1: 添加 4 个 pending 状态**

在 `HomeChatSheet.kt` 的 `HomeChatSheet` 组合函数内，与 `var showHistory by remember ...`（约 111 行）同级追加：

```kotlin
    var pendingDeleteConversation by remember { mutableStateOf<HomeConversationEntity?>(null) }
    var pendingDeleteFragmentIndex by remember { mutableStateOf<Int?>(null) }
    var showNewConversationConfirm by remember { mutableStateOf(false) }
    var showOrganizeConfirm by remember { mutableStateOf(false) }
```

并添加 import：

```kotlin
import com.lingji.app.ui.components.DeleteConfirmDialog
```

- [ ] **Step 2: 门控删除会话入口（trailingIcon）**

找到删除会话的 `trailingIcon` 中 Box 的 `clickable`（约 253-261 行），原内容：

```kotlin
                                        clickable {
                                            Log.d(TAG, "EVENT: delete conversation clicked | id=${conv.id}")
                                            onDeleteConversation(conv.id)
                                            showHistory = false
                                        },
```

替换为：

```kotlin
                                        clickable {
                                            Log.d(TAG, "EVENT: delete conversation clicked | id=${conv.id}")
                                            pendingDeleteConversation = conv
                                            showHistory = false
                                        },
```

- [ ] **Step 3: 门控「新对话」菜单项**

找到「新对话」`DropdownMenuItem` 的 `onClick`（约 220-224 行），原内容：

```kotlin
                            onClick = {
                                Log.d(TAG, "EVENT: new conversation clicked")
                                onNewConversation()
                                showHistory = false
                            },
```

替换为（无碎片时保持原行为直接新建，不弹窗）：

```kotlin
                            onClick = {
                                Log.d(TAG, "EVENT: new conversation clicked")
                                if (fragments.isEmpty()) {
                                    onNewConversation()
                                } else {
                                    showNewConversationConfirm = true
                                }
                                showHistory = false
                            },
```

- [ ] **Step 4: 门控碎片删除入口**

找到碎片列表尾部 Close 图标的 `IconButton`（约 389-390 行），将 `onClick = { onDeleteFragment(index) }` 替换为：

```kotlin
                                    IconButton(
                                        onClick = { pendingDeleteFragmentIndex = index },
                                        modifier = Modifier.size(20.dp)
                                    ) {
```

- [ ] **Step 5: 门控「整理」按钮**

找到整理按钮 `Surface(onClick = onOrganizeFragments, ...)`（约 408-409 行），将 `onClick = onOrganizeFragments` 替换为：

```kotlin
                        onClick = { showOrganizeConfirm = true },
```

- [ ] **Step 6: 在函数体末尾追加 4 个确认弹窗**

在 `HomeChatSheet` 组合函数的最外层末尾（最后一个闭合大括号之前、UI 树完成后）追加：

```kotlin
    pendingDeleteConversation?.let { conv ->
        DeleteConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_conversation_confirm, conv.title),
            onConfirm = {
                onDeleteConversation(conv.id)
                pendingDeleteConversation = null
            },
            onDismiss = { pendingDeleteConversation = null }
        )
    }

    pendingDeleteFragmentIndex?.let { index ->
        DeleteConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_fragment_confirm),
            onConfirm = {
                onDeleteFragment(index)
                pendingDeleteFragmentIndex = null
            },
            onDismiss = { pendingDeleteFragmentIndex = null }
        )
    }

    if (showNewConversationConfirm) {
        DeleteConfirmDialog(
            title = stringResource(R.string.home_chat_new_conversation),
            text = stringResource(R.string.new_conversation_clear_confirm, fragments.size),
            confirmText = stringResource(R.string.clear_and_start),
            onConfirm = {
                onNewConversation()
                showNewConversationConfirm = false
            },
            onDismiss = { showNewConversationConfirm = false }
        )
    }

    if (showOrganizeConfirm) {
        DeleteConfirmDialog(
            title = stringResource(R.string.organize),
            text = stringResource(R.string.organize_clear_confirm),
            confirmText = stringResource(R.string.organize),
            onConfirm = {
                onOrganizeFragments()
                showOrganizeConfirm = false
            },
            onDismiss = { showOrganizeConfirm = false }
        )
    }
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/chat/HomeChatSheet.kt
git commit -m "feat: 首页会话/碎片删除及新对话、整理清空增加二次确认"
```

---

### Task 5: FragmentSubjectScreen 新增删除碎片与清空聊天历史确认

**Covers:** [S5a, S5d]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/screens/FragmentSubjectScreen.kt:374-377, 402`

**Interfaces:**
- Consumes: `DeleteConfirmDialog(title, text, onConfirm, onDismiss, confirmText?)`（Task 1 产出）；实体类型 `com.lingji.app.domain.model.Fragment`（该文件已 import）；Task 1 新增的 `R.string.delete_fragment_confirm`、`R.string.clear_chat_history_confirm`、`R.string.clear`；已存在 `R.string.cd_clear_history`

- [ ] **Step 1: 添加 2 个 pending 状态**

在 `FragmentSubjectScreen.kt` 组合函数内、与 `var showPlanDialog by remember { mutableStateOf(false) }`（约 145 行）同级追加：

```kotlin
    var pendingDeleteFragment by remember { mutableStateOf<Fragment?>(null) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
```

并添加 import（若不存在）：

```kotlin
import com.lingji.app.ui.components.DeleteConfirmDialog
```

- [ ] **Step 2: 门控删除碎片入口**

找到 `FragmentList(...)` 调用中的 `onDelete` 回调（约 402 行），原内容：

```kotlin
                                    onDelete = { viewModel.deleteFragment(liveSubject.id, it.id) },
```

替换为：

```kotlin
                                    onDelete = { pendingDeleteFragment = it },
```

- [ ] **Step 3: 门控清空聊天历史入口**

找到 `onClearHistory = {` 回调（约 374-377 行），原内容：

```kotlin
                                    onClearHistory = {
                                        viewModel.clearNoteChatHistory(liveSubject.id)
                                        noteChatAnswer = ""
                                    },
```

替换为：

```kotlin
                                    onClearHistory = {
                                        showClearChatConfirm = true
                                    },
```

- [ ] **Step 4: 在弹窗区追加 2 个确认弹窗**

在该文件 `if (showPlanDialog) { ... }` 弹窗块之后（约 530 行附近，与其他 LingjiDialog 弹窗同级）追加：

```kotlin
    pendingDeleteFragment?.let { fragment ->
        DeleteConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_fragment_confirm),
            onConfirm = {
                viewModel.deleteFragment(liveSubject.id, fragment.id)
                pendingDeleteFragment = null
            },
            onDismiss = { pendingDeleteFragment = null }
        )
    }

    if (showClearChatConfirm) {
        DeleteConfirmDialog(
            title = stringResource(R.string.cd_clear_history),
            text = stringResource(R.string.clear_chat_history_confirm),
            confirmText = stringResource(R.string.clear),
            onConfirm = {
                viewModel.clearNoteChatHistory(liveSubject.id)
                noteChatAnswer = ""
                showClearChatConfirm = false
            },
            onDismiss = { showClearChatConfirm = false }
        )
    }
```

注意：确认弹窗的缩进层级必须与 `if (showPlanDialog)` 块相同（组合函数根层级，4 空格）；`liveSubject` 在该层级可访问（若编译报 `liveSubject` 未解析，将其放入弹窗块同级且能访问 `liveSubject` 的最近作用域）。

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/screens/FragmentSubjectScreen.kt
git commit -m "feat: 碎片删除与笔记聊天历史清空增加二次确认"
```

---

### Task 6: 全量验证与模拟器同步

**Covers:** [S8]

**Files:** 无新改动（验证 task；若验证失败则回上游 task 修复）

**Interfaces:**
- Consumes: 前 5 个 task 的全部产出

- [ ] **Step 1: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 跑现有单元测试确认无回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`，0 failed（本次改动纯 UI 层，不应影响任何逻辑层测试）

- [ ] **Step 3: 模拟器同步**

Run: `./gradlew :app:installDebug`
Expected: 若有运行中的模拟器/设备则安装成功；若无可用设备，跳过并在最终报告中记录原因（AGENTS.md 允许）。

- [ ] **Step 4: 手动验证清单（如有模拟器）**

在模拟器上逐项点检（无模拟器则跳过并记录）：
1. 首页卡片删除笔记 -> 弹红色确认弹窗
2. 文件夹删除 -> 弹确认
3. 笔记本删除页面 -> 弹确认
4. 首页聊天：会话删除图标 -> 弹确认；碎片 × -> 弹确认
5. 首页聊天：「新对话」（有碎片时）-> 弹「清空并开始」确认；无碎片时直接新建不弹窗
6. 首页聊天：「整理 (N 条碎片)」-> 弹「整理」确认
7. 碎片学科页：碎片删除图标 -> 弹确认；聊天历史 DeleteSweep -> 弹「清空」确认
8. 笔记本页：聊天历史 DeleteSweep -> 弹「清空」确认
9. 每个弹窗点「取消」-> 不执行删除且弹窗关闭

- [ ] **Step 5: 完成报告**

汇总各 task 结果、验证输出、手动点检结果。

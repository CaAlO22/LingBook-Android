# 回车键横竖屏行为改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一所有带"发送"语义的输入框回车键行为：竖屏回车换行；横屏回车发送、Ctrl/Shift+回车换行。

**Architecture:** 新增一个共享 Modifier 扩展 `enterSendBehavior`，内部读取 `LocalConfiguration` 判断横竖屏并统一处理回车键；将 3 处重复的内联 `onPreviewKeyEvent` 逻辑替换为该扩展调用。

**Tech Stack:** Kotlin + Jetpack Compose，`Modifier.composed` + `onPreviewKeyEvent` + `LocalConfiguration`。

## Global Constraints

- 项目验证命令：`./gradlew :app:compileDebugKotlin`（见 AGENTS.md）。
- 完成后需同步到模拟器：`./gradlew :app:installDebug`（见 AGENTS.md）。
- 供应商/公共文件最小侵入：本改动只触及输入组件自身文件 + 一个新公共组件文件，不触碰 `SettingsScreen.kt`/`LLMService.kt`/`Models.kt`。
- 字符串资源无需新增（行为改造不涉及用户可见文案）。
- 测试说明：本项目无 UI 键盘事件单测设施，回车键行为无法用单元测试覆盖；验证以 Kotlin 编译通过 + 模拟器安装为准（与 AGENTS.md 一致）。

## 改造范围（确认：仅 3 处带发送语义的输入框）

| 文件 | 角色 | 现状 |
|---|---|---|
| `InputCapsule.kt` | 碎片输入 | Enter=发送，Ctrl/Shift+Enter=换行 |
| `PageChatBar.kt` | AI对话（页/笔记） | Ctrl+Enter=发送，Enter=换行 |
| `HomeChatSheet.kt` | AI对话+碎片输入 | Ctrl+Enter=发送，Enter=换行 |

不在范围内（无发送动作，回车保持默认换行）：`NoteEditor`、`NotebookPageEditor`、各对话框内的 `GlassOutlinedTextField`、单行标题输入框。

---

### Task 1: 新增共享 Modifier 扩展 `enterSendBehavior`

**Files:**
- Create: `app/src/main/java/com/lingji/app/ui/components/EnterKeyBehavior.kt`

**Interfaces:**
- Produces: `Modifier.enterSendBehavior(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, onSend: () -> Unit): Modifier` —— 横屏 Enter 调 `onSend()`；横屏 Ctrl/Shift+Enter 手动插入 `\n`（调 `onValueChange`）；竖屏不拦截（默认换行）。

- [ ] **Step 1: 创建 `EnterKeyBehavior.kt`**

```kotlin
package com.lingji.app.ui.components

import android.content.res.Configuration
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 统一的回车键行为：
 * - 竖屏：回车换行（交给默认处理，不拦截）。
 * - 横屏：回车发送，Ctrl/Shift+回车换行（手动插入换行以保证跨输入法一致）。
 *
 * @param value 当前输入框内容，横屏 Ctrl/Shift+回车时据此插入换行。
 * @param onValueChange 内容变更回调。
 * @param onSend 发送回调（横屏下普通回车触发）。
 */
fun Modifier.enterSendBehavior(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit
): Modifier = composed {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
        if (isLandscape) {
            if (event.isCtrlPressed || event.isShiftPressed) {
                onValueChange(insertNewline(value))
                true
            } else {
                onSend()
                true
            }
        } else {
            // 竖屏：回车换行，交给默认处理
            false
        }
    }
}

private fun insertNewline(value: TextFieldValue): TextFieldValue {
    val newText = value.text.replaceRange(
        value.selection.start, value.selection.end, "\n"
    )
    val cursor = value.selection.start + 1
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（新文件独立可编译）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/lingji/app/ui/components/EnterKeyBehavior.kt
git commit -m "feat: add enterSendBehavior modifier for orientation-aware enter key"
```

---

### Task 2: 改造 `InputCapsule.kt`（碎片输入）

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/components/InputCapsule.kt`（onPreviewKeyEvent 块 72-94 行；清理多余 import）

**Interfaces:**
- Consumes: `Modifier.enterSendBehavior`（同包，无需 import）。

- [ ] **Step 1: 替换 onPreviewKeyEvent 块为 enterSendBehavior 调用**

将 BasicTextField modifier 中的：
```kotlin
.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
        return@onPreviewKeyEvent false
    }
    if (event.isCtrlPressed || event.isShiftPressed) {
        val current = text
        val newText = current.text.replaceRange(
            current.selection.start,
            current.selection.end,
            "\n"
        )
        val cursor = current.selection.start + 1
        text = TextFieldValue(
            text = newText,
            selection = TextRange(cursor)
        )
        true
    } else {
        submitText()
        true
    }
},
```
替换为：
```kotlin
.enterSendBehavior(text, { text = it }, submitText),
```

- [ ] **Step 2: 清理不再使用的 import**

删除以下 import（替换后已无引用）：
- `androidx.compose.ui.input.key.Key`
- `androidx.compose.ui.input.key.KeyEventType`
- `androidx.compose.ui.input.key.isCtrlPressed`
- `androidx.compose.ui.input.key.isShiftPressed`
- `androidx.compose.ui.input.key.key`
- `androidx.compose.ui.input.key.onPreviewKeyEvent`
- `androidx.compose.ui.input.key.type`
- `androidx.compose.ui.text.TextRange`

保留 `TextFieldValue`（仍用于 `text` 状态）。

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/lingji/app/ui/components/InputCapsule.kt
git commit -m "refactor: use enterSendBehavior in InputCapsule"
```

---

### Task 3: 改造 `PageChatBar.kt`（AI对话）

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/components/PageChatBar.kt`（onPreviewKeyEvent 块 341-358 行；删除 ctrlDown 状态 79 行；清理 import）

**Interfaces:**
- Consumes: `Modifier.enterSendBehavior`（同包，无需 import）。

- [ ] **Step 1: 替换 onPreviewKeyEvent 块为 enterSendBehavior 调用**

将：
```kotlin
.onPreviewKeyEvent { event ->
    val keyCode = event.nativeKeyEvent.keyCode
    if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
        ctrlDown = event.type == KeyEventType.KeyDown
        return@onPreviewKeyEvent false
    }
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
        return@onPreviewKeyEvent false
    }
    if (ctrlDown) {
        ctrlDown = false
        submitQuestion()
        true
    } else {
        false
    }
},
```
替换为：
```kotlin
.enterSendBehavior(question, { question = it }, submitQuestion),
```

- [ ] **Step 2: 删除 ctrlDown 状态**

删除第 79 行：
```kotlin
var ctrlDown by remember { mutableStateOf(false) }
```

- [ ] **Step 3: 清理不再使用的 import**

删除：
- `import android.view.KeyEvent`
- `import androidx.compose.ui.input.key.Key`
- `import androidx.compose.ui.input.key.KeyEventType`
- `import androidx.compose.ui.input.key.key`
- `import androidx.compose.ui.input.key.onPreviewKeyEvent`
- `import androidx.compose.ui.input.key.type`

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/lingji/app/ui/components/PageChatBar.kt
git commit -m "refactor: use enterSendBehavior in PageChatBar"
```

---

### Task 4: 改造 `HomeChatSheet.kt`（AI对话+碎片输入）

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/chat/HomeChatSheet.kt`（onPreviewKeyEvent 块 530-545 行；删除 ctrlDown 状态 102 行；清理 import；新增 import）

**Interfaces:**
- Consumes: `Modifier.enterSendBehavior`（跨包，需 import `com.lingji.app.ui.components.enterSendBehavior`）。

- [ ] **Step 1: 新增 import**

在 `com.lingji.app.ui.components` 相关 import 区域添加：
```kotlin
import com.lingji.app.ui.components.enterSendBehavior
```

- [ ] **Step 2: 替换 onPreviewKeyEvent 块为 enterSendBehavior 调用**

将：
```kotlin
.onPreviewKeyEvent { event ->
    val keyCode = event.nativeKeyEvent.keyCode
    if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
        ctrlDown = event.type == KeyEventType.KeyDown
        return@onPreviewKeyEvent false
    }
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
    if (ctrlDown) {
        ctrlDown = false
        submitInput()
        true
    } else {
        false
    }
},
```
替换为：
```kotlin
.enterSendBehavior(inputText, { inputText = it }, submitInput),
```

- [ ] **Step 3: 删除 ctrlDown 状态**

删除第 102 行：
```kotlin
var ctrlDown by remember { mutableStateOf(false) }
```

- [ ] **Step 4: 清理不再使用的 import**

删除：
- `import android.view.KeyEvent`
- `import androidx.compose.ui.input.key.Key`
- `import androidx.compose.ui.input.key.KeyEventType`
- `import androidx.compose.ui.input.key.key`
- `import androidx.compose.ui.input.key.onPreviewKeyEvent`
- `import androidx.compose.ui.input.key.type`

保留 `LocalConfiguration`（仍用于 screenWidthDp）。

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/lingji/app/ui/chat/HomeChatSheet.kt
git commit -m "refactor: use enterSendBehavior in HomeChatSheet"
```

---

### Task 5: 全量编译 + 模拟器同步

**Files:** 无（仅验证）。

- [ ] **Step 1: 全量编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL，无未使用 import 警告相关报错。

- [ ] **Step 2: 同步到模拟器**

Run: `./gradlew :app:installDebug`
Expected: 安装成功（若无可用模拟器/设备，记录原因后跳过）。

---

## Self-Review

- **范围覆盖**：3 个带发送语义的输入框全部覆盖（InputCapsule/PageChatBar/HomeChatSheet）；不在范围内的纯编辑器/对话框输入明确排除。✓
- **行为一致**：竖屏 Enter=换行（不拦截）；横屏 Enter=发送、Ctrl/Shift+Enter=换行。三处统一。✓
- **类型一致**：`enterSendBehavior(value, onValueChange, onSend)` 签名在 3 个调用点参数类型均为 `(TextFieldValue, (TextFieldValue)->Unit, ()->Unit)`，与各组件状态（`text`/`question`/`inputText`）匹配。✓
- **无占位符**：每步含完整代码/命令。✓

# 修复：导入 .ling 笔记后首页看不到（2026-09-27）

## 现象

通过「导入 → 从文件 / 从剪贴板」导入 `.ling` 笔记，App 弹出「导入成功」，
但首页（`gallery`）列表中看不到该笔记。

复现样本：`英语文章阅读_20260925_203813.ling`（LING64GZ 格式，NOTEBOOK 类型，92 页）。

## 根因

导入后笔记确实已写入 Room，只是**被首页的文件夹过滤条件挡住了**。

`SubjectUiState.homeItems`：

```kotlin
val noteItems = subjects.filter { it.folderId == null }.map { HomeItem.NoteItem(it) }
```

首页只渲染 `folderId == null` 的笔记 + 文件夹卡片。而 `.ling` 文件是整份
`Subject` 序列化，**包含 `folderId`**（样本为 `f275308c`）。导入时
`SubjectViewModel.importSubject` 只重新生成了笔记 id，原样保留了 `folderId`：

- 若本机存在同 id 文件夹 → 笔记进了那个文件夹，首页不显示；
- 若本机不存在该文件夹（跨设备导入/重装）→ 笔记成为**孤儿**，
  既不在文件夹里、也不在首页，**任何入口都看不到**。

## 附带发现（数据损坏）

`notebook_pages.id` 是主键且 `@Insert(onConflict = REPLACE)`。导入时页面 id 原样保留，
因此**同一文件导入第二次时，会把第一次副本的 92 个页面全部 REPLACE 抢走**
（`subjectId` 被改写为新笔记），导致先导入的笔记变成空笔记本。

## 修复内容

1. `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectUiState.kt`
   - `homeItems` 改为按"本机实际存在的文件夹 id 集合"判断归属，
     `folderId` 悬空的笔记**回落到首页展示**（自愈，可救回已导入的孤儿笔记）。

2. `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt`
   - `importSubject(subject)` 归一化为"全新副本"：
     - 重新生成笔记 id（原有行为）；
     - `folderId = null` + `orderIndex` 置顶 → 导入后立刻在首页最前可见；
     - 新增 `remapInternalIds()`：重新生成 fragments / unmergedFragments / pages 的 id，
       并同步重映射 `pageIndex`、`pageIndexEntries[].pageId`、`lastOpenedPageId`，
       避免重复导入时的主键冲突抢页。

3. `app/src/test/java/com/lingji/app/ui/viewmodel/SubjectUiStateHomeItemsTest.kt`（新增）
   - 覆盖：悬空 folderId 回落首页 / 有效 folderId 仍留在文件夹 / 无归属与悬空并存。

## 验证

- `./gradlew :app:compileDebugKotlin` ✅
- `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.ui.viewmodel.*"` ✅（3 tests, 0 failures）
- `./gradlew :app:assembleDebug` ✅
- `./gradlew :app:installDebug` ⏭ 跳过：`adb devices` 无设备，且本机无可用 AVD。

## 行为变更提示

导入的笔记**不再继承源文件的文件夹归属**，统一落到首页顶部（可再手动拖入文件夹）。
这样设计是因为导出端的 `folderId` 对目标设备无意义，且用户预期是"导入后能在首页看到"。

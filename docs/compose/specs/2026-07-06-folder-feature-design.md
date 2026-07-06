# 文件夹功能设计规格

## [S1] 问题

灵记首页当前以扁平网格展示所有笔记（Subject），没有分组能力。用户需要文件夹功能来组织笔记：
- 首页可以创建文件夹
- 首页长按笔记可以拖动排序，或拖入文件夹中
- 文件夹内的笔记可以拖动排序，长按弹出菜单可从文件夹中移出

## [S2] 方案概述

- **单层文件夹**：文件夹只包含笔记，不支持嵌套
- **混合展示**：文件夹和笔记卡片在首页同一个网格中混合显示，均可拖拽排序
- **拖拽交互**：长按笔记开始拖拽；拖到文件夹卡片上松手=放入文件夹；拖到卡片间隙松手=重新排序
- **手动实现**：使用 Compose 内置 `pointerInput` + `detectDragGesturesAfterLongPress` 实现拖拽，不引入新依赖
- **菜单调整**：长按=拖拽；原有的删除/重命名/导出等操作改用卡片上的小图标按钮触发

## [S3] 数据模型与数据库

### [S3.1] FolderEntity 新表

```kotlin
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
```

### [S3.2] SubjectEntity 变更

新增可空列 `folderId`：

```kotlin
val folderId: String? = null  // null = 在首页, 非 null = 在对应文件夹内
```

### [S3.3] 数据库迁移 (v9 → v10)

```sql
CREATE TABLE IF NOT EXISTS folders (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    orderIndex INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL
)
ALTER TABLE subjects ADD COLUMN folderId TEXT
```

- `LingjiDatabase` version 改为 10
- `entities` 数组加入 `FolderEntity::class`
- `AppModule.kt` 注册 `MIGRATION_9_10` 并添加 `folderDao()` provider

### [S3.4] 排序模型

- **首页**：文件夹 + `folderId IS NULL` 的笔记共享同一 `orderIndex` 空间，按 `orderIndex DESC` 排序（与现有笔记排序方向一致）
- **文件夹内**：该 `folderId` 下的笔记按自身 `orderIndex DESC` 排序
- 拖拽重排时：根据新位置对受影响的所有条目重新分配 `orderIndex`

### [S3.5] FolderDao

```kotlin
@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY orderIndex DESC, createdAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE folders SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: String, orderIndex: Int)
}
```

### [S3.6] SubjectDao 变更

新增：
```kotlin
@Query("UPDATE subjects SET folderId = :folderId WHERE id = :id")
suspend fun updateFolderId(id: String, folderId: String?)
```

### [S3.7] Repository 变更

在 `SubjectRepository` 中新增文件夹方法（因文件夹与笔记强耦合，不单独建 FolderRepository）：

- `getAllFolders(): Flow<List<Folder>>`
- `createFolder(name: String)`
- `deleteFolder(id: String)` — 同时将其下笔记的 `folderId` 置 null（回到首页）
- `renameFolder(id: String, name: String)`
- `moveSubjectToFolder(subjectId: String, folderId: String)` — 设置 `folderId`，分配文件夹内 orderIndex
- `removeSubjectFromFolder(subjectId: String)` — 置 `folderId = null`，分配首页 orderIndex
- `reorderHomeItems(orderedItems: List<HomeItem>)` — 对首页混合列表重新分配 orderIndex
- `reorderFolderItems(folderId: String, orderedSubjectIds: List<String>)` — 对文件夹内笔记重新分配 orderIndex

`assemble()` 方法需传递 `folderId` 到 `Subject` domain model。

## [S4] Domain Model

### [S4.1] 新增类型 (`Models.kt`)

```kotlin
data class Folder(
    val id: String = generateId(),
    val name: String,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

sealed interface HomeItem {
    data class FolderItem(val folder: Folder, val noteCount: Int) : HomeItem
    data class NoteItem(val subject: Subject) : HomeItem
}
```

### [S4.2] Subject 变更

`Subject` data class 新增字段：
```kotlin
val folderId: String? = null
```

`Subject.create()` 和 `SubjectRepository.assemble()` / `toEntity()` 同步更新。

## [S5] UI 架构与拖拽交互

### [S5.1] 首页 (`SubjectGalleryScreen`)

网格展示混合列表 `homeItems`：

```
LazyVerticalGrid {
    AddSubjectCard          // 现有
    AddFolderCard           // 新增 — 点击弹出文件夹名称对话框
    items(homeItems) {      // 文件夹 + 首页笔记，按 orderIndex DESC 排序
        when (item) {
            is FolderItem -> FolderCard(...)
            is NoteItem   -> SubjectCard(...)
        }
    }
}
```

- **AddFolderCard**：与 AddSubjectCard 风格一致的虚线边框卡片，点击后弹出 LingjiDialog 输入文件夹名称
- **FolderCard**：文件夹图标 + 名称 + 笔记数量；点击 → 导航到文件夹视图；长按 → 拖拽（在首页重排序）
- **SubjectCard**（首页）：长按启动拖拽；原有下拉菜单操作（删除/重命名/导出等）改为卡片上的小 "⋯" 图标按钮

### [S5.2] 文件夹视图 (`FolderScreen` 新增)

导航路由 `"folder/{folderId}"`：
- TopAppBar 显示文件夹名称 + 返回按钮
- LazyVerticalGrid 只展示该文件夹内的笔记
- 顶部有 AddSubjectCard（创建的笔记直接属于此文件夹）
- 笔记可拖拽重排序
- 长按笔记 → 下拉菜单，包含现有操作 **外加 "从文件夹中移出"**（将 folderId 置 null，笔记回到首页）

### [S5.3] 拖拽状态管理

```kotlin
class DragState {
    var isDragging by mutableStateOf(false)
    var draggedItem by mutableStateOf<HomeItem?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var dragStartPos by mutableStateOf(Offset.Zero)
    var dropTargetFolderId by mutableStateOf<String?>(null)
    var reorderTargetIndex by mutableStateOf(-1)
}
```

### [S5.4] 拖拽流程

1. 每张卡片绑定 `pointerInput` + `detectDragGesturesAfterLongPress`
2. 拖拽开始：`isDragging=true`，记录 `draggedItem`，捕获卡片屏幕坐标
3. 拖拽中：更新 `dragOffset`；利用 `LazyGridState.layoutInfo.visibleItemsInfo` 做命中测试
   - 手指在 FolderCard 区域 → 设 `dropTargetFolderId`，高亮该文件夹
   - 手指在间隙或其他笔记区域 → 计算 reorder 插入位置，显示插入指示线
4. 拖拽结束：
   - `dropTargetFolderId != null` → 调用 `moveSubjectToFolder(subjectId, folderId)`
   - 否则 → 调用 `reorderHomeItems(newOrderedIds)` 或 `reorderFolderItems(...)`
5. 拖拽中渲染：在网格顶层用 Box 渲染浮动卡片（跟随手指），原位置卡片 `alpha(0.3f)`

### [S5.5] SubjectCard 菜单调整

现有长按下拉菜单改为卡片右下角 "⋯" IconButton 触发：
- 导出、复制到剪贴板、置顶、上移、下移、删除（保留现有项）
- 文件夹视图内额外增加 "从文件夹中移出"
- 上移/下移在支持拖拽后可考虑移除，但先保留以兼容不习惯拖拽的用户

## [S6] ViewModel 与状态

### [S6.1] SubjectUiState 变更

```kotlin
data class SubjectUiState(
    // 现有字段...
    val folders: List<Folder> = emptyList(),
    val currentFolderId: String? = null,
) {
    val homeItems: List<HomeItem>
        get() = (folders.map { FolderItem(it, subjects.count { s -> s.folderId == it.id }) } +
                subjects.filter { it.folderId == null }.map { NoteItem(it) })
                .sortedByDescending { item ->
                    when (item) {
                        is HomeItem.FolderItem -> item.folder.orderIndex
                        is HomeItem.NoteItem -> item.subject.orderIndex
                    }
                }
}
```

### [S6.2] SubjectViewModel 新方法

```kotlin
fun createFolder(name: String)
fun deleteFolder(id: String)
fun renameFolder(id: String, name: String)
fun moveSubjectToFolder(subjectId: String, folderId: String)
fun removeSubjectFromFolder(subjectId: String)
fun reorderHomeItems(orderedItems: List<HomeItem>)
fun reorderFolderItems(folderId: String, orderedSubjectIds: List<String>)
```

`init` 块的 `combine` 加入 `subjectRepository.getAllFolders()`。

## [S7] 导航变更

`LingjiNavigation.kt` 新增路由：

```kotlin
composable(
    "folder/{folderId}",
    arguments = listOf(navArgument("folderId") { type = NavType.StringType })
) { backStackEntry ->
    val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
    FolderScreen(
        viewModel = viewModel,
        folderId = folderId,
        onSubjectClick = { id -> viewModel.setCurrentSubject(id); navController.navigate("subject/$id") },
        onBack = { navController.popBackStack() }
    )
}
```

文件夹卡片点击 → `navController.navigate("folder/$folderId")`。

## [S8] 文件清单

### [S8.1] 新建文件

| 文件路径 | 用途 |
|---------|------|
| `data/db/entities/FolderEntity.kt` | Room 实体 |
| `data/db/dao/FolderDao.kt` | 文件夹 DAO |
| `ui/components/FolderCard.kt` | 文件夹卡片组件 |
| `ui/components/DragState.kt` | 拖拽状态持有者 |
| `ui/screens/FolderScreen.kt` | 文件夹视图页面 |

### [S8.2] 修改文件

| 文件路径 | 改动 |
|---------|------|
| `data/db/entities/SubjectEntity.kt` | 加 `folderId: String? = null` |
| `data/db/dao/SubjectDao.kt` | 加 `updateFolderId()` |
| `data/db/LingjiDatabase.kt` | 加 FolderEntity, version=10, MIGRATION_9_10 |
| `di/AppModule.kt` | 注册 migration, 加 folderDao provider |
| `domain/model/Models.kt` | 加 Folder, HomeItem, Subject.folderId |
| `data/repository/SubjectRepository.kt` | 加文件夹方法, assemble 传 folderId |
| `ui/viewmodel/SubjectUiState.kt` | 加 folders, currentFolderId, homeItems |
| `ui/viewmodel/SubjectViewModel.kt` | 加文件夹方法, init 加 folders flow |
| `ui/screens/SubjectGalleryScreen.kt` | 混合网格, AddFolderCard, 拖拽集成 |
| `ui/components/SubjectCard.kt` | 加拖拽 pointerInput, 菜单改图标按钮 |
| `ui/navigation/LingjiNavigation.kt` | 加 folder 路由 |
| `res/values/strings.xml` | 加文件夹相关字符串 |

### [S8.3] 不变文件

- `FragmentSubjectScreen.kt` / `NotebookSubjectScreen.kt` — 笔记编辑页不变
- LLM/AI 相关全部不变
- Settings 不变
- Provider 架构不变

## [S9] 验证

每次修改后执行：
```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:installDebug
```

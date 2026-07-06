# 文件夹功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add folder functionality to the home page — create folders, drag notes to reorder or drop into folders, and manage notes inside folders with drag-reorder and remove-from-folder.

**Architecture:** Single-level folders stored in a new `folders` Room table. `SubjectEntity` gains a nullable `folderId`. Home page displays a mixed grid of folders and loose notes via a `HomeItem` sealed interface. Drag-and-drop is implemented manually with Compose `pointerInput` + `detectDragGesturesAfterLongPress` — no new dependencies.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Material3

## Global Constraints

- Verification: `./gradlew :app:compileDebugKotlin` after each task (per AGENTS.md)
- Deploy: `./gradlew :app:installDebug` after UI tasks (per AGENTS.md)
- Strings: all user-facing text in `app/src/main/res/values/strings.xml`, grouped by feature with comments
- Ordering: `orderIndex DESC` (higher = earlier in list), matching existing subject ordering
- Package base: `com.lingji.app`
- No new dependencies — drag-drop uses Compose built-in APIs only
- Database version: 9 → 10, must add migration to both `LingjiDatabase.kt` and `AppModule.kt`

---

### Task 1: Data & Domain Foundation

**Covers:** [S3], [S4]

**Files:**
- Create: `app/src/main/java/com/lingji/app/data/db/entities/FolderEntity.kt`
- Create: `app/src/main/java/com/lingji/app/data/db/dao/FolderDao.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/entities/SubjectEntity.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/dao/SubjectDao.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt`
- Modify: `app/src/main/java/com/lingji/app/di/AppModule.kt`
- Modify: `app/src/main/java/com/lingji/app/domain/model/Models.kt`

**Interfaces:**
- Produces: `FolderEntity`, `FolderDao`, `Folder` domain model, `HomeItem` sealed interface, `Subject.folderId` field, `SubjectDao.updateFolderId()`, `MIGRATION_9_10`

- [ ] **Step 1: Create FolderEntity**

Create `app/src/main/java/com/lingji/app/data/db/entities/FolderEntity.kt`:

```kotlin
package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create FolderDao**

Create `app/src/main/java/com/lingji/app/data/db/dao/FolderDao.kt`:

```kotlin
package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY orderIndex DESC, createdAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): FolderEntity?

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

- [ ] **Step 3: Add folderId to SubjectEntity**

Modify `app/src/main/java/com/lingji/app/data/db/entities/SubjectEntity.kt` — add `folderId` field after `lastOpenedPageId`:

```kotlin
package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val aggregatedNote: String,
    val prevAggregatedNote: String?,
    val studyPlan: String,
    val createdAt: Long,
    val orderIndex: Int = 0,
    val pageIndexJson: String = "",
    val lastOpenedPageId: String? = null,
    val folderId: String? = null
)
```

- [ ] **Step 4: Add updateFolderId to SubjectDao**

Modify `app/src/main/java/com/lingji/app/data/db/dao/SubjectDao.kt` — add this query before the `upsert` method:

```kotlin
@Query("UPDATE subjects SET folderId = :folderId WHERE id = :id")
suspend fun updateFolderId(id: String, folderId: String?)
```

- [ ] **Step 5: Add FolderEntity to database, bump version, add migration**

Modify `app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt`:

1. Add `FolderEntity::class` to the `entities` array (after `HomeFragmentEntity::class`)
2. Change `version = 9` to `version = 10`
3. Add import: `import com.lingji.app.data.db.entities.FolderEntity`
4. Add this migration after `MIGRATION_8_9`:

```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS folders (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "orderIndex INTEGER NOT NULL DEFAULT 0, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE subjects ADD COLUMN folderId TEXT")
    }
}
```

5. Add `abstract fun folderDao(): FolderDao` after `abstract fun homeChatDao(): HomeChatDao`
6. Add import: `import com.lingji.app.data.db.dao.FolderDao`

- [ ] **Step 6: Register migration and add FolderDao provider in AppModule**

Modify `app/src/main/java/com/lingji/app/di/AppModule.kt`:

1. Add `LingjiDatabase.MIGRATION_9_10` to the `.addMigrations(...)` call
2. Add this provider after `provideHomeChatDao`:

```kotlin
@Provides
fun provideFolderDao(database: LingjiDatabase) = database.folderDao()
```

- [ ] **Step 7: Add Folder and HomeItem domain models, add folderId to Subject**

Modify `app/src/main/java/com/lingji/app/domain/model/Models.kt`:

1. Add `Folder` data class after `Subject`:

```kotlin
data class Folder(
    val id: String = generateId(),
    val name: String,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
```

2. Add `HomeItem` sealed interface after `Folder`:

```kotlin
sealed interface HomeItem {
    data class FolderItem(val folder: Folder, val noteCount: Int) : HomeItem
    data class NoteItem(val subject: Subject) : HomeItem
}
```

3. Add `val folderId: String? = null` field to the `Subject` data class (after `lastOpenedPageId`)

4. Update `Subject.toEntity()` mapping in `SubjectRepository.kt` is handled in Task 2, but add the field to `Subject.create()` — no change needed since it defaults to null.

- [ ] **Step 8: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/lingji/app/data/db/entities/FolderEntity.kt app/src/main/java/com/lingji/app/data/db/dao/FolderDao.kt app/src/main/java/com/lingji/app/data/db/entities/SubjectEntity.kt app/src/main/java/com/lingji/app/data/db/dao/SubjectDao.kt app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt app/src/main/java/com/lingji/app/di/AppModule.kt app/src/main/java/com/lingji/app/domain/model/Models.kt
git commit -m "feat: add folder data model, DAO, and DB migration v10"
```

---

### Task 2: Repository Layer

**Covers:** [S3.7]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/data/repository/SubjectRepository.kt`

**Interfaces:**
- Consumes: `FolderDao`, `FolderEntity`, `Folder`, `Subject.folderId`, `SubjectDao.updateFolderId()`
- Produces: `SubjectRepository.getAllFolders()`, `createFolder()`, `deleteFolder()`, `renameFolder()`, `moveSubjectToFolder()`, `removeSubjectFromFolder()`, `reorderHomeItems()`, `reorderFolderItems()`

- [ ] **Step 1: Add FolderDao injection and folder methods to SubjectRepository**

Modify `app/src/main/java/com/lingji/app/data/repository/SubjectRepository.kt`:

1. Add `FolderDao` to constructor parameters:

```kotlin
@Singleton
class SubjectRepository @Inject constructor(
    private val subjectDao: SubjectDao,
    private val fragmentDao: FragmentDao,
    private val pageDao: NotebookPageDao,
    private val folderDao: FolderDao
) {
```

Add imports:
```kotlin
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.entities.FolderEntity
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.HomeItem
import kotlinx.coroutines.flow.first
```

2. Add folder methods (place after existing subject methods, before `private suspend fun loadSubject`):

```kotlin
fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders().map { entities ->
    entities.map { it.toDomain() }
}

suspend fun createFolder(name: String) {
    val count = folderDao.getAllFolders().first().size
    val folder = Folder(name = name, orderIndex = count)
    folderDao.insert(folder.toEntity())
}

suspend fun deleteFolder(id: String) {
    // Unlink all subjects in this folder (move them back to home)
    val subjects = subjectDao.getSubjectsByFolderOnce(id)
    subjects.forEach { subjectDao.updateFolderId(it.id, null) }
    val folder = folderDao.getFolderById(id) ?: return
    folderDao.delete(folder)
}

suspend fun renameFolder(id: String, name: String) = folderDao.rename(id, name)

suspend fun moveSubjectToFolder(subjectId: String, folderId: String) {
    subjectDao.updateFolderId(subjectId, folderId)
    // Assign orderIndex at end of folder's note list
    val count = subjectDao.getSubjectsByFolderOnce(folderId).size
    subjectDao.updateOrderIndex(subjectId, count)
}

suspend fun removeSubjectFromFolder(subjectId: String) {
    subjectDao.updateFolderId(subjectId, null)
    // Assign orderIndex at end of home page list
    val homeSubjects = subjectDao.getSubjectsByFolderOnce(null)
    subjectDao.updateOrderIndex(subjectId, homeSubjects.size)
}

suspend fun reorderHomeItems(orderedItems: List<HomeItem>) {
    orderedItems.forEachIndexed { index, item ->
        when (item) {
            is HomeItem.FolderItem -> folderDao.updateOrderIndex(item.folder.id, index)
            is HomeItem.NoteItem -> subjectDao.updateOrderIndex(item.subject.id, index)
        }
    }
}

suspend fun reorderFolderItems(folderId: String, orderedSubjectIds: List<String>) {
    orderedSubjectIds.forEachIndexed { index, id ->
        subjectDao.updateOrderIndex(id, index)
    }
}
```

3. Add `getSubjectsByFolderOnce` query to `SubjectDao` — modify `app/src/main/java/com/lingji/app/data/db/dao/SubjectDao.kt`:

```kotlin
@Query("SELECT * FROM subjects WHERE folderId IS :folderId ORDER BY orderIndex DESC, createdAt DESC")
suspend fun getSubjectsByFolderOnce(folderId: String?): List<SubjectEntity>
```

4. Update the `assemble()` method to pass `folderId`:

```kotlin
return Subject(
    id = entity.id,
    title = entity.title,
    type = SubjectType.valueOf(entity.type.uppercase()),
    fragments = domainFragments,
    unmergedFragments = unmerged,
    aggregatedNote = entity.aggregatedNote,
    prevAggregatedNote = entity.prevAggregatedNote,
    studyPlan = entity.studyPlan,
    createdAt = entity.createdAt,
    orderIndex = entity.orderIndex,
    pages = if (entity.type.equals("notebook", true)) domainPages else null,
    pageIndex = if (entity.type.equals("notebook", true)) domainPages.mapIndexed { idx, p -> PageIndex(p.id, p.title, idx) } else null,
    pageIndexEntries = if (entity.type.equals("notebook", true)) pageIndexEntries else null,
    lastOpenedPageId = entity.lastOpenedPageId,
    folderId = entity.folderId
)
```

5. Update `Subject.toEntity()`:

```kotlin
private fun Subject.toEntity() = SubjectEntity(
    id = id,
    title = title,
    type = type.name.lowercase(),
    aggregatedNote = aggregatedNote,
    prevAggregatedNote = prevAggregatedNote,
    studyPlan = studyPlan,
    createdAt = createdAt,
    orderIndex = orderIndex,
    pageIndexJson = pageIndexEntries?.let { gson.toJson(it) } ?: "",
    lastOpenedPageId = lastOpenedPageId,
    folderId = folderId
)
```

6. Add `FolderEntity.toDomain()` and `Folder.toEntity()` extension functions:

```kotlin
private fun FolderEntity.toDomain() = Folder(id = id, name = name, orderIndex = orderIndex, createdAt = createdAt)
private fun Folder.toEntity() = FolderEntity(id = id, name = name, orderIndex = orderIndex, createdAt = createdAt)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lingji/app/data/repository/SubjectRepository.kt app/src/main/java/com/lingji/app/data/db/dao/SubjectDao.kt
git commit -m "feat: add folder repository methods and subject folderId mapping"
```

---

### Task 3: ViewModel & State

**Covers:** [S6]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectUiState.kt`
- Modify: `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt`

**Interfaces:**
- Consumes: `SubjectRepository` folder methods, `Folder`, `HomeItem`
- Produces: `SubjectUiState.folders`, `SubjectUiState.homeItems`, `SubjectViewModel.createFolder()`, `deleteFolder()`, `renameFolder()`, `moveSubjectToFolder()`, `removeSubjectFromFolder()`, `reorderHomeItems()`, `reorderFolderItems()`

- [ ] **Step 1: Add folders and homeItems to SubjectUiState**

Modify `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectUiState.kt`:

1. Add imports:
```kotlin
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.HomeItem
```

2. Add `folders` and `currentFolderId` fields to `SubjectUiState` (after `homeFragments`):

```kotlin
val folders: List<Folder> = emptyList(),
val currentFolderId: String? = null,
```

3. Add computed `homeItems` property inside `SubjectUiState`:

```kotlin
val homeItems: List<HomeItem>
    get() {
        val folderItems = folders.map { folder ->
            HomeItem.FolderItem(folder, subjects.count { it.folderId == folder.id })
        }
        val noteItems = subjects.filter { it.folderId == null }.map { HomeItem.NoteItem(it) }
        return (folderItems + noteItems).sortedByDescending { item ->
            when (item) {
                is HomeItem.FolderItem -> item.folder.orderIndex
                is HomeItem.NoteItem -> item.subject.orderIndex
            }
        }
    }
```

- [ ] **Step 2: Add folder methods to SubjectViewModel**

Modify `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt`:

1. Add imports:
```kotlin
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.HomeItem
```

2. Update the `init` block's `combine` to include folders. Change from:

```kotlin
combine(
    subjectRepository.getAllSubjects(),
    settingsRepository.getSettings()
) { subjects, settings ->
```

To:

```kotlin
combine(
    subjectRepository.getAllSubjects(),
    subjectRepository.getAllFolders(),
    settingsRepository.getSettings()
) { subjects, folders, settings ->
```

And add `folders = folders` to the `_uiState.update`:

```kotlin
_uiState.update {
    it.copy(
        subjects = subjects,
        folders = folders,
        settings = settings,
        isSettingsOpen = it.isSettingsOpen || !settings.isConfigured()
    )
}
```

3. Add folder methods (place after `moveSubjectToBottom`):

```kotlin
fun createFolder(name: String) {
    viewModelScope.launch { subjectRepository.createFolder(name) }
}

fun deleteFolder(id: String) {
    viewModelScope.launch { subjectRepository.deleteFolder(id) }
}

fun renameFolder(id: String, name: String) {
    viewModelScope.launch { subjectRepository.renameFolder(id, name) }
}

fun moveSubjectToFolder(subjectId: String, folderId: String) {
    viewModelScope.launch { subjectRepository.moveSubjectToFolder(subjectId, folderId) }
}

fun removeSubjectFromFolder(subjectId: String) {
    viewModelScope.launch { subjectRepository.removeSubjectFromFolder(subjectId) }
}

fun reorderHomeItems(orderedItems: List<HomeItem>) {
    viewModelScope.launch { subjectRepository.reorderHomeItems(orderedItems) }
}

fun reorderFolderItems(folderId: String, orderedSubjectIds: List<String>) {
    viewModelScope.launch { subjectRepository.reorderFolderItems(folderId, orderedSubjectIds) }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/viewmodel/SubjectUiState.kt app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt
git commit -m "feat: add folder state and ViewModel methods"
```

---

### Task 4: UI Components — DragState, FolderCard, SubjectCard Changes

**Covers:** [S5.3], [S5.4], [S5.5]

**Files:**
- Create: `app/src/main/java/com/lingji/app/ui/components/DragState.kt`
- Create: `app/src/main/java/com/lingji/app/ui/components/FolderCard.kt`
- Modify: `app/src/main/java/com/lingji/app/ui/components/SubjectCard.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `HomeItem`, `Folder`, `Subject`
- Produces: `DragState` class, `FolderCard` composable, updated `SubjectCard` with drag + icon-button menu

- [ ] **Step 1: Add folder strings to strings.xml**

Modify `app/src/main/res/values/strings.xml` — add after the `move_down` line (line 34):

```xml
<!-- 文件夹 -->
<string name="new_folder">新建文件夹</string>
<string name="new_folder_hint">输入文件夹名称…</string>
<string name="folder_count">%1$d 篇笔记</string>
<string name="remove_from_folder">从文件夹中移出</string>
<string name="cd_more_actions">更多操作</string>
<string name="delete_folder_confirm">确定要删除文件夹"%1$s"吗？文件夹内的笔记将移回首页。</string>
```

- [ ] **Step 2: Create DragState**

Create `app/src/main/java/com/lingji/app/ui/components/DragState.kt`:

```kotlin
package com.lingji.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.lingji.app.domain.model.HomeItem

/**
 * Holds drag-and-drop state for the home grid.
 * Tracks the dragged item, finger offset, and current drop target.
 */
class DragState {
    var isDragging by mutableStateOf(false)
        private set
    var draggedItem by mutableStateOf<HomeItem?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var dragStartPos by mutableStateOf(Offset.Zero)
        private set
    var dropTargetFolderId by mutableStateOf<String?>(null)
        private set
    var reorderTargetIndex by mutableStateOf(-1)
        private set

    fun startDrag(item: HomeItem, startPos: Offset) {
        isDragging = true
        draggedItem = item
        dragStartPos = startPos
        dragOffset = Offset.Zero
        dropTargetFolderId = null
        reorderTargetIndex = -1
    }

    fun updateDrag(delta: Offset) {
        dragOffset += delta
    }

    fun setDropTarget(folderId: String?) {
        dropTargetFolderId = folderId
    }

    fun setReorderTarget(index: Int) {
        reorderTargetIndex = index
    }

    fun endDrag(): DragResult {
        val result = if (dropTargetFolderId != null) {
            DragResult.MoveToFolder(dropTargetFolderId!!)
        } else if (reorderTargetIndex >= 0) {
            DragResult.Reorder(reorderTargetIndex)
        } else {
            DragResult.None
        }
        reset()
        return result
    }

    fun cancelDrag() {
        reset()
    }

    private fun reset() {
        isDragging = false
        draggedItem = null
        dragOffset = Offset.Zero
        dragStartPos = Offset.Zero
        dropTargetFolderId = null
        reorderTargetIndex = -1
    }
}

sealed interface DragResult {
    data object None : DragResult
    data class MoveToFolder(val folderId: String) : DragResult
    data class Reorder(val targetIndex: Int) : DragResult
}
```

- [ ] **Step 3: Create FolderCard**

Create `app/src/main/java/com/lingji/app/ui/components/FolderCard.kt`:

```kotlin
package com.lingji.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.Folder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    folder: Folder,
    noteCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    isDropTarget: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val containerColor = if (isDropTarget) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            if (isDropTarget) 2.dp else 1.dp,
            if (isDropTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_actions),
                        tint = Color(0xFFD6D3D1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(R.string.folder_count, noteCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = { onRename(); menuExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { onDelete(); menuExpanded = false }
                )
            }
        }
    }
}
```

- [ ] **Step 4: Update SubjectCard — add drag callback, replace long-press menu with icon button**

Modify `app/src/main/java/com/lingji/app/ui/components/SubjectCard.kt`:

1. Add imports:
```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import com.lingji.app.domain.model.HomeItem
```

2. Add new parameters to `SubjectCard`: `onDragStart: (Offset) -> Unit`, `isDragging: Boolean = false`, `isDropTarget: Boolean = false`, and an optional `onRemoveFromFolder: (() -> Unit)? = null`:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubjectCard(
    subject: Subject,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onCopyExport: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    onRemoveFromFolder: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
```

3. Replace the `combinedClickable` on the Card with `clickable` + `pointerInput` for drag. Change the Card modifier:

```kotlin
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .pointerInput(subject.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() }
                )
            }
            .alpha(if (isDragging) 0.3f else 1f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isDropTarget) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            if (isDropTarget) 2.dp else 1.dp,
            if (isDropTarget) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
```

4. Add a "⋯" IconButton next to the existing delete/rename icons. In the Row that contains the delete and rename IconButtons (the `Row(horizontalArrangement = Arrangement.spacedBy(2.dp))`), add a more-actions button before the delete button:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.cd_more_actions),
            tint = Color(0xFFD6D3D1),
            modifier = Modifier.size(18.dp)
        )
    }
    IconButton(
        onClick = { showDeleteDialog = true },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.cd_delete),
            tint = Color(0xFFD6D3D1),
            modifier = Modifier.size(18.dp)
        )
    }
    IconButton(
        onClick = { onRename(); menuExpanded = false },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(R.string.cd_rename),
            tint = Color(0xFFD6D3D1),
            modifier = Modifier.size(18.dp)
        )
    }
}
```

5. Add import for `Icons.Default.MoreVert`:
```kotlin
import androidx.compose.material.icons.filled.MoreVert
```

6. Add `alpha` import:
```kotlin
import androidx.compose.ui.draw.alpha
```

7. Add `clickable` import (replace `combinedClickable` usage):
```kotlin
import androidx.compose.foundation.clickable
```
Remove the `import androidx.compose.foundation.combinedClickable` if no longer used.

8. In the DropdownMenu items, add "从文件夹中移出" if `onRemoveFromFolder != null`:

```kotlin
DropdownMenu(
    expanded = menuExpanded,
    onDismissRequest = { menuExpanded = false }
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.export)) },
        onClick = { onExport(); menuExpanded = false }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.copy_to_clipboard)) },
        onClick = { onCopyExport(); menuExpanded = false }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.move_to_top)) },
        onClick = { onMoveToTop(); menuExpanded = false }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.move_up)) },
        onClick = { onMoveUp(); menuExpanded = false },
        enabled = canMoveUp
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.move_down)) },
        onClick = { onMoveDown(); menuExpanded = false },
        enabled = canMoveDown
    )
    if (onRemoveFromFolder != null) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.remove_from_folder)) },
            onClick = { onRemoveFromFolder(); menuExpanded = false }
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.delete)) },
        onClick = {
            showDeleteDialog = true
            menuExpanded = false
        }
    )
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/components/DragState.kt app/src/main/java/com/lingji/app/ui/components/FolderCard.kt app/src/main/java/com/lingji/app/ui/components/SubjectCard.kt app/src/main/res/values/strings.xml
git commit -m "feat: add DragState, FolderCard, update SubjectCard with drag + icon menu"
```

---

### Task 5: Home Screen Integration

**Covers:** [S5.1], [S5.4]

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt`

**Interfaces:**
- Consumes: `SubjectUiState.homeItems`, `SubjectViewModel` folder methods, `DragState`, `FolderCard`, updated `SubjectCard`
- Produces: Mixed grid with folders + notes, AddFolderCard, drag-and-drop on home page

- [ ] **Step 1: Add imports to SubjectGalleryScreen**

Add these imports to `app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt`:

```kotlin
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.CreateNewFolder
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.HomeItem
import com.lingji.app.ui.components.DragState
import com.lingji.app.ui.components.FolderCard
```

- [ ] **Step 2: Add DragState and folder dialog state to SubjectGalleryScreen**

Inside the `SubjectGalleryScreen` composable, after `val hazeState = remember { HazeState() }`, add:

```kotlin
val dragState = remember { DragState() }
var showAddFolderDialog by remember { mutableStateOf(false) }
var renameFolderId by remember { mutableStateOf<String?>(null) }
var renameFolderDefault by remember { mutableStateOf("") }
var deleteFolderId by remember { mutableStateOf<String?>(null) }
val gridState = rememberLazyGridState()
```

Add import: `import androidx.compose.foundation.lazy.grid.rememberLazyGridState`

- [ ] **Step 3: Replace the grid items section with mixed homeItems**

Replace the existing `LazyVerticalGrid` content block (the `if (uiState.subjects.isEmpty()) {...} else {...}` section) with:

```kotlin
if (uiState.subjects.isEmpty() && uiState.folders.isEmpty()) {
    EmptySubjectState(onCreate = { showAddDialog = true })
} else {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "__add__") {
            AddSubjectCard(
                onAdd = { title, type ->
                    viewModel.addSubject(title, type)
                }
            )
        }
        item(key = "__add_folder__") {
            AddFolderCard(
                onCreate = { name ->
                    viewModel.createFolder(name)
                }
            )
        }
        items(uiState.homeItems, key = { item ->
            when (item) {
                is HomeItem.FolderItem -> "folder_${item.folder.id}"
                is HomeItem.NoteItem -> "note_${item.subject.id}"
            }
        }) { homeItem ->
            when (homeItem) {
                is HomeItem.FolderItem -> {
                    val isDropTarget = dragState.dropTargetFolderId == homeItem.folder.id
                    FolderCard(
                        folder = homeItem.folder,
                        noteCount = homeItem.noteCount,
                        onClick = { onFolderClick(homeItem.folder.id) },
                        onLongClick = { },
                        onRename = {
                            renameFolderId = homeItem.folder.id
                            renameFolderDefault = homeItem.folder.name
                        },
                        onDelete = {
                            deleteFolderId = homeItem.folder.id
                        },
                        isDropTarget = isDropTarget
                    )
                }
                is HomeItem.NoteItem -> {
                    val subject = homeItem.subject
                    val isDragging = dragState.draggedItem is HomeItem.NoteItem &&
                        (dragState.draggedItem as HomeItem.NoteItem).subject.id == subject.id
                    SubjectCard(
                        subject = subject,
                        onClick = { onSubjectClick(subject.id) },
                        onDelete = { viewModel.deleteSubject(subject.id) },
                        onRename = {
                            renameSubjectId = subject.id
                            renameDefault = subject.title
                        },
                        onExport = {
                            exportSubject = subject
                            exportLauncher.launch(viewModel.buildExportFileName(subject.title))
                        },
                        onCopyExport = {
                            scope.launch {
                                try {
                                    val encoded = viewModel.exportSubjectToText(subject)
                                    if (encoded.length > CLIPBOARD_SIZE_LIMIT) {
                                        showClipboardTooLargeDialog = true
                                    } else {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(subject.title, encoded))
                                        Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onMoveToTop = { viewModel.moveSubjectToTop(subject.id) },
                        onMoveUp = { viewModel.moveSubjectUp(subject.id) },
                        onMoveDown = { viewModel.moveSubjectDown(subject.id) },
                        canMoveUp = true,
                        canMoveDown = true,
                        onDragStart = { offset ->
                            dragState.startDrag(homeItem, offset)
                        },
                        onDrag = { dragAmount ->
                            dragState.updateDrag(dragAmount)
                            val globalPos = dragState.dragStartPos + dragState.dragOffset
                            handleDragHitTest(globalPos, gridState, uiState.homeItems, dragState)
                        },
                        onDragEnd = {
                            val result = dragState.endDrag()
                            handleDragEnd(result, dragState, viewModel, uiState.homeItems)
                        },
                        onDragCancel = { dragState.cancelDrag() },
                        isDragging = isDragging
                    )
                }
            }
        }
    }

    // Drag overlay — purely visual floating card following finger
    // (no pointerInput here; the drag gesture is handled by the card's own pointerInput)
    if (dragState.isDragging && dragState.draggedItem != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Empty pointerInput to capture touches during drag so underlying
                    // cards don't receive click events
                    detectDragGesturesAfterLongPress(
                        onDragStart = { },
                        onDrag = { change, _ -> change.consume() },
                        onDragEnd = { },
                        onDragCancel = { }
                    )
                }
        )
    }
}
```

- [ ] **Step 4: Add onFolderClick callback parameter**

Update the `SubjectGalleryScreen` function signature to add `onFolderClick: (String) -> Unit`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectGalleryScreen(
    viewModel: SubjectViewModel,
    onSubjectClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
```

- [ ] **Step 5: Add AddFolderCard composable**

Add this composable at the end of the file (after `AddSubjectDialog`):

```kotlin
@Composable
private fun AddFolderCard(
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isCreating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    LaunchedEffect(isCreating) {
        if (isCreating) focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = !isCreating,
                onClick = { isCreating = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        border = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dashedBorder(strokeColor),
            contentAlignment = Alignment.Center
        ) {
            SubjectCardSizingReference(modifier = Modifier.alpha(0f))
            if (isCreating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (name.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.new_folder_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(
                            onClick = {
                                isCreating = false
                                name = ""
                            }
                        ) { Text(stringResource(R.string.cancel)) }
                        TextButton(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onCreate(name.trim())
                                    isCreating = false
                                    name = ""
                                }
                            },
                            enabled = name.isNotBlank()
                        ) { Text(stringResource(R.string.confirm)) }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.new_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: Add hit-test helper functions**

Add these top-level helper functions at the end of the file:

```kotlin
private fun handleDragHitTest(
    globalPos: Offset,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    homeItems: List<HomeItem>,
    dragState: DragState
) {
    val layoutInfo = gridState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo

    // Check if finger is over a folder card
    val folderTarget = visibleItems.firstOrNull { itemInfo ->
        val key = itemInfo.key
        key is String && key.startsWith("folder_") &&
            globalPos.x >= itemInfo.offset.x &&
            globalPos.x <= itemInfo.offset.x + itemInfo.size.width &&
            globalPos.y >= itemInfo.offset.y &&
            globalPos.y <= itemInfo.offset.y + itemInfo.size.height
    }

    if (folderTarget != null) {
        val folderId = (folderTarget.key as String).removePrefix("folder_")
        dragState.setDropTarget(folderId)
        dragState.setReorderTarget(-1)
    } else {
        dragState.setDropTarget(null)
        // Compute reorder index based on position
        val noteItems = visibleItems.filter { it.key is String && (it.key as String).startsWith("note_") }
        val insertIndex = noteItems.indexOfFirst { itemInfo ->
            globalPos.y < itemInfo.offset.y + itemInfo.size.height / 2
        }
        dragState.setReorderTarget(if (insertIndex >= 0) insertIndex else noteItems.size)
    }
}

private fun handleDragEnd(
    result: DragResult,
    dragState: DragState,
    viewModel: SubjectViewModel,
    homeItems: List<HomeItem>
) {
    val draggedItem = dragState.draggedItem ?: return
    when (result) {
        is DragResult.MoveToFolder -> {
            if (draggedItem is HomeItem.NoteItem) {
                viewModel.moveSubjectToFolder(draggedItem.subject.id, result.folderId)
            }
        }
        is DragResult.Reorder -> {
            // Build new ordered list and call reorderHomeItems
            val mutableList = homeItems.toMutableList()
            val draggedIndex = mutableList.indexOf(draggedItem)
            if (draggedIndex >= 0 && draggedIndex != result.targetIndex) {
                mutableList.removeAt(draggedIndex)
                val insertPos = if (result.targetIndex > draggedIndex) result.targetIndex - 1 else result.targetIndex
                mutableList.add(insertPos.coerceIn(0, mutableList.size), draggedItem)
                viewModel.reorderHomeItems(mutableList)
            }
        }
        is DragResult.None -> { }
    }
}
```

- [ ] **Step 7: Add folder rename and delete dialogs**

After the existing rename subject dialog block, add:

```kotlin
renameFolderId?.let { id ->
    var name by remember(id) { mutableStateOf(renameFolderDefault) }
    LingjiDialog(
        onDismissRequest = { renameFolderId = null },
        title = { Text(stringResource(R.string.rename)) },
        text = {
            GlassOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.new_title)) },
                singleLine = true
            )
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.save),
                onClick = {
                    if (name.isNotBlank()) viewModel.renameFolder(id, name)
                    renameFolderId = null
                }
            )
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = { renameFolderId = null }
            )
        }
    )
}

deleteFolderId?.let { id ->
    val folder = uiState.folders.find { it.id == id }
    LingjiDialog(
        onDismissRequest = { deleteFolderId = null },
        title = { Text(stringResource(R.string.delete)) },
        text = {
            Text(stringResource(R.string.delete_folder_confirm, folder?.name ?: ""))
        },
        confirmButton = {
            LingjiDialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = {
                    viewModel.deleteFolder(id)
                    deleteFolderId = null
                },
                isDestructive = true
            )
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = { deleteFolderId = null }
            )
        }
    )
}
```

- [ ] **Step 8: Verify compilation and install**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL (installs to emulator if available)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt
git commit -m "feat: integrate folders and drag-drop into home screen"
```

---

### Task 6: Folder Screen & Navigation

**Covers:** [S5.2], [S7]

**Files:**
- Create: `app/src/main/java/com/lingji/app/ui/screens/FolderScreen.kt`
- Modify: `app/src/main/java/com/lingji/app/ui/navigation/LingjiNavigation.kt`

**Interfaces:**
- Consumes: `SubjectViewModel` folder methods, `SubjectUiState`, `SubjectCard`, `DragState`
- Produces: `FolderScreen` composable, folder navigation route

- [ ] **Step 1: Create FolderScreen**

Create `app/src/main/java/com/lingji/app/ui/screens/FolderScreen.kt`:

```kotlin
package com.lingji.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.ui.components.AddSubjectCard
import com.lingji.app.ui.components.SubjectCard
import com.lingji.app.ui.viewmodel.SubjectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    viewModel: SubjectViewModel,
    folderId: String,
    onSubjectClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val folder = uiState.folders.find { it.id == folderId }
    val folderSubjects = uiState.subjects.filter { it.folderId == folderId }
        .sortedByDescending { it.orderIndex }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var renameSubjectId by remember { mutableStateOf<String?>(null) }
    var renameDefault by remember { mutableStateOf("") }
    var exportSubject by remember { mutableStateOf<Subject?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val subject = exportSubject ?: return@rememberLauncherForActivityResult
        exportSubject = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                viewModel.exportSubject(subject, uri)
                Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "__add__") {
                    AddSubjectCard(
                        onAdd = { title, type ->
                            viewModel.addSubject(title, type)
                            // Move the newly created subject into this folder
                            // (addSubject creates with default orderIndex; we need to set folderId)
                            // This is handled by a variant — see note below
                        }
                    )
                }
                items(folderSubjects, key = { it.id }) { subject ->
                    val index = folderSubjects.indexOf(subject)
                    SubjectCard(
                        subject = subject,
                        onClick = { onSubjectClick(subject.id) },
                        onDelete = { viewModel.deleteSubject(subject.id) },
                        onRename = {
                            renameSubjectId = subject.id
                            renameDefault = subject.title
                        },
                        onExport = {
                            exportSubject = subject
                            exportLauncher.launch(viewModel.buildExportFileName(subject.title))
                        },
                        onCopyExport = {
                            scope.launch {
                                try {
                                    val encoded = viewModel.exportSubjectToText(subject)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(subject.title, encoded))
                                    Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onMoveToTop = { viewModel.moveSubjectToTop(subject.id) },
                        onMoveUp = { viewModel.moveSubjectUp(subject.id) },
                        onMoveDown = { viewModel.moveSubjectDown(subject.id) },
                        canMoveUp = index > 0,
                        canMoveDown = index < folderSubjects.lastIndex,
                        onRemoveFromFolder = { viewModel.removeSubjectFromFolder(subject.id) }
                    )
                }
            }
        }
    }

    renameSubjectId?.let { id ->
        var title by remember(id) { mutableStateOf(renameDefault) }
        com.lingji.app.ui.components.LingjiDialog(
            onDismissRequest = { renameSubjectId = null },
            title = { Text(stringResource(R.string.rename_subject)) },
            text = {
                com.lingji.app.ui.components.GlassOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.new_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                com.lingji.app.ui.components.LingjiDialogConfirmButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        if (title.isNotBlank()) viewModel.renameSubject(id, title)
                        renameSubjectId = null
                    }
                )
            },
            dismissButton = {
                com.lingji.app.ui.components.LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { renameSubjectId = null }
                )
            }
        )
    }
}
```

**Note on AddSubjectCard in folder:** The existing `AddSubjectCard` calls `viewModel.addSubject(title, type)` which creates a subject with `folderId = null`. To create a subject directly inside the folder, add a `folderId` parameter to `AddSubjectCard` and pass it through. Alternatively, after creating, immediately call `viewModel.moveSubjectToFolder(subjectId, folderId)`. The simplest approach: add an optional `folderId` parameter to the `AddSubjectCard` composable and to `viewModel.addSubject()`.

Add this overload to `SubjectViewModel`:

```kotlin
fun addSubject(title: String, type: SubjectType, folderId: String? = null) {
    viewModelScope.launch {
        val subject = Subject.create(title, type).copy(folderId = folderId)
        subjectRepository.insert(subject)
        _uiState.update { it.copy(currentSubjectId = subject.id) }
    }
}
```

Update `AddSubjectCard` to accept optional `folderId: String? = null` and pass it to `onAdd`.

- [ ] **Step 2: Add folder route to navigation**

Modify `app/src/main/java/com/lingji/app/ui/navigation/LingjiNavigation.kt`:

1. Add import:
```kotlin
import com.lingji.app.ui.screens.FolderScreen
```

2. Add the folder route after the `"subject/{id}"` composable block:

```kotlin
composable(
    "folder/{folderId}",
    arguments = listOf(navArgument("folderId") { type = NavType.StringType })
) { backStackEntry ->
    val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
    FolderScreen(
        viewModel = viewModel,
        folderId = folderId,
        onSubjectClick = { id ->
            viewModel.setCurrentSubject(id)
            navController.navigate("subject/$id")
        },
        onBack = { navController.popBackStack() }
    )
}
```

3. Update the `"gallery"` composable to pass `onFolderClick`:

```kotlin
composable("gallery") {
    SubjectGalleryScreen(
        viewModel = viewModel,
        onSubjectClick = { id ->
            viewModel.setCurrentSubject(id)
            navController.navigate("subject/$id")
        },
        onFolderClick = { folderId ->
            navController.navigate("folder/$folderId")
        },
        onOpenSettings = { navController.navigate("settings") }
    )
}
```

- [ ] **Step 3: Update AddSubjectCard to accept folderId**

Modify the `AddSubjectCard` composable in `SubjectGalleryScreen.kt`:

1. Add `folderId: String? = null` parameter
2. Pass it to `onAdd`: change `onAdd(title.trim(), type)` to `onAdd(title.trim(), type, folderId)`
3. Update the `onAdd` type signature to `(String, SubjectType, String?) -> Unit`

Update all call sites of `AddSubjectCard` in both `SubjectGalleryScreen` and `FolderScreen` to pass the new parameter.

In `SubjectGalleryScreen`:
```kotlin
AddSubjectCard(
    onAdd = { title, type, _ ->
        viewModel.addSubject(title, type)
    }
)
```

In `FolderScreen`:
```kotlin
AddSubjectCard(
    folderId = folderId,
    onAdd = { title, type, fid ->
        viewModel.addSubject(title, type, fid)
    }
)
```

- [ ] **Step 4: Verify compilation and install**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/screens/FolderScreen.kt app/src/main/java/com/lingji/app/ui/navigation/LingjiNavigation.kt app/src/main/java/com/lingji/app/ui/screens/SubjectGalleryScreen.kt app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt
git commit -m "feat: add FolderScreen and folder navigation route"
```

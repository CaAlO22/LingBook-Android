# Agent 笔记修改撤销功能 Implementation Plan

> [!NOTE]
> This document may not reflect the current implementation.
> See the final report for up-to-date state:
> [Final Report](../reports/agent-note-undo.md)

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 FRAGMENT 和 NOTEBOOK 两种笔记类型实现多步撤销，agent 一轮会话对多页的修改作为一个整体撤销点。

**Architecture:** 新建 `note_revisions` 表记录每次内容修改前的全文快照（含 batchId 标识 agent 会话）。通过 Kotlin 协程上下文元素 `EditBatch` 在 ViewModel 层注入 batchId，Repository 的 suspend update 方法从 `coroutineContext` 读取，对现有 agent 调用链零侵入。撤销时按 batch 整体回退到会话开始前的状态。

**Tech Stack:** Kotlin + Jetpack Compose + Room + Hilt + kotlinx-coroutines + mockk

## Global Constraints

- 验证命令：`./gradlew :app:compileDebugKotlin`，完成后 `./gradlew :app:installDebug` 同步模拟器（AGENTS.md 规范）。
- 字符串资源统一放 `app/src/main/res/values/strings.xml`，按功能分组注释。
- 依赖注入用 Hilt；Repository 是 `@Singleton @Inject constructor`。
- Room 数据库当前 version = 12，本计划升至 13，需写 `MIGRATION_12_13`。
- 测试用 `io.mockk` + `kotlinx.coroutines.test.runTest`，位于 `app/src/test`。
- 旧 `prevAggregatedNote` 字段保留不动（向后兼容）；旧 `rollbackAggregatedNote` 方法删除，由新 `rollbackLast` 替代。
- 覆盖范围：`edit_replace` 改内容、`update_page` 改标题、`organize`/`refine` 重写聚合笔记、用户手动保存。不覆盖 create/delete page、fragment 增删、studyPlan。

---

### Task 1: NoteRevisionEntity + NoteRevisionDao + 数据库迁移

**Covers:** [S2]
**Files:**
- Create: `app/src/main/java/com/lingji/app/data/db/entities/NoteRevisionEntity.kt`
- Create: `app/src/main/java/com/lingji/app/data/db/dao/NoteRevisionDao.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt`
- Modify: `app/src/main/java/com/lingji/app/di/AppModule.kt`

**Interfaces:**
- Produces: `NoteRevisionEntity`（data class，11 字段）、`NoteRevisionDao`（insert / getLatestForSubject / getByBatchForSubject / deleteByBatchForSubject / deleteById / trimForSubject）

- [ ] **Step 1: 创建 NoteRevisionEntity**

文件 `app/src/main/java/com/lingji/app/data/db/entities/NoteRevisionEntity.kt`：

```kotlin
package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_revisions")
data class NoteRevisionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val pageId: String?,
    val field: String,
    val prevContent: String,
    val prevTitle: String?,
    val batchId: String?,
    val createdAt: Long
)
```

- [ ] **Step 2: 创建 NoteRevisionDao**

文件 `app/src/main/java/com/lingji/app/data/db/dao/NoteRevisionDao.kt`：

```kotlin
package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.NoteRevisionEntity

@Dao
interface NoteRevisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(revision: NoteRevisionEntity)

    @Query("SELECT * FROM note_revisions WHERE subjectId = :subjectId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForSubject(subjectId: String): NoteRevisionEntity?

    @Query("SELECT * FROM note_revisions WHERE subjectId = :subjectId AND batchId = :batchId ORDER BY createdAt ASC")
    suspend fun getByBatchForSubject(subjectId: String, batchId: String): List<NoteRevisionEntity>

    @Query("DELETE FROM note_revisions WHERE subjectId = :subjectId AND batchId = :batchId")
    suspend fun deleteByBatchForSubject(subjectId: String, batchId: String)

    @Query("DELETE FROM note_revisions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM note_revisions WHERE subjectId = :subjectId AND id NOT IN (SELECT id FROM note_revisions WHERE subjectId = :subjectId ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimForSubject(subjectId: String, keep: Int)
}
```

- [ ] **Step 3: 注册到 LingjiDatabase**

修改 `app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt`：

在 import 区加（entity 和 dao 各一行，按字母序插入）：
```kotlin
import com.lingji.app.data.db.dao.NoteRevisionDao
import com.lingji.app.data.db.entities.NoteRevisionEntity
```

`@Database` 的 `entities` 列表末尾加 `NoteRevisionEntity::class`，`version = 12` 改为 `version = 13`：

```kotlin
@Database(
    entities = [
        SubjectEntity::class,
        FragmentEntity::class,
        NotebookPageEntity::class,
        SettingsEntity::class,
        SubjectSummaryEntity::class,
        HomeConversationEntity::class,
        HomeMessageEntity::class,
        HomeFragmentEntity::class,
        FolderEntity::class,
        NoteRevisionEntity::class
    ],
    version = 13,
    exportSchema = false
)
```

在 `MIGRATION_11_12` 之后、`companion object` 结束前加：

```kotlin
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS note_revisions (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "subjectId TEXT NOT NULL, " +
                "pageId TEXT, " +
                "field TEXT NOT NULL, " +
                "prevContent TEXT NOT NULL, " +
                "prevTitle TEXT, " +
                "batchId TEXT, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_revisions_subjectId ON note_revisions(subjectId)")
    }
}
```

在 abstract dao 声明区（`abstract fun folderDao(): FolderDao` 之后）加：
```kotlin
abstract fun noteRevisionDao(): NoteRevisionDao
```

- [ ] **Step 4: 注册迁移和 DAO 提供者到 AppModule**

修改 `app/src/main/java/com/lingji/app/di/AppModule.kt`：

`addMigrations(...)` 调用末尾加 `LingjiDatabase.MIGRATION_12_13`：

```kotlin
.addMigrations(
    LingjiDatabase.MIGRATION_1_2,
    LingjiDatabase.MIGRATION_2_3,
    LingjiDatabase.MIGRATION_3_4,
    LingjiDatabase.MIGRATION_4_5,
    LingjiDatabase.MIGRATION_5_6,
    LingjiDatabase.MIGRATION_6_7,
    LingjiDatabase.MIGRATION_7_8,
    LingjiDatabase.MIGRATION_8_9,
    LingjiDatabase.MIGRATION_9_10,
    LingjiDatabase.MIGRATION_10_11,
    LingjiDatabase.MIGRATION_11_12,
    LingjiDatabase.MIGRATION_12_13
)
```

在 `provideFolderDao` 之后加：
```kotlin
@Provides
fun provideNoteRevisionDao(database: LingjiDatabase) = database.noteRevisionDao()
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lingji/app/data/db/entities/NoteRevisionEntity.kt app/src/main/java/com/lingji/app/data/db/dao/NoteRevisionDao.kt app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt app/src/main/java/com/lingji/app/di/AppModule.kt
git commit -m "feat: add note_revisions table for multi-step undo"
```

---

### Task 2: EditBatch 协程上下文元素

**Covers:** [S3]
**Files:**
- Create: `app/src/main/java/com/lingji/app/data/edit/EditBatch.kt`
- Test: `app/src/test/java/com/lingji/app/data/edit/EditBatchTest.kt`

**Interfaces:**
- Produces: `EditBatch(val batchId: String)` 协程上下文元素，通过 `coroutineContext[EditBatch]?.batchId` 读取

- [ ] **Step 1: 写失败测试**

文件 `app/src/test/java/com/lingji/app/data/edit/EditBatchTest.kt`：

```kotlin
package com.lingji.app.data.edit

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditBatchTest {

    @Test
    fun editBatch_presentInsideWithContext() = runTest {
        withContext(EditBatch("batch-1")) {
            assertEquals("batch-1", coroutineContext[EditBatch]?.batchId)
        }
    }

    @Test
    fun editBatch_absentWithoutWithContext() = runTest {
        assertNull(coroutineContext[EditBatch])
    }

    @Test
    fun editBatch_nestedContextOverrides() = runTest {
        withContext(EditBatch("outer")) {
            withContext(EditBatch("inner")) {
                assertEquals("inner", coroutineContext[EditBatch]?.batchId)
            }
            assertEquals("outer", coroutineContext[EditBatch]?.batchId)
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.data.edit.EditBatchTest"`
Expected: FAIL（EditBatch 未定义，编译错误）

- [ ] **Step 3: 实现 EditBatch**

文件 `app/src/main/java/com/lingji/app/data/edit/EditBatch.kt`：

```kotlin
package com.lingji.app.data.edit

import kotlin.coroutines.CoroutineContext

/**
 * 协程上下文元素：标记当前协程处于一次 agent 编辑会话中。
 *
 * [com.lingji.app.data.repository.SubjectRepository] 的 update 方法会读取此元素，
 * 将修改归入同一 batch，支持按会话整体撤销。
 * 用法：`withContext(EditBatch(UUID.randomUUID().toString())) { ... }`
 */
class EditBatch(val batchId: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<EditBatch>
    override val key: CoroutineContext.Key<*> = Key
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.data.edit.EditBatchTest"`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/data/edit/EditBatch.kt app/src/test/java/com/lingji/app/data/edit/EditBatchTest.kt
git commit -m "feat: add EditBatch coroutine context element"
```

---

### Task 3: SubjectRepository - revision 存档 + rollbackLast

**Covers:** [S2, S3, S4]
**Files:**
- Modify: `app/src/main/java/com/lingji/app/data/repository/SubjectRepository.kt`
- Test: `app/src/test/java/com/lingji/app/data/repository/SubjectRepositoryUndoTest.kt`

**Interfaces:**
- Consumes: `NoteRevisionDao`（Task 1）、`EditBatch`（Task 2）
- Produces: `SubjectRepository.updateAggregatedNote` / `updatePage`（带 revision 存档）、`rollbackLast(subjectId): Boolean`

- [ ] **Step 1: 写失败测试**

文件 `app/src/test/java/com/lingji/app/data/repository/SubjectRepositoryUndoTest.kt`：

```kotlin
package com.lingji.app.data.repository

import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.NoteRevisionDao
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.SubjectEntity
import com.lingji.app.data.edit.EditBatch
import com.lingji.app.domain.model.NotebookPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubjectRepositoryUndoTest {

    private val subjectDao = mockk<SubjectDao>()
    private val fragmentDao = mockk<FragmentDao>()
    private val pageDao = mockk<NotebookPageDao>()
    private val folderDao = mockk<FolderDao>()
    private val revisionDao = mockk<NoteRevisionDao>(relaxed = true)

    private lateinit var repo: SubjectRepository

    private val fragmentEntity = SubjectEntity(
        id = "s1", title = "T", type = "fragment",
        aggregatedNote = "old", prevAggregatedNote = null,
        studyPlan = "", createdAt = 0L, orderIndex = 0,
        pageIndexJson = "", lastOpenedPageId = null, folderId = null
    )

    @Before
    fun setup() {
        repo = SubjectRepository(subjectDao, fragmentDao, pageDao, folderDao, revisionDao)
    }

    @Test
    fun updateAggregatedNote_storesRevisionWithBatchId() = runTest {
        coEvery { subjectDao.getSubjectById("s1") } returns fragmentEntity
        coEvery { subjectDao.updateAggregatedNote("s1", "new", "old") } returns Unit
        val revisionSlot = slot<com.lingji.app.data.db.entities.NoteRevisionEntity>()
        coEvery { revisionDao.insert(capture(revisionSlot)) } returns Unit

        withContext(EditBatch("batch-1")) {
            repo.updateAggregatedNote("s1", "new")
        }

        assertEquals("old", revisionSlot.captured.prevContent)
        assertEquals("batch-1", revisionSlot.captured.batchId)
        assertEquals("aggregated", revisionSlot.captured.field)
        coVerify { subjectDao.updateAggregatedNote("s1", "new", "old") }
    }

    @Test
    fun updateAggregatedNote_storesRevisionWithNullBatchId_whenNoEditBatch() = runTest {
        coEvery { subjectDao.getSubjectById("s1") } returns fragmentEntity
        coEvery { subjectDao.updateAggregatedNote("s1", "new", "old") } returns Unit
        val revisionSlot = slot<com.lingji.app.data.db.entities.NoteRevisionEntity>()
        coEvery { revisionDao.insert(capture(revisionSlot)) } returns Unit

        repo.updateAggregatedNote("s1", "new")

        assertEquals(null, revisionSlot.captured.batchId)
    }

    @Test
    fun updatePage_storesRevisionWithBatchId() = runTest {
        val pageEntity = NotebookPageEntity(
            id = "p1", subjectId = "s2", title = "旧标题", content = "旧内容",
            orderIndex = 0, createdAt = 0L, updatedAt = 0L, indexedAt = 0L
        )
        coEvery { pageDao.getPagesBySubjectOnce("s2") } returns listOf(pageEntity)
        coEvery { pageDao.update("p1", "新标题", "新内容", any()) } returns Unit
        val revisionSlot = slot<com.lingji.app.data.db.entities.NoteRevisionEntity>()
        coEvery { revisionDao.insert(capture(revisionSlot)) } returns Unit

        withContext(EditBatch("batch-2")) {
            repo.updatePage("s2", NotebookPage(id = "p1", title = "新标题", content = "新内容", createdAt = 0L, updatedAt = 100L, indexedAt = 0L))
        }

        assertEquals("旧内容", revisionSlot.captured.prevContent)
        assertEquals("旧标题", revisionSlot.captured.prevTitle)
        assertEquals("p1", revisionSlot.captured.pageId)
        assertEquals("batch-2", revisionSlot.captured.batchId)
        assertEquals("page", revisionSlot.captured.field)
    }

    @Test
    fun rollbackLast_returnsFalse_whenNoRevision() = runTest {
        coEvery { revisionDao.getLatestForSubject("s1") } returns null

        assertFalse(repo.rollbackLast("s1"))
    }

    @Test
    fun rollbackLast_restoresSingleAggregatedRevision() = runTest {
        val revision = com.lingji.app.data.db.entities.NoteRevisionEntity(
            id = "r1", subjectId = "s1", pageId = null, field = "aggregated",
            prevContent = "old", prevTitle = null, batchId = null, createdAt = 100L
        )
        coEvery { revisionDao.getLatestForSubject("s1") } returns revision
        coEvery { subjectDao.updateAggregatedNote("s1", "old", null) } returns Unit
        coEvery { revisionDao.deleteById("r1") } returns Unit

        val result = repo.rollbackLast("s1")

        assertTrue(result)
        coVerify { subjectDao.updateAggregatedNote("s1", "old", null) }
        coVerify { revisionDao.deleteById("r1") }
    }

    @Test
    fun rollbackLast_restoresEntireBatch_withMultiplePages() = runTest {
        val batchId = "batch-3"
        val r1 = com.lingji.app.data.db.entities.NoteRevisionEntity(
            id = "r1", subjectId = "s2", pageId = "p1", field = "page",
            prevContent = "p1-old", prevTitle = "t1-old", batchId = batchId, createdAt = 100L
        )
        val r2 = com.lingji.app.data.db.entities.NoteRevisionEntity(
            id = "r2", subjectId = "s2", pageId = "p2", field = "page",
            prevContent = "p2-old", prevTitle = "t2-old", batchId = batchId, createdAt = 200L
        )
        coEvery { revisionDao.getLatestForSubject("s2") } returns r2
        coEvery { revisionDao.getByBatchForSubject("s2", batchId) } returns listOf(r1, r2)
        coEvery { pageDao.update("p1", "t1-old", "p1-old", any()) } returns Unit
        coEvery { pageDao.update("p2", "t2-old", "p2-old", any()) } returns Unit
        coEvery { revisionDao.deleteByBatchForSubject("s2", batchId) } returns Unit

        val result = repo.rollbackLast("s2")

        assertTrue(result)
        coVerify { pageDao.update("p1", "t1-old", "p1-old", any()) }
        coVerify { pageDao.update("p2", "t2-old", "p2-old", any()) }
        coVerify { revisionDao.deleteByBatchForSubject("s2", batchId) }
    }

    @Test
    fun rollbackLast_batchWithRepeatedPage_usesEarliestPrevContent() = runTest {
        val batchId = "batch-4"
        val r1 = com.lingji.app.data.db.entities.NoteRevisionEntity(
            id = "r1", subjectId = "s1", pageId = null, field = "aggregated",
            prevContent = "v1", prevTitle = null, batchId = batchId, createdAt = 100L
        )
        val r2 = com.lingji.app.data.db.entities.NoteRevisionEntity(
            id = "r2", subjectId = "s1", pageId = null, field = "aggregated",
            prevContent = "v2", prevTitle = null, batchId = batchId, createdAt = 200L
        )
        coEvery { revisionDao.getLatestForSubject("s1") } returns r2
        coEvery { revisionDao.getByBatchForSubject("s1", batchId) } returns listOf(r1, r2)
        coEvery { subjectDao.updateAggregatedNote("s1", "v1", null) } returns Unit
        coEvery { revisionDao.deleteByBatchForSubject("s1", batchId) } returns Unit

        repo.rollbackLast("s1")

        coVerify { subjectDao.updateAggregatedNote("s1", "v1", null) }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.data.repository.SubjectRepositoryUndoTest"`
Expected: FAIL（SubjectRepository 构造函数不接收 NoteRevisionDao，编译错误）

- [ ] **Step 3: 修改 SubjectRepository 构造函数与 import**

修改 `app/src/main/java/com/lingji/app/data/repository/SubjectRepository.kt`。

顶部 import 区加：
```kotlin
import com.lingji.app.data.db.dao.NoteRevisionDao
import com.lingji.app.data.db.entities.NoteRevisionEntity
import com.lingji.app.data.edit.EditBatch
import kotlin.coroutines.coroutineContext
import java.util.UUID
```

构造函数加 `revisionDao` 参数（在 `folderDao` 之后）：
```kotlin
@Singleton
class SubjectRepository @Inject constructor(
    private val subjectDao: SubjectDao,
    private val fragmentDao: FragmentDao,
    private val pageDao: NotebookPageDao,
    private val folderDao: FolderDao,
    private val revisionDao: NoteRevisionDao
) {
```

- [ ] **Step 4: 改 updateAggregatedNote 存 revision**

替换 `SubjectRepository.kt` 中的 `updateAggregatedNote` 方法（原 line 78-81）：

```kotlin
suspend fun updateAggregatedNote(id: String, content: String) {
    val entity = subjectDao.getSubjectById(id) ?: return
    val batchId = coroutineContext[EditBatch]?.batchId
    revisionDao.insert(
        NoteRevisionEntity(
            id = UUID.randomUUID().toString(),
            subjectId = id,
            pageId = null,
            field = "aggregated",
            prevContent = entity.aggregatedNote,
            prevTitle = null,
            batchId = batchId,
            createdAt = System.currentTimeMillis()
        )
    )
    trimRevisions(id)
    subjectDao.updateAggregatedNote(id, content, entity.aggregatedNote)
}
```

- [ ] **Step 5: 改 updatePage 存 revision**

替换 `SubjectRepository.kt` 中的 `updatePage` 方法（原 line 122-124）：

```kotlin
suspend fun updatePage(subjectId: String, page: NotebookPage) {
    val existing = pageDao.getPagesBySubjectOnce(subjectId).find { it.id == page.id } ?: return
    val batchId = coroutineContext[EditBatch]?.batchId
    revisionDao.insert(
        NoteRevisionEntity(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            pageId = page.id,
            field = "page",
            prevContent = existing.content,
            prevTitle = existing.title,
            batchId = batchId,
            createdAt = System.currentTimeMillis()
        )
    )
    trimRevisions(subjectId)
    pageDao.update(page.id, page.title, page.content, page.updatedAt)
}
```

- [ ] **Step 6: 替换 rollbackAggregatedNote 为 rollbackLast**

删除原 `rollbackAggregatedNote` 方法（原 line 83-87），替换为：

```kotlin
suspend fun rollbackLast(subjectId: String): Boolean {
    val latest = revisionDao.getLatestForSubject(subjectId) ?: return false
    val batchId = latest.batchId
    val revisions = if (batchId != null) {
        revisionDao.getByBatchForSubject(subjectId, batchId)
    } else {
        listOf(latest)
    }
    val grouped = revisions.groupBy { it.pageId }
    for ((_, group) in grouped) {
        val earliest = group.minByOrNull { it.createdAt } ?: continue
        when (earliest.field) {
            "aggregated" -> {
                subjectDao.updateAggregatedNote(subjectId, earliest.prevContent, null)
            }
            "page" -> {
                earliest.pageId?.let { pid ->
                    pageDao.update(pid, earliest.prevTitle ?: "", earliest.prevContent, System.currentTimeMillis())
                }
            }
        }
    }
    if (batchId != null) {
        revisionDao.deleteByBatchForSubject(subjectId, batchId)
    } else {
        revisionDao.deleteById(latest.id)
    }
    return true
}

private suspend fun trimRevisions(subjectId: String) {
    revisionDao.trimForSubject(subjectId, MAX_REVISIONS_PER_SUBJECT)
}

companion object {
    private const val MAX_REVISIONS_PER_SUBJECT = 20
}
```

注意：`companion object` 若已存在则把常量合并进去；若不存在则在类内合适位置添加。原类无 companion object，新增。

- [ ] **Step 7: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.data.repository.SubjectRepositoryUndoTest"`
Expected: 7 tests PASS

- [ ] **Step 8: 运行全量单元测试确保无回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 所有测试 PASS（注意 NoteToolsTest 等用 mockk mock SubjectRepository，不受构造函数变化影响，因为 mock 的是类本身）

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/lingji/app/data/repository/SubjectRepository.kt app/src/test/java/com/lingji/app/data/repository/SubjectRepositoryUndoTest.kt
git commit -m "feat: revision archival + batch-aware rollback in SubjectRepository"
```

---

### Task 4: SubjectViewModel - 注入 EditBatch + undoLastEdit

**Covers:** [S3, S6]
**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt`

**Interfaces:**
- Consumes: `EditBatch`（Task 2）、`SubjectRepository.rollbackLast`（Task 3）
- Produces: `undoLastEdit(onResult: (Boolean) -> Unit)`；`chatWithAgent`/`organize`/`refine`/`runHomeAgent` 包裹 EditBatch

- [ ] **Step 1: 加 import**

在 `SubjectViewModel.kt` import 区加（`java.util.UUID` 已存在，无需重复）：
```kotlin
import com.lingji.app.data.edit.EditBatch
```

- [ ] **Step 2: chatWithAgent 包裹 EditBatch**

在 `chatWithAgent` 方法中，将 `agentService.runAgentLoop(...)` 整个调用用 `withContext(EditBatch(UUID.randomUUID().toString())) { }` 包裹。原代码（约 line 501-535）结构：

```kotlin
processingJob = viewModelScope.launch {
    setProcessing(true, "Agent 思考中…")
    try {
        withContext(EditBatch(UUID.randomUUID().toString())) {
            agentService.runAgentLoop(
                subjectId = subjectId,
                question = question,
                priorMessages = conversationHistory.flatMap { (q, a) ->
                    listOf(ChatMessage(role = "user", content = q), ChatMessage(role = "assistant", content = a))
                },
                onReasoning = { reasoning -> appendReasoning(reasoning) },
                onToolCall = { toolName, args, result ->
                    val display = buildString {
                        append("🔧 调用工具: $toolName\n")
                        if (args.isNotBlank() && args != "{}") append("  参数: $args\n")
                        append("  结果: ${result.take(500)}")
                        if (result.length > 500) append("…")
                    }
                    appendStream(display + "\n\n")
                },
                onToken = { token ->
                    appendStream(token)
                    onToken(token)
                },
                onComplete = { answer -> onComplete(answer) },
                onError = { msg ->
                    _uiState.update { it.copy(aiErrorMessage = msg) }
                    onError(msg)
                },
                onAssessment = { decision, reason, extraSteps ->
                    val display = if (decision == "CONTINUE") {
                        "🔄 监督者判断任务未完成，追加 $extraSteps 轮工具调用（$reason）\n\n"
                    } else {
                        "⏹️ 监督者判断应终止：$reason\n\n"
                    }
                    appendStream(display)
                }
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        _uiState.update { it.copy(aiErrorMessage = e.message ?: "Agent 请求失败") }
        onError(e.message ?: "Agent 请求失败")
    } finally {
        setProcessing(false)
    }
}
```

关键：`withContext(EditBatch(...)) { agentService.runAgentLoop(...) }` 包在 try 块内。

- [ ] **Step 3: organize 包裹 EditBatch**

在 `organize` 方法中，将 try 块内的逻辑用 `withContext(EditBatch(UUID.randomUUID().toString())) { }` 包裹：

```kotlin
fun organize(subject: Subject, hint: String? = null) {
    if (_uiState.value.isProcessing) return
    if (!ensureAiConfigured()) return
    viewModelScope.launch {
        setProcessing(true, if (subject.unmergedFragments.isNotEmpty()) "正在整理并合并新碎片" else "正在基于全量碎片重构笔记")
        try {
            withContext(EditBatch(UUID.randomUUID().toString())) {
                val updated = if (subject.unmergedFragments.isNotEmpty()) {
                    val fragmentsToMerge = subject.unmergedFragments
                    val note = llmService.mergeFragment(
                        subject.aggregatedNote,
                        fragmentsToMerge,
                        _uiState.value.settings,
                        hint,
                        onToken = { token -> appendStream(token) },
                        onReasoning = { token -> appendReasoning(token) }
                    )
                    subjectRepository.updateAggregatedNote(subject.id, note)
                    subjectRepository.completeBatchMerge(subject.id, fragmentsToMerge.map { it.id })
                    note
                } else {
                    llmService.refineNote(
                        subject.fragments,
                        subject.aggregatedNote,
                        _uiState.value.settings,
                        hint,
                        onToken = { token -> appendStream(token) },
                        onReasoning = { token -> appendReasoning(token) }
                    )
                }
                subjectRepository.updateAggregatedNote(subject.id, updated)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(aiErrorMessage = e.message ?: "整理失败") }
        } finally {
            setProcessing(false)
        }
    }
}
```

- [ ] **Step 4: refine 包裹 EditBatch**

```kotlin
fun refine(subject: Subject, hint: String? = null) {
    if (_uiState.value.isProcessing) return
    if (!ensureAiConfigured()) return
    viewModelScope.launch {
        setProcessing(true, "正在基于全量碎片重构笔记")
        try {
            withContext(EditBatch(UUID.randomUUID().toString())) {
                val note = llmService.refineNote(
                    subject.fragments,
                    subject.aggregatedNote,
                    _uiState.value.settings,
                    hint,
                    onToken = { token -> appendStream(token) },
                    onReasoning = { token -> appendReasoning(token) }
                )
                subjectRepository.updateAggregatedNote(subject.id, note)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(aiErrorMessage = e.message ?: "重构失败") }
        } finally {
            setProcessing(false)
        }
    }
}
```

- [ ] **Step 5: runHomeAgent 包裹 EditBatch**

在 `runHomeAgent` 方法中，将 `homeAgentService.runHomeAgentLoop(...)` 调用用 `withContext(EditBatch(UUID.randomUUID().toString())) { }` 包裹：

```kotlin
private suspend fun runHomeAgent(question: String, convId: String, images: List<String> = emptyList()) {
    val priorMessages = homeAgentMessageCache["_current"] ?: emptyList()
    val collectedMessages = mutableListOf<ChatMessage>()
    val toolCallDescriptions = mutableListOf<HomeChatMessage>()
    withContext(EditBatch(UUID.randomUUID().toString())) {
        homeAgentService.runHomeAgentLoop(
            question = question,
            priorMessages = priorMessages,
            images = images,
            onReasoning = { appendReasoning(it) },
            onToolCall = { toolName, args, result ->
                // AI 岛显示原始工具调用信息（工具名 + 参数 + 结果）
                val islandDisplay = buildString {
                    append("🔧 调用工具: $toolName\n")
                    if (args.isNotBlank() && args != "{}") append("  参数: $args\n")
                    append("  结果: ${result.take(500)}")
                    if (result.length > 500) append("…")
                }
                appendStream(islandDisplay + "\n\n")
                // 聊天列表保持简洁显示
                val chatDisplay = formatToolCall(toolName, args)
                val toolMsg = HomeChatMessage(role = "tool", content = chatDisplay)
                toolCallDescriptions.add(toolMsg)
                _uiState.update { it.copy(homeMessages = it.homeMessages + toolMsg) }
            },
            onToken = { token ->
                appendStream(token)
                _uiState.update { it.copy(homeStreamLine = it.homeStreamLine + token) }
            },
            onAgentMessages = { messages ->
                collectedMessages.clear(); collectedMessages.addAll(messages)
                homeAgentMessageCache["_current"] = messages
            },
            onComplete = { answer ->
                setProcessing(false)
                val assistantMsg = HomeChatMessage(role = "assistant", content = answer)
                _uiState.update { it.copy(homeMessages = it.homeMessages + assistantMsg, homeStreamLine = "", homeIsLoading = false) }
                homeMessageCache[convId] = _uiState.value.homeMessages
                val visibleEntities = toolCallDescriptions.map { msg ->
                    HomeMessageEntity(id = UUID.randomUUID().toString(), conversation_id = convId, role = msg.role, content = msg.content, tool_calls_json = null, timestamp = msg.timestamp)
                } + listOf(HomeMessageEntity(id = UUID.randomUUID().toString(), conversation_id = convId, role = "assistant", content = answer, tool_calls_json = null, timestamp = System.currentTimeMillis()))
                viewModelScope.launch { homeChatDao.insertMessages(visibleEntities); homeChatDao.updateConversationTimestamp(id = convId, title = question.take(50).replace("\n", " "), updatedAt = System.currentTimeMillis()) }
            },
            onError = { msg -> setProcessing(false); _uiState.update { it.copy(aiErrorMessage = msg, homeIsLoading = false) } },
            onAssessment = { decision, reason, extraSteps ->
                val display = if (decision == "CONTINUE") {
                    "🔄 监督者判断任务未完成，追加 $extraSteps 轮工具调用（$reason）\n"
                } else {
                    "⏹️ 监督者判断应终止：$reason\n"
                }
                appendStream(display)
                val assessMsg = HomeChatMessage(role = "system", content = display.trim())
                _uiState.update { it.copy(homeMessages = it.homeMessages + assessMsg) }
            }
        )
    }
}
```

- [ ] **Step 6: 替换 rollbackAggregatedNote 为 undoLastEdit**

删除原 `rollbackAggregatedNote` 方法（原 line 565-568），替换为：

```kotlin
fun undoLastEdit(onResult: (Boolean) -> Unit = {}) {
    val subjectId = _uiState.value.currentSubjectId ?: run { onResult(false); return }
    viewModelScope.launch {
        onResult(subjectRepository.rollbackLast(subjectId))
    }
}
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt
git commit -m "feat: inject EditBatch in agent sessions + add undoLastEdit"
```

---

### Task 5: UI 接入 + 字符串资源

**Covers:** [S6]
**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/lingji/app/ui/screens/FragmentSubjectScreen.kt`
- Modify: `app/src/main/java/com/lingji/app/ui/screens/notebook/NotebookSubjectTopBar.kt`
- Modify: `app/src/main/java/com/lingji/app/ui/screens/NotebookSubjectScreen.kt`

**Interfaces:**
- Consumes: `SubjectViewModel.undoLastEdit`（Task 4）

- [ ] **Step 1: 添加字符串资源**

在 `strings.xml` 中（`<string name="rollback">回退</string>` 附近，按设置/笔记分组）加：

```xml
<!-- 撤销笔记修改 -->
<string name="undo_edit">撤销修改</string>
<string name="undone">已撤销最近一次修改</string>
<string name="nothing_to_undo">没有可撤销的修改</string>
```

- [ ] **Step 2: FragmentSubjectScreen 改回滚为撤销**

在 `FragmentSubjectScreen.kt` 中，找到原"回滚"菜单项（约 line 225-231）：

```kotlin
DropdownMenuItem(
    text = { Text(stringResource(R.string.rollback)) },
    onClick = {
        viewModel.rollbackAggregatedNote()
        showMenu = false
    }
)
```

替换为：

```kotlin
DropdownMenuItem(
    text = { Text(stringResource(R.string.undo_edit)) },
    onClick = {
        viewModel.undoLastEdit { undone ->
            val msg = if (undone) R.string.undone else R.string.nothing_to_undo
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
        showMenu = false
    }
)
```

注意：`context` 和 `Toast` 在该文件已导入使用（见 line 246, 272 等），无需新增 import。

- [ ] **Step 3: NotebookSubjectTopBar 加 onUndoEdit 参数和菜单项**

修改 `NotebookSubjectTopBar.kt`：

函数签名加 `onUndoEdit: () -> Unit` 参数（在 `onCopyToClipboard` 之后）：

```kotlin
@Composable
fun NotebookSubjectTopBar(
    title: String,
    isPreview: Boolean,
    isPagesEmpty: Boolean,
    onBack: () -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onBuildIndex: () -> Unit,
    onBuildDirectory: () -> Unit,
    onExport: () -> Unit,
    onExportPdf: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onUndoEdit: () -> Unit
) {
```

在 `DropdownMenu` 内、`build_index` 菜单项**之前**加撤销项：

```kotlin
DropdownMenuItem(
    text = { Text(stringResource(R.string.undo_edit)) },
    onClick = {
        showMoreMenu = false
        onUndoEdit()
    }
)
DropdownMenuItem(
    text = { Text(stringResource(R.string.build_index)) },
    onClick = {
        showMoreMenu = false
        onBuildIndex()
    },
    leadingIcon = {
        Icon(Icons.Default.Refresh, contentDescription = null)
    }
)
```

- [ ] **Step 4: NotebookSubjectScreen 传入 onUndoEdit**

修改 `NotebookSubjectScreen.kt` 中 `NotebookSubjectTopBar(...)` 调用（约 line 330-358），在 `onCopyToClipboard = { ... }` 之后加：

```kotlin
onCopyToClipboard = {
    scope.launch {
        try {
            val encoded = viewModel.exportSubjectToText(liveSubject)
            if (encoded.length > CLIPBOARD_SIZE_LIMIT) {
                showClipboardTooLargeDialog = true
                return@launch
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(liveSubject.title, encoded))
            Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
        }
    }
},
onUndoEdit = {
    viewModel.undoLastEdit { undone ->
        val msg = if (undone) R.string.undone else R.string.nothing_to_undo
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
```

注意：`onCopyToClipboard` 原本末尾无逗号（是最后一个参数），现在后面加了 `onUndoEdit`，需给 `onCopyToClipboard` 的 `}` 后加逗号。

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/lingji/app/ui/screens/FragmentSubjectScreen.kt app/src/main/java/com/lingji/app/ui/screens/notebook/NotebookSubjectTopBar.kt app/src/main/java/com/lingji/app/ui/screens/NotebookSubjectScreen.kt
git commit -m "feat: wire undo entry into FRAGMENT and NOTEBOOK screens"
```

---

### Task 6: 全量编译 + 模拟器同步

**Covers:** [S7]
**Files:** (none)

- [ ] **Step 1: 全量单元测试**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 所有测试 PASS

- [ ] **Step 2: 编译 debug APK**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 同步模拟器**

Run: `./gradlew :app:installDebug`
Expected: 若有可用模拟器/设备则安装成功；若无则记录原因跳过（AGENTS.md 规范）。

- [ ] **Step 4: 手动验证清单**

在模拟器上验证：
1. FRAGMENT 笔记：用 agent 修改内容后，顶部菜单点"撤销修改"，内容回退，Toast 显示"已撤销最近一次修改"。
2. FRAGMENT 笔记：连续 agent 修改 2 次后，点 2 次撤销，逐步回退。
3. NOTEBOOK 笔记：用 agent 修改某页内容后，顶部菜单点"撤销修改"，该页内容回退。
4. NOTEBOOK 笔记：agent 一轮会话修改 2 个页后，点 1 次撤销，两页同时回退。
5. 无可撤销时点"撤销修改"，Toast 显示"没有可撤销的修改"。
6. 用户手动编辑保存后，也能撤销（回退到手动保存前）。

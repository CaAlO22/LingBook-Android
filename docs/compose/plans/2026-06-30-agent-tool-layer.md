# Agent Tool Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an isolated tool layer that exposes note operations as OpenAI function-calling tools, ready for future agent integration.

**Architecture:** `Tool` interface + `ToolRegistry` following the existing `ProviderRegistry` pattern. Each tool group is a factory function returning `List<Tool>`. A new `subject_summaries` Room table backs the `summarize_all_notes` tool. No changes to `LLMService`, `SettingsScreen`, or `Models.kt`.

**Tech Stack:** Kotlin 1.9.24, Room 2.6.1, Hilt 2.50, Gson 2.10.1, JUnit 4.13.2, MockK (to be added)

## Global Constraints

- OpenAI function calling format: `{type: "function", function: {name, description, parameters}}`
- Tool names use snake_case (e.g. `create_subject`)
- Tool return values are JSON strings on success, `"Error: <msg>"` on failure
- Database version goes 5 → 6 with a proper Migration
- Verification: `./gradlew :app:compileDebugKotlin` after each task; `./gradlew :app:installDebug` at the end
- Do NOT modify `LLMService.kt`, `SettingsScreen.kt`, `Models.kt`, `SubjectRepository.kt`, `IndexService.kt`

---

### Task 1: Add MockK test dependency

**Covers:** (scaffolding)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `mockk` available as `testImplementation` for all subsequent test tasks

- [ ] **Step 1: Add mockk to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
mockk = "1.13.10"
```
Add to `[libraries]`:
```toml
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

- [ ] **Step 2: Add testImplementation lines in build.gradle.kts**

In `app/build.gradle.kts`, after the existing `testImplementation(libs.junit)` line, add:
```kotlin
testImplementation(libs.mockk)
testImplementation(libs.coroutines.test)
```

- [ ] **Step 3: Verify gradle sync**

Run: `./gradlew :app:dependencies --configuration testDebugUnitTestRuntimeClasspath`
Expected: completes without error, mockk and coroutines-test appear in output.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add mockk and coroutines-test for unit testing"
```

---

### Task 2: SubjectSummaryEntity + DAO + DB migration

**Covers:** [S4]

**Files:**
- Create: `app/src/main/java/com/lingji/app/data/db/entities/SubjectSummaryEntity.kt`
- Create: `app/src/main/java/com/lingji/app/data/db/dao/SubjectSummaryDao.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt`
- Modify: `app/src/main/java/com/lingji/app/di/AppModule.kt`
- Test: `app/src/test/java/com/lingji/app/data/db/dao/SubjectSummaryDaoTest.kt`

**Interfaces:**
- Produces: `SubjectSummaryDao` with methods `getAll()`, `getBySubjectId(id)`, `upsert(entity)`, `deleteBySubjectId(id)`; injectable via Hilt; DB at version 6 with `MIGRATION_5_6`.

- [ ] **Step 1: Create SubjectSummaryEntity**

`app/src/main/java/com/lingji/app/data/db/entities/SubjectSummaryEntity.kt`:
```kotlin
package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subject_summaries")
data class SubjectSummaryEntity(
    @PrimaryKey val subjectId: String,
    val summary: String,
    val summarizedAt: Long
)
```

- [ ] **Step 2: Create SubjectSummaryDao**

`app/src/main/java/com/lingji/app/data/db/dao/SubjectSummaryDao.kt`:
```kotlin
package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.SubjectSummaryEntity

@Dao
interface SubjectSummaryDao {
    @Query("SELECT * FROM subject_summaries")
    suspend fun getAll(): List<SubjectSummaryEntity>

    @Query("SELECT * FROM subject_summaries WHERE subjectId = :subjectId")
    suspend fun getBySubjectId(subjectId: String): SubjectSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubjectSummaryEntity)

    @Query("DELETE FROM subject_summaries WHERE subjectId = :subjectId")
    suspend fun deleteBySubjectId(subjectId: String)
}
```

- [ ] **Step 3: Update LingjiDatabase**

In `LingjiDatabase.kt`:
- Add `SubjectSummaryEntity::class` to entities array
- Change `version = 5` to `version = 6`
- Add `import com.lingji.app.data.db.entities.SubjectSummaryEntity`
- Add `import com.lingji.app.data.db.dao.SubjectSummaryDao`
- Add migration in companion object:
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS subject_summaries (" +
                "subjectId TEXT NOT NULL PRIMARY KEY, " +
                "summary TEXT NOT NULL, " +
                "summarizedAt INTEGER NOT NULL)"
        )
    }
}
```
- Add abstract method: `abstract fun subjectSummaryDao(): SubjectSummaryDao`

- [ ] **Step 4: Update AppModule**

In `AppModule.kt`, add import for `SubjectSummaryDao` and add after `provideSettingsDao`:
```kotlin
@Provides
fun provideSubjectSummaryDao(database: LingjiDatabase) = database.subjectSummaryDao()
```
Also add `LingjiDatabase.MIGRATION_5_6` to the `.addMigrations(...)` call.

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lingji/app/data/db/ app/src/main/java/com/lingji/app/di/AppModule.kt
git commit -m "feat: add subject_summaries table with DB migration 5→6"
```

---

### Task 3: Tool interface + JsonSchemaBuilder + toOpenAITool

**Covers:** [S2]

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/Tool.kt`
- Create: `app/src/main/java/com/lingji/app/domain/tool/JsonSchemaBuilder.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/ToolTest.kt`

**Interfaces:**
- Produces: `Tool` interface with `name`, `description`, `parameters: JsonObject`, `suspend fun execute(params: JsonObject): String`; `Tool.toOpenAITool(): JsonObject` extension; `buildJsonObject`/`buildJsonArray` helpers.

- [ ] **Step 1: Create JsonSchemaBuilder.kt**

`app/src/main/java/com/lingji/app/domain/tool/JsonSchemaBuilder.kt`:
```kotlin
package com.lingji.app.domain.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** DSL helper for building JsonObject with `"key" to value` syntax. */
fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject {
    val builder = JsonObjectBuilder()
    builder.block()
    return builder.obj
}

/** DSL helper for building JsonArray with `+ value` or `+ { block }` syntax. */
fun buildJsonArray(block: JsonArrayBuilder.() -> Unit): JsonArray {
    val builder = JsonArrayBuilder()
    builder.block()
    return builder.arr
}

class JsonObjectBuilder {
    val obj = JsonObject()

    infix fun String.to(value: String) = obj.addProperty(this, value)
    infix fun String.to(value: Number) = obj.addProperty(this, value)
    infix fun String.to(value: Boolean) = obj.addProperty(this, value)
    infix fun String.to(value: JsonObject) = obj.add(this, value)
    infix fun String.to(value: JsonArray) = obj.add(this, value)
}

class JsonArrayBuilder {
    val arr = JsonArray()

    operator fun String.unaryPlus() = arr.add(this)
    operator fun JsonObject.unaryPlus() = arr.add(this)
    operator fun Number.unaryPlus() = arr.add(this)
    operator fun Boolean.unaryPlus() = arr.add(this)
}
```

- [ ] **Step 2: Create Tool.kt**

`app/src/main/java/com/lingji/app/domain/tool/Tool.kt`:
```kotlin
package com.lingji.app.domain.tool

import com.google.gson.JsonObject

interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject

    suspend fun execute(params: JsonObject): String
}

/** Convert a Tool to OpenAI function-calling format. */
fun Tool.toOpenAITool(): JsonObject = buildJsonObject {
    "type" to "function"
    "function" to buildJsonObject {
        "name" to name
        "description" to description
        "parameters" to parameters
    }
}
```

- [ ] **Step 3: Write test**

`app/src/test/java/com/lingji/app/domain/tool/ToolTest.kt`:
```kotlin
package com.lingji.app.domain.tool

import com.google.gson.JsonObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolTest {

    private val sampleTool = object : Tool {
        override val name = "test_tool"
        override val description = "A test tool"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "x" to buildJsonObject { "type" to "string" }
            }
        }
        override suspend fun execute(params: JsonObject): String = "ok"
    }

    @Test
    fun toOpenAITool_producesCorrectStructure() {
        val result = sampleTool.toOpenAITool()
        assertEquals("function", result.get("type").asString)
        val fn = result.getAsJsonObject("function")
        assertEquals("test_tool", fn.get("name").asString)
        assertEquals("A test tool", fn.get("description").asString)
        assertEquals("object", fn.getAsJsonObject("parameters").get("type").asString)
    }

    @Test
    fun buildJsonObject_createsCorrectPairs() {
        val obj = buildJsonObject {
            "a" to "hello"
            "b" to 42
            "c" to true
        }
        assertEquals("hello", obj.get("a").asString)
        assertEquals(42, obj.get("b").asInt)
        assertEquals(true, obj.get("c").asBoolean)
    }

    @Test
    fun execute_returnsResult() = runTest {
        val result = sampleTool.execute(JsonObject())
        assertEquals("ok", result)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.ToolTest"`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/tool/ app/src/test/java/com/lingji/app/domain/tool/
git commit -m "feat: add Tool interface, JsonSchemaBuilder, and toOpenAITool"
```

---

### Task 4: SubjectTools (5 tools)

**Covers:** [S5]

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/subject/SubjectTools.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/subject/SubjectToolsTest.kt`

**Interfaces:**
- Consumes: `SubjectRepository` (existing methods: `getAllSubjects()` returns Flow, `getSubjectByIdOnce(id)`, `insert(subject)`, `delete(id)`, `rename(id, title)`)
- Consumes: `Tool` interface from Task 3
- Produces: `SubjectTools.create(repo: SubjectRepository): List<Tool>` returning 5 tools named `list_subjects`, `get_subject`, `create_subject`, `delete_subject`, `rename_subject`

- [ ] **Step 1: Write failing test**

`app/src/test/java/com/lingji/app/domain/tool/subject/SubjectToolsTest.kt`:
```kotlin
package com.lingji.app.domain.tool.subject

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = SubjectTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    @Test
    fun create_returnsFiveTools() {
        assertEquals(5, tools.size)
        assertTrue(toolMap.containsKey("list_subjects"))
        assertTrue(toolMap.containsKey("get_subject"))
        assertTrue(toolMap.containsKey("create_subject"))
        assertTrue(toolMap.containsKey("delete_subject"))
        assertTrue(toolMap.containsKey("rename_subject"))
    }

    @Test
    fun list_subjects_returnsJsonArray() = runTest {
        coEvery { repo.getAllSubjects() } returns flowOf(
            Subject(id = "s1", title = "Math", type = SubjectType.NOTEBOOK),
            Subject(id = "s2", title = "Physics", type = SubjectType.FRAGMENT)
        )
        val result = toolMap["list_subjects"]!!.execute(JsonObject())
        assertTrue(result.contains("\"id\":\"s1\""))
        assertTrue(result.contains("\"title\":\"Math\""))
        assertTrue(result.contains("\"type\":\"NOTEBOOK\""))
    }

    @Test
    fun get_subject_returnsOverview() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns Subject(
            id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
            fragments = listOf(), unmergedFragments = listOf(),
            pages = listOf()
        )
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_subject"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"s1\""))
        assertTrue(result.contains("\"title\":\"Math\""))
    }

    @Test
    fun get_subject_notFound_returnsError() = runTest {
        coEvery { repo.getSubjectByIdOnce("nope") } returns null
        val params = JsonObject().apply { addProperty("subject_id", "nope") }
        val result = toolMap["get_subject"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun create_subject_callsRepoInsert() = runTest {
        coEvery { repo.insert(any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("title", "New Note")
            addProperty("type", "notebook")
        }
        val result = toolMap["create_subject"]!!.execute(params)
        coVerify { repo.insert(any()) }
        assertTrue(result.contains("\"id\""))
        assertTrue(result.contains("\"title\":\"New Note\""))
    }

    @Test
    fun delete_subject_callsRepoDelete() = runTest {
        coEvery { repo.delete("s1") } returns Unit
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["delete_subject"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.delete("s1") }
    }

    @Test
    fun rename_subject_callsRepoRename() = runTest {
        coEvery { repo.rename("s1", "New Title") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("new_title", "New Title")
        }
        val result = toolMap["rename_subject"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.rename("s1", "New Title") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.subject.SubjectToolsTest"`
Expected: FAIL — `SubjectTools` not found

- [ ] **Step 3: Write SubjectTools implementation**

`app/src/main/java/com/lingji/app/domain/tool/subject/SubjectTools.kt`:
```kotlin
package com.lingji.app.domain.tool.subject

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import kotlinx.coroutines.flow.first

object SubjectTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListSubjects(repo),
        GetSubject(repo),
        CreateSubject(repo),
        DeleteSubject(repo),
        RenameSubject(repo)
    )

    private class ListSubjects(private val repo: SubjectRepository) : Tool {
        override val name = "list_subjects"
        override val description = "列出所有笔记的 id、标题和类型。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {}
        }
        override suspend fun execute(params: JsonObject): String {
            val subjects = repo.getAllSubjects().first()
            val arr = buildJsonArray {
                for (s in subjects) {
                    +buildJsonObject {
                        "id" to s.id
                        "title" to s.title
                        "type" to s.type.name
                    }
                }
            }
            return arr.toString()
        }
    }

    private class GetSubject(private val repo: SubjectRepository) : Tool {
        override val name = "get_subject"
        override val description = "获取指定笔记的概览信息：标题、类型、页面数、碎片数。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记 ID"
                }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val s = repo.getSubjectByIdOnce(id)
                ?: return "Error: Subject not found: $id"
            return buildJsonObject {
                "id" to s.id
                "title" to s.title
                "type" to s.type.name
                "page_count" to (s.pages?.size ?: 0)
                "fragment_count" to (s.fragments.size + s.unmergedFragments.size)
            }.toString()
        }
    }

    private class CreateSubject(private val repo: SubjectRepository) : Tool {
        override val name = "create_subject"
        override val description = "创建一个新笔记。type 可选 notebook 或 fragment，默认 notebook。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "title" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记标题"
                }
                "type" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记类型：notebook 或 fragment",
                    "enum" to buildJsonArray { +"notebook"; +"fragment" }
                }
            }
            "required" to buildJsonArray { +"title" }
        }
        override suspend fun execute(params: JsonObject): String {
            val title = params.get("title")?.asString
                ?: return "Error: Missing required parameter: title"
            val typeStr = params.get("type")?.asString ?: "notebook"
            val type = runCatching { SubjectType.valueOf(typeStr.uppercase()) }
                .getOrElse { SubjectType.NOTEBOOK }
            val subject = Subject.create(title, type)
            repo.insert(subject)
            return buildJsonObject {
                "id" to subject.id
                "title" to subject.title
                "type" to subject.type.name
            }.toString()
        }
    }

    private class DeleteSubject(private val repo: SubjectRepository) : Tool {
        override val name = "delete_subject"
        override val description = "删除指定笔记及其所有页面、碎片和摘要。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "要删除的笔记 ID"
                }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            repo.delete(id)
            return """{"success":true}"""
        }
    }

    private class RenameSubject(private val repo: SubjectRepository) : Tool {
        override val name = "rename_subject"
        override val description = "重命名指定笔记。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject {
                    "type" to "string"
                    "description" to "笔记 ID"
                }
                "new_title" to buildJsonObject {
                    "type" to "string"
                    "description" to "新标题"
                }
            }
            "required" to buildJsonArray { +"subject_id"; +"new_title" }
        }
        override suspend fun execute(params: JsonObject): String {
            val id = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val newTitle = params.get("new_title")?.asString
                ?: return "Error: Missing required parameter: new_title"
            repo.rename(id, newTitle)
            return """{"success":true}"""
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.subject.SubjectToolsTest"`
Expected: 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/tool/subject/ app/src/test/java/com/lingji/app/domain/tool/subject/
git commit -m "feat: add 5 Subject management tools"
```

---

### Task 5: PageTools (5 tools)

**Covers:** [S5]

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/page/PageTools.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/page/PageToolsTest.kt`

**Interfaces:**
- Consumes: `SubjectRepository` methods: `getSubjectByIdOnce(id)` returns `Subject?` (with `.pages: List<NotebookPage>?`), `addPage(subjectId, page)`, `updatePage(subjectId, page)`, `deletePage(subjectId, pageId)`
- Consumes: `Tool` interface, `buildJsonObject`/`buildJsonArray` from Task 3
- Produces: `PageTools.create(repo): List<Tool>` returning 5 tools: `list_pages`, `get_page`, `create_page`, `update_page`, `delete_page`

- [ ] **Step 1: Write failing test**

`app/src/test/java/com/lingji/app/domain/tool/page/PageToolsTest.kt`:
```kotlin
package com.lingji.app.domain.tool.page

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = PageTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    private val testSubject = Subject(
        id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
        pages = listOf(
            NotebookPage(id = "p1", title = "Page 1", content = "Content 1", orderIndex = 0),
            NotebookPage(id = "p2", title = "Page 2", content = "Content 2", orderIndex = 1)
        )
    )

    @Test
    fun create_returnsFiveTools() {
        assertEquals(5, tools.size)
    }

    @Test
    fun list_pages_returnsArray() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["list_pages"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"p1\""))
        assertTrue(result.contains("\"title\":\"Page 1\""))
    }

    @Test
    fun get_page_returnsContent() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
        }
        val result = toolMap["get_page"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"p1\""))
        assertTrue(result.contains("\"content\":\"Content 1\""))
    }

    @Test
    fun get_page_notFound_returnsError() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "nope")
        }
        val result = toolMap["get_page"]!!.execute(params)
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun create_page_callsAddPage() = runTest {
        coEvery { repo.addPage(eq("s1"), any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("title", "New Page")
        }
        val result = toolMap["create_page"]!!.execute(params)
        coVerify { repo.addPage(eq("s1"), any()) }
        assertTrue(result.contains("\"id\""))
    }

    @Test
    fun update_page_callsUpdatePage() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        coEvery { repo.updatePage(eq("s1"), any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
            addProperty("content", "Updated content")
        }
        val result = toolMap["update_page"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updatePage(eq("s1"), any()) }
    }

    @Test
    fun delete_page_callsDeletePage() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        coEvery { repo.deletePage("s1", "p1") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("page_id", "p1")
        }
        val result = toolMap["delete_page"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.deletePage("s1", "p1") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.page.PageToolsTest"`
Expected: FAIL — `PageTools` not found

- [ ] **Step 3: Write PageTools implementation**

`app/src/main/java/com/lingji/app/domain/tool/page/PageTools.kt`:
```kotlin
package com.lingji.app.domain.tool.page

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object PageTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListPages(repo),
        GetPage(repo),
        CreatePage(repo),
        UpdatePage(repo),
        DeletePage(repo)
    )

    private class ListPages(private val repo: SubjectRepository) : Tool {
        override val name = "list_pages"
        override val description = "列出指定笔记下的所有页面：id、标题、顺序。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val pages = subject.pages ?: emptyList()
            val arr = buildJsonArray {
                pages.forEachIndexed { idx, p ->
                    +buildJsonObject {
                        "id" to p.id
                        "title" to p.title
                        "order" to idx
                    }
                }
            }
            return arr.toString()
        }
    }

    private class GetPage(private val repo: SubjectRepository) : Tool {
        override val name = "get_page"
        override val description = "读取指定页面的完整内容。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID" }
            }
            "required" to buildJsonArray { +"subject_id"; +"page_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val pageId = params.get("page_id")?.asString
                ?: return "Error: Missing required parameter: page_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val page = subject.pages?.find { it.id == pageId }
                ?: return "Error: Page not found: $pageId"
            return buildJsonObject {
                "id" to page.id
                "title" to page.title
                "content" to page.content
                "updated_at" to page.updatedAt
            }.toString()
        }
    }

    private class CreatePage(private val repo: SubjectRepository) : Tool {
        override val name = "create_page"
        override val description = "在指定笔记末尾新增一个页面。title 和 content 可选。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "title" to buildJsonObject { "type" to "string"; "description" to "页面标题" }
                "content" to buildJsonObject { "type" to "string"; "description" to "页面内容（Markdown）" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val title = params.get("title")?.asString ?: ""
            val content = params.get("content")?.asString ?: ""
            val page = NotebookPage(title = title, content = content)
            repo.addPage(subjectId, page)
            return buildJsonObject {
                "id" to page.id
                "title" to page.title
            }.toString()
        }
    }

    private class UpdatePage(private val repo: SubjectRepository) : Tool {
        override val name = "update_page"
        override val description = "更新指定页面的标题和/或内容。仅传出的参数会被更新。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID" }
                "title" to buildJsonObject { "type" to "string"; "description" to "新标题（可选）" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新内容（可选）" }
            }
            "required" to buildJsonArray { +"subject_id"; +"page_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val pageId = params.get("page_id")?.asString
                ?: return "Error: Missing required parameter: page_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val existing = subject.pages?.find { it.id == pageId }
                ?: return "Error: Page not found: $pageId"
            val updated = existing.copy(
                title = params.get("title")?.asString ?: existing.title,
                content = params.get("content")?.asString ?: existing.content,
                updatedAt = System.currentTimeMillis()
            )
            repo.updatePage(subjectId, updated)
            return """{"success":true}"""
        }
    }

    private class DeletePage(private val repo: SubjectRepository) : Tool {
        override val name = "delete_page"
        override val description = "删除指定笔记中的指定页面。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "page_id" to buildJsonObject { "type" to "string"; "description" to "页面 ID" }
            }
            "required" to buildJsonArray { +"subject_id"; +"page_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val pageId = params.get("page_id")?.asString
                ?: return "Error: Missing required parameter: page_id"
            repo.deletePage(subjectId, pageId)
            return """{"success":true}"""
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.page.PageToolsTest"`
Expected: 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/tool/page/ app/src/test/java/com/lingji/app/domain/tool/page/
git commit -m "feat: add 5 NotebookPage management tools"
```

---

### Task 6: FragmentTools (4 tools)

**Covers:** [S5]

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/fragment/FragmentTools.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/fragment/FragmentToolsTest.kt`

**Interfaces:**
- Consumes: `SubjectRepository` methods: `getSubjectByIdOnce(id)` (`.fragments`, `.unmergedFragments`), `addFragment(subjectId, fragment)`, `updateFragment(subjectId, fragmentId, content)`, `deleteFragment(subjectId, fragmentId)`
- Produces: `FragmentTools.create(repo): List<Tool>` returning 4 tools: `list_fragments`, `add_fragment`, `update_fragment`, `delete_fragment`

- [ ] **Step 1: Write failing test**

`app/src/test/java/com/lingji/app/domain/tool/fragment/FragmentToolsTest.kt`:
```kotlin
package com.lingji.app.domain.tool.fragment

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = FragmentTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    private val testSubject = Subject(
        id = "s1", title = "Notes", type = SubjectType.FRAGMENT,
        fragments = listOf(Fragment(id = "f1", content = "Old fragment")),
        unmergedFragments = listOf(Fragment(id = "f2", content = "New fragment"))
    )

    @Test
    fun create_returnsFourTools() {
        assertEquals(4, tools.size)
    }

    @Test
    fun list_fragments_returnsAll() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["list_fragments"]!!.execute(params)
        assertTrue(result.contains("\"id\":\"f1\""))
        assertTrue(result.contains("\"id\":\"f2\""))
    }

    @Test
    fun add_fragment_callsRepo() = runTest {
        coEvery { repo.addFragment(eq("s1"), any()) } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("content", "New note")
        }
        val result = toolMap["add_fragment"]!!.execute(params)
        coVerify { repo.addFragment(eq("s1"), any()) }
        assertTrue(result.contains("\"id\""))
    }

    @Test
    fun update_fragment_callsRepo() = runTest {
        coEvery { repo.updateFragment("s1", "f1", "Updated") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("fragment_id", "f1")
            addProperty("content", "Updated")
        }
        val result = toolMap["update_fragment"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updateFragment("s1", "f1", "Updated") }
    }

    @Test
    fun delete_fragment_callsRepo() = runTest {
        coEvery { repo.deleteFragment("s1", "f1") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("fragment_id", "f1")
        }
        val result = toolMap["delete_fragment"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.deleteFragment("s1", "f1") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.fragment.FragmentToolsTest"`
Expected: FAIL — `FragmentTools` not found

- [ ] **Step 3: Write FragmentTools implementation**

`app/src/main/java/com/lingji/app/domain/tool/fragment/FragmentTools.kt`:
```kotlin
package com.lingji.app.domain.tool.fragment

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object FragmentTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        ListFragments(repo),
        AddFragment(repo),
        UpdateFragment(repo),
        DeleteFragment(repo)
    )

    private class ListFragments(private val repo: SubjectRepository) : Tool {
        override val name = "list_fragments"
        override val description = "列出指定笔记下的所有碎片（含未合并的）。返回 id、内容、时间戳、是否已合并。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val all = subject.fragments + subject.unmergedFragments
            val arr = buildJsonArray {
                for (f in all) {
                    +buildJsonObject {
                        "id" to f.id
                        "content" to f.content
                        "timestamp" to f.timestamp
                        "is_merged" to f.isMerged
                    }
                }
            }
            return arr.toString()
        }
    }

    private class AddFragment(private val repo: SubjectRepository) : Tool {
        override val name = "add_fragment"
        override val description = "向指定笔记添加一条新的碎片。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "碎片内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            val fragment = Fragment(content = content)
            repo.addFragment(subjectId, fragment)
            return buildJsonObject { "id" to fragment.id }.toString()
        }
    }

    private class UpdateFragment(private val repo: SubjectRepository) : Tool {
        override val name = "update_fragment"
        override val description = "修改指定碎片的内容。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "fragment_id" to buildJsonObject { "type" to "string"; "description" to "碎片 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"fragment_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val fragmentId = params.get("fragment_id")?.asString
                ?: return "Error: Missing required parameter: fragment_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            repo.updateFragment(subjectId, fragmentId, content)
            return """{"success":true}"""
        }
    }

    private class DeleteFragment(private val repo: SubjectRepository) : Tool {
        override val name = "delete_fragment"
        override val description = "删除指定碎片。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "fragment_id" to buildJsonObject { "type" to "string"; "description" to "碎片 ID" }
            }
            "required" to buildJsonArray { +"subject_id"; +"fragment_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val fragmentId = params.get("fragment_id")?.asString
                ?: return "Error: Missing required parameter: fragment_id"
            repo.deleteFragment(subjectId, fragmentId)
            return """{"success":true}"""
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.fragment.FragmentToolsTest"`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/tool/fragment/ app/src/test/java/com/lingji/app/domain/tool/fragment/
git commit -m "feat: add 4 Fragment management tools"
```

---

### Task 7: NoteTools (4 tools — aggregated note + study plan)

**Covers:** [S5]

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/note/NoteTools.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/note/NoteToolsTest.kt`

**Interfaces:**
- Consumes: `SubjectRepository` methods: `getSubjectByIdOnce(id)` (`.aggregatedNote`, `.studyPlan`), `updateAggregatedNote(id, content)`, `updateStudyPlan(id, content)`
- Produces: `NoteTools.create(repo): List<Tool>` returning 4 tools: `get_aggregated_note`, `update_aggregated_note`, `get_study_plan`, `update_study_plan`

- [ ] **Step 1: Write failing test**

`app/src/test/java/com/lingji/app/domain/tool/note/NoteToolsTest.kt`:
```kotlin
package com.lingji.app.domain.tool.note

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val tools = NoteTools.create(repo)
    private val toolMap = tools.associateBy { it.name }

    private val testSubject = Subject(
        id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
        aggregatedNote = "# Math\n\nContent here",
        studyPlan = "Plan A"
    )

    @Test
    fun create_returnsFourTools() {
        assertEquals(4, tools.size)
    }

    @Test
    fun get_aggregated_note_returnsContent() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_aggregated_note"]!!.execute(params)
        assertTrue(result.contains("\"content\":\"# Math"))
    }

    @Test
    fun update_aggregated_note_callsRepo() = runTest {
        coEvery { repo.updateAggregatedNote("s1", "New content") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("content", "New content")
        }
        val result = toolMap["update_aggregated_note"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updateAggregatedNote("s1", "New content") }
    }

    @Test
    fun get_study_plan_returnsContent() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns testSubject
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_study_plan"]!!.execute(params)
        assertTrue(result.contains("\"content\":\"Plan A\""))
    }

    @Test
    fun update_study_plan_callsRepo() = runTest {
        coEvery { repo.updateStudyPlan("s1", "New plan") } returns Unit
        val params = JsonObject().apply {
            addProperty("subject_id", "s1")
            addProperty("content", "New plan")
        }
        val result = toolMap["update_study_plan"]!!.execute(params)
        assertTrue(result.contains("\"success\":true"))
        coVerify { repo.updateStudyPlan("s1", "New plan") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.note.NoteToolsTest"`
Expected: FAIL — `NoteTools` not found

- [ ] **Step 3: Write NoteTools implementation**

`app/src/main/java/com/lingji/app/domain/tool/note/NoteTools.kt`:
```kotlin
package com.lingji.app.domain.tool.note

import com.google.gson.JsonObject
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject

object NoteTools {

    fun create(repo: SubjectRepository): List<Tool> = listOf(
        GetAggregatedNote(repo),
        UpdateAggregatedNote(repo),
        GetStudyPlan(repo),
        UpdateStudyPlan(repo)
    )

    private class GetAggregatedNote(private val repo: SubjectRepository) : Tool {
        override val name = "get_aggregated_note"
        override val description = "读取指定笔记的聚合笔记内容（由碎片聚合而成的 Markdown 笔记）。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            return buildJsonObject { "content" to subject.aggregatedNote }.toString()
        }
    }

    private class UpdateAggregatedNote(private val repo: SubjectRepository) : Tool {
        override val name = "update_aggregated_note"
        override val description = "更新指定笔记的聚合笔记内容。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新的聚合笔记内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            repo.updateAggregatedNote(subjectId, content)
            return """{"success":true}"""
        }
    }

    private class GetStudyPlan(private val repo: SubjectRepository) : Tool {
        override val name = "get_study_plan"
        override val description = "读取指定笔记的学习计划。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            return buildJsonObject { "content" to subject.studyPlan }.toString()
        }
    }

    private class UpdateStudyPlan(private val repo: SubjectRepository) : Tool {
        override val name = "update_study_plan"
        override val description = "更新指定笔记的学习计划。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
                "content" to buildJsonObject { "type" to "string"; "description" to "新的学习计划内容" }
            }
            "required" to buildJsonArray { +"subject_id"; +"content" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val content = params.get("content")?.asString
                ?: return "Error: Missing required parameter: content"
            repo.updateStudyPlan(subjectId, content)
            return """{"success":true}"""
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.note.NoteToolsTest"`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/tool/note/ app/src/test/java/com/lingji/app/domain/tool/note/
git commit -m "feat: add 4 aggregated note and study plan tools"
```

---

### Task 8: SearchTools (3 tools — page index, search, summarize_all_notes)

**Covers:** [S5, S6]

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/search/SearchTools.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/search/SearchToolsTest.kt`

**Interfaces:**
- Consumes: `SubjectRepository.getAllSubjects().first()` for listing all subjects; `getSubjectByIdOnce(id)` for page index; `IndexService.searchPages(query, pages, indexes)` for search; `LLMService.generate(prompt, settings, systemPrompt)` for summarization; `SettingsRepository.getSettingsOnce()` for AI config; `SubjectSummaryDao` for summary caching
- Produces: `SearchTools.create(repo, llmService, settingsRepo, summaryDao): List<Tool>` returning 3 tools: `get_page_index`, `search_pages`, `summarize_all_notes`

- [ ] **Step 1: Write failing test**

`app/src/test/java/com/lingji/app/domain/tool/search/SearchToolsTest.kt`:
```kotlin
package com.lingji.app.domain.tool.search

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.entities.SubjectSummaryEntity
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchToolsTest {

    private val repo = mockk<SubjectRepository>()
    private val llmService = mockk<LLMService>()
    private val settingsRepo = mockk<SettingsRepository>()
    private val summaryDao = mockk<SubjectSummaryDao>()
    private val indexService = mockk<IndexService>()

    private val tools = SearchTools.create(repo, llmService, settingsRepo, summaryDao, indexService)
    private val toolMap = tools.associateBy { it.name }

    @Test
    fun create_returnsThreeTools() {
        assertEquals(3, tools.size)
    }

    @Test
    fun get_page_index_returnsEntries() = runTest {
        coEvery { repo.getSubjectByIdOnce("s1") } returns Subject(
            id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "Algebra", content = "x+1=2")),
            pageIndexEntries = listOf(
                PageIndexEntry(pageId = "p1", title = "Algebra", keywords = listOf("equation"), summary = "Basic algebra")
            )
        )
        val params = JsonObject().apply { addProperty("subject_id", "s1") }
        val result = toolMap["get_page_index"]!!.execute(params)
        assertTrue(result.contains("\"page_id\":\"p1\""))
        assertTrue(result.contains("\"summary\":\"Basic algebra\""))
    }

    @Test
    fun search_pages_returnsResults() = runTest {
        val page = NotebookPage(id = "p1", title = "Algebra", content = "x+1=2")
        val subject = Subject(
            id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
            pages = listOf(page),
            pageIndexEntries = listOf(
                PageIndexEntry(pageId = "p1", title = "Algebra", keywords = listOf("equation"), summary = "Basic algebra")
            )
        )
        coEvery { repo.getAllSubjects() } returns flowOf(subject)
        coEvery { indexService.searchPages(any(), any(), any()) } returns listOf(
            SearchResult(page = page, score = 0.8f, matchedKeywords = listOf("equation"), summarySnippet = "Basic algebra")
        )
        val params = JsonObject().apply { addProperty("query", "equation") }
        val result = toolMap["search_pages"]!!.execute(params)
        assertTrue(result.contains("\"page_id\":\"p1\""))
        assertTrue(result.contains("\"score\":0.8"))
    }

    @Test
    fun summarize_all_notes_usesCachedSummary() = runTest {
        val subject = Subject(
            id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "P1", content = "content", updatedAt = 1000L)),
            pageIndexEntries = null
        )
        coEvery { repo.getAllSubjects() } returns flowOf(subject)
        coEvery { summaryDao.getBySubjectId("s1") } returns SubjectSummaryEntity("s1", "Cached summary", 2000L)
        val params = JsonObject()
        val result = toolMap["summarize_all_notes"]!!.execute(params)
        assertTrue(result.contains("\"summary\":\"Cached summary\""))
        // Should NOT call LLM since cached summary is newer than updatedAt
        coVerify(exactly = 0) { llmService.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun summarize_all_notes_regeneratesWhenStale() = runTest {
        val subject = Subject(
            id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "P1", content = "content", updatedAt = 5000L)),
            pageIndexEntries = null
        )
        coEvery { repo.getAllSubjects() } returns flowOf(subject)
        coEvery { summaryDao.getBySubjectId("s1") } returns SubjectSummaryEntity("s1", "Old summary", 2000L)
        coEvery { settingsRepo.getSettingsOnce() } returns AISettings()
        coEvery { llmService.generate(any(), any(), any(), any(), any()) } returns "Fresh summary"
        coEvery { summaryDao.upsert(any()) } returns Unit
        val params = JsonObject()
        val result = toolMap["summarize_all_notes"]!!.execute(params)
        assertTrue(result.contains("\"summary\":\"Fresh summary\""))
        coVerify { summaryDao.upsert(any()) }
    }

    @Test
    fun summarize_all_notes_generatesWhenNoCache() = runTest {
        val subject = Subject(
            id = "s1", title = "Math", type = SubjectType.NOTEBOOK,
            pages = listOf(NotebookPage(id = "p1", title = "P1", content = "content", updatedAt = 1000L)),
            pageIndexEntries = null
        )
        coEvery { repo.getAllSubjects() } returns flowOf(subject)
        coEvery { summaryDao.getBySubjectId("s1") } returns null
        coEvery { settingsRepo.getSettingsOnce() } returns AISettings()
        coEvery { llmService.generate(any(), any(), any(), any(), any()) } returns "New summary"
        coEvery { summaryDao.upsert(any()) } returns Unit
        val params = JsonObject()
        val result = toolMap["summarize_all_notes"]!!.execute(params)
        assertTrue(result.contains("\"summary\":\"New summary\""))
        coVerify { summaryDao.upsert(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.search.SearchToolsTest"`
Expected: FAIL — `SearchTools` not found

- [ ] **Step 3: Write SearchTools implementation**

`app/src/main/java/com/lingji/app/domain/tool/search/SearchTools.kt`:
```kotlin
package com.lingji.app.domain.tool.search

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.entities.SubjectSummaryEntity
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.tool.Tool
import com.lingji.app.domain.tool.buildJsonArray
import com.lingji.app.domain.tool.buildJsonObject
import kotlinx.coroutines.flow.first

object SearchTools {

    fun create(
        repo: SubjectRepository,
        llmService: LLMService,
        settingsRepo: SettingsRepository,
        summaryDao: SubjectSummaryDao,
        indexService: IndexService
    ): List<Tool> = listOf(
        GetPageIndex(repo),
        SearchPages(repo, indexService),
        SummarizeAllNotes(repo, llmService, settingsRepo, summaryDao)
    )

    private class GetPageIndex(private val repo: SubjectRepository) : Tool {
        override val name = "get_page_index"
        override val description = "读取指定笔记的页面索引：每页的标题、关键词和摘要。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "笔记 ID" }
            }
            "required" to buildJsonArray { +"subject_id" }
        }
        override suspend fun execute(params: JsonObject): String {
            val subjectId = params.get("subject_id")?.asString
                ?: return "Error: Missing required parameter: subject_id"
            val subject = repo.getSubjectByIdOnce(subjectId)
                ?: return "Error: Subject not found: $subjectId"
            val entries = subject.pageIndexEntries ?: emptyList()
            val arr = buildJsonArray {
                for (e in entries) {
                    +buildJsonObject {
                        "page_id" to e.pageId
                        "title" to e.title
                        "keywords" to buildJsonArray { e.keywords.forEach { +it } }
                        "summary" to e.summary
                    }
                }
            }
            return arr.toString()
        }
    }

    private class SearchPages(
        private val repo: SubjectRepository,
        private val indexService: IndexService
    ) : Tool {
        override val name = "search_pages"
        override val description = "按关键词搜索笔记页面。可指定 subject_id 搜索特定笔记，不传则搜索全部笔记。返回匹配页面的标题、摘要片段和匹配度。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "query" to buildJsonObject { "type" to "string"; "description" to "搜索关键词" }
                "subject_id" to buildJsonObject { "type" to "string"; "description" to "可选：限定搜索的笔记 ID" }
            }
            "required" to buildJsonArray { +"query" }
        }
        override suspend fun execute(params: JsonObject): String {
            val query = params.get("query")?.asString
                ?: return "Error: Missing required parameter: query"
            val subjectId = params.get("subject_id")?.asString

            val subjects = if (subjectId != null) {
                listOfNotNull(repo.getSubjectByIdOnce(subjectId))
            } else {
                repo.getAllSubjects().first()
            }

            val results = mutableListOf<String>()
            for (s in subjects) {
                val pages = s.pages ?: continue
                val entries = s.pageIndexEntries ?: continue
                val searchResults = indexService.searchPages(query, pages, entries)
                for (r in searchResults) {
                    results.add(buildJsonObject {
                        "subject_id" to s.id
                        "page_id" to r.page.id
                        "title" to r.page.title
                        "snippet" to r.summarySnippet
                        "score" to r.score
                    }.toString())
                }
            }
            if (results.isEmpty()) return "[]"
            return "[" + results.joinToString(",") + "]"
        }
    }

    private class SummarizeAllNotes(
        private val repo: SubjectRepository,
        private val llmService: LLMService,
        private val settingsRepo: SettingsRepository,
        private val summaryDao: SubjectSummaryDao
    ) : Tool {
        override val name = "summarize_all_notes"
        override val description = "获取所有笔记的标题和摘要，帮助了解用户有哪些笔记及各自主题。无参数。对于无摘要或摘要后有过修改的笔记会自动重新生成摘要。"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {}
        }
        override suspend fun execute(params: JsonObject): String {
            val subjects = repo.getAllSubjects().first()
            val settings = runCatching { settingsRepo.getSettingsOnce() }.getOrNull()
            val arr = buildJsonArray {
                for (s in subjects) {
                    val cached = summaryDao.getBySubjectId(s.id)
                    val needsRefresh = needsRegeneration(s, cached)
                    val summary = if (needsRefresh) {
                        generateSummary(s, settings)
                    } else {
                        cached!!.summary
                    }
                    +buildJsonObject {
                        "subject_id" to s.id
                        "subject_title" to s.title
                        "summary" to summary
                    }
                }
            }
            return arr.toString()
        }

        private fun needsRegeneration(
            subject: com.lingji.app.domain.model.Subject,
            cached: SubjectSummaryEntity?
        ): Boolean {
            if (cached == null) return true
            val lastModified = when (subject.type) {
                SubjectType.NOTEBOOK -> subject.pages?.maxOfOrNull { it.updatedAt } ?: 0L
                SubjectType.FRAGMENT -> {
                    val allFrags = subject.fragments + subject.unmergedFragments
                    allFrags.maxOfOrNull { it.timestamp } ?: 0L
                }
            }
            return cached.summarizedAt < lastModified
        }

        private suspend fun generateSummary(
            subject: com.lingji.app.domain.model.Subject,
            settings: com.lingji.app.domain.model.AISettings?
        ): String {
            if (settings == null || settings.apiKey.isBlank()) return "AI未配置"
            val content = when (subject.type) {
                SubjectType.NOTEBOOK -> {
                    val pages = subject.pages ?: emptyList()
                    pages.joinToString("\n\n") { p ->
                        val title = if (p.title.isNotBlank()) "## ${p.title}\n" else ""
                        "$title${p.content}"
                    }
                }
                SubjectType.FRAGMENT -> {
                    if (subject.aggregatedNote.isNotBlank()) subject.aggregatedNote
                    else subject.fragments.joinToString("\n") { it.content }
                }
            }
            if (content.isBlank()) return "（无内容）"
            return runCatching {
                val raw = llmService.generate(
                    prompt = "笔记标题：${subject.title}\n\n笔记内容：\n$content",
                    settings = settings,
                    systemPrompt = "你是一个摘要专家。请用100-200字概括以下笔记的核心内容，突出主题和关键知识点。只输出摘要文本，不要添加任何额外格式或说明。"
                )
                val cleaned = LLMService.sanitizeOutput(raw).trim()
                summaryDao.upsert(SubjectSummaryEntity(subject.id, cleaned, System.currentTimeMillis()))
                cleaned
            }.getOrElse { "摘要生成失败" }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.search.SearchToolsTest"`
Expected: 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/tool/search/ app/src/test/java/com/lingji/app/domain/tool/search/
git commit -m "feat: add page index, search, and summarize_all_notes tools"
```

---

### Task 9: ToolRegistry

**Covers:** [S3]

**Files:**
- Create: `app/src/main/java/com/lingji/app/domain/tool/ToolRegistry.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/ToolRegistryTest.kt`

**Interfaces:**
- Consumes: All tool factory methods: `SubjectTools.create(repo)`, `PageTools.create(repo)`, `FragmentTools.create(repo)`, `NoteTools.create(repo)`, `SearchTools.create(repo, llmService, settingsRepo, summaryDao, indexService)`
- Consumes: `Tool.toOpenAITool()` from Task 3
- Produces: `ToolRegistry` class injectable via Hilt with methods `getTool(name)`, `getAllTools()`, `toOpenAITools()`, `executeTool(name, params)`

- [ ] **Step 1: Write failing test**

`app/src/test/java/com/lingji/app/domain/tool/ToolRegistryTest.kt`:
```kotlin
package com.lingji.app.domain.tool

import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    private val repo = mockk<SubjectRepository>()
    private val llmService = mockk<LLMService>()
    private val settingsRepo = mockk<SettingsRepository>()
    private val summaryDao = mockk<SubjectSummaryDao>()
    private val indexService = mockk<IndexService>()

    private val registry = ToolRegistry(repo, llmService, settingsRepo, summaryDao, indexService)

    @Test
    fun getAllTools_returns21Tools() {
        val tools = registry.getAllTools()
        assertEquals(21, tools.size)
    }

    @Test
    fun getTool_returnsToolByName() {
        assertNotNull(registry.getTool("list_subjects"))
        assertNotNull(registry.getTool("create_page"))
        assertNotNull(registry.getTool("summarize_all_notes"))
    }

    @Test
    fun getTool_unknownReturnsNull() {
        assertEquals(null, registry.getTool("nonexistent"))
    }

    @Test
    fun executeTool_unknownReturnsError() = runTest {
        val result = registry.executeTool("nonexistent", JsonObject())
        assertTrue(result.startsWith("Error: Unknown tool"))
    }

    @Test
    fun toOpenAITools_returnsJsonArray() {
        val arr = registry.toOpenAITools()
        assertEquals(21, arr.size())
        val first = arr[0].asJsonObject
        assertEquals("function", first.get("type").asString)
        assertNotNull(first.getAsJsonObject("function").get("name"))
    }

    @Test
    fun toolNamesAreUnique() {
        val names = registry.getAllTools().map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.ToolRegistryTest"`
Expected: FAIL — `ToolRegistry` not found

- [ ] **Step 3: Write ToolRegistry implementation**

`app/src/main/java/com/lingji/app/domain/tool/ToolRegistry.kt`:
```kotlin
package com.lingji.app.domain.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.tool.fragment.FragmentTools
import com.lingji.app.domain.tool.note.NoteTools
import com.lingji.app.domain.tool.page.PageTools
import com.lingji.app.domain.tool.search.SearchTools
import com.lingji.app.domain.tool.subject.SubjectTools
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    subjectRepository: SubjectRepository,
    llmService: LLMService,
    settingsRepository: SettingsRepository,
    subjectSummaryDao: SubjectSummaryDao,
    indexService: IndexService
) {
    private val tools: Map<String, Tool> = buildList {
        addAll(SubjectTools.create(subjectRepository))
        addAll(PageTools.create(subjectRepository))
        addAll(FragmentTools.create(subjectRepository))
        addAll(NoteTools.create(subjectRepository))
        addAll(SearchTools.create(subjectRepository, llmService, settingsRepository, subjectSummaryDao, indexService))
    }.associateBy { it.name }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun toOpenAITools(): JsonArray = JsonArray().apply {
        tools.values.forEach { add(it.toOpenAITool()) }
    }

    suspend fun executeTool(name: String, params: JsonObject): String {
        val tool = tools[name] ?: return "Error: Unknown tool '$name'"
        return runCatching { tool.execute(params) }
            .getOrElse { "Error: ${it.message ?: "Unknown error"}" }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.ToolRegistryTest"`
Expected: 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/tool/ToolRegistry.kt app/src/test/java/com/lingji/app/domain/tool/ToolRegistryTest.kt
git commit -m "feat: add ToolRegistry with 21 registered tools"
```

---

### Task 10: Final compilation + install verification

**Covers:** [S7, S9]

**Files:** (no new files)

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL tests PASS (existing + new)

- [ ] **Step 2: Compile debug Kotlin**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Install to emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL (or skip if no emulator, note reason)

- [ ] **Step 4: Final commit if any remaining changes**

```bash
git status
# Only commit if there are uncommitted changes
git add -A && git commit -m "chore: final verification pass" || echo "Nothing to commit"
```

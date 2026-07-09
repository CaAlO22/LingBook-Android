# edit_replace Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `edit_replace` tool that lets the AI do targeted find-and-replace on note content, avoiding image corruption caused by full-content rewrite tools.

**Architecture:** New `EditReplace` Tool class in `NoteTools.kt`. NOTEBOOK type edits page content (requires `page_id`); FRAGMENT type edits aggregated note (no `page_id` needed). Literal string search, 1-based occurrence. Registered in `SINGLE_NOTE_TOOLS`; system prompts updated to steer AI toward `edit_replace` for content edits.

**Tech Stack:** Kotlin, JUnit + MockK for tests.

## Global Constraints

- Language: Kotlin + Jetpack Compose (project convention per AGENTS.md)
- Verification: `./gradlew :app:compileDebugKotlin` after every change (AGENTS.md mandate)
- Emulator sync: `./gradlew :app:installDebug` after changes (AGENTS.md mandate)
- Tool naming: snake_case (matches all existing tools)
- subject_id auto-injected by ScopedToolRegistry in scoped mode

---

### Task 1: Add edit_replace tool with TDD

**Covers:** Core tool implementation

**Files:**
- Modify: `app/src/main/java/com/lingji/app/domain/tool/note/NoteTools.kt`
- Test: `app/src/test/java/com/lingji/app/domain/tool/note/NoteToolsTest.kt`

**Interfaces:**
- Consumes: `SubjectRepository.getSubjectByIdOnce`, `SubjectRepository.updatePage`, `SubjectRepository.updateAggregatedNote`
- Produces: `edit_replace` tool (name, available via `NoteTools.create()`)

- [ ] **Step 1: Write failing tests**

Add to `NoteToolsTest.kt`:
- `edit_replace_notebookPage_replacesFirstOccurrence` — find "代数" replace "几何" on page p1, verify repo.updatePage called with content "几何内容"
- `edit_replace_notebookPage_replacesSecondOccurrence` — content with duplicate text, occurrence=2
- `edit_replace_fragment_editsAggregatedNote` — FRAGMENT subject, no page_id, verify repo.updateAggregatedNote
- `edit_replace_notebookWithoutPageId_returnsError` — NOTEBOOK without page_id returns error
- `edit_replace_occurrenceNotFound_returnsError` — find text not present returns error
- Update `create_returnsFourTools` → `create_returnsFiveTools` with `assertEquals(5, tools.size)`

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.note.NoteToolsTest"`
Expected: FAIL (edit_replace tool not found / count mismatch)

- [ ] **Step 3: Implement EditReplace class**

Add `EditReplace(repo)` to `NoteTools.create()` list and implement the private class with `replaceNth` helper. Add `SubjectType` import.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingji.app.domain.tool.note.NoteToolsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add edit_replace tool for safe content editing without image corruption"
```

---

### Task 2: Register tool and update system prompts

**Covers:** Tool registration + AI guidance

**Files:**
- Modify: `app/src/main/java/com/lingji/app/domain/tool/ScopedToolRegistry.kt:48-55` (SINGLE_NOTE_TOOLS set)
- Modify: `app/src/main/java/com/lingji/app/data/remote/AgentService.kt:47-57` (system prompt)
- Modify: `app/src/main/java/com/lingji/app/data/remote/HomeAgentService.kt:51-59` (system prompt)

- [ ] **Step 1: Add "edit_replace" to SINGLE_NOTE_TOOLS**

- [ ] **Step 2: Update AgentService system prompt** — add instruction to prefer edit_replace for content edits, avoid update_page content param (corrupts images), update_page only for titles

- [ ] **Step 3: Update HomeAgentService system prompt** — same guidance

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Install to emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL (or skipped if no emulator)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: register edit_replace in scoped tools and update system prompts"
```

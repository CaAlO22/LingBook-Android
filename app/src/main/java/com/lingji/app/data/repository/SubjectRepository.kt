package com.lingji.app.data.repository

import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.NoteRevisionDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.db.dao.SyncTombstoneDao
import com.lingji.app.data.db.dao.TodoItemDao
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.FolderEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.NoteRevisionEntity
import com.lingji.app.data.db.entities.SubjectEntity
import com.lingji.app.data.db.entities.SyncTombstoneEntity
import com.lingji.app.data.db.entities.TodoItemEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.model.HomeItem
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndex
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.domain.model.TodoItem
import com.lingji.app.domain.model.TodoRepeat
import com.lingji.app.domain.model.TodoRepeatType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.lingji.app.data.edit.EditBatch
import com.lingji.app.data.reminder.TodoReminderScheduler
import kotlin.coroutines.coroutineContext
import java.util.UUID

@Singleton
class SubjectRepository @Inject constructor(
    private val subjectDao: SubjectDao,
    private val fragmentDao: FragmentDao,
    private val pageDao: NotebookPageDao,
    private val folderDao: FolderDao,
    private val revisionDao: NoteRevisionDao,
    private val summaryDao: SubjectSummaryDao,
    private val tombstoneDao: SyncTombstoneDao,
    private val todoItemDao: TodoItemDao,
    private val reminderScheduler: TodoReminderScheduler
) {
    private val gson = Gson()

    /** 云同步：记录删除墓碑，把删除传播到服务器与其他设备。 */
    private suspend fun recordTombstones(vararg entries: Pair<String, String>) {
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        tombstoneDao.insertAll(entries.map { (tbl, id) -> SyncTombstoneEntity(tbl, id, now) })
    }
    fun getAllSubjects(): Flow<List<Subject>> = combine(
        subjectDao.getAllSubjects(),
        fragmentDao.getAllFragments(),
        pageDao.getAllPages(),
        todoItemDao.getAllTodoItems()
    ) { subjects, fragments, pages, todoItems ->
        subjects.map { entity ->
            val subjectFragments = fragments.filter { it.subjectId == entity.id }
            val subjectPages = pages.filter { it.subjectId == entity.id }
            val subjectTodoItems = todoItems.filter { it.subjectId == entity.id }
            assemble(entity, subjectFragments, subjectPages, subjectTodoItems)
        }
    }

    fun getSubjectById(id: String): Flow<Subject?> = combine(
        subjectDao.getAllSubjects().map { list -> list.find { it.id == id } },
        fragmentDao.getFragmentsBySubject(id),
        pageDao.getPagesBySubject(id),
        todoItemDao.getAllTodoItems()
    ) { entity, fragments, pages, todoItems ->
        entity?.let {
            assemble(it, fragments, pages, todoItems.filter { item -> item.subjectId == id })
        }
    }

    suspend fun getSubjectByIdOnce(id: String): Subject? {
        val entity = subjectDao.getSubjectById(id) ?: return null
        val fragments = fragmentDao.getFragmentsBySubjectOnce(id)
        val pages = pageDao.getPagesBySubjectOnce(id)
        val todoItems = todoItemDao.getTodoItemsBySubjectOnce(id)
        return assemble(entity, fragments, pages, todoItems)
    }

    suspend fun insert(subject: Subject) {
        subjectDao.upsert(subject.toEntity())
        syncFragments(subject)
        syncPages(subject)
        syncTodoItems(subject)
        if (subject.type == SubjectType.TODO) reminderScheduler.refreshSubject(subject.id)
    }

    suspend fun delete(id: String) {
        val entity = subjectDao.getSubjectById(id) ?: return
        // 先收集将被级联删除的实体 id，为其记录墓碑
        val fragmentIds = fragmentDao.getFragmentsBySubjectOnce(id).map { it.id }
        val pageIds = pageDao.getPagesBySubjectOnce(id).map { it.id }
        val todoItemIds = todoItemDao.getTodoItemsBySubjectOnce(id).map { it.id }
        subjectDao.delete(entity)
        fragmentDao.deleteBySubject(id)
        pageDao.deleteBySubject(id)
        todoItemDao.deleteBySubject(id)
        summaryDao.deleteBySubjectId(id)
        reminderScheduler.cancelSubject(id)
        todoItemIds.forEach { reminderScheduler.cancelItem(it) }
        recordTombstones(
            *arrayOf("subjects" to id, "subject_summaries" to id) +
                fragmentIds.map { "fragments" to it } +
                pageIds.map { "notebook_pages" to it } +
                todoItemIds.map { "todo_items" to it }
        )
    }

    suspend fun rename(id: String, title: String) = subjectDao.rename(id, title, System.currentTimeMillis())

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
        subjectDao.updateAggregatedNote(id, content, entity.aggregatedNote, System.currentTimeMillis())
    }

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
                    subjectDao.updateAggregatedNote(subjectId, earliest.prevContent, null, System.currentTimeMillis())
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

    suspend fun updateStudyPlan(id: String, content: String) = subjectDao.updateStudyPlan(id, content, System.currentTimeMillis())

    suspend fun moveSubject(id: String, orderIndex: Int) = subjectDao.updateOrderIndex(id, orderIndex, System.currentTimeMillis())

    suspend fun addFragment(subjectId: String, fragment: Fragment) {
        fragmentDao.insert(fragment.toEntity(subjectId, isUnmerged = true))
    }

    suspend fun updateFragment(subjectId: String, fragmentId: String, content: String) {
        fragmentDao.updateContent(fragmentId, content, System.currentTimeMillis())
    }

    suspend fun deleteFragment(subjectId: String, fragmentId: String) {
        fragmentDao.deleteById(fragmentId)
        recordTombstones("fragments" to fragmentId)
    }

    suspend fun completeBatchMerge(subjectId: String, mergedFragmentIds: List<String>) {
        fragmentDao.markUnmergedMergedByIds(subjectId, mergedFragmentIds, System.currentTimeMillis())
    }

    // ───────────────────────── 待办列表 ─────────────────────────

    /** 返回新建的待办项，供 Agent 工具把 id 回传给调用方（UI 侧忽略返回值）。 */
    suspend fun addTodoItem(
        subjectId: String,
        content: String,
        reminderEnabled: Boolean = false,
        reminderMinutes: Int = -1,
        reminderAt: Long = 0
    ): TodoItem {
        // 新增条目追加到末尾，保留用户手动拖动后的相对顺序。
        val nextOrder = todoItemDao.maxSortOrder(subjectId) + 1
        val item = TodoItem(
            content = content,
            sortOrder = nextOrder,
            reminderEnabled = reminderEnabled,
            reminderMinutes = reminderMinutes,
            reminderAt = reminderAt
        )
        todoItemDao.insert(item.toEntity(subjectId))
        if (reminderEnabled) reminderScheduler.refreshSubject(subjectId)
        return item
    }

    suspend fun setTodoItemDone(itemId: String, isDone: Boolean) {
        todoItemDao.updateDone(itemId, isDone, System.currentTimeMillis())
        val item = todoItemDao.getByIdOnce(itemId) ?: return
        // 信息岛：自动排序开启时，勾选完成的条目移到末尾，取消完成的条目自动上浮
        if (subjectDao.getSubjectById(item.subjectId)?.todoAutoSortDone == true) {
            applyTodoAutoSort(item.subjectId, itemId, isDone)
        }
        reminderScheduler.refreshSubject(item.subjectId)
    }

    /**
     * 信息岛：按完成状态重排——完成项沉到末尾；取消完成项浮到未完成区末尾（首个完成项之前）。
     * 顺序重写位次，供云同步感知。
     */
    private suspend fun applyTodoAutoSort(subjectId: String, itemId: String, isDone: Boolean) {
        val entities = todoItemDao.getTodoItemsBySubjectOnce(subjectId)
        val originalIds = entities.map { it.id }
        if (originalIds.size <= 1) return
        val remaining = entities.filter { it.id != itemId }
        if (remaining.size == entities.size) return
        val orderedIds = remaining.map { it.id }.toMutableList()
        val targetIndex = if (isDone) {
            orderedIds.size
        } else {
            remaining.indexOfFirst { it.isDone }.let { if (it < 0) orderedIds.size else it }
        }
        orderedIds.add(targetIndex, itemId)
        if (orderedIds == originalIds) return
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id -> todoItemDao.updateSortOrder(id, index, now) }
    }

    /** 信息岛：待办条目编辑——仅更新正文并刷新 updatedAt，供云同步感知修改。 */
    suspend fun updateTodoItemContent(itemId: String, content: String) {
        todoItemDao.updateContent(itemId, content, System.currentTimeMillis())
    }

    /** 信息岛：编辑待办正文与提醒设置，并立即重排该列表的提醒。 */
    suspend fun updateTodoItem(
        itemId: String,
        content: String,
        reminderEnabled: Boolean,
        reminderMinutes: Int,
        reminderAt: Long
    ) {
        val item = todoItemDao.getByIdOnce(itemId) ?: return
        todoItemDao.updateContentAndReminder(
            id = itemId,
            content = content,
            reminderEnabled = reminderEnabled,
            reminderMinutes = reminderMinutes,
            reminderAt = reminderAt,
            updatedAt = System.currentTimeMillis()
        )
        reminderScheduler.refreshSubject(item.subjectId)
    }

    /** 信息岛：仅更新单条待办提醒。 */
    suspend fun updateTodoItemReminder(
        itemId: String,
        reminderEnabled: Boolean,
        reminderMinutes: Int,
        reminderAt: Long
    ) {
        val item = todoItemDao.getByIdOnce(itemId) ?: return
        todoItemDao.updateReminder(
            id = itemId,
            reminderEnabled = reminderEnabled,
            reminderMinutes = reminderMinutes,
            reminderAt = reminderAt,
            updatedAt = System.currentTimeMillis()
        )
        reminderScheduler.refreshSubject(item.subjectId)
    }

    suspend fun deleteTodoItem(itemId: String) {
        todoItemDao.deleteById(itemId)
        reminderScheduler.cancelItem(itemId)
        recordTombstones("todo_items" to itemId)
    }

    /** 按拖动后的顺序重写位次，顺带刷新 updatedAt 供云同步感知。 */
    suspend fun reorderTodoItems(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        android.util.Log.d(
            "LingjiDrag",
            "repo.reorder n=${orderedIds.size} first=${orderedIds.firstOrNull()} last=${orderedIds.lastOrNull()}"
        )
        orderedIds.forEachIndexed { index, id -> todoItemDao.updateSortOrder(id, index, now) }
        android.util.Log.d("LingjiDrag", "repo.reorder done")
    }

    /** 信息岛：更新整个待办列表的提醒设置，并立即重排闹钟。 */
    suspend fun updateTodoReminder(
        subjectId: String,
        reminderEnabled: Boolean,
        reminderMinutes: Int,
        reminderAt: Long
    ) {
        subjectDao.updateTodoReminder(
            id = subjectId,
            reminderEnabled = reminderEnabled,
            reminderMinutes = reminderMinutes,
            reminderAt = reminderAt,
            updatedAt = System.currentTimeMillis()
        )
        reminderScheduler.refreshSubject(subjectId)
    }

    /** 信息岛：开关待办自动排序（勾选完成的条目移到末尾）。 */
    suspend fun updateTodoAutoSortDone(subjectId: String, enabled: Boolean) {
        subjectDao.updateTodoAutoSortDone(subjectId, enabled, System.currentTimeMillis())
    }

    /** 新周期开始时清除所有勾选，并记录本次重置时间。 */
    suspend fun resetTodoDone(subjectId: String, lastResetAt: Long) {
        val now = System.currentTimeMillis()
        todoItemDao.resetDoneBySubject(subjectId, now)
        subjectDao.updateTodoLastResetAt(subjectId, lastResetAt, now)
        // 新周期开始后重新排定提醒（一次性提醒已在完成时清除，这里只需处理重复提醒）。
        reminderScheduler.refreshSubject(subjectId)
    }

    suspend fun addPage(subjectId: String, page: NotebookPage) {
        val count = pageDao.getPagesBySubjectOnce(subjectId).size
        pageDao.insert(page.toEntity(subjectId, count))
    }

    suspend fun insertPageAt(subjectId: String, page: NotebookPage, position: Int) {
        val existing = pageDao.getPagesBySubjectOnce(subjectId).toMutableList()
        val entity = page.toEntity(subjectId, position)
        existing.add(position, entity)
        pageDao.deleteBySubject(subjectId)
        val now = System.currentTimeMillis()
        pageDao.insertAll(existing.mapIndexed { idx, p -> p.copy(orderIndex = idx, updatedAt = now) })
    }

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

    suspend fun deletePage(subjectId: String, pageId: String) {
        pageDao.deleteById(pageId)
        recordTombstones("notebook_pages" to pageId)
    }

    suspend fun movePage(subjectId: String, pageId: String, newIndex: Int) {
        val pages = pageDao.getPagesBySubjectOnce(subjectId).toMutableList()
        val currentIndex = pages.indexOfFirst { it.id == pageId }
        if (currentIndex == -1 || currentIndex == newIndex) return
        val moved = pages.removeAt(currentIndex)
        val targetIndex = newIndex.coerceIn(0, pages.size)
        pages.add(targetIndex, moved)
        pageDao.deleteBySubject(subjectId)
        val now = System.currentTimeMillis()
        pageDao.insertAll(pages.mapIndexed { idx, p -> p.copy(orderIndex = idx, updatedAt = now) })
    }

    suspend fun markPagesIndexed(subjectId: String, pageIds: List<String>, indexedAt: Long) {
        for (pageId in pageIds) {
            pageDao.updateIndexedAt(pageId, indexedAt)
        }
    }

    suspend fun savePageIndexEntries(subjectId: String, entries: List<PageIndexEntry>) {
        val current = getSubjectByIdOnce(subjectId)?.pageIndexEntries ?: emptyList()
        val newPageIds = entries.map { it.pageId }.toSet()
        val merged = current.filter { it.pageId !in newPageIds } + entries
        subjectDao.updatePageIndexJson(subjectId, gson.toJson(merged), System.currentTimeMillis())
    }

    suspend fun updatePageIndexEntry(subjectId: String, pageId: String, entry: PageIndexEntry) {
        val current = getSubjectByIdOnce(subjectId)?.pageIndexEntries ?: emptyList()
        val updated = current.map { if (it.pageId == pageId) entry else it }
            .let { if (it.none { e -> e.pageId == pageId }) it + entry else it }
        subjectDao.updatePageIndexJson(subjectId, gson.toJson(updated), System.currentTimeMillis())
        pageDao.updateIndexedAt(pageId, System.currentTimeMillis())
    }

    suspend fun updateLastOpenedPageId(subjectId: String, pageId: String?) {
        subjectDao.updateLastOpenedPageId(subjectId, pageId, System.currentTimeMillis())
    }

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
        subjectDao.clearFolderAssociation(id, System.currentTimeMillis())
        val folder = folderDao.getFolderById(id) ?: return
        folderDao.delete(folder)
        recordTombstones("folders" to id)
    }

    suspend fun renameFolder(id: String, name: String) = folderDao.rename(id, name, System.currentTimeMillis())

    suspend fun moveSubjectToFolder(subjectId: String, folderId: String) {
        val now = System.currentTimeMillis()
        subjectDao.updateFolderId(subjectId, folderId, now)
        // Assign orderIndex at top of folder's note list
        val count = subjectDao.getSubjectsByFolderOnce(folderId).size
        subjectDao.updateOrderIndex(subjectId, count, System.currentTimeMillis())
    }

    suspend fun removeSubjectFromFolder(subjectId: String) {
        subjectDao.updateFolderId(subjectId, null, System.currentTimeMillis())
        // Assign orderIndex at top of home page list
        val homeSubjects = subjectDao.getSubjectsByFolderOnce(null)
        subjectDao.updateOrderIndex(subjectId, homeSubjects.size, System.currentTimeMillis())
    }

    suspend fun reorderHomeItems(orderedItems: List<HomeItem>) {
        val size = orderedItems.size
        orderedItems.forEachIndexed { index, item ->
            // Assign descending orderIndex: first item (index 0) gets highest value
            // because display sorts by orderIndex DESC
            val orderIndex = size - 1 - index
            when (item) {
                is HomeItem.FolderItem -> folderDao.updateOrderIndex(item.folder.id, orderIndex, System.currentTimeMillis())
                is HomeItem.NoteItem -> subjectDao.updateOrderIndex(item.subject.id, orderIndex, System.currentTimeMillis())
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun reorderFolderItems(folderId: String, orderedSubjectIds: List<String>) {
        val size = orderedSubjectIds.size
        orderedSubjectIds.forEachIndexed { index, id ->
            subjectDao.updateOrderIndex(id, size - 1 - index, System.currentTimeMillis())
        }
    }

    private suspend fun loadSubject(entity: SubjectEntity): Subject {
        val fragments = fragmentDao.getFragmentsBySubjectOnce(entity.id)
        val pages = pageDao.getPagesBySubjectOnce(entity.id)
        val todoItems = todoItemDao.getTodoItemsBySubjectOnce(entity.id)
        return assemble(entity, fragments, pages, todoItems)
    }

    private fun assemble(
        entity: SubjectEntity,
        fragments: List<FragmentEntity>,
        pages: List<NotebookPageEntity>,
        todoItems: List<TodoItemEntity> = emptyList()
    ): Subject {
        val domainFragments = fragments.filter { !it.isUnmerged }.map { it.toDomain() }
        val unmerged = fragments.filter { it.isUnmerged }.map { it.toDomain() }
        val domainPages = pages.map { it.toDomain() }
        val pageIndexEntries = try {
            val type = object : TypeToken<List<PageIndexEntry>>() {}.type
            gson.fromJson<List<PageIndexEntry>>(entity.pageIndexJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
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
            folderId = entity.folderId,
            todoItems = todoItems.map { it.toDomain() },
            todoRepeat = TodoRepeat(
                type = runCatching { TodoRepeatType.valueOf(entity.todoRepeatType.uppercase()) }
                    .getOrDefault(TodoRepeatType.ONCE),
                weekDays = entity.todoRepeatDays.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .toSet(),
                intervalDays = entity.todoIntervalDays.coerceAtLeast(1),
                anchorAt = entity.todoRepeatAnchorAt
            ),
            todoLastResetAt = entity.todoLastResetAt,
            // 信息岛：列表整体提醒必须回读，否则保存后 UI 仍显示未设置，且后续 insert 会用默认值覆盖
            todoReminderEnabled = entity.todoReminderEnabled,
            todoReminderMinutes = entity.todoReminderMinutes,
            todoReminderAt = entity.todoReminderAt,
            todoAutoSortDone = entity.todoAutoSortDone
        )
    }

    private suspend fun syncFragments(subject: Subject) {
        fragmentDao.deleteBySubject(subject.id)
        val all = subject.fragments.map { it.toEntity(subject.id, isUnmerged = false) } +
                subject.unmergedFragments.map { it.toEntity(subject.id, isUnmerged = true) }
        fragmentDao.insertAll(all)
    }

    private suspend fun syncPages(subject: Subject) {
        subject.pages ?: return
        pageDao.deleteBySubject(subject.id)
        pageDao.insertAll(subject.pages.mapIndexed { idx, p -> p.toEntity(subject.id, idx) })
    }

    private suspend fun syncTodoItems(subject: Subject) {
        if (subject.type != SubjectType.TODO) return
        todoItemDao.deleteBySubject(subject.id)
        todoItemDao.insertAll(subject.todoItems.map { it.toEntity(subject.id) })
    }

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
        folderId = folderId,
        todoRepeatType = todoRepeat.type.name,
        todoRepeatDays = todoRepeat.weekDays.sorted().joinToString(","),
        todoIntervalDays = todoRepeat.intervalDays.coerceAtLeast(1),
        todoRepeatAnchorAt = todoRepeat.anchorAt,
        todoLastResetAt = todoLastResetAt,
        todoReminderEnabled = todoReminderEnabled,
        todoReminderMinutes = todoReminderMinutes,
        todoReminderAt = todoReminderAt,
        todoAutoSortDone = todoAutoSortDone
    )

    private fun TodoItem.toEntity(subjectId: String) = TodoItemEntity(
        id = id,
        subjectId = subjectId,
        content = content,
        isDone = isDone,
        sortOrder = sortOrder,
        createdAt = createdAt,
        reminderEnabled = reminderEnabled,
        reminderMinutes = reminderMinutes,
        reminderAt = reminderAt
    )

    private fun TodoItemEntity.toDomain() = TodoItem(
        id = id,
        content = content,
        isDone = isDone,
        sortOrder = sortOrder,
        createdAt = createdAt,
        reminderEnabled = reminderEnabled,
        reminderMinutes = reminderMinutes,
        reminderAt = reminderAt
    )

    private fun Fragment.toEntity(subjectId: String, isUnmerged: Boolean) = FragmentEntity(
        id = id,
        subjectId = subjectId,
        content = content,
        timestamp = timestamp,
        isUnmerged = isUnmerged
    )

    private fun FragmentEntity.toDomain() = Fragment(id, content, timestamp, !isUnmerged)

    private fun NotebookPage.toEntity(subjectId: String, orderIndex: Int) = NotebookPageEntity(
        id = id,
        subjectId = subjectId,
        title = title,
        content = content,
        orderIndex = orderIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        indexedAt = indexedAt
    )

    private fun NotebookPageEntity.toDomain() = NotebookPage(id, title, content, createdAt, updatedAt, indexedAt)

    private fun FolderEntity.toDomain() = Folder(id = id, name = name, orderIndex = orderIndex, createdAt = createdAt)
    private fun Folder.toEntity() = FolderEntity(id = id, name = name, orderIndex = orderIndex, createdAt = createdAt)
}

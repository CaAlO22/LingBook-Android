package com.lingji.app.data.repository

import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.NoteRevisionDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.FolderEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.NoteRevisionEntity
import com.lingji.app.data.db.entities.SubjectEntity
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.lingji.app.data.edit.EditBatch
import kotlin.coroutines.coroutineContext
import java.util.UUID

@Singleton
class SubjectRepository @Inject constructor(
    private val subjectDao: SubjectDao,
    private val fragmentDao: FragmentDao,
    private val pageDao: NotebookPageDao,
    private val folderDao: FolderDao,
    private val revisionDao: NoteRevisionDao
) {
    private val gson = Gson()
    fun getAllSubjects(): Flow<List<Subject>> = combine(
        subjectDao.getAllSubjects(),
        fragmentDao.getAllFragments(),
        pageDao.getAllPages()
    ) { subjects, fragments, pages ->
        subjects.map { entity ->
            val subjectFragments = fragments.filter { it.subjectId == entity.id }
            val subjectPages = pages.filter { it.subjectId == entity.id }
            assemble(entity, subjectFragments, subjectPages)
        }
    }

    fun getSubjectById(id: String): Flow<Subject?> = combine(
        subjectDao.getAllSubjects().map { list -> list.find { it.id == id } },
        fragmentDao.getFragmentsBySubject(id),
        pageDao.getPagesBySubject(id)
    ) { entity, fragments, pages ->
        entity?.let { assemble(it, fragments, pages) }
    }

    suspend fun getSubjectByIdOnce(id: String): Subject? {
        val entity = subjectDao.getSubjectById(id) ?: return null
        val fragments = fragmentDao.getFragmentsBySubjectOnce(id)
        val pages = pageDao.getPagesBySubjectOnce(id)
        return assemble(entity, fragments, pages)
    }

    suspend fun insert(subject: Subject) {
        subjectDao.upsert(subject.toEntity())
        syncFragments(subject)
        syncPages(subject)
    }

    suspend fun delete(id: String) {
        val entity = subjectDao.getSubjectById(id) ?: return
        subjectDao.delete(entity)
        fragmentDao.deleteBySubject(id)
        pageDao.deleteBySubject(id)
    }

    suspend fun rename(id: String, title: String) = subjectDao.rename(id, title)

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

    suspend fun updateStudyPlan(id: String, content: String) = subjectDao.updateStudyPlan(id, content)

    suspend fun moveSubject(id: String, orderIndex: Int) = subjectDao.updateOrderIndex(id, orderIndex)

    suspend fun addFragment(subjectId: String, fragment: Fragment) {
        fragmentDao.insert(fragment.toEntity(subjectId, isUnmerged = true))
    }

    suspend fun updateFragment(subjectId: String, fragmentId: String, content: String) {
        fragmentDao.updateContent(fragmentId, content)
    }

    suspend fun deleteFragment(subjectId: String, fragmentId: String) {
        fragmentDao.delete(FragmentEntity(fragmentId, subjectId, "", 0))
    }

    suspend fun completeBatchMerge(subjectId: String, mergedFragmentIds: List<String>) {
        fragmentDao.markUnmergedMergedByIds(subjectId, mergedFragmentIds)
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
        pageDao.insertAll(existing.mapIndexed { idx, p -> p.copy(orderIndex = idx) })
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
        pageDao.delete(NotebookPageEntity(pageId, subjectId, "", "", 0, 0, 0, 0))
    }

    suspend fun movePage(subjectId: String, pageId: String, newIndex: Int) {
        val pages = pageDao.getPagesBySubjectOnce(subjectId).toMutableList()
        val currentIndex = pages.indexOfFirst { it.id == pageId }
        if (currentIndex == -1 || currentIndex == newIndex) return
        val moved = pages.removeAt(currentIndex)
        val targetIndex = newIndex.coerceIn(0, pages.size)
        pages.add(targetIndex, moved)
        pageDao.deleteBySubject(subjectId)
        pageDao.insertAll(pages.mapIndexed { idx, p -> p.copy(orderIndex = idx) })
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
        subjectDao.updatePageIndexJson(subjectId, gson.toJson(merged))
    }

    suspend fun updatePageIndexEntry(subjectId: String, pageId: String, entry: PageIndexEntry) {
        val current = getSubjectByIdOnce(subjectId)?.pageIndexEntries ?: emptyList()
        val updated = current.map { if (it.pageId == pageId) entry else it }
            .let { if (it.none { e -> e.pageId == pageId }) it + entry else it }
        subjectDao.updatePageIndexJson(subjectId, gson.toJson(updated))
        pageDao.updateIndexedAt(pageId, System.currentTimeMillis())
    }

    suspend fun updateLastOpenedPageId(subjectId: String, pageId: String?) {
        subjectDao.updateLastOpenedPageId(subjectId, pageId)
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
        subjectDao.clearFolderAssociation(id)
        val folder = folderDao.getFolderById(id) ?: return
        folderDao.delete(folder)
    }

    suspend fun renameFolder(id: String, name: String) = folderDao.rename(id, name)

    suspend fun moveSubjectToFolder(subjectId: String, folderId: String) {
        subjectDao.updateFolderId(subjectId, folderId)
        // Assign orderIndex at top of folder's note list
        val count = subjectDao.getSubjectsByFolderOnce(folderId).size
        subjectDao.updateOrderIndex(subjectId, count)
    }

    suspend fun removeSubjectFromFolder(subjectId: String) {
        subjectDao.updateFolderId(subjectId, null)
        // Assign orderIndex at top of home page list
        val homeSubjects = subjectDao.getSubjectsByFolderOnce(null)
        subjectDao.updateOrderIndex(subjectId, homeSubjects.size)
    }

    suspend fun reorderHomeItems(orderedItems: List<HomeItem>) {
        val size = orderedItems.size
        orderedItems.forEachIndexed { index, item ->
            // Assign descending orderIndex: first item (index 0) gets highest value
            // because display sorts by orderIndex DESC
            val orderIndex = size - 1 - index
            when (item) {
                is HomeItem.FolderItem -> folderDao.updateOrderIndex(item.folder.id, orderIndex)
                is HomeItem.NoteItem -> subjectDao.updateOrderIndex(item.subject.id, orderIndex)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun reorderFolderItems(folderId: String, orderedSubjectIds: List<String>) {
        val size = orderedSubjectIds.size
        orderedSubjectIds.forEachIndexed { index, id ->
            subjectDao.updateOrderIndex(id, size - 1 - index)
        }
    }

    private suspend fun loadSubject(entity: SubjectEntity): Subject {
        val fragments = fragmentDao.getFragmentsBySubjectOnce(entity.id)
        val pages = pageDao.getPagesBySubjectOnce(entity.id)
        return assemble(entity, fragments, pages)
    }

    private fun assemble(entity: SubjectEntity, fragments: List<FragmentEntity>, pages: List<NotebookPageEntity>): Subject {
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
            folderId = entity.folderId
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

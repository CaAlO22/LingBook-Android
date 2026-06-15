package com.lingji.app.data.repository

import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.entities.FragmentEntity
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.SubjectEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lingji.app.domain.model.Fragment
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.PageIndex
import com.lingji.app.domain.model.PageIndexEntry
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val subjectDao: SubjectDao,
    private val fragmentDao: FragmentDao,
    private val pageDao: NotebookPageDao
) {
    private val gson = Gson()
    fun getAllSubjects(): Flow<List<Subject>> = combine(
        subjectDao.getAllSubjects(),
        fragmentDao.getFragmentsBySubject(""),
        pageDao.getPagesBySubject("")
    ) { subjects, _, _ ->
        subjects.map { entity -> loadSubject(entity) }
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
        subjectDao.updateAggregatedNote(id, content, entity.aggregatedNote)
    }

    suspend fun rollbackAggregatedNote(id: String) {
        val entity = subjectDao.getSubjectById(id) ?: return
        val prev = entity.prevAggregatedNote ?: return
        subjectDao.updateAggregatedNote(id, prev, null)
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
        fragmentDao.deleteUnmergedByIds(subjectId, mergedFragmentIds)
        // Mark remaining unmerged as not unmerged? Keep existing logic: deleted ones are removed.
        // The rest remains with isUnmerged=1.
    }

    suspend fun addPage(subjectId: String, page: NotebookPage) {
        val count = pageDao.getPagesBySubjectOnce(subjectId).size
        pageDao.insert(page.toEntity(subjectId, count))
    }

    suspend fun updatePage(subjectId: String, page: NotebookPage) {
        pageDao.update(page.id, page.title, page.content, page.updatedAt)
    }

    suspend fun deletePage(subjectId: String, pageId: String) {
        pageDao.delete(NotebookPageEntity(pageId, subjectId, "", "", 0, 0, 0, 0))
    }

    suspend fun markPagesIndexed(subjectId: String, pageIds: List<String>, indexedAt: Long) {
        for (pageId in pageIds) {
            pageDao.updateIndexedAt(pageId, indexedAt)
        }
    }

    suspend fun savePageIndexEntries(subjectId: String, entries: List<PageIndexEntry>) {
        subjectDao.updatePageIndexJson(subjectId, gson.toJson(entries))
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
            pageIndexEntries = if (entity.type.equals("notebook", true)) pageIndexEntries else null
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
        pageIndexJson = pageIndexEntries?.let { gson.toJson(it) } ?: ""
    )

    private fun Fragment.toEntity(subjectId: String, isUnmerged: Boolean) = FragmentEntity(
        id = id,
        subjectId = subjectId,
        content = content,
        timestamp = timestamp,
        isUnmerged = isUnmerged
    )

    private fun FragmentEntity.toDomain() = Fragment(id, content, timestamp)

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
}

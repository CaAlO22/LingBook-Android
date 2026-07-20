package com.lingji.app.data.repository

import com.lingji.app.data.db.dao.FragmentDao
import com.lingji.app.data.db.dao.FolderDao
import com.lingji.app.data.db.dao.NotebookPageDao
import com.lingji.app.data.db.dao.SubjectDao
import com.lingji.app.data.db.dao.NoteRevisionDao
import com.lingji.app.data.db.entities.NotebookPageEntity
import com.lingji.app.data.db.entities.NoteRevisionEntity
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
        val revisionSlot = slot<NoteRevisionEntity>()
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
        val revisionSlot = slot<NoteRevisionEntity>()
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
        val revisionSlot = slot<NoteRevisionEntity>()
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
        val revision = NoteRevisionEntity(
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
        val r1 = NoteRevisionEntity(
            id = "r1", subjectId = "s2", pageId = "p1", field = "page",
            prevContent = "p1-old", prevTitle = "t1-old", batchId = batchId, createdAt = 100L
        )
        val r2 = NoteRevisionEntity(
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
        val r1 = NoteRevisionEntity(
            id = "r1", subjectId = "s1", pageId = null, field = "aggregated",
            prevContent = "v1", prevTitle = null, batchId = batchId, createdAt = 100L
        )
        val r2 = NoteRevisionEntity(
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

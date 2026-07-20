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

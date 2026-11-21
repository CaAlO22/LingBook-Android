package com.lingji.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同步协议版本判定。
 *
 * 关键约定：**没有版本号字段的旧服务器按 1 处理**，否则本次升级会让现网所有旧服务器直接失联。
 */
class SyncProtocolTest {

    @Test
    fun legacyServerWithoutVersion_isTreatedAsVersion1() {
        assertEquals(SyncProtocol.LEGACY_SCHEMA_VERSION, SyncProtocol.normalizeServerVersion(null))
        assertEquals(1, SyncProtocol.normalizeServerVersion(null))
    }

    @Test
    fun invalidVersionValues_fallBackToLegacy() {
        assertEquals(1, SyncProtocol.normalizeServerVersion(0))
        assertEquals(1, SyncProtocol.normalizeServerVersion(-5))
    }

    @Test
    fun explicitVersion_isPreserved() {
        assertEquals(3, SyncProtocol.normalizeServerVersion(3))
    }

    @Test
    fun currentServerVersion_isCompatible() {
        assertTrue(SyncProtocol.isServerCompatible(SyncProtocol.SCHEMA_VERSION))
        assertTrue(SyncProtocol.isServerCompatible(SyncProtocol.SCHEMA_VERSION + 1))
    }

    @Test
    fun olderServer_isRejected() {
        if (SyncProtocol.SCHEMA_VERSION > 1) {
            assertFalse(SyncProtocol.isServerCompatible(SyncProtocol.SCHEMA_VERSION - 1))
        }
        assertFalse(SyncProtocol.isServerCompatible(0))
    }
}

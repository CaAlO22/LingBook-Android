package com.lingji.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设备 ID 的格式与派生规则。 */
class DeviceIdFormatTest {

    @Test
    fun derive_producesReadablePrefixedId() {
        val id = DeviceIdFormat.derive("android-id|com.lingji.app")
        assertTrue(id, id.startsWith(DeviceIdFormat.PREFIX))
        // LJ- + 3 组 4 位，且不含易混字符
        assertTrue(id, id.matches(Regex("LJ-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}")))
        assertTrue(id, DeviceIdFormat.isValid(id))
    }

    @Test
    fun derive_isStableForSameSeed() {
        val seed = "9774d56d682e549c|com.lingji.app"
        assertEquals(DeviceIdFormat.derive(seed), DeviceIdFormat.derive(seed))
    }

    @Test
    fun derive_differsForDifferentSeed() {
        assertNotEquals(
            DeviceIdFormat.derive("device-a|com.lingji.app"),
            DeviceIdFormat.derive("device-b|com.lingji.app")
        )
    }

    @Test
    fun derive_avoidsConfusableCharacters() {
        val body = DeviceIdFormat.derive("seed").removePrefix(DeviceIdFormat.PREFIX)
        assertFalse(body, body.any { it in "01IO" })
    }

    @Test
    fun normalize_stripsWhitespaceFromPastedText() {
        assertEquals("LJ-7K3M-9Q2X-P4TN", DeviceIdFormat.normalize("  LJ-7K3M-9Q2X-P4TN\n"))
    }

    @Test
    fun isValid_acceptsDerivedAndCustomIds() {
        assertTrue(DeviceIdFormat.isValid(DeviceIdFormat.derive("x")))
        assertTrue(DeviceIdFormat.isValid("my_phone-01"))
        // 历史版本遗留的 UUID 形式仍可用
        assertTrue(DeviceIdFormat.isValid("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
    }

    @Test
    fun isValid_rejectsEmptyTooShortAndIllegalChars() {
        assertFalse(DeviceIdFormat.isValid(""))
        assertFalse(DeviceIdFormat.isValid("abc"))
        assertFalse(DeviceIdFormat.isValid("bad id"))
        assertFalse(DeviceIdFormat.isValid("bad/id"))
        assertFalse(DeviceIdFormat.isValid("x".repeat(DeviceIdFormat.MAX_LENGTH + 1)))
    }
}

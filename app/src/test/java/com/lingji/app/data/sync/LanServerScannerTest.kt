package com.lingji.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 局域网扫描的网段展开规则。 */
class LanServerScannerTest {

    @Test
    fun subnetHosts_coversWholeLastOctetExceptSelf() {
        val hosts = LanServerScanner.subnetHosts("192.168.1.7")
        assertEquals(253, hosts.size)
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.254"))
        assertFalse(hosts.contains("192.168.1.7"))
        assertFalse(hosts.contains("192.168.1.0"))
        assertFalse(hosts.contains("192.168.1.255"))
    }

    @Test
    fun subnetHosts_keepsSamePrefix() {
        val hosts = LanServerScanner.subnetHosts("10.20.30.40")
        assertTrue(hosts.all { it.startsWith("10.20.30.") })
        assertEquals(253, hosts.size)
    }

    @Test
    fun subnetHosts_handlesHotspotRange() {
        val hosts = LanServerScanner.subnetHosts("192.168.43.12")
        assertTrue(hosts.contains("192.168.43.1"))
        assertFalse(hosts.contains("192.168.43.12"))
    }

    @Test
    fun subnetHosts_rejectsInvalidInput() {
        assertTrue(LanServerScanner.subnetHosts("").isEmpty())
    }
}

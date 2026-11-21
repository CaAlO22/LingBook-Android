package com.lingji.app.data.remote.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * 局域网探测的判定条件：只认 `/api/ping` 返回的 `serverTime`，
 * 局域网里其他恰好开着 HTTP 的设备（HTML、其他 JSON）都不能被当成灵记服务器。
 *
 * 用极简 ServerSocket 搭桩：Android 单元测试的 classpath 里没有 jdk.httpserver。
 */
class SyncApiPingTest {

    private lateinit var lingjiServer: MiniHttpServer
    private lateinit var plainServer: MiniHttpServer
    private val api = SyncApi()

    @Before
    fun setUp() {
        lingjiServer = MiniHttpServer { path, auth ->
            when {
                path != "/api/ping" -> 404 to """{"ok":false,"error":"not found"}"""
                auth != "Bearer right" -> 401 to """{"ok":false,"error":"unauthorized"}"""
                else -> 200 to """{"ok":true,"serverTime":1730000000000}"""
            }
        }
        plainServer = MiniHttpServer { _, _ -> 200 to "<html>router admin</html>" }
    }

    @After
    fun tearDown() {
        lingjiServer.close()
        plainServer.close()
    }

    @Test
    fun pingFast_returnsServerTimeForLingjiServer() {
        assertEquals(1730000000000L, api.pingFast("http://127.0.0.1:${lingjiServer.port}", "right"))
    }

    @Test
    fun pingFast_rejectsNonLingjiHttpService() {
        // 返回 200 但正文是 HTML：拿不到 serverTime，不能当成灵记服务器
        assertNull(api.pingFast("http://127.0.0.1:${plainServer.port}", "right"))
    }

    @Test
    fun pingFast_rejectsWrongToken() {
        assertNull(api.pingFast("http://127.0.0.1:${lingjiServer.port}", "wrong"))
    }

    @Test
    fun pingFast_returnsNullWhenNothingListens() {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertNull(api.pingFast("http://127.0.0.1:$closedPort", ""))
    }

    @Test
    fun serverInfo_readsSchemaVersion() {
        val server = MiniHttpServer { _, _ -> 200 to """{"ok":true,"serverTime":1,"schemaVersion":3}""" }
        try {
            val info = api.serverInfo("http://127.0.0.1:${server.port}", "")
            assertEquals(3, info.schemaVersion)
            assertEquals(1L, info.serverTime)
        } finally {
            server.close()
        }
    }

    @Test
    fun serverInfo_legacyServerWithoutVersion_fallsBackToVersion1() {
        // 加版本号之前的旧服务器：不能因此判定为「过旧」而断掉同步
        val server = MiniHttpServer { _, _ -> 200 to """{"ok":true,"serverTime":1}""" }
        try {
            assertEquals(1, api.serverInfo("http://127.0.0.1:${server.port}", "").schemaVersion)
        } finally {
            server.close()
        }
    }

    /** 只回一个响应就关连接的最小 HTTP 桩。 */
    private class MiniHttpServer(
        private val respond: (path: String, authorization: String?) -> Pair<Int, String>
    ) {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort

        init {
            thread(isDaemon = true, name = "mini-http") {
                while (!socket.isClosed) {
                    try {
                        socket.accept().use(::handle)
                    } catch (_: Exception) {
                        return@thread
                    }
                }
            }
        }

        private fun handle(conn: Socket) {
            conn.soTimeout = 3_000
            val input = conn.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            var authorization: String? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Authorization:", ignoreCase = true)) {
                    authorization = line.substringAfter(':').trim()
                }
            }
            val path = requestLine.split(' ').getOrNull(1) ?: "/"
            val (code, body) = respond(path, authorization)
            val bytes = body.toByteArray(Charsets.UTF_8)
            val header = buildString {
                append("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            conn.getOutputStream().apply {
                write(header.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }

        fun close() {
            runCatching { socket.close() }
        }
    }
}

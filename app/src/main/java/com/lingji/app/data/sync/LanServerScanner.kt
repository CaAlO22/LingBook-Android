package com.lingji.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import com.lingji.app.data.remote.sync.SyncApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/** 局域网扫描结果。 */
sealed interface LanScanResult {
    data class Found(val host: String, val port: Int) : LanScanResult

    /** 持续到时限仍未发现灵记服务器。 */
    data object NotFound : LanScanResult

    /** 没有拿到本机局域网地址（未连 Wi-Fi / 只有移动网络）。 */
    data object NoLocalNetwork : LanScanResult
}

/**
 * 局域网服务器扫描。
 *
 * 只扫本机所在网段的 /24（保留 IP 的三段，遍历 1–254），逐个请求 `/api/ping`：
 * 命中即返回，否则在 [DEFAULT_DURATION_MS] 内反复重扫 —— 服务器可能刚好在扫描期间启动。
 *
 * 局域网内不存在的主机不会回 RST，只能等 connectTimeout 超时，所以单次探测必须短、
 * 并发必须够高，否则 15 秒扫不完一轮。
 */
@Singleton
class LanServerScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SyncApi
) {

    /**
     * 扫描 [port] 端口上的灵记服务器。
     *
     * @param onProgress 每轮扫描开始时回调剩余秒数，用于界面倒计时。
     * @return 找到的服务器，或超时 / 无局域网。
     */
    suspend fun scan(
        port: Int,
        token: String,
        durationMs: Long = DEFAULT_DURATION_MS,
        onProgress: (remainingSeconds: Int) -> Unit = {}
    ): LanScanResult {
        val localIp = localIpv4() ?: return LanScanResult.NoLocalNetwork
        val hosts = subnetHosts(localIp)
        val deadline = System.currentTimeMillis() + durationMs
        var reported = -1

        while (System.currentTimeMillis() < deadline) {
            val remaining = ((deadline - System.currentTimeMillis()) / 1000).toInt()
            if (remaining != reported) {
                reported = remaining
                onProgress(remaining)
            }
            val host = probeRound(hosts, port, token)
            if (host != null) return LanScanResult.Found(host, port)
        }
        onProgress(0)
        return LanScanResult.NotFound
    }

    /** 一轮全量探测，任一主机命中即返回。 */
    private suspend fun probeRound(hosts: List<String>, port: Int, token: String): String? =
        coroutineScope {
            val found = Channel<String>(Channel.UNLIMITED)
            val jobs = hosts.map { host ->
                launch(Dispatchers.IO) {
                    if (api.pingFast("http://$host:$port", token) != null) found.trySend(host)
                }
            }
            // 一轮最多等 ROUND_TIMEOUT_MS，避免个别地址把整轮拖死
            val winner = withTimeoutOrNull(ROUND_TIMEOUT_MS) { found.receive() }
            jobs.forEach { it.cancel() }
            winner
        }

    /** 本机当前的局域网 IPv4 地址。 */
    private fun localIpv4(): String? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val link = manager.getLinkProperties(manager.activeNetwork ?: return null) ?: return null
        return link.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }

    companion object {
        /** 需求约定的扫描时长。 */
        const val DEFAULT_DURATION_MS = 15_000L

        /** 单轮上限：要短于一次探测超时，命中后立即返回。 */
        private const val ROUND_TIMEOUT_MS = 2_500L

        /**
         * 由本机地址推出待扫的 /24 主机列表（去掉本机）。
         *
         * 固定按前三段展开：``10.20.30.7`` → ``10.20.30.1`` … ``10.20.30.254``。
         * 家用 / 办公路由器分配给终端的地址几乎都在同一 /24 内，15 秒足够扫完。
         */
        fun subnetHosts(localIp: String): List<String> {
            val prefix = localIp.substringBeforeLast('.', missingDelimiterValue = "")
            if (prefix.isEmpty()) return emptyList()
            return (1..254)
                .map { "$prefix.$it" }
                .filter { it != localIp }
        }
    }
}

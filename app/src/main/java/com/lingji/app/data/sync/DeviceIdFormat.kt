package com.lingji.app.data.sync

import java.security.MessageDigest

/**
 * 云同步设备 ID 的格式约定与生成规则。
 *
 * ID 用于同步时区分「哪些变更来自本机」，因此必须：可读、可抄写、可跨设备粘贴。
 * 生成时以设备自身标识为种子做 SHA-256，再转成去掉易混字符（0/1/I/O）的 32 进制，
 * 输出形如 `LJ-7K3M-9Q2X-P4TN` 的 12 位有效字符。
 *
 * 校验较宽松：只约束长度与字符集，允许用户粘贴其他设备生成的 ID（这是多设备合并的前提），
 * 也允许历史版本遗留的 UUID 形式。
 */
object DeviceIdFormat {

    /** 有效字符数下限（含用户自定义的短 ID）。 */
    const val MIN_LENGTH = 4

    /** 有效字符数上限，避免把整段文本误粘进来。 */
    const val MAX_LENGTH = 64

    /** 生成 ID 的固定前缀，便于用户一眼认出这是「灵记设备 ID」。 */
    const val PREFIX = "LJ-"

    /** 32 进制字母表：去掉 0/1/I/O，避免手抄时混淆。 */
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /** 生成 ID 的有效字符数（不含分隔短横线）。 */
    private const val DERIVED_LENGTH = 12

    private val ALLOWED = Regex("^[A-Za-z0-9_-]+$")

    /** 去掉空白（含换行）：用户从聊天窗口粘贴时常带尾随空格或换行。 */
    fun normalize(raw: String): String = raw.filterNot { it.isWhitespace() }

    /** 长度与字符集校验。 */
    fun isValid(value: String): Boolean =
        value.length in MIN_LENGTH..MAX_LENGTH && ALLOWED.matches(value)

    /**
     * 由种子派生设备 ID。
     *
     * 同一种子永远得到同一结果，便于「设备恢复出厂后重新派生出同一个 ID」；
     * 种子不可用时调用方应退化为随机种子，此时结果与设备无关但依然唯一。
     */
    fun derive(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(DERIVED_LENGTH)
        var buffer = 0
        var bits = 0
        var index = 0
        while (sb.length < DERIVED_LENGTH) {
            if (bits < 5) {
                buffer = (buffer shl 8) or (digest[index++].toInt() and 0xFF)
                bits += 8
            }
            val shift = bits - 5
            sb.append(ALPHABET[(buffer shr shift) and 0x1F])
            bits -= 5
        }
        return PREFIX + sb.chunked(4).joinToString("-")
    }
}

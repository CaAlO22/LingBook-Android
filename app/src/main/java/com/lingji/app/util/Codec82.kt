package com.lingji.app.util

import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Codec82 {
    const val LING82_PREFIX = "LING82GZ:"
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_~!\$*()[]{}<>=+@%^.:"
    private val base = BigInteger.valueOf(ALPHABET.length.toLong())
    private val gson = Gson()

    fun encode(obj: Any): String {
        val json = if (obj is String) compactJson(obj) else gson.toJson(obj)
        val gz = gzipCompress(json.toByteArray(Charsets.UTF_8))
        val encoded = base82Encode(gz)
        return LING82_PREFIX + encoded
    }

    private fun compactJson(text: String): String {
        return try {
            gson.toJson(gson.fromJson(text, Any::class.java))
        } catch (_: Exception) {
            text
        }
    }

    fun decode(text: String): Any {
        if (!text.startsWith(LING82_PREFIX)) {
            return gson.fromJson(text, Any::class.java)
        }
        val payload = text.removePrefix(LING82_PREFIX)
        val gz = base82Decode(payload)
        val json = gzipDecompress(gz)
        return gson.fromJson(String(json, Charsets.UTF_8), Any::class.java)
    }

    private fun base82Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        var zeros = 0
        while (zeros < bytes.size && bytes[zeros] == 0.toByte()) zeros++
        val value = bytes.drop(zeros).fold(BigInteger.ZERO) { acc, b ->
            (acc.shiftLeft(8)) + BigInteger.valueOf(b.toLong() and 0xFF)
        }
        val sb = StringBuilder()
        var v = value
        while (v > BigInteger.ZERO) {
            val rem = v.mod(base).toInt()
            sb.insert(0, ALPHABET[rem])
            v = v.divide(base)
        }
        return ALPHABET[0].toString().repeat(zeros) + sb.toString()
    }

    private fun base82Decode(text: String): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        var zeros = 0
        while (zeros < text.length && text[zeros] == ALPHABET[0]) zeros++
        var value = BigInteger.ZERO
        for (i in zeros until text.length) {
            val idx = ALPHABET.indexOf(text[i])
            require(idx >= 0) { "Invalid base82 char: ${text[i]}" }
            value = value.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        val bytes = mutableListOf<Byte>()
        var v = value
        while (v > BigInteger.ZERO) {
            bytes.add((v.mod(BigInteger.valueOf(256)).toInt()).toByte())
            v = v.divide(BigInteger.valueOf(256))
        }
        bytes.reverse()
        val leading = ByteArray(zeros) { 0 }
        val body = bytes.toByteArray()
        return leading + body
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(data)
        val bos = ByteArrayOutputStream()
        GZIPInputStream(bis).use { gzip ->
            val buffer = ByteArray(8192)
            var read: Int
            while (gzip.read(buffer).also { read = it } > 0) {
                bos.write(buffer, 0, read)
            }
        }
        return bos.toByteArray()
    }
}

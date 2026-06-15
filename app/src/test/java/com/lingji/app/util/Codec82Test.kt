package com.lingji.app.util

import com.google.gson.Gson
import com.lingji.app.domain.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Codec82Test {
    private val gson = Gson()

    @Test
    fun `round trip encoding and decoding`() {
        val data = mapOf(
            "title" to "测试学科",
            "fragments" to listOf(
                mapOf("id" to "a1", "content" to "第一条碎片", "timestamp" to 1L),
                mapOf("id" to "b2", "content" to "第二条碎片", "timestamp" to 2L)
            )
        )
        val encoded = Codec82.encode(data)
        assertTrue("Encoded text must start with LING82 prefix", encoded.startsWith(Codec82.LING82_PREFIX))
        val decoded = Codec82.decode(encoded)
        @Suppress("UNCHECKED_CAST")
        val map = decoded as Map<String, Any>
        assertEquals("测试学科", map["title"])
    }

    @Test
    fun `compact json string before encoding`() {
        val prettyJson = """
            {
                "title": "测试学科",
                "fragments": [
                    { "id": "a1", "content": "碎片一", "timestamp": 1 }
                ]
            }
        """.trimIndent()
        val encoded = Codec82.encode(prettyJson)
        val decoded = Codec82.decode(encoded)
        @Suppress("UNCHECKED_CAST")
        val map = decoded as Map<String, Any>
        assertEquals("测试学科", map["title"])
    }

    @Test
    fun `decode plain json without prefix`() {
        val json = """{"title":"Plain","fragments":[]}"""
        val decoded = Codec82.decode(json)
        @Suppress("UNCHECKED_CAST")
        val map = decoded as Map<String, Any>
        assertEquals("Plain", map["title"])
    }

    @Test
    fun `web exported fragment subject can be imported`() {
        val webSubject = mapOf(
            "id" to "web123",
            "title" to "Web导入测试",
            "fragments" to listOf(
                mapOf("id" to "f1", "content" to "来自Web的碎片", "timestamp" to 1700000000000L)
            ),
            "unmergedFragments" to emptyList<Map<String, Any>>(),
            "aggregatedNote" to "# Web导入测试\n\n这是笔记内容",
            "studyPlan" to "",
            "createdAt" to 1700000000000L
        )
        val encoded = Codec82.encode(gson.toJson(webSubject))
        val decoded = Codec82.decode(encoded)
        @Suppress("UNCHECKED_CAST")
        val map = decoded as Map<String, Any>
        assertEquals("Web导入测试", map["title"])
    }

    @Test
    fun `decode real web generated ling82 string`() {
        val encoded = "LING82GZ:1V]Q_)dmz~btG6k%}wYJwx5H\$)jOv=\$%fJ=RW9vDpgfZ[6_s!mIRst+-Mf%G(A15-Pbm+F4{TxPjD\$kJ*wW7IOa2Xd<5z8rPX@@pbdUclaRk9s-eDx%lbpJ*uZWW3Nl[X9aYnQo3Yx{i){^FDSq4<6F!.-TbeCsrm-x4DU{!7r%~dTPCQJlnn1KDQtY:f2<N[8tK]ZJxDTk]_1fM{7m{39yv+GS%E.jm4:F^Ie_Nag>6>W_IT:iH.t__JXIZgk.Tpp^Y)8ltgRm{:"
        val decoded = Codec82.decode(encoded)
        @Suppress("UNCHECKED_CAST")
        val map = decoded as Map<String, Any>
        assertEquals("Web导入测试", map["title"])
        @Suppress("UNCHECKED_CAST")
        val fragments = map["fragments"] as List<Map<String, Any>>
        assertEquals(1, fragments.size)
        assertEquals("来自Web的碎片", fragments[0]["content"])
    }

    @Test
    fun `decode user provided web ling82 file`() {
        val file = File("D:/LingBook/Andriod-Ling/tmp/web_lingcode.txt")
        assertTrue("File should exist", file.exists())
        val encoded = file.readText().trim()
        assertTrue("Encoded text must start with LING82 prefix", encoded.startsWith(Codec82.LING82_PREFIX))
        val decoded = Codec82.decode(encoded)
        @Suppress("UNCHECKED_CAST")
        val map = decoded as Map<String, Any>
        assertEquals("时事政治", map["title"])
        val subject = gson.fromJson(gson.toJson(decoded), Subject::class.java)
        assertEquals("时事政治", subject.title)
        assertTrue("Fragments should not be empty", subject.fragments.isNotEmpty())
    }
}

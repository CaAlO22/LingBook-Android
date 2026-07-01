package com.lingji.app.domain.tool

import com.google.gson.JsonObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolTest {

    private val sampleTool = object : Tool {
        override val name = "test_tool"
        override val description = "A test tool"
        override val parameters = buildJsonObject {
            "type" to "object"
            "properties" to buildJsonObject {
                "x" to buildJsonObject { "type" to "string" }
            }
        }
        override suspend fun execute(params: JsonObject): String = "ok"
    }

    @Test
    fun toOpenAITool_producesCorrectStructure() {
        val result = sampleTool.toOpenAITool()
        assertEquals("function", result.get("type").asString)
        val fn = result.getAsJsonObject("function")
        assertEquals("test_tool", fn.get("name").asString)
        assertEquals("A test tool", fn.get("description").asString)
        assertEquals("object", fn.getAsJsonObject("parameters").get("type").asString)
    }

    @Test
    fun buildJsonObject_createsCorrectPairs() {
        val obj = buildJsonObject {
            "a" to "hello"
            "b" to 42
            "c" to true
        }
        assertEquals("hello", obj.get("a").asString)
        assertEquals(42, obj.get("b").asInt)
        assertEquals(true, obj.get("c").asBoolean)
    }

    @Test
    fun execute_returnsResult() = runTest {
        val result = sampleTool.execute(JsonObject())
        assertEquals("ok", result)
    }
}

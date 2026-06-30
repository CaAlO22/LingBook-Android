package com.lingji.app.domain.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** DSL helper for building JsonObject with `"key" to value` syntax. */
fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject {
    val builder = JsonObjectBuilder()
    builder.block()
    return builder.obj
}

/** DSL helper for building JsonArray with `+ value` or `+ { block }` syntax. */
fun buildJsonArray(block: JsonArrayBuilder.() -> Unit): JsonArray {
    val builder = JsonArrayBuilder()
    builder.block()
    return builder.arr
}

class JsonObjectBuilder {
    val obj = JsonObject()

    infix fun String.to(value: String) = obj.addProperty(this, value)
    infix fun String.to(value: Number) = obj.addProperty(this, value)
    infix fun String.to(value: Boolean) = obj.addProperty(this, value)
    infix fun String.to(value: JsonObject) = obj.add(this, value)
    infix fun String.to(value: JsonArray) = obj.add(this, value)
}

class JsonArrayBuilder {
    val arr = JsonArray()

    operator fun String.unaryPlus() = arr.add(this)
    operator fun JsonObject.unaryPlus() = arr.add(this)
    operator fun Number.unaryPlus() = arr.add(this)
    operator fun Boolean.unaryPlus() = arr.add(this)
}

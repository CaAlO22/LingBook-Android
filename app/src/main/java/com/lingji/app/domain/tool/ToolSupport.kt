package com.lingji.app.domain.tool

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType

/**
 * 工具层统一错误码。
 *
 * 触发条件与可行动建议见 docs/compose/specs/2026-11-21-agent-tool-layer-fix.md 第三节。
 * 机器可读字段（code / subject_type / suggestion / candidates）让 Agent 能区分
 * 「换个参数重试」与「这条路不通、必须换策略」，避免反复重试同一调用。
 */
object ToolErrorCode {
    const val UNKNOWN_TOOL = "UNKNOWN_TOOL"
    const val MISSING_PARAMETER = "MISSING_PARAMETER"
    const val INVALID_PARAMETER = "INVALID_PARAMETER"
    const val SUBJECT_NOT_FOUND = "SUBJECT_NOT_FOUND"
    const val OBJECT_NOT_FOUND = "OBJECT_NOT_FOUND"
    const val UNSUPPORTED_FOR_SUBJECT_TYPE = "UNSUPPORTED_FOR_SUBJECT_TYPE"
    const val AMBIGUOUS_TITLE = "AMBIGUOUS_TITLE"
    const val SUBJECT_INDEX_OUT_OF_RANGE = "SUBJECT_INDEX_OUT_OF_RANGE"
    const val AMBIGUOUS_MATCH = "AMBIGUOUS_MATCH"
    const val EXECUTION_FAILED = "EXECUTION_FAILED"
}

/**
 * 生成 `Error: {json}`。保留 "Error: " 前缀：AgentService / HomeAgentService 的
 * systemPrompt 依赖该前缀判定失败，禁止声称操作已完成。
 */
fun toolError(code: String, message: String, vararg fields: Pair<String, Any?>): String {
    val obj = JsonObject()
    obj.addProperty("code", code)
    obj.addProperty("message", message)
    for ((key, value) in fields) {
        when (value) {
            null -> obj.add(key, JsonNull.INSTANCE)
            is String -> obj.addProperty(key, value)
            is Number -> obj.addProperty(key, value)
            is Boolean -> obj.addProperty(key, value)
            is JsonObject -> obj.add(key, value)
            is JsonArray -> obj.add(key, value)
            else -> obj.addProperty(key, value.toString())
        }
    }
    return "Error: $obj"
}

fun missingParameter(vararg names: String): String = toolError(
    ToolErrorCode.MISSING_PARAMETER,
    "缺少必填参数：${names.joinToString(" / ")}",
    "parameter" to names.joinToString(" / "),
    "suggestion" to "补齐参数后重试"
)

fun subjectNotFound(subjectId: String): String = toolError(
    ToolErrorCode.SUBJECT_NOT_FOUND,
    "笔记不存在：$subjectId",
    "subject_id" to subjectId,
    "suggestion" to "调用 list_subjects 重新获取 id，或改用 subject_title 按标题定位"
)

fun objectNotFound(kind: String, id: String): String = toolError(
    ToolErrorCode.OBJECT_NOT_FOUND,
    "$kind 不存在：$id",
    "object_type" to kind,
    "object_id" to id,
    "suggestion" to "先调用对应的 list_* 工具重新获取 id"
)

/** 按笔记类型给出真实可用的替代工具，错误文案禁止指向未实现的能力。 */
fun suggestionFor(type: SubjectType): String = when (type) {
    SubjectType.NOTEBOOK -> "这是笔记本笔记，请改用 list_pages / create_page / update_page / delete_page / edit_replace 管理页面内容"
    SubjectType.FRAGMENT -> "这是碎片笔记，请改用 list_fragments / add_fragment / update_fragment / delete_fragment / edit_replace 管理内容"
    SubjectType.TODO -> "这是待办笔记，请改用 list_todos / add_todo / update_todo / delete_todo 管理待办项"
}

/** 类型不匹配的结构化错误：带 subject_type、支持的类型和明确的换工具建议。 */
fun unsupportedForSubjectType(
    subject: Subject,
    operation: String,
    supportedTypes: List<SubjectType>,
    suggestion: String? = suggestionFor(subject.type)
): String = toolError(
    ToolErrorCode.UNSUPPORTED_FOR_SUBJECT_TYPE,
    "笔记「${subject.title}」是 ${subject.type.name} 类型，$operation 不支持该类型；" +
        "仅支持 ${supportedTypes.joinToString(" / ") { it.name }} 类型。",
    "operation" to operation,
    "subject_id" to subject.id,
    "subject_type" to subject.type.name,
    "supported_types" to buildJsonArray { supportedTypes.forEach { +it.name } },
    "suggestion" to suggestion
)

// ───────────────────────── 参数读取 ─────────────────────────
// LLM 可能漏传、传 null、或把数字写成字符串；统一在这里容错，避免工具抛裸异常。

fun JsonObject.paramString(name: String): String? = runCatching {
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()

fun JsonObject.paramInt(name: String): Int? = runCatching {
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt
}.getOrNull()

fun JsonObject.paramBoolean(name: String): Boolean? = runCatching {
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asBoolean
}.getOrNull()

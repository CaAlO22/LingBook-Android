package com.lingji.app.domain.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lingji.app.data.db.dao.SubjectSummaryDao
import com.lingji.app.data.remote.IndexService
import com.lingji.app.data.remote.LLMService
import com.lingji.app.data.repository.SettingsRepository
import com.lingji.app.data.repository.SubjectRepository
import com.lingji.app.domain.tool.fragment.FragmentTools
import com.lingji.app.domain.tool.image.ConversationImageStore
import com.lingji.app.domain.tool.image.ImageTools
import com.lingji.app.domain.tool.note.NoteTools
import com.lingji.app.domain.tool.page.PageTools
import com.lingji.app.domain.tool.search.SearchTools
import com.lingji.app.domain.tool.subject.SubjectTools
import com.lingji.app.domain.tool.todo.TodoTools
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    private val subjectRepository: SubjectRepository,
    llmService: LLMService,
    settingsRepository: SettingsRepository,
    subjectSummaryDao: SubjectSummaryDao,
    indexService: IndexService,
    conversationImageStore: ConversationImageStore
) {
    private val tools: Map<String, Tool> = buildList {
        addAll(SubjectTools.create(subjectRepository, subjectSummaryDao))
        addAll(PageTools.create(subjectRepository))
        addAll(FragmentTools.create(subjectRepository))
        addAll(TodoTools.create(subjectRepository))
        addAll(NoteTools.create(subjectRepository))
        addAll(ImageTools.create(subjectRepository, conversationImageStore))
        addAll(SearchTools.create(subjectRepository, llmService, settingsRepository, subjectSummaryDao, indexService))
    }.associateBy { it.name }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    /**
     * 对外开放 schema 时，为所有声明 subject_id 的工具追加 subject_title 可读定位参数。
     * LLM 复述 36 位 UUID 的逐 token 采样误差是实际失败源；标题是自然语言，
     * 错一两个字还能靠包含匹配救回。重名时返回候选列表，绝不擅自选一个。
     */
    fun toOpenAITools(): JsonArray = JsonArray().apply {
        tools.values.forEach { add(withSubjectTitleAlias(it.toOpenAITool())) }
    }

    suspend fun executeTool(name: String, params: JsonObject): String {
        val tool = tools[name] ?: return toolError(
            ToolErrorCode.UNKNOWN_TOOL,
            "Unknown tool '$name'",
            "tool_name" to name,
            "suggestion" to "从工具列表中选取已存在的工具名"
        )
        return runCatching {
            when (val resolved = resolveSubject(params)) {
                is SubjectResolution.Fail -> resolved.error
                is SubjectResolution.Ok -> tool.execute(resolved.params)
            }
        }.getOrElse {
            toolError(
                ToolErrorCode.EXECUTION_FAILED,
                it.message ?: "Unknown error",
                "tool_name" to name,
                "suggestion" to null
            )
        }
    }

    private sealed interface SubjectResolution {
        data class Ok(val params: JsonObject) : SubjectResolution
        data class Fail(val error: String) : SubjectResolution
    }

    /**
     * 定位优先级：subject_id > (subject_title + subject_index) > subject_title。
     *
     * 标题重名时返回带 0 起步 index 的候选；调用方原样带上 subject_title 再加候选的
     * index 重试即可选定，全程不必复述 36 位 UUID。index 只在当次候选列表内有效，
     * 越界会连同候选一起返回，方便重新选择而不误伤别的笔记。
     */
    private suspend fun resolveSubject(params: JsonObject): SubjectResolution {
        if (!params.paramString("subject_id").isNullOrEmpty()) return SubjectResolution.Ok(params)

        val title = params.paramString("subject_title")
        val index = params.paramInt("subject_index")
        if (params.has("subject_index") && index == null) {
            return SubjectResolution.Fail(
                toolError(
                    ToolErrorCode.INVALID_PARAMETER,
                    "subject_index 必须是 0 起步的整数",
                    "parameter" to "subject_index",
                    "suggestion" to "用候选列表里的 index（0、1…）重新调用，或改用 subject_id"
                )
            )
        }
        if (title == null) {
            return if (index == null) SubjectResolution.Ok(params) else SubjectResolution.Fail(
                toolError(
                    ToolErrorCode.MISSING_PARAMETER,
                    "subject_index 必须和 subject_title 一起使用",
                    "parameter" to "subject_title",
                    "suggestion" to "先用 subject_title 取得候选列表，再用 subject_title + subject_index 重试；或直接传 subject_id"
                )
            )
        }

        val all = subjectRepository.getAllSubjects().first()
        val exact = all.filter { it.title.equals(title, ignoreCase = true) }
        val matches = exact.ifEmpty { all.filter { it.title.contains(title, ignoreCase = true) } }
        val candidates = buildJsonArray {
            matches.forEachIndexed { i, s ->
                +buildJsonObject {
                    "index" to i
                    "id" to s.id
                    "title" to s.title
                    "type" to s.type.name
                }
            }
        }

        if (matches.isEmpty()) {
            return SubjectResolution.Fail(
                toolError(
                    ToolErrorCode.SUBJECT_NOT_FOUND,
                    "没有标题包含「$title」的笔记",
                    "subject_title" to title,
                    "suggestion" to "调用 list_subjects 查看全部笔记标题"
                )
            )
        }
        val selectedIndex = when {
            index != null -> index
            matches.size == 1 -> 0
            else -> return SubjectResolution.Fail(
                toolError(
                    ToolErrorCode.AMBIGUOUS_TITLE,
                    "标题「$title」匹配到 ${matches.size} 篇笔记，无法自动确定，请从候选中选一个",
                    "subject_title" to title,
                    "candidates" to candidates,
                    "suggestion" to "把 candidates 里目标笔记的 index 作为 subject_index，连同原 subject_title 一起重新调用；或用候选的 id 作为 subject_id 重新调用（不要凭空编造 id）"
                )
            )
        }
        if (selectedIndex < 0 || selectedIndex >= matches.size) {
            return SubjectResolution.Fail(
                toolError(
                    ToolErrorCode.SUBJECT_INDEX_OUT_OF_RANGE,
                    "subject_index=$selectedIndex 超出候选范围（有效值 0..${matches.size - 1}）",
                    "subject_title" to title,
                    "subject_index" to selectedIndex,
                    "candidate_count" to matches.size,
                    "candidates" to candidates,
                    "suggestion" to "用 candidates 里实际存在的 index 重新调用；候选顺序可能变化，请按候选内容确认"
                )
            )
        }
        return SubjectResolution.Ok(
            JsonObject().apply {
                params.entrySet().forEach { (key, value) -> add(key, value) }
                addProperty("subject_id", matches[selectedIndex].id)
            }
        )
    }

    private fun withSubjectTitleAlias(schema: JsonObject): JsonObject {
        val originalFunction = schema.getAsJsonObject("function") ?: return schema
        val originalParams = originalFunction.getAsJsonObject("parameters") ?: return schema
        val originalProps = originalParams.getAsJsonObject("properties") ?: return schema
        if (!originalProps.has("subject_id")) return schema

        val copy = schema.deepCopy()
        val function = copy.getAsJsonObject("function")
        val parameters = function.getAsJsonObject("parameters")
        val properties = parameters.getAsJsonObject("properties")
        properties.add("subject_title", buildJsonObject {
            "type" to "string"
            "description" to "笔记标题（可读定位）。与 subject_id 二选一；重名时返回带 index（0、1…）的候选列表，此时原样带上 subject_title 再加 subject_index 重试即可选定，不必复述长 id。"
        })
        properties.add("subject_index", buildJsonObject {
            "type" to "integer"
            "minimum" to 0
            "description" to "候选序号，从 0 开始。仅在标题重名、工具返回 AMBIGUOUS_TITLE 的候选列表后使用；必须与 subject_title 一起传，不能单独使用。"
        })
        // subject_id 不再是硬性必填：与 subject_title 二选一
        parameters.getAsJsonArray("required")?.let { required ->
            val filtered = JsonArray().apply {
                required.forEach { if (it.asString != "subject_id") add(it) }
            }
            if (filtered.size() > 0) parameters.add("required", filtered)
            else parameters.remove("required")
        }
        function.addProperty(
            "description",
            originalFunction.get("description").asString +
                "\n定位：subject_id 与 subject_title 二选一（原本必填 subject_id 的工具仍需提供其一）；" +
                "标题重名会返回带 index 的候选列表，用 subject_title + subject_index 组合重试即可选定，无需抄写 id。"
        )
        return copy
    }
}

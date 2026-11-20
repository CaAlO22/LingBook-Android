package com.lingji.app.domain.tool

import com.google.gson.JsonObject
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.domain.model.Subject

/**
 * 页面的人类可读定位。id 是 36 位 UUID，LLM 逐 token 复现容易出错；
 * page_index（整数）和 page_title（自然语言）作为可选精确匹配入口。
 */
sealed interface PageLookup {
    data class Ok(val page: NotebookPage, val index: Int) : PageLookup
    data class Fail(val error: String) : PageLookup
}

/** 按 page_id > page_index > page_title 的优先级定位；标题歧义时返回候选。 */
fun locatePage(subject: Subject, params: JsonObject): PageLookup {
    val pages = subject.pages ?: emptyList()
    val id = params.paramString("page_id")
    val index = params.paramInt("page_index")
    val title = params.paramString("page_title")

    if (id == null && index == null && title == null) {
        return PageLookup.Fail(missingParameter("page_id", "page_index", "page_title"))
    }
    val candidates: List<NotebookPage> = when {
        id != null -> pages.filter { it.id == id }
        index != null -> {
            if (index < 0 || index >= pages.size) {
                return PageLookup.Fail(
                    toolError(
                        ToolErrorCode.OBJECT_NOT_FOUND,
                        "页面序号 $index 超出范围（当前共 ${pages.size} 页，序号从 0 开始）",
                        "object_type" to "page",
                        "suggestion" to "调用 list_pages 重新获取序号"
                    )
                )
            }
            listOf(pages[index])
        }
        else -> {
            val exact = pages.filter { it.title == title }
            if (exact.isNotEmpty()) exact
            else pages.filter { it.title.contains(title!!, ignoreCase = true) }
        }
    }
    return when {
        candidates.isEmpty() -> PageLookup.Fail(
            toolError(
                ToolErrorCode.OBJECT_NOT_FOUND,
                "页面不存在：${id ?: title ?: "index=$index"}",
                "object_type" to "page",
                "subject_id" to subject.id,
                "suggestion" to "调用 list_pages 查看该笔记本的全部页面"
            )
        )
        candidates.size > 1 -> PageLookup.Fail(
            toolError(
                ToolErrorCode.AMBIGUOUS_MATCH,
                "匹配到 ${candidates.size} 个页面，请改用 page_id 或 page_index 精确指定",
                "candidates" to buildJsonArray {
                    candidates.forEach { page ->
                        +buildJsonObject {
                            "index" to pages.indexOf(page)
                            "id" to page.id
                            "title" to page.title
                        }
                    }
                },
                "suggestion" to "从 candidates 中选定一页，用其 id 或 index 重新调用"
            )
        )
        else -> PageLookup.Ok(candidates.first(), pages.indexOf(candidates.first()))
    }
}

package com.lingji.app.util

/**
 * 极简的 Markdown → HTML 转换器，覆盖标题、列表、引用、代码块、加粗/斜体、
 * 链接、图片、表格与水平线。复杂语义不追求 CommonMark 完备性，足够用于
 * 笔记导出 PDF 场景。
 *
 * 之所以不复用 Markwon：Markwon 输出的是 Android Spannable，无法直接喂给
 * WebView；引入第三方 HTML 渲染器会带来包体增长，所以这里手写一个简化版。
 */
object MarkdownToHtml {

    fun convert(markdown: String): String {
        val collapsed = collapseBlankLines(markdown)
        val lines = collapsed.split('\n')
        val out = StringBuilder()
        var i = 0
        var inUl = false
        var inOl = false

        fun closeLists() {
            if (inUl) { out.append("</ul>\n"); inUl = false }
            if (inOl) { out.append("</ol>\n"); inOl = false }
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd('\r')

            // 围栏代码块
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                closeLists()
                val fence = if (line.trimStart().startsWith("```")) "```" else "~~~"
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                    sb.append(escapeHtml(lines[i])).append('\n')
                    i++
                }
                i++ // skip closing fence
                out.append("<pre><code>").append(sb).append("</code></pre>\n")
                continue
            }

            if (line.isBlank()) {
                closeLists()
                i++
                continue
            }

            // 标题
            val headingMatch = Regex("^(#{1,6})\\s+(.*)$").matchEntire(line)
            if (headingMatch != null) {
                closeLists()
                val level = headingMatch.groupValues[1].length
                out.append("<h$level>")
                    .append(inlineMd(headingMatch.groupValues[2]))
                    .append("</h$level>\n")
                i++
                continue
            }

            // 水平线
            if (Regex("^\\s*([-*_])\\s*\\1\\s*\\1[\\s\\S]*$").matches(line)) {
                closeLists()
                out.append("<hr/>\n")
                i++
                continue
            }

            // 引用
            if (line.trimStart().startsWith("> ")) {
                closeLists()
                val content = line.trimStart().removePrefix("> ")
                out.append("<blockquote>").append(inlineMd(content)).append("</blockquote>\n")
                i++
                continue
            }

            // 无序列表
            val ulMatch = Regex("^\\s*[-*+]\\s+(.*)$").matchEntire(line)
            if (ulMatch != null) {
                if (inOl) { out.append("</ol>\n"); inOl = false }
                if (!inUl) { out.append("<ul>\n"); inUl = true }
                out.append("<li>").append(inlineMd(ulMatch.groupValues[1])).append("</li>\n")
                i++
                continue
            }

            // 有序列表
            val olMatch = Regex("^\\s*\\d+\\.\\s+(.*)$").matchEntire(line)
            if (olMatch != null) {
                if (inUl) { out.append("</ul>\n"); inUl = false }
                if (!inOl) { out.append("<ol>\n"); inOl = true }
                out.append("<li>").append(inlineMd(olMatch.groupValues[1])).append("</li>\n")
                i++
                continue
            }

            // 表格（简化：两行以上以 | 分隔，第二行是分隔符）
            if (line.contains('|') && i + 1 < lines.size &&
                Regex("^\\s*\\|?[\\s\\-:|]+\\|?\\s*$").matches(lines[i + 1])
            ) {
                closeLists()
                val headerCells = splitRow(line)
                out.append("<table>\n<thead><tr>")
                headerCells.forEach { out.append("<th>").append(inlineMd(it)).append("</th>") }
                out.append("</tr></thead>\n<tbody>\n")
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    val cells = splitRow(lines[i])
                    out.append("<tr>")
                    cells.forEach { out.append("<td>").append(inlineMd(it)).append("</td>") }
                    out.append("</tr>\n")
                    i++
                }
                out.append("</tbody></table>\n")
                continue
            }

            // 普通段落（连续非空行合并）
            closeLists()
            val paragraphLines = mutableListOf(line)
            i++
            while (i < lines.size && lines[i].isNotBlank() &&
                !lines[i].trimStart().startsWith("#") &&
                !lines[i].trimStart().startsWith("```") &&
                !lines[i].trimStart().startsWith("~~~") &&
                !lines[i].trimStart().startsWith("> ")
            ) {
                paragraphLines.add(lines[i])
                i++
            }
            out.append("<p>")
                .append(paragraphLines.joinToString("<br/>") { inlineMd(it) })
                .append("</p>\n")
        }
        closeLists()
        return out.toString()
    }

    private fun collapseBlankLines(src: String): String {
        // 与 MarkdownView 一致：编辑里双换行（空一行）折叠为单换行；保护代码块。
        val placeholders = mutableListOf<String>()
        val fenceRegex = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")
        var protectedText = src.replace(fenceRegex) { match ->
            placeholders += match.value
            "\u0001${placeholders.lastIndex}\u0001"
        }
        protectedText = protectedText.replace(Regex("\\n[ \\t]*\\n+"), "\n")
        placeholders.forEachIndexed { i, v ->
            protectedText = protectedText.replace("\u0001${i}\u0001", v)
        }
        return protectedText
    }

    private fun splitRow(line: String): List<String> {
        val trimmed = line.trim().trim('|')
        return trimmed.split('|').map { it.trim() }
    }

    private fun inlineMd(src: String): String {
        var s = escapeHtml(src)
        // 图片
        s = Regex("!\\[(.*?)\\]\\((.*?)\\)").replace(s) { m ->
            "<img alt=\"${m.groupValues[1]}\" src=\"${m.groupValues[2]}\"/>"
        }
        // 链接
        s = Regex("\\[(.*?)\\]\\((.*?)\\)").replace(s) { m ->
            "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>"
        }
        // 行内代码
        s = Regex("`([^`]+)`").replace(s) { m -> "<code>${m.groupValues[1]}</code>" }
        // 加粗
        s = Regex("\\*\\*(.+?)\\*\\*").replace(s) { m -> "<strong>${m.groupValues[1]}</strong>" }
        s = Regex("__(.+?)__").replace(s) { m -> "<strong>${m.groupValues[1]}</strong>" }
        // 斜体
        s = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").replace(s) { m -> "<em>${m.groupValues[1]}</em>" }
        s = Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)").replace(s) { m -> "<em>${m.groupValues[1]}</em>" }
        // 删除线
        s = Regex("~~(.+?)~~").replace(s) { m -> "<del>${m.groupValues[1]}</del>" }
        return s
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}

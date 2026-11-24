package com.lingji.app.ui.components

import android.view.Gravity
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.data.DataUriSchemeHandler
import io.noties.markwon.inlineparser.MarkwonInlineParser
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

/**
 * 将编辑器中的"双换行"折叠为"硬换行"，使预览效果符合用户直觉：
 * - 编辑里单换行 → 预览不换行（CommonMark 默认软换行行为）
 * - 编辑里双换行（空一行） → 预览单换行（不再产生空段落）
 *
 * 折叠时会保护围栏代码块，避免破坏代码块内部的空行；
 * 同时保留 GFM 表格块前后的空行，否则表格无法成块解析（会退化成普通管道符文本）。
 */
internal fun foldBlankLines(markdown: String): String {
    if (markdown.isEmpty()) return markdown
    val placeholders = mutableListOf<String>()
    val fenceRegex = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")
    var protectedText = markdown.replace(fenceRegex) { match ->
        placeholders += match.value
        "\u0001${placeholders.lastIndex}\u0001"
    }
    protectedText = foldBlankRuns(protectedText)
    placeholders.forEachIndexed { i, v ->
        protectedText = protectedText.replace("\u0001${i}\u0001", v)
    }
    return protectedText
}

/** GFM 表格分隔行，如 `|---|:--:|`。 */
private val tableDelimiterRow = Regex("^[ \\t]*\\|?[ \\t]*:?-+:?[ \\t]*(\\|[ \\t]*:?-+:?[ \\t]*)*\\|?[ \\t]*$")

/** 标记 GFM 表格块（表头行 + 分隔行 + 后续非空行）。 */
private fun markTableLines(lines: List<String>): BooleanArray {
    val inTable = BooleanArray(lines.size)
    var i = 0
    while (i < lines.size) {
        val isHeader = i + 1 < lines.size &&
            lines[i].contains('|') &&
            tableDelimiterRow.matches(lines[i + 1])
        if (!isHeader) {
            i++
            continue
        }
        var end = i
        while (end < lines.size && lines[end].isNotBlank()) {
            inTable[end] = true
            end++
        }
        i = end
    }
    return inTable
}

/** 折叠连续空行；表格块前后的空行保留为真正的空行。 */
private fun foldBlankRuns(text: String): String {
    val lines = text.split('\n')
    val inTable = markTableLines(lines)
    val out = StringBuilder()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.isNotBlank()) {
            out.append(line)
            if (i + 1 >= lines.size) {
                i++
                continue
            }
            if (lines[i + 1].isNotBlank()) {
                out.append('\n')
                i++
                continue
            }
            var end = i + 1
            while (end < lines.size && lines[end].isBlank()) end++
            if (end >= lines.size) {
                // 末尾空行按原样保留
                repeat(lines.size - 1 - i) { out.append('\n') }
                i = lines.size
            } else {
                out.append(if (inTable[i] || inTable[end]) "\n\n" else "  \n")
                i = end
            }
        } else {
            // 文档开头的空行按原样保留
            out.append(line).append('\n')
            i++
        }
    }
    return out.toString()
}

@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    textSizeSp: Float? = null,
    compact: Boolean = false,
    alignEnd: Boolean = false,
    onTextViewReady: ((TextView) -> Unit)? = null
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        val inlineParserBuilder = MarkwonInlineParser.factoryBuilderNoDefaults()
        inlineParserBuilder.addInlineProcessor(BackslashLatexInlineProcessor())
        inlineParserBuilder.includeDefaults()
        inlineParserBuilder.addInlineProcessor(DollarLatexInlineProcessor())

        Markwon.builder(context)
            .usePlugin(
                ImagesPlugin.create { plugin ->
                    plugin.addSchemeHandler(DataUriSchemeHandler.create())
                }
            )
            .usePlugin(TablePlugin.create(context))
            .usePlugin(MarkwonInlineParserPlugin.create(inlineParserBuilder))
            .usePlugin(JLatexMathPlugin.create(48f) { it.inlinesEnabled(true) })
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }
    val rendered = if (compact) foldBlankLines(markdown) else markdown
    AndroidView(
        factory = { ctx -> TextView(ctx) },
        update = { textView ->
            markwon.setMarkdown(textView, rendered)
            if (textColor != null) textView.setTextColor(textColor.toArgb())
            if (textSizeSp != null) textView.textSize = textSizeSp
            textView.gravity = if (alignEnd) Gravity.END else Gravity.START
            onTextViewReady?.invoke(textView)
        },
        modifier = modifier
    )
}

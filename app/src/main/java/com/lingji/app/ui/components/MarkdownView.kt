package com.lingji.app.ui.components

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
 * 折叠时会保护围栏代码块，避免破坏代码块内部的空行。
 */
internal fun foldBlankLines(markdown: String): String {
    if (markdown.isEmpty()) return markdown
    val placeholders = mutableListOf<String>()
    val fenceRegex = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")
    var protectedText = markdown.replace(fenceRegex) { match ->
        placeholders += match.value
        "\u0001${placeholders.lastIndex}\u0001"
    }
    protectedText = protectedText.replace(Regex("\\n[ \\t]*\\n+"), "  \n")
    placeholders.forEachIndexed { i, v ->
        protectedText = protectedText.replace("\u0001${i}\u0001", v)
    }
    return protectedText
}

@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    textSizeSp: Float? = null,
    compact: Boolean = false
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
        },
        modifier = modifier
    )
}

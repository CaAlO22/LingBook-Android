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

@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    textSizeSp: Float? = null,
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
    AndroidView(
        factory = { ctx -> TextView(ctx) },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
            if (textColor != null) textView.setTextColor(textColor.toArgb())
            if (textSizeSp != null) textView.textSize = textSizeSp
            textView.gravity = if (alignEnd) Gravity.END else Gravity.START
            onTextViewReady?.invoke(textView)
        },
        modifier = modifier
    )
}

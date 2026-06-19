package com.lingji.app.ui.components

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.data.DataUriSchemeHandler

@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                val markwon = Markwon.builder(context)
                    .usePlugin(
                        ImagesPlugin.create { plugin ->
                            plugin.addSchemeHandler(DataUriSchemeHandler.create())
                        }
                    )
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(StrikethroughPlugin.create())
                    .build()
                markwon.setMarkdown(this, markdown)
            }
        },
        update = { textView ->
            val markwon = Markwon.builder(textView.context)
                .usePlugin(
                    ImagesPlugin.create { plugin ->
                        plugin.addSchemeHandler(DataUriSchemeHandler.create())
                    }
                )
                .usePlugin(TablePlugin.create(textView.context))
                .usePlugin(StrikethroughPlugin.create())
                .build()
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier
    )
}

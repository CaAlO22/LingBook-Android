package com.lingji.app.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 将 Markdown 字符串渲染到隐藏 WebView 后，通过系统 [PrintManager] 打印为 PDF。
 *
 * 不引入第三方 PDF 库；中文/图片/代码块均交由 WebView 排版与系统打印框架处理，
 * 用户在系统打印对话框中选择「另存为 PDF」即可保存。
 */
object MarkdownPdfExporter {

    data class Section(val title: String, val markdown: String)

    fun exportToPdf(
        context: Context,
        title: String,
        markdown: String,
        onError: (Throwable) -> Unit = {}
    ) {
        exportSectionsToPdf(
            context = context,
            docTitle = title,
            sections = listOf(Section(title, markdown)),
            onError = onError
        )
    }

    /**
     * 导出多页 Markdown 为单个 PDF：每个 [Section] 在 PDF 中占据独立的一页起点。
     */
    fun exportSectionsToPdf(
        context: Context,
        docTitle: String,
        sections: List<Section>,
        onError: (Throwable) -> Unit = {}
    ) {
        try {
            val html = buildHtml(docTitle, sections)
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val jobName = docTitle.ifBlank { "LingBook" }
                        val adapter = view.createPrintDocumentAdapter(jobName)
                        val attrs = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        printManager.print(jobName, adapter, attrs)
                    }
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    private fun buildHtml(docTitle: String, sections: List<Section>): String {
        val safeDocTitle = escapeHtml(docTitle.ifBlank { "LingBook" })
        val bodyBuilder = StringBuilder()
        sections.forEachIndexed { idx, section ->
            val sectionTitle = escapeHtml(section.title.ifBlank { "Page ${idx + 1}" })
            val sectionBody = MarkdownToHtml.convert(section.markdown)
            val pageBreakClass = if (idx == 0) "section" else "section page-break"
            bodyBuilder.append("<section class=\"").append(pageBreakClass).append("\">\n")
                .append("<h1>").append(sectionTitle).append("</h1>\n")
                .append(sectionBody)
                .append("</section>\n")
        }
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8" />
            <title>$safeDocTitle</title>
            <style>
            body { font-family: serif; line-height: 1.6; color: #222; padding: 16px; }
            h1, h2, h3, h4 { color: #111; line-height: 1.3; }
            pre { background: #f5f5f5; padding: 10px; border-radius: 6px; overflow-wrap: break-word; white-space: pre-wrap; }
            code { font-family: monospace; background: #f5f5f5; padding: 1px 4px; border-radius: 3px; }
            blockquote { border-left: 4px solid #ccc; padding-left: 10px; color: #555; margin-left: 0; }
            img { max-width: 100%; height: auto; }
            table { border-collapse: collapse; width: 100%; }
            table, th, td { border: 1px solid #ddd; padding: 6px 8px; }
            .section { }
            .page-break { page-break-before: always; break-before: page; }
            </style>
            </head>
            <body>
            $bodyBuilder
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}

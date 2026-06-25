package com.lingji.app.util

import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebSettings
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
        forcePrintWhite: Boolean = false,
        onError: (Throwable) -> Unit = {}
    ) {
        exportSectionsToPdf(
            context = context,
            docTitle = title,
            sections = listOf(Section(title, markdown)),
            forcePrintWhite = forcePrintWhite,
            onError = onError
        )
    }

    /**
     * 导出多页 Markdown 为单个 PDF：每个 [Section] 在 PDF 中占据独立的一页起点。
     *
     * @param forcePrintWhite 是否强制白底浅色渲染。开启后：
     *  - WebView 关闭系统强制暗色策略（避免暗色模式下颜色被反相）；
     *  - 注入 ``color-scheme: light only`` 与显式背景/前景色 CSS；
     *  - 图片与代码块去除半透明背景，便于纸张打印。
     */
    fun exportSectionsToPdf(
        context: Context,
        docTitle: String,
        sections: List<Section>,
        forcePrintWhite: Boolean = false,
        onError: (Throwable) -> Unit = {}
    ) {
        try {
            val html = buildHtml(docTitle, sections, forcePrintWhite)
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadsImagesAutomatically = true
                setBackgroundColor(android.graphics.Color.WHITE)
                if (forcePrintWhite) {
                    applyForceLightWebSettings(settings)
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            "(window.MathJax && MathJax.startup ? MathJax.startup.promise : Promise.resolve()).then(function(){ return true; })"
                        ) {
                            printWebView(context, docTitle, view)
                        }
                    }
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    private fun printWebView(context: Context, docTitle: String, view: WebView) {
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

    @Suppress("DEPRECATION")
    private fun applyForceLightWebSettings(settings: WebSettings) {
        // Android 10+ 在系统暗色模式下会主动给 WebView 内容反相；导出 PDF 时显式关闭，
        // 否则用户在暗色系统下导出的 PDF 会变成浅字深底，纸张打印体验很差。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                settings.forceDark = WebSettings.FORCE_DARK_OFF
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildHtml(
        docTitle: String,
        sections: List<Section>,
        forcePrintWhite: Boolean
    ): String {
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
        val printWhiteCss = if (forcePrintWhite) """
            :root { color-scheme: light only; }
            html, body { background: #ffffff !important; color: #000000 !important; }
            * { background-color: transparent !important; color: inherit !important; border-color: #888 !important; }
            h1, h2, h3, h4, h5, h6, p, li, td, th, blockquote, code, pre { color: #000000 !important; }
            pre, code { background: #f5f5f5 !important; color: #000000 !important; }
            blockquote { color: #333333 !important; border-left-color: #888 !important; }
            img { background: #ffffff !important; }
            table, th, td { border-color: #888 !important; }
            @media print {
                html, body { background: #ffffff !important; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            }
        """.trimIndent() else ""
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8" />
            <meta name="color-scheme" content="${if (forcePrintWhite) "light only" else "light dark"}" />
            <title>$safeDocTitle</title>
            <script>
            MathJax = {
                tex: {
                    inlineMath: [['$', '$'], ['\\\\(', '\\\\)']],
                    displayMath: [['$$', '$$'], ['\\\\[', '\\\\]']]
                },
                svg: { fontCache: 'global' }
            };
            </script>
            <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js"></script>
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
            $printWhiteCss
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

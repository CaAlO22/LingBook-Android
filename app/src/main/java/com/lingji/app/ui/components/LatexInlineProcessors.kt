package com.lingji.app.ui.components

import io.noties.markwon.ext.latex.JLatexMathNode
import io.noties.markwon.inlineparser.InlineProcessor
import org.commonmark.node.Node
import java.util.regex.Pattern

internal class DollarLatexInlineProcessor : InlineProcessor() {
    override fun specialCharacter() = '$'
    override fun parse(): Node? {
        val matched = match(RE) ?: return null
        val content = matched.substring(1, matched.length - 1)
        return JLatexMathNode().apply { latex(content) }
    }
    companion object {
        private val RE = Pattern.compile("\\$(?!\\$)([^\\n$]+?)\\$")
    }
}

internal class BackslashLatexInlineProcessor : InlineProcessor() {
    override fun specialCharacter() = '\\'
    override fun parse(): Node? {
        val matched = match(RE) ?: return null
        val content = matched.substring(2, matched.length - 2)
        return JLatexMathNode().apply { latex(content) }
    }
    companion object {
        private val RE = Pattern.compile("\\\\\\(([^\\n]+?)\\\\\\)")
    }
}

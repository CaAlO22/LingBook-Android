package com.lingji.app.ui.components

import io.noties.markwon.ext.latex.JLatexMathNode
import io.noties.markwon.inlineparser.InlineProcessor
import org.commonmark.node.Node
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 行内数学公式处理器。
 *
 * 注意：不能使用父类的 `match(Pattern)`——它内部用 `Matcher.find()`（非锚定），
 * 匹配成功后直接把解析位置跳到匹配末尾，当前位置与匹配起点之间的文本会被静默丢弃
 * （表现为预览丢段落、整篇被吞进一个公式）。这里统一用 `Matcher.lookingAt()`
 * 严格从当前位置开始匹配。
 */
internal class DollarLatexInlineProcessor : InlineProcessor() {

    override fun specialCharacter() = '$'

    override fun parse(): Node? {
        // 优先匹配 $$...$$（允许跨行），其次匹配单行 $...$
        matchAt(BLOCK_RE)?.let { matched ->
            return JLatexMathNode().apply { latex(matched.substring(2, matched.length - 2)) }
        }
        matchAt(INLINE_RE)?.let { matched ->
            return JLatexMathNode().apply { latex(matched.substring(1, matched.length - 1)) }
        }
        // 未匹配到公式时也要消费当前 '$'，否则会落到 Markwon 自带的非锚定 `$$` 处理器上，
        // 触发同样的“向后跳匹配、丢弃中间文本”问题。
        index += 1
        return text("$")
    }

    private fun matchAt(pattern: Pattern): String? {
        val matcher: Matcher = pattern.matcher(input)
        matcher.region(index, input.length)
        if (!matcher.lookingAt()) return null
        index = matcher.end()
        return matcher.group()
    }

    companion object {
        private val BLOCK_RE = Pattern.compile("\\$\\$([\\s\\S]+?)\\$\\$")
        private val INLINE_RE = Pattern.compile("\\$(?!\\$)([^\\n$]+?)\\$")
    }
}

internal class BackslashLatexInlineProcessor : InlineProcessor() {

    override fun specialCharacter() = '\\'

    override fun parse(): Node? {
        val matcher = RE.matcher(input)
        matcher.region(index, input.length)
        if (!matcher.lookingAt()) return null
        index = matcher.end()
        return JLatexMathNode().apply { latex(matcher.group(1)) }
    }

    companion object {
        private val RE = Pattern.compile("\\\\\\(([^\\n]+?)\\\\\\)")
    }
}

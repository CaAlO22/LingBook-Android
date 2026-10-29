package com.lingji.app.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方向键光标行为的纯逻辑测试。
 *
 * 上下移动依赖 TextLayoutResult（Android 运行时对象），这里只覆盖不依赖布局的分支：
 * 左右按字符移动、代理对不劈开、边界不越界、Shift 扩展/收缩选区、无布局时上下退化为首尾。
 */
class ArrowKeyCursorBehaviorTest {

    private fun valueOf(text: String, start: Int, end: Int = start) =
        TextFieldValue(text = text, selection = TextRange(start, end))

    private fun move(
        value: TextFieldValue,
        direction: CursorDirection,
        byWord: Boolean = false
    ): Int = resolveCursorTarget(value = value, direction = direction, byWord = byWord, layout = null)

    @Test
    fun left_right_moves_by_one_character() {
        val value = valueOf("abcd", start = 2)
        assertEquals(1, move(value, CursorDirection.LEFT))
        assertEquals(3, move(value, CursorDirection.RIGHT))
    }

    @Test
    fun left_right_does_not_go_out_of_bounds() {
        assertEquals(0, move(valueOf("abc", start = 0), CursorDirection.LEFT))
        assertEquals(3, move(valueOf("abc", start = 3), CursorDirection.RIGHT))
    }

    @Test
    fun left_right_skips_whole_surrogate_pair() {
        // "a😀b"：0='a'，1..2 是表情的两个 UTF-16 单元，3='b'，4=结尾
        val text = "a😀b"
        assertEquals(1, move(valueOf(text, start = 3), CursorDirection.LEFT))
        assertEquals(3, move(valueOf(text, start = 1), CursorDirection.RIGHT))
    }

    @Test
    fun up_down_without_layout_falls_back_to_text_edges() {
        val value = valueOf("line1\nline2", start = 8)
        assertEquals(0, move(value, CursorDirection.UP))
        assertEquals(value.text.length, move(value, CursorDirection.DOWN))
    }

    @Test
    fun shift_extends_selection_from_original_anchor() {
        val collapsed = valueOf("abcd", start = 2)
        val extended = resolveSelection(
            selection = collapsed.selection,
            target = 3,
            extend = true
        )
        assertEquals(TextRange(2, 3), extended)

        // 已有选区时向左移动应收缩右端，而不是整体平移
        val selected = valueOf("abcd", start = 1, end = 3)
        val shrunk = resolveSelection(
            selection = selected.selection,
            target = 2,
            extend = true
        )
        assertEquals(TextRange(1, 2), shrunk)
    }

    @Test
    fun plain_arrow_collapses_selection() {
        val selected = valueOf("abcd", start = 1, end = 3)
        val collapsed = resolveSelection(
            selection = selected.selection,
            target = 3,
            extend = false
        )
        assertTrue(collapsed.collapsed)
        assertEquals(3, collapsed.start)
    }

    @Test
    fun direction_mapping_covers_four_arrow_keys_and_ignores_others() {
        assertEquals(CursorDirection.LEFT, Key.DirectionLeft.toCursorDirection())
        assertEquals(CursorDirection.RIGHT, Key.DirectionRight.toCursorDirection())
        assertEquals(CursorDirection.UP, Key.DirectionUp.toCursorDirection())
        assertEquals(CursorDirection.DOWN, Key.DirectionDown.toCursorDirection())
        assertNull(Key.Enter.toCursorDirection())
    }
}

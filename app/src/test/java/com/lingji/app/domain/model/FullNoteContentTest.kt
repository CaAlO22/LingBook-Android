package com.lingji.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FullNoteContentTest {

    @Test
    fun notebook_returnsJoinedPages_notAggregatedNote() {
        val subject = Subject(
            id = "s1",
            title = "我的笔记本",
            type = SubjectType.NOTEBOOK,
            // NOTEBOOK 类型的 aggregatedNote 只是初始欢迎语，不应被当作正文
            aggregatedNote = "欢迎来到您的新笔记本。",
            pages = listOf(
                NotebookPage(id = "p1", title = "第一章", content = "第一章的内容"),
                NotebookPage(id = "p2", title = "第二章", content = "第二章的内容")
            )
        )
        val expected = "## 第一章\n第一章的内容\n\n## 第二章\n第二章的内容"
        assertEquals(expected, subject.fullNoteContent())
    }

    @Test
    fun notebook_untitledPageHasNoTitleHeader() {
        val subject = Subject(
            id = "s1",
            title = "笔记本",
            type = SubjectType.NOTEBOOK,
            pages = listOf(
                NotebookPage(id = "p1", title = "", content = "无标题页内容")
            )
        )
        assertEquals("无标题页内容", subject.fullNoteContent())
    }

    @Test
    fun notebook_noPages_returnsEmpty() {
        val subject = Subject(
            id = "s1",
            title = "空笔记本",
            type = SubjectType.NOTEBOOK,
            aggregatedNote = "欢迎语",
            pages = emptyList()
        )
        assertEquals("", subject.fullNoteContent())
    }

    @Test
    fun fragment_returnsAggregatedNote() {
        val subject = Subject(
            id = "s2",
            title = "碎片笔记",
            type = SubjectType.FRAGMENT,
            aggregatedNote = "# 整理后的笔记\n正文"
        )
        assertEquals("# 整理后的笔记\n正文", subject.fullNoteContent())
    }

    @Test
    fun fragment_emptyAggregatedNote_fallsBackToFragments() {
        val subject = Subject(
            id = "s3",
            title = "未整理碎片",
            type = SubjectType.FRAGMENT,
            aggregatedNote = "",
            fragments = listOf(
                Fragment(id = "f1", content = "碎片一"),
                Fragment(id = "f2", content = "碎片二")
            )
        )
        assertEquals("碎片一\n碎片二", subject.fullNoteContent())
    }
}

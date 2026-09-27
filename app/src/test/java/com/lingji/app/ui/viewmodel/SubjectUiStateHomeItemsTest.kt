package com.lingji.app.ui.viewmodel

import com.lingji.app.domain.model.Folder
import com.lingji.app.domain.model.HomeItem
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页展示项（homeItems）的回归测试。
 *
 * 关键场景：跨设备导入的笔记会带着导出端的 folderId，
 * 而本机并不存在该文件夹。此时笔记必须回落到首页展示，
 * 否则会"既不在文件夹里、也不在首页"，表现为导入成功后笔记消失。
 */
class SubjectUiStateHomeItemsTest {

    @Test
    fun `note with dangling folderId falls back to home`() {
        val state = SubjectUiState(
            subjects = listOf(
                Subject(id = "s1", title = "英语文章阅读", type = SubjectType.NOTEBOOK, folderId = "missing")
            ),
            folders = emptyList()
        )

        val noteItems = state.homeItems.filterIsInstance<HomeItem.NoteItem>()
        assertEquals(1, noteItems.size)
        assertEquals("英语文章阅读", noteItems.first().subject.title)
    }

    @Test
    fun `note with existing folderId stays inside folder`() {
        val state = SubjectUiState(
            subjects = listOf(
                Subject(id = "s1", title = "已归档笔记", type = SubjectType.NOTEBOOK, folderId = "f1")
            ),
            folders = listOf(Folder(id = "f1", name = "英语"))
        )

        assertEquals(1, state.homeItems.filterIsInstance<HomeItem.FolderItem>().size)
        assertTrue(state.homeItems.filterIsInstance<HomeItem.NoteItem>().isEmpty())
    }

    @Test
    fun `unfiled note and orphan note are both shown on home`() {
        val state = SubjectUiState(
            subjects = listOf(
                Subject(id = "s1", title = "无归属", type = SubjectType.NOTEBOOK, folderId = null),
                Subject(id = "s2", title = "悬空归属", type = SubjectType.NOTEBOOK, folderId = "missing")
            ),
            folders = listOf(Folder(id = "f1", name = "英语"))
        )

        val titles = state.homeItems.filterIsInstance<HomeItem.NoteItem>().map { it.subject.title }
        assertEquals(setOf("无归属", "悬空归属"), titles.toSet())
    }
}

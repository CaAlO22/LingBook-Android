package com.lingji.app.ui.screens.notebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingji.app.domain.model.HorizontalSwipeAction
import com.lingji.app.domain.model.NotebookPage
import com.lingji.app.ui.components.NotebookPageEditor
import com.lingji.app.ui.components.NotebookPageEditorHostState

@Composable
fun NotebookEditorArea(
    page: NotebookPage,
    pages: List<NotebookPage>,
    currentPageIndex: Int,
    swipeAction: HorizontalSwipeAction,
    editorHostState: NotebookPageEditorHostState,
    lastCreatedPageId: String?,
    onUpdate: (NotebookPage) -> Unit,
    onPageChange: (String) -> Unit
) {
    when (swipeAction) {
        HorizontalSwipeAction.TOGGLE_PREVIEW -> {
            val pagerState = rememberPagerState(
                initialPage = if (editorHostState.isPreview) 1 else 0,
                pageCount = { 2 }
            )
            LaunchedEffect(pagerState.currentPage) {
                editorHostState.setPreview(pagerState.currentPage == 1)
            }
            LaunchedEffect(editorHostState.isPreview) {
                val target = if (editorHostState.isPreview) 1 else 0
                if (pagerState.currentPage != target) {
                    pagerState.animateScrollToPage(target)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { pageIndex ->
                if (editorHostState.isPreview != (pageIndex == 1)) {
                    return@HorizontalPager
                }
                NotebookPageEditor(
                    page = page,
                    onUpdate = onUpdate,
                    onFocus = { },
                    autoFocusContent = page.id == lastCreatedPageId,
                    fillHeight = true,
                    hostState = editorHostState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                )
            }
        }
        HorizontalSwipeAction.CHANGE_PAGE -> {
            val pagerState = rememberPagerState(
                initialPage = currentPageIndex.coerceAtLeast(0),
                pageCount = { pages.size }
            )
            LaunchedEffect(pagerState.currentPage) {
                pages.getOrNull(pagerState.currentPage)?.let { p ->
                    onPageChange(p.id)
                }
            }
            LaunchedEffect(currentPageIndex) {
                if (currentPageIndex in pages.indices &&
                    pagerState.currentPage != currentPageIndex
                ) {
                    pagerState.animateScrollToPage(currentPageIndex)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { pageIndex ->
                val displayPage = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                NotebookPageEditor(
                    page = displayPage,
                    onUpdate = onUpdate,
                    onFocus = { },
                    autoFocusContent = displayPage.id == lastCreatedPageId,
                    fillHeight = true,
                    hostState = editorHostState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                )
            }
        }
        HorizontalSwipeAction.NONE -> {
            NotebookPageEditor(
                page = page,
                onUpdate = onUpdate,
                onFocus = { },
                autoFocusContent = page.id == lastCreatedPageId,
                fillHeight = true,
                hostState = editorHostState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            )
        }
    }
}

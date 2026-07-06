package com.lingji.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lingji.app.ui.components.AiRunningIsland
import com.lingji.app.ui.screens.FolderScreen
import com.lingji.app.ui.screens.FragmentSubjectScreen
import com.lingji.app.ui.viewmodel.UpdateViewModel
import com.lingji.app.ui.screens.NotebookSubjectScreen
import com.lingji.app.ui.screens.SettingsScreen
import com.lingji.app.ui.screens.SubjectGalleryScreen
import com.lingji.app.ui.viewmodel.SubjectViewModel

@Composable
fun LingjiNavigation(updateViewModel: UpdateViewModel) {
    val navController = rememberNavController()
    val viewModel: SubjectViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "gallery",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("gallery") {
                SubjectGalleryScreen(
                    viewModel = viewModel,
                    onSubjectClick = { id ->
                        viewModel.setCurrentSubject(id)
                        navController.navigate("subject/$id")
                    },
                    onFolderClick = { folderId ->
                        navController.navigate("folder/$folderId")
                    },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }
            composable(
                "subject/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val subjectId = backStackEntry.arguments?.getString("id") ?: return@composable
                val subject = viewModel.uiState.value.subjects.find { it.id == subjectId }
                    ?: return@composable
                when (subject.type) {
                    com.lingji.app.domain.model.SubjectType.NOTEBOOK ->
                        NotebookSubjectScreen(
                            viewModel = viewModel,
                            subject = subject,
                            onBack = { navController.popBackStack() }
                        )
                    else ->
                        FragmentSubjectScreen(
                            viewModel = viewModel,
                            subject = subject,
                            onBack = { navController.popBackStack() }
                        )
                }
            }
            composable(
                "folder/{folderId}",
                arguments = listOf(navArgument("folderId") { type = NavType.StringType })
            ) { backStackEntry ->
                val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
                FolderScreen(
                    viewModel = viewModel,
                    folderId = folderId,
                    onSubjectClick = { id ->
                        viewModel.setCurrentSubject(id)
                        navController.navigate("subject/$id")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    updateViewModel = updateViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        AiRunningIsland(
            visible = uiState.isProcessing,
            title = uiState.processingMessage ?: "AI 运行中…",
            lines = uiState.aiIslandLines,
            onStop = { viewModel.stopAiProcessing() },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

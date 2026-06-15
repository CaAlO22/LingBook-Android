package com.lingji.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lingji.app.ui.screens.FragmentSubjectScreen
import com.lingji.app.ui.screens.NotebookSubjectScreen
import com.lingji.app.ui.screens.SettingsScreen
import com.lingji.app.ui.screens.SubjectGalleryScreen
import com.lingji.app.ui.viewmodel.SubjectViewModel

@Composable
fun LingjiNavigation() {
    val navController = rememberNavController()
    val viewModel: SubjectViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "gallery") {
        composable("gallery") {
            SubjectGalleryScreen(
                viewModel = viewModel,
                onSubjectClick = { id ->
                    viewModel.setCurrentSubject(id)
                    navController.navigate("subject/$id")
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
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

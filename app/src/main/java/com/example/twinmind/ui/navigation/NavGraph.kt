package com.example.twinmind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.twinmind.ui.dashboard.DashboardScreen
import com.example.twinmind.ui.recording.RecordingScreen
import com.example.twinmind.ui.summary.SummaryScreen

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Recording : Screen("recording/{sessionId}") {
        fun createRoute(sessionId: Long) = "recording/$sessionId"
    }
    data object Summary : Screen("summary/{sessionId}") {
        fun createRoute(sessionId: Long) = "summary/$sessionId"
    }
}

@Composable
fun TwinMindNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToRecording = { sessionId ->
                    navController.navigate(Screen.Recording.createRoute(sessionId))
                },
                onNavigateToSummary = { sessionId ->
                    navController.navigate(Screen.Summary.createRoute(sessionId))
                }
            )
        }

        composable(
            route = Screen.Recording.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            RecordingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSummary = { sessionId ->
                    navController.navigate(Screen.Summary.createRoute(sessionId)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(
            route = Screen.Summary.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            SummaryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

package com.idiots3.riskscanner.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.idiots3.riskscanner.ui.screens.AppDetailScreen
import com.idiots3.riskscanner.ui.screens.DashboardScreen
import com.idiots3.riskscanner.ui.screens.AppListScreen
import com.idiots3.riskscanner.ui.screens.ScoringRubricScreen
import com.idiots3.riskscanner.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object AppList : Screen("app_list")
    object ScoringRubric : Screen("scoring_rubric")
    object AppDetail : Screen("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToAppList = { navController.navigate(Screen.AppList.route) },
                onNavigateToAppDetail = { packageName ->
                    navController.navigate(Screen.AppDetail.createRoute(packageName))
                },
                onNavigateToRubric = { navController.navigate(Screen.ScoringRubric.route) }
            )
        }
        composable(Screen.AppList.route) {
            AppListScreen(
                viewModel = viewModel,
                onNavigateToAppDetail = { packageName ->
                    navController.navigate(Screen.AppDetail.createRoute(packageName))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ScoringRubric.route) {
            ScoringRubricScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.AppDetail.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppDetailScreen(
                packageName = packageName,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

package com.idiots3.riskscanner.navigation

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.idiots3.riskscanner.ui.screens.AppDetailScreen
import com.idiots3.riskscanner.ui.screens.AppListScreen
import com.idiots3.riskscanner.ui.screens.DashboardScreen
import com.idiots3.riskscanner.ui.screens.DeviceHubScreen
import com.idiots3.riskscanner.ui.screens.ScoringRubricScreen
import com.idiots3.riskscanner.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Overview : Screen("overview")
    object Apps : Screen("apps")
    object Device : Screen("device")
    object ScoringRubric : Screen("scoring_rubric")
    object AppDetail : Screen("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/${Uri.encode(packageName)}"
    }
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_LEVEL_TABS = listOf(
    TopLevelTab(Screen.Overview.route, "Overview", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TopLevelTab(Screen.Apps.route, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    TopLevelTab(Screen.Device.route, "Device", Icons.Filled.Shield, Icons.Outlined.Shield)
)

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TOP_LEVEL_TABS.any { it.route == currentRoute }

    fun switchToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TOP_LEVEL_TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { switchToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
            modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Overview.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToApps = { switchToTab(Screen.Apps.route) },
                    onNavigateToDeviceHub = { switchToTab(Screen.Device.route) },
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    },
                    onNavigateToRubric = { navController.navigate(Screen.ScoringRubric.route) }
                )
            }

            composable(Screen.Apps.route) {
                AppListScreen(
                    viewModel = viewModel,
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }

            composable(Screen.Device.route) {
                DeviceHubScreen(
                    viewModel = viewModel,
                    onNavigateToAppsWithSpecialAccess = { switchToTab(Screen.Apps.route) },
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
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
            ) { backStack ->
                val encoded = backStack.arguments?.getString("packageName") ?: ""
                val packageName = Uri.decode(encoded)
                AppDetailScreen(
                    packageName = packageName,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

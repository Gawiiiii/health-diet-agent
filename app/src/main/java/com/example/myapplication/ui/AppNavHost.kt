package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.screens.CaptureScreen
import com.example.myapplication.ui.screens.HistoryScreen
import com.example.myapplication.ui.screens.ResultScreen
import com.example.myapplication.ui.screens.SettingsScreen
import com.example.myapplication.viewmodel.AppViewModelFactory
import com.example.myapplication.viewmodel.CaptureViewModel
import com.example.myapplication.viewmodel.HistoryViewModel
import com.example.myapplication.viewmodel.ResultViewModel
import com.example.myapplication.viewmodel.SettingsViewModel

private object Routes {
    const val Capture = "capture"
    const val History = "history"
    const val Settings = "settings"
    const val Result = "result"
}

@Composable
fun AppNavHost(
    viewModelFactory: AppViewModelFactory,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.Capture,
        modifier = modifier
    ) {
        composable(Routes.Capture) {
            val viewModel: CaptureViewModel = viewModel(factory = viewModelFactory)
            CaptureScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate(Routes.History) },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenResult = { id -> navController.navigate("${Routes.Result}/$id") }
            )
        }
        composable(Routes.History) {
            val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenResult = { id -> navController.navigate("${Routes.Result}/$id") }
            )
        }
        composable(Routes.Settings) {
            val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "${Routes.Result}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val viewModel: ResultViewModel = viewModel(factory = viewModelFactory)
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            ResultScreen(
                viewModel = viewModel,
                historyId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

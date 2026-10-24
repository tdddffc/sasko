package com.weatherfocus.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.weatherfocus.app.ui.WeatherViewModel
import com.weatherfocus.app.ui.month.MonthScreen
import com.weatherfocus.app.ui.settings.SettingsScreen
import com.weatherfocus.app.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val MONTH = "month"
    const val SETTINGS = "settings"
    const val RADAR = "radar"
}

@Composable
fun WeatherNavGraph(viewModel: WeatherViewModel, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.TODAY) {
        composable(Routes.TODAY) {
            TodayScreen(
                viewModel = viewModel,
                onOpenMonth = { navController.navigate(Routes.MONTH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenRadar = { navController.navigate(Routes.RADAR) }
            )
        }
        composable(Routes.MONTH) {
            MonthScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.RADAR) {
            com.weatherfocus.app.ui.today.RadarScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}

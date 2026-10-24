package com.weatherfocus.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.weatherfocus.app.ui.WeatherViewModel
import com.weatherfocus.app.ui.nav.WeatherNavGraph
import com.weatherfocus.app.ui.theme.WeatherOnlyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()

            // Android 13+ requires this runtime permission or notifications are silently dropped,
            // even if the manifest declares it and the channel/settings are all correctly configured.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* result not needed here; Settings screen shows current status */ }
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            WeatherOnlyTheme(themeMode = state.settings.themeMode) {
                WeatherNavGraph(viewModel = viewModel)
            }
        }
    }
}

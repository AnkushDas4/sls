package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ChatScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    enableEdgeToEdge()
    setContent {
      val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
      val viewModel: ChatViewModel = viewModel(
          factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
      )
      val settings by viewModel.settings.collectAsState()
      val isDarkTheme = when (settings.theme) {
          Theme.LIGHT -> false
          Theme.DARK -> true
          Theme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
      }
      MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = settings.enableDynamicColor) {
        com.example.ui.ErrorBoundary {
          var currentScreen by remember { mutableStateOf("chat") }
          val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()

          LaunchedEffect(isFirstLaunch) {
              if (isFirstLaunch) {
                  currentScreen = "settings"
              }
          }

          when (currentScreen) {
              "chat" -> ChatScreen(
                  viewModel = viewModel,
                  onNavigateToSettings = { currentScreen = "settings" }
              )
              "settings" -> SettingsScreen(
                  viewModel = viewModel,
                  onNavigateBack = { currentScreen = "chat" },
                  onNavigateToModelResources = { currentScreen = "model_resources" }
              )
              "model_resources" -> com.example.ui.ModelResourcesScreen(
                  onNavigateBack = { currentScreen = "settings" }
              )
          }
        }
      }
    }
  }
}

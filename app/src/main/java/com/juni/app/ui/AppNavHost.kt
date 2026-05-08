package com.juni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.juni.app.ui.camera.CameraScreen
import com.juni.app.ui.chat.ChatScreen
import com.juni.app.ui.preview.PreviewScreen
import com.juni.app.ui.settings.SettingsScreen
import com.juni.app.ui.vault.VaultDebugScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val VAULT = "vault"
    const val CHAT = "chat"
    const val CAMERA = "camera"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                PreviewScreen(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenVault = { nav.navigate(Routes.VAULT) },
                    onOpenChat = { nav.navigate(Routes.CHAT) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.VAULT) {
                VaultDebugScreen(
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(
                    onBack = { nav.popBackStack() },
                    onOpenCamera = { nav.navigate(Routes.CAMERA) },
                )
            }
            composable(Routes.CAMERA) {
                CameraScreen(
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

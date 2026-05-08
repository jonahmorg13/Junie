package com.juni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.juni.app.ui.camera.CameraScreen
import com.juni.app.ui.chat.ChatScreen
import com.juni.app.ui.conversations.ConversationsScreen
import com.juni.app.ui.settings.SettingsScreen

object Routes {
    const val CONVERSATIONS = "conversations"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"

    private const val CHAT_PREFIX = "chat"
    fun chat(conversationId: String) = "$CHAT_PREFIX/$conversationId"
    const val CHAT_PATTERN = "$CHAT_PREFIX/{conversationId}"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        NavHost(navController = nav, startDestination = Routes.CONVERSATIONS) {
            composable(Routes.CONVERSATIONS) {
                ConversationsScreen(
                    onOpenConversation = { id -> nav.navigate(Routes.chat(id)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(
                route = Routes.CHAT_PATTERN,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
            ) {
                ChatScreen(
                    onBack = { nav.popBackStack() },
                    onOpenCamera = { nav.navigate(Routes.CAMERA) },
                )
            }
            composable(Routes.CAMERA) {
                CameraScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

package com.juni.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.juni.app.ui.preview.PreviewScreen

/**
 * Phase-1/2 placeholder. Real Navigation Compose graph (Conversations,
 * Chat, Camera, Settings) lands once those screens exist.
 */
@Composable
fun AppNavHost() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        PreviewScreen()
    }
}

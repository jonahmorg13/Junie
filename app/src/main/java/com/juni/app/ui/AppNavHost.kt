package com.juni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juni.app.ui.theme.TermAccent

/**
 * Phase 1 placeholder. Real Navigation Compose graph lands once we have
 * Conversations, Chat, Camera, and Settings screens.
 */
@Composable
fun AppNavHost() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "juni · ready",
            style = TextStyle(
                color = TermAccent,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
            ),
        )
    }
}

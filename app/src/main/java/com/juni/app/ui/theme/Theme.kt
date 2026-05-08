package com.juni.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Terminal palette — referenced via CompositionLocal in later tasks.
 * Phase 1 just sets the window background; full primitives land in task #2.
 */
val TermBg = Color(0xFF0E0E10)
val TermFg = Color(0xFFC8C8C2)
val TermAccent = Color(0xFFE97C3C)
val TermMuted = Color(0xFF5A5A5A)
val TermGreen = Color(0xFF8FBF7F)
val TermRed = Color(0xFFCF6A6A)

@Composable
fun JuniTheme(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg),
    ) {
        content()
    }
}

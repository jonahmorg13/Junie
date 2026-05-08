package com.juni.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.juni.app.R

val TermBg = Color(0xFF0E0E10)
/** Slightly lifted surface for floating elements (toasts, dialogs) — pops against TermBg. */
val TermSurface = Color(0xFF1F1F25)
val TermFg = Color(0xFFC8C8C2)
val TermDim = Color(0xFF888884)
val TermAccent = Color(0xFFE97C3C)
val TermMuted = Color(0xFF3A3A3D)
val TermGreen = Color(0xFF8FBF7F)
val TermRed = Color(0xFFCF6A6A)
val TermSelection = Color(0xFF3A2A1A)

val TermFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

object TermType {
    val body = TextStyle(
        fontFamily = TermFont,
        fontSize = 14.sp,
        color = TermFg,
    )
    val small = TextStyle(
        fontFamily = TermFont,
        fontSize = 12.sp,
        color = TermFg,
    )
    val header = TextStyle(
        fontFamily = TermFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TermAccent,
    )
}

@Composable
fun JuniTheme(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg),
    ) {
        content()
    }
}

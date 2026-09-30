package com.lingualoop.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepBlue = Color(0xFF123B63)
val SoftBlue = Color(0xFFE0EDF9)
val SuccessGreen = Color(0xFF1C6B2C)
val ErrorRed = Color(0xFFA61C1C)
val WarnAmber = Color(0xFF7C4A03)

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF5C86AB),
    background = Color(0xFFF7F9FB),
    surface = Color.White,
    onSurface = Color(0xFF1F2933),
    error = ErrorRed,
)

@Composable
fun LinguaLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}

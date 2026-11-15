package com.routemate.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF14263D)
val Blue = Color(0xFF3478F6)
val Canvas = Color(0xFFF5F7FA)
val Ink = Color(0xFF1F2937)
val Muted = Color(0xFF64748B)
val Green = Color(0xFF22A06B)
val Orange = Color(0xFFF59E0B)
private val Colors = lightColorScheme(primary = Blue, onPrimary = Color.White, secondary = Navy, background = Canvas, surface = Color.White, onSurface = Ink, onBackground = Ink, outline = Color(0xFFDCE3ED), surfaceVariant = Color(0xFFEEF3FA), onSurfaceVariant = Muted)
@Composable fun RouteMateTheme(content: @Composable () -> Unit) {
    // Intentional light-only dashboard in MVP, respecting Android font scaling.
    MaterialTheme(colorScheme = Colors, typography = Typography(
        headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    ), content = content)
}

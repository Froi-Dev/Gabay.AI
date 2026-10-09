package com.example.gabayai.ui

import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val GabayBlue = Color(0xFF0038A8)
val GabayRed = Color(0xFFCE1126)
val GabayYellow = Color(0xFFFCD116)
val Ink = Color(0xFF0F172A)
val Muted = Color(0xFF475569)
val Guidance = Color(0xFFEFF4FC)

private val Colors = lightColorScheme(
    primary = GabayBlue, onPrimary = Color.White,
    secondary = GabayBlue, onSecondary = Color.White,
    tertiary = GabayYellow, onTertiary = Ink,
    background = Color(0xFFF8FAFC), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Guidance, onSurfaceVariant = Muted,
    outline = Color(0xFFD7DFEA), error = GabayRed, onError = Color.White,
)

private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
)

@Composable
fun GabayAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp)),
        content = content)
}

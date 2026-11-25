package com.slncloth.shop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun SlnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val context = LocalContext.current
    M3Theme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onSurface,
            ) {
                content()
            }
        },
    )
}

private val Typography = androidx.compose.material3.Typography(
    displayLarge = androidx.compose.material3.TextStyle(
        fontFamily = androidx.compose.ui.text.FontFamily.Default,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleMedium = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = androidx.compose.material3.TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = androidx.compose.material3.TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = androidx.compose.material3.TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = androidx.compose.material3.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

private val Shapes = androidx.compose.material3.Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

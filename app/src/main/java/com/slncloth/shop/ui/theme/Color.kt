package com.slncloth.shop.ui.theme

import androidx.compose.material3.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.platform.LocalContext
import com.slncloth.shop.R

private val tealPrimary = Color(0xFF00695C)
private val tealPrimaryDark = Color(0xFF004D40)
private val tealContainer = Color(0xFFB2DFDB)
private val shopBackground = Color(0xFFF7FBF9)

val LightColorScheme = ColorScheme(
    primary = tealPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = tealContainer,
    onPrimaryContainer = Color(0xFF001F1B),
    secondary = Color(0xFF00695C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCF0EC),
    onSecondaryContainer = Color(0xFF003733),
    tertiary = Color(0xFF5B5F6E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7DBE4),
    onTertiaryContainer = Color(0xFF20232C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410002),
    background = shopBackground,
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE7EEEC),
    onSurfaceVariant = Color(0xFF44494B),
    outline = Color(0xFF74777A),
    outlineVariant = Color(0xFFC4CDCE),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF303336),
    inverseOnSurface = Color(0xFFF6F7F8),
    inversePrimary = Color(0xFF86E7DD),
    surfaceDisabled = Color(0xFFFAFAFA),
    onSurfaceDisabled = Color(0xFF7F8488),
)

val DarkColorScheme = ColorScheme(
    primary = Color(0xFF86E7DD),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFF86E7DD),
    secondary = Color(0xFF86E7DD),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF006356),
    onSecondaryContainer = Color(0xFF86E7DD),
    tertiary = Color(0xFFB9BEC9),
    onTertiary = Color(0xFF32353E),
    tertiaryContainer = Color(0xFF484B54),
    onTertiaryContainer = Color(0xFFD8DAE2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF930000),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6EA),
    surface = Color(0xFF1B1B1D),
    onSurface = Color(0xFFE6E6EA),
    surfaceVariant = Color(0xFF44494B),
    onSurfaceVariant = Color(0xFFC4CDCE),
    outline = Color(0xFF8E9395),
    outlineVariant = Color(0xFF44494B),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E6EA),
    inverseOnSurface = Color(0xFF2F3134),
    inversePrimary = Color(0xFF00695C),
    surfaceDisabled = Color(0xFF121212),
    onSurfaceDisabled = Color(0xFF474B4E),
)

fun darkColorScheme() = dynamicDarkColorScheme(darkColors = DarkColorScheme)
fun lightColorScheme() = dynamicLightColorScheme(lightColors = LightColorScheme)

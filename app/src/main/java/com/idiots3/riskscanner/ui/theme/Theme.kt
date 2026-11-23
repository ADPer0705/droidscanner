package com.idiots3.riskscanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFF6EE7B7),
    background = Color(0xFF070C10),
    surface = Color(0xFF0E161C),
    surfaceVariant = Color(0xFF17242C),
    primaryContainer = Color(0xFF0E3A2F),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondaryContainer = Color(0xFF0D3340),
    onSecondaryContainer = Color(0xFFA5F3FC),
    onPrimary = Color(0xFF032118),
    onSecondary = Color(0xFF04222A),
    onTertiary = Color(0xFF032118),
    onBackground = Color(0xFFE2ECEF),
    onSurface = Color(0xFFE2ECEF),
    onSurfaceVariant = Color(0xFF93A8B2),
    outline = Color(0xFF5D7480),
    outlineVariant = Color(0xFF23343E),
    error = Color(0xFFFF6B6B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF047857),
    secondary = Color(0xFF0E7490),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFF4F8F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8F0EE),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F1C21),
    onSurface = Color(0xFF0F1C21),
    onSurfaceVariant = Color(0xFF475B63),
    outline = Color(0xFF6B8088),
    outlineVariant = Color(0xFFCDE0DB),
    error = Color(0xFFD32F2F)
)

@Composable
fun RiskAuditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

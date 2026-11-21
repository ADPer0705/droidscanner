package com.idiots3.riskscanner.ui.theme

import androidx.compose.ui.graphics.Color

/** Accessible risk-level palette calibrated for both light and dark Material 3 surfaces. */
object RiskColors {
    // Light theme foregrounds + tinted backgrounds
    val Critical = Color(0xFFD32F2F)
    val CriticalBackground = Color(0x1FD32F2F)
    val High = Color(0xFFE65100)
    val HighBackground = Color(0x1FE65100)
    val Medium = Color(0xFF9E7000)
    val MediumBackground = Color(0x24F9A825)
    val Low = Color(0xFF2E7D32)
    val LowBackground = Color(0x1F2E7D32)

    // Dark theme foregrounds + tinted backgrounds
    val CriticalDark = Color(0xFFFF5252)
    val CriticalBackgroundDark = Color(0x29FF5252)
    val HighDark = Color(0xFFFFAB40)
    val HighBackgroundDark = Color(0x29FFAB40)
    val MediumDark = Color(0xFFFFD54F)
    val MediumBackgroundDark = Color(0x29FFD54F)
    val LowDark = Color(0xFF69F0AE)
    val LowBackgroundDark = Color(0x2469F0AE)
}

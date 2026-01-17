package com.altron.alertbuddy.ui.theme

import androidx.compose.ui.graphics.Color

// Base colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
// Badge colors
val BadgeBackground = Color(0xFFD32F2F)
val BadgeText = Color(0xFFFFFFFF)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Alert Buddy Color Palette
object AlertBuddyColors {
    // Primary colors
    val Primary = Color(0xFF1976D2)
    val PrimaryDark = Color(0xFF0D47A1)
    val PrimaryLight = Color(0xFF42A5F5)

    // Severity colors
    val Critical = Color(0xFFD32F2F)
    val CriticalDark = Color(0xFFB71C1C)
    val Warning = Color(0xFFF57C00)
    val WarningDark = Color(0xFFE65100)
    val Info = Color(0xFF1976D2)
    val InfoDark = Color(0xFF0D47A1)

    // Background colors
    val Background = Color(0xFFF5F5F5)
    val BackgroundDark = Color(0xFF121212)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF1E1E1E)
    val CardBackground = Color(0xFFFFFFFF)
    val CardBackgroundDark = Color(0xFF2D2D2D)

    // Text colors
    val TextPrimary = Color(0xFF212121)
    val TextPrimaryDark = Color(0xFFE0E0E0)
    val TextSecondary = Color(0xFF757575)
    val TextSecondaryDark = Color(0xFFB0B0B0)

    // Status colors
    val Success = Color(0xFF4CAF50)
    val Error = Color(0xFFD32F2F)
    val Acknowledged = Color(0xFF4CAF50)
    val Unacknowledged = Color(0xFFD32F2F)


    // Other
    val Divider = Color(0xFFE0E0E0)
    val DividerDark = Color(0xFF424242)

    val BadgeBackground = Color(0xFFD32F2F)
    val BadgeText = Color(0xFFFFFFFF)
}

// Severity enum and helper function
enum class Severity {
    CRITICAL, WARNING, INFO
}

fun getSeverityColor(severity: String): Color {
    return when (severity.uppercase()) {
        "CRITICAL" -> AlertBuddyColors.Critical
        "WARNING" -> AlertBuddyColors.Warning
        "INFO" -> AlertBuddyColors.Info
        else -> AlertBuddyColors.Info
    }
}

fun getSeverityBackgroundColor(severity: String): Color {
    return when (severity.uppercase()) {
        "CRITICAL" -> AlertBuddyColors.Critical.copy(alpha = 0.1f)
        "WARNING" -> AlertBuddyColors.Warning.copy(alpha = 0.1f)
        "INFO" -> AlertBuddyColors.Info.copy(alpha = 0.1f)
        else -> AlertBuddyColors.Info.copy(alpha = 0.1f)
    }
}
package com.altron.alertbuddy.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ============================================================================
 * Theme.kt - App Theme Configuration with Dark Mode Support
 * ============================================================================
 *
 * PURPOSE:
 * Defines the color schemes and theme configuration for Alert Buddy.
 * Supports both light and dark themes with manual override capability.
 *
 * FEATURES:
 * - Light and dark color schemes
 * - Manual theme override (System/Light/Dark)
 * - Severity colors for alert badges
 * - Status bar color adaptation
 *
 * THEME MODES:
 * - SYSTEM: Follows device dark mode setting (default)
 * - LIGHT: Always light theme
 * - DARK: Always dark theme
 *
 * USAGE:
 * Wrap your app content with AlertBuddyTheme:
 * ```
 * AlertBuddyTheme {
 *     // Your app content
 * }
 * ```
 *
 * ============================================================================
 */

// ============================================================================
// THEME MODE ENUM
// ============================================================================

/**
 * Available theme modes for the app.
 */
enum class ThemeMode {
    SYSTEM,  // Follow system dark mode setting
    LIGHT,   // Always use light theme
    DARK     // Always use dark theme
}

// ============================================================================
// THEME PREFERENCES
// ============================================================================

/**
 * Manages theme preference storage and retrieval.
 */
object ThemePreferences {
    private const val PREFS_NAME = "alert_buddy_theme"
    private const val KEY_THEME_MODE = "theme_mode"

    /**
     * Get the current theme mode from SharedPreferences.
     */
    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeString = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeString ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    /**
     * Save the theme mode to SharedPreferences.
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}

// ============================================================================
// COMPOSITION LOCAL FOR THEME STATE
// ============================================================================

/**
 * CompositionLocal to provide current dark theme state throughout the app.
 */
val LocalIsDarkTheme = compositionLocalOf { false }

/**
 * CompositionLocal to provide theme mode throughout the app.
 */
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }

// ============================================================================
// COLOR SCHEMES
// Note: AlertBuddyColors is defined in Color.kt
// ============================================================================

private val LightColorScheme = lightColorScheme(
    primary = AlertBuddyColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AlertBuddyColors.Primary,
    onPrimaryContainer = Color.White,
    background = AlertBuddyColors.BackgroundLight,
    onBackground = AlertBuddyColors.OnBackgroundLight,
    surface = AlertBuddyColors.SurfaceLight,
    onSurface = AlertBuddyColors.OnSurfaceLight,
    surfaceVariant = AlertBuddyColors.SurfaceVariantLight,
    onSurfaceVariant = AlertBuddyColors.OnSurfaceSecondaryLight,
    error = AlertBuddyColors.Error,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = AlertBuddyColors.PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = AlertBuddyColors.PrimaryDark,
    onPrimaryContainer = Color.White,
    background = AlertBuddyColors.BackgroundDark,
    onBackground = AlertBuddyColors.OnBackgroundDark,
    surface = AlertBuddyColors.SurfaceDark,
    onSurface = AlertBuddyColors.OnSurfaceDark,
    surfaceVariant = AlertBuddyColors.SurfaceVariantDark,
    onSurfaceVariant = AlertBuddyColors.OnSurfaceSecondaryDark,
    error = AlertBuddyColors.Error,
    onError = Color.White
)

// ============================================================================
// MAIN THEME COMPOSABLE
// ============================================================================

/**
 * Alert Buddy theme wrapper with dark mode support.
 *
 * @param themeMode The theme mode to use (SYSTEM, LIGHT, or DARK)
 * @param content The content to display with this theme
 */
@Composable
fun AlertBuddyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()

    // Determine if dark theme should be used
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.statusBarColor = colorScheme.background.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalThemeMode provides themeMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// ============================================================================
// TYPOGRAPHY
// ============================================================================

/**
 * App typography configuration using Material3 defaults.
 */
val AppTypography = Typography()

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get severity color for alert badges.
 *
 * @param severity The severity level of the alert
 * @return The appropriate color for the severity
 */
@Suppress("unused")
fun getSeverityColor(severity: com.altron.alertbuddy.data.Severity): Color {
    return when (severity) {
        com.altron.alertbuddy.data.Severity.CRITICAL -> AlertBuddyColors.Critical
        com.altron.alertbuddy.data.Severity.WARNING -> AlertBuddyColors.Warning
        com.altron.alertbuddy.data.Severity.INFO -> AlertBuddyColors.Info
    }
}

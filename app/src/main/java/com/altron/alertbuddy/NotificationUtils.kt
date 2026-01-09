package com.altron.alertbuddy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Utility object responsible for managing
 * Android notification channels.
 *
 * Why this exists:
 * - Android 8.0+ REQUIRES notification channels
 * - Channels define importance, sound, vibration
 * - Centralising this avoids duplication
 *
 * This is intentionally simple for Phase 1 testing.
 */
object NotificationUtils {

    /**
     * Channel ID used across the app for alert notifications.
     * In later phases, this can be expanded to multiple channels
     * (e.g. Critical, Warning, Info).
     */
    const val ALERT_CHANNEL_ID = "alert_channel"

    /**
     * Creates the notification channel if it does not exist.
     * Safe to call multiple times.
     */
    fun createAlertChannel(context: Context) {
        // Notification channels are only required on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Monitoring Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical monitoring and system alerts"
                enableVibration(true)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }
}
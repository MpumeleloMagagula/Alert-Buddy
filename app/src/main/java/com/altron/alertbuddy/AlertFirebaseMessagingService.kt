package com.altron.alertbuddy

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FirebaseMessagingService responsible for receiving
 * alert messages from Firebase Cloud Messaging (FCM).
 *
 * This service runs even when:
 * - The app is in the background
 * - The app is killed
 * - The device is idle (Doze mode)
 *
 * Phase 1 responsibility:
 * - Log incoming messages
 * - Show a local notification for visual confirmation
 *
 * Future phases:
 * - Persist messages in Room DB
 * - Trigger foreground alert service
 * - Start repeating alarm beeps
 */
class AlertFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Log raw payload for debugging and verification
        Log.d("FCM", "Message received: ${message.data}")

        // Extract expected fields from data payload
        val title = message.data["title"] ?: "Alert"
        val body = message.data["message"] ?: "New alert received"

        // Ensure notification channel exists before showing notification
        NotificationUtils.createAlertChannel(this)

        // Intent that opens the app when notification is tapped
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(
            this,
            NotificationUtils.ALERT_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Display the notification
        NotificationManagerCompat.from(this).notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }

    /**
     * Called whenever Firebase issues a new registration token.
     * This can happen on:
     * - First install
     * - App reinstallation
     * - Token refresh
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
    }
}
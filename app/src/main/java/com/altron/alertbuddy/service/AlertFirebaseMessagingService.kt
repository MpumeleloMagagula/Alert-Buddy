package com.altron.alertbuddy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.altron.alertbuddy.MainActivity
import com.altron.alertbuddy.data.AlertBuddyDatabase
import com.altron.alertbuddy.data.Message
import com.altron.alertbuddy.data.Severity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class AlertFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AlertFCM"
        private const val CHANNEL_ID_CRITICAL = "alert_buddy_critical"
        private const val CHANNEL_ID_WARNING = "alert_buddy_warning"
        private const val CHANNEL_ID_INFO = "alert_buddy_info"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
        // TODO: Send token to your server for targeting this device
        // You would typically call your backend API here to register the token
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if message contains data payload
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val channelId = data["channel"] ?: return
        val channelName = data["channelName"] ?: channelId
        val title = data["title"] ?: "New Alert"
        val messageBody = data["message"] ?: ""
        val severityStr = data["severity"] ?: "info"
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        val metadata = data["metadata"]

        val severity = when (severityStr.lowercase()) {
            "critical" -> Severity.CRITICAL
            "warning" -> Severity.WARNING
            else -> Severity.INFO
        }

        // Create message object
        val message = Message(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            channelName = channelName,
            title = title,
            message = messageBody,
            severity = severity,
            timestamp = timestamp,
            isRead = false,
            metadata = metadata
        )

        // Save to database
        serviceScope.launch {
            try {
                val database = AlertBuddyDatabase.getDatabase(applicationContext)
                database.messageDao().insertMessage(message)
                Log.d(TAG, "Message saved to database: ${message.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save message", e)
            }
        }

        // Show notification
        showNotification(message)
    }

    private fun showNotification(message: Message) {
        val notificationChannelId = when (message.severity) {
            Severity.CRITICAL -> CHANNEL_ID_CRITICAL
            Severity.WARNING -> CHANNEL_ID_WARNING
            Severity.INFO -> CHANNEL_ID_INFO
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("messageId", message.id)
            putExtra("channelId", message.channelId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            message.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Replace with your app icon
            .setContentTitle(message.title)
            .setContentText(message.message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.message))
            .setPriority(
                when (message.severity) {
                    Severity.CRITICAL -> NotificationCompat.PRIORITY_MAX
                    Severity.WARNING -> NotificationCompat.PRIORITY_HIGH
                    Severity.INFO -> NotificationCompat.PRIORITY_DEFAULT
                }
            )

        // Add sound for critical alerts
        if (message.severity == Severity.CRITICAL) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            notificationBuilder.setSound(alarmSound)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(message.id.hashCode(), notificationBuilder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Critical channel - high priority with alarm sound
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical system alerts that require immediate attention"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }

            // Warning channel
            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                "Warning Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warning alerts that may need attention"
                enableVibration(true)
            }

            // Info channel
            val infoChannel = NotificationChannel(
                CHANNEL_ID_INFO,
                "Info Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Informational alerts"
            }

            notificationManager.createNotificationChannel(criticalChannel)
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(infoChannel)
        }
    }
}

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
import com.altron.alertbuddy.R
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.data.Message
import com.altron.alertbuddy.data.Severity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Firebase Cloud Messaging Service.
 * Handles incoming push notifications from FCM and stores them in the local database.
 * Also triggers the AlertService to start beeping for unread alerts.
 */
class AlertFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AlertFCMService"
        private const val CHANNEL_ID = "alert_buddy_notifications"
        private const val CHANNEL_NAME = "Alert Notifications"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: AlertRepository

    override fun onCreate() {
        super.onCreate()
        repository = AlertRepository(applicationContext)
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        sendTokenToServer(token)
    }

    /**
     * Called when a message is received from FCM.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if user is logged in before processing
        val prefs = getSharedPreferences("alert_buddy_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        if (!isLoggedIn) {
            Log.d(TAG, "User not logged in, ignoring message")
            return
        }

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Handle notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message notification body: ${it.body}")
            showNotification(it.title ?: "Alert", it.body ?: "")
        }
    }

    /**
     * Handle the data payload from FCM message.
     */
    private fun handleDataMessage(data: Map<String, String>) {
        serviceScope.launch {
            try {
                val messageId = data["messageId"] ?: UUID.randomUUID().toString()
                val channelId = data["channelId"] ?: "default"
                val channelName = data["channelName"] ?: "Alerts"
                val title = data["title"] ?: "New Alert"
                val messageText = data["message"] ?: data["body"] ?: ""
                val severityStr = data["severity"] ?: "INFO"
                val metadata = data["metadata"]

                // Parse severity
                val severity = try {
                    Severity.valueOf(severityStr.uppercase())
                } catch (_: Exception) {
                    Severity.INFO
                }

                // Create message entity
                val message = Message(
                    id = messageId,
                    channelId = channelId,
                    channelName = channelName,
                    title = title,
                    message = messageText,
                    severity = severity,
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    metadata = metadata
                )

                // Store in database
                repository.insertMessage(message)
                Log.d(TAG, "Message stored: $messageId")

                // Show notification
                showAlertNotification(message)

                // Start AlertService to begin beeping
                AlertService.startService(applicationContext)

            } catch (e: Exception) {
                Log.e(TAG, "Error handling data message", e)
            }
        }
    }

    /**
     * Show a notification for a new alert.
     */
    private fun showAlertNotification(message: Message) {
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

        // Choose notification color based on severity
        val color = when (message.severity) {
            Severity.CRITICAL -> 0xFFDC2626.toInt()
            Severity.WARNING -> 0xFFF59E0B.toInt()
            Severity.INFO -> 0xFF3B82F6.toInt()
        }

        // Use alarm sound for critical alerts
        val soundUri = if (message.severity == Severity.CRITICAL) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)  // Use app launcher icon
            .setContentTitle("[${message.severity}] ${message.title}")
            .setContentText(message.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.message))
            .setPriority(
                if (message.severity == Severity.CRITICAL)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setColor(color)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(message.id.hashCode(), notificationBuilder.build())
    }

    /**
     * Show a simple notification.
     */
    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)  // Use app launcher icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    /**
     * Create notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Critical infrastructure alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Send FCM token to backend server.
     */
    private fun sendTokenToServer(token: String) {
        Log.d(TAG, "TODO: Send FCM token to server: $token")
    }
}
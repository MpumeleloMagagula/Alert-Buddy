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

/**
 * Firebase Cloud Messaging Service for Alert Buddy.
 *
 * This service handles incoming push notifications from FCM in two ways:
 * 1. DATA payloads - Custom alert data from your backend server (full control)
 * 2. NOTIFICATION payloads - Test messages from Firebase Console (simple display)
 *
 * IMPORTANT: FCM behavior differs based on app state:
 * - App in FOREGROUND: Both payload types delivered to onMessageReceived()
 * - App in BACKGROUND: Notification payloads handled by system automatically,
 *                      only data payloads delivered to onMessageReceived()
 *
 * When alerts are received, they are:
 * - Stored in the local Room database (data payloads only)
 * - Displayed as system notifications
 * - Trigger the AlertService for persistent beeping (critical alerts)
 */
class AlertFirebaseMessagingService : FirebaseMessagingService() {

    // Coroutine scope for background database operations
    // SupervisorJob ensures one coroutine failure doesn't cancel others
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // Tag for Logcat filtering - use "AlertFCM" in Logcat to see these logs
        private const val TAG = "AlertFCM"

        // Notification channel IDs - each severity level has its own channel
        // Users can customize notification settings per channel in Android Settings
        private const val CHANNEL_ID_CRITICAL = "alert_buddy_critical"
        private const val CHANNEL_ID_WARNING = "alert_buddy_warning"
        private const val CHANNEL_ID_INFO = "alert_buddy_info"
    }

    /**
     * Called when the service is first created.
     * We create notification channels here so they're ready before any message arrives.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== AlertFirebaseMessagingService CREATED ===")
        createNotificationChannels()
    }

    /**
     * Called when a new FCM registration token is generated.
     *
     * This happens when:
     * - App is installed for the first time
     * - User clears app data
     * - App is restored on a new device
     * - Previous token expires or is invalidated
     *
     * @param token The new FCM token - send this to your backend to target this device
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "New FCM token generated: $token")
        Log.d(TAG, "========================================")
        // TODO: Send this token to your backend server
        // Your backend needs this token to send push notifications to this specific device
        // Example: ApiClient.registerDeviceToken(token)
    }

    /**
     * Called when a message is received from FCM.
     *
     * This method handles TWO types of payloads:
     *
     * 1. DATA payload (remoteMessage.data):
     *    - Contains custom key-value pairs from your backend
     *    - Always delivered here regardless of app state
     *    - You have full control over how to display the notification
     *    - Used for production alerts with severity, channel, metadata, etc.
     *
     * 2. NOTIFICATION payload (remoteMessage.notification):
     *    - Contains title and body fields
     *    - Only delivered here when app is in FOREGROUND
     *    - When app is in background, Android system shows it automatically
     *    - Used for testing from Firebase Console
     *
     * @param remoteMessage The message received from FCM
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Log detailed info for debugging
        Log.d(TAG, "===========================================")
        Log.d(TAG, "onMessageReceived CALLED")
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Data size: ${remoteMessage.data.size}")
        Log.d(TAG, "Has notification: ${remoteMessage.notification != null}")
        Log.d(TAG, "===========================================")

        // STEP 1: Handle DATA payload (from your backend server)
        // Data payloads contain structured alert information
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Processing DATA payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // STEP 2: Handle NOTIFICATION payload (from Firebase Console tests)
        // This ensures test notifications from Firebase Console are displayed
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Processing NOTIFICATION payload: ${notification.title} - ${notification.body}")
            showSimpleNotification(
                title = notification.title ?: "Alert Buddy",
                body = notification.body ?: "New notification received"
            )
        }
    }

    /**
     * Process a DATA payload from FCM.
     *
     * Your backend should send messages with this structure:
     * {
     *   "channel": "infinity-dal-ms",           // Required: Channel ID
     *   "channelName": "Infinity DAL MS",       // Optional: Display name
     *   "title": "CPU Alert",                   // Alert title
     *   "message": "Server CPU at 95%",         // Alert body
     *   "severity": "critical|warning|info",    // Determines notification style
     *   "timestamp": "1234567890",              // Unix timestamp in milliseconds
     *   "metadata": "{\"server\": \"prod-01\"}" // Optional: JSON metadata
     * }
     *
     * @param data Map of key-value pairs from the FCM data payload
     */
    private fun handleDataMessage(data: Map<String, String>) {
        // Channel ID is required - if not provided, we can't categorize the alert
        val channelId = data["channel"]
        if (channelId == null) {
            Log.w(TAG, "Data message missing 'channel' field - ignoring")
            return
        }

        // Extract all fields with sensible defaults
        val channelName = data["channelName"] ?: channelId
        val title = data["title"] ?: "New Alert"
        val messageBody = data["message"] ?: ""
        val severityStr = data["severity"] ?: "info"
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        val metadata = data["metadata"]

        // Parse severity level - determines notification priority and sound
        val severity = when (severityStr.lowercase()) {
            "critical" -> Severity.CRITICAL  // Red, alarm sound, persistent beeping
            "warning" -> Severity.WARNING    // Orange, high priority
            else -> Severity.INFO            // Blue, standard notification
        }

        // Create message entity for Room database storage
        val message = Message(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            channelName = channelName,
            title = title,
            message = messageBody,
            severity = severity,
            timestamp = timestamp,
            isRead = false,  // New messages are always unread
            metadata = metadata
        )

        // Save to database in background (non-blocking)
        serviceScope.launch {
            try {
                val database = AlertBuddyDatabase.getDatabase(applicationContext)
                database.messageDao().insertMessage(message)
                Log.d(TAG, "Message saved to database: ${message.id}")

                // Start AlertService for persistent beeping
                // This ensures critical alerts aren't missed even if notification is swiped
                AlertService.startService(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save message to database", e)
            }
        }

        // Show notification immediately (don't wait for database save)
        showNotification(message)
    }

    /**
     * Display a full notification for a stored alert message.
     *
     * Notification appearance varies by severity:
     * - CRITICAL: Alarm sound, max priority, strong vibration pattern
     * - WARNING: High priority, default vibration
     * - INFO: Default priority and sound
     *
     * @param message The Message entity to display as a notification
     */
    private fun showNotification(message: Message) {
        // Select notification channel based on severity
        // Each channel can have different user-configured settings
        val notificationChannelId = when (message.severity) {
            Severity.CRITICAL -> CHANNEL_ID_CRITICAL
            Severity.WARNING -> CHANNEL_ID_WARNING
            Severity.INFO -> CHANNEL_ID_INFO
        }

        // Create intent to open the app when notification is tapped
        // We pass message and channel IDs so the app can navigate to the right screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("messageId", message.id)
            putExtra("channelId", message.channelId)
        }

        // PendingIntent wraps the intent for later execution
        // FLAG_IMMUTABLE is required for Android 12+ (API 31+)
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.id.hashCode(),  // Unique request code per message
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification with all the details
        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)  // Icon shown in status bar
            .setContentTitle(message.title)       // Notification title
            .setContentText(message.message)      // Short preview text
            .setAutoCancel(true)                  // Dismiss when tapped
            .setContentIntent(pendingIntent)      // Action when tapped
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.message))  // Expanded view
            .setPriority(
                when (message.severity) {
                    Severity.CRITICAL -> NotificationCompat.PRIORITY_MAX
                    Severity.WARNING -> NotificationCompat.PRIORITY_HIGH
                    Severity.INFO -> NotificationCompat.PRIORITY_DEFAULT
                }
            )

        // Add alarm sound for critical alerts so they're impossible to miss
        if (message.severity == Severity.CRITICAL) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            builder.setSound(alarmSound)
        }

        // Post the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(message.id.hashCode(), builder.build())
        Log.d(TAG, "Notification displayed for message: ${message.id}")
    }

    /**
     * Display a simple notification for Firebase Console test messages.
     *
     * This handles NOTIFICATION payloads which only contain title and body.
     * Used primarily for testing push notifications from Firebase Console.
     *
     * Unlike showNotification(), this does NOT:
     * - Save to database
     * - Start AlertService
     * - Use severity-based styling
     *
     * @param title The notification title
     * @param body The notification body text
     */
    private fun showSimpleNotification(title: String, body: String) {
        Log.d(TAG, "Showing simple notification: $title - $body")

        // Create intent to open app when tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Use current time as unique notification ID
        // This prevents new notifications from overwriting old ones
        val notificationId = System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build a basic notification using the INFO channel
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_INFO)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // Ensure it pops up

        // Post the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "Simple notification posted with ID: $notificationId")
    }

    /**
     * Create notification channels for different severity levels.
     *
     * Required for Android O (API 26) and above.
     *
     * Why multiple channels?
     * - Users can customize each channel independently in Android Settings
     * - Critical alerts can bypass Do Not Disturb
     * - Different sounds/vibration patterns per severity
     *
     * Channel settings are persistent - once created, only the user can modify them.
     */
    private fun createNotificationChannels() {
        // Notification channels only exist on Android O (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // CRITICAL channel - for urgent alerts that need immediate attention
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical system alerts that require immediate attention"
                enableVibration(true)
                // Vibration pattern: wait 0ms, vibrate 500ms, wait 200ms, repeat...
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                // Use alarm sound so it's impossible to miss
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }

            // WARNING channel - for important but not critical alerts
            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                "Warning Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warning alerts that may need attention"
                enableVibration(true)
                // Uses default notification sound
            }

            // INFO channel - for informational messages
            val infoChannel = NotificationChannel(
                CHANNEL_ID_INFO,
                "Info Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Informational alerts and test notifications"
                // Default settings - no special sound or vibration
            }

            // Register all channels with the system
            notificationManager.createNotificationChannel(criticalChannel)
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(infoChannel)

            Log.d(TAG, "Notification channels created successfully")
        }
    }
}
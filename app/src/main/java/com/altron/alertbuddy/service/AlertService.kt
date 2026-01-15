package com.altron.alertbuddy.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.altron.alertbuddy.MainActivity
import com.altron.alertbuddy.R
import com.altron.alertbuddy.data.AlertBuddyDatabase
import kotlinx.coroutines.*

/**
 * AlertService - Persistent Background Service for Critical Alert Monitoring
 *
 * PURPOSE:
 * This foreground service runs continuously to ensure critical infrastructure alerts
 * are NEVER missed. It beeps every 60 seconds when there are unread alerts,
 * even when the app is closed, in the background, or the screen is off.
 *
 * WHY A FOREGROUND SERVICE?
 * - Android aggressively kills background services to save battery
 * - A foreground service displays a persistent notification and won't be killed
 * - This is essential for Altron's mission-critical infrastructure alerting
 * - Engineers on-call MUST be alerted until they explicitly acknowledge
 *
 * LIFECYCLE:
 * 1. STARTED when:
 *    - User logs in (from MainActivity)
 *    - Device boots up (from BootReceiver, if user was logged in)
 *    - New FCM alert arrives (from AlertFirebaseMessagingService)
 *
 * 2. RUNS continuously:
 *    - Checks database for unread alerts every 60 seconds
 *    - Plays alarm sound if unread alerts exist
 *    - Updates notification with current unread count
 *
 * 3. STOPS when:
 *    - User logs out (AlertService.stopService called from SettingsScreen)
 *    - All alerts are marked as read (self-stops)
 *
 * BEHAVIOR DETAILS:
 * - Shows a persistent notification in the status bar (cannot be swiped away)
 * - Notification shows current unread count: "3 unread alerts - Tap to view"
 * - Uses device's alarm sound (loud, attention-grabbing)
 * - Survives app close - service keeps running in background
 * - Uses START_STICKY so system restarts it if killed due to low memory
 */
class AlertService : Service() {

    companion object {
        // Tag for Logcat filtering - use "AlertService" in Logcat to see these logs
        private const val TAG = "AlertService"

        // Unique ID for the foreground notification
        // Must be > 0 and unique across the app
        private const val NOTIFICATION_ID = 1001

        // Notification channel ID for the service notification
        // Separate from alert channels so users can configure independently
        private const val CHANNEL_ID = "alert_buddy_service"

        // How often to check for unread alerts (60 seconds = 1 minute)
        // This is the core beeping interval
        private const val BEEP_INTERVAL_MS = 60_000L

        /**
         * Start the AlertService as a foreground service.
         *
         * Call this when:
         * - User successfully logs in
         * - Device boots up and user was previously logged in
         * - New FCM alert arrives
         *
         * @param context Application context (use applicationContext, not Activity)
         */
        fun startService(context: Context) {
            Log.d(TAG, "startService() called")
            val intent = Intent(context, AlertService::class.java)

            // On Android O (API 26) and above, we must use startForegroundService()
            // This gives us 5 seconds to call startForeground() or the app crashes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the AlertService.
         *
         * Call this when:
         * - User logs out
         * - User explicitly disables alert monitoring (future feature)
         *
         * @param context Application context
         */
        fun stopService(context: Context) {
            Log.d(TAG, "stopService() called")
            val intent = Intent(context, AlertService::class.java)
            context.stopService(intent)
        }
    }

    // Coroutine scope for database operations
    // SupervisorJob: If one coroutine fails, others continue
    // Dispatchers.IO: Optimized thread pool for I/O operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Handler for scheduling periodic beeps on the main thread
    // We use the main looper so UI-related callbacks work correctly
    private val handler = Handler(Looper.getMainLooper())

    // MediaPlayer instance for playing the alarm sound
    // Kept as instance variable to properly release resources
    private var mediaPlayer: MediaPlayer? = null

    // Flag to track if the periodic beep loop is running
    private var isRunning = false

    /**
     * Runnable that executes the beep check every BEEP_INTERVAL_MS (60 seconds).
     *
     * This is a self-rescheduling runnable:
     * 1. Check for unread alerts
     * 2. Play beep if unread exist
     * 3. Schedule next check in 60 seconds
     * 4. Repeat until isRunning becomes false
     */
    private val beepRunnable = object : Runnable {
        override fun run() {
            // Perform the unread check and beep
            checkUnreadAndBeep()

            // Schedule next execution if service is still running
            if (isRunning) {
                handler.postDelayed(this, BEEP_INTERVAL_MS)
            }
        }
    }

    /**
     * Called when the service is first created.
     * Create notification channel here so it's ready before we post notifications.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate - Service created")
        createNotificationChannel()
    }

    /**
     * Called every time startService() or startForegroundService() is called.
     * This may be called multiple times for the same service instance.
     *
     * IMPORTANT: Must call startForeground() within 5 seconds of startForegroundService()!
     *
     * @param intent The intent used to start the service
     * @param flags Additional flags about this start request
     * @param startId A unique ID for this particular start request
     * @return START_STICKY - tells Android to restart this service if it gets killed
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - Service starting")

        // CRITICAL: Must call startForeground immediately!
        // If we don't call this within 5 seconds of startForegroundService(), the app crashes
        // We show "Checking for alerts..." initially while we query the database
        startForeground(NOTIFICATION_ID, createNotification("Checking for alerts..."))

        // Check for unread alerts in background, then decide what to do
        serviceScope.launch {
            try {
                val database = AlertBuddyDatabase.getDatabase(applicationContext)
                val unreadCount = database.messageDao().getTotalUnreadCount()

                // Switch to main thread for UI operations
                withContext(Dispatchers.Main) {
                    if (unreadCount > 0) {
                        // Has unread alerts - update notification and start the beep loop
                        updateNotification("$unreadCount unread alert${if (unreadCount > 1) "s" else ""} - Tap to view")
                        isRunning = true
                        handler.post(beepRunnable)
                        Log.d(TAG, "Service running - found $unreadCount unread alerts")
                    } else {
                        // No unread alerts - no need to keep running
                        Log.d(TAG, "No unread alerts - stopping service")
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking unread count on start", e)
                stopSelf()
            }
        }

        // START_STICKY: If the system kills this service due to low memory,
        // Android will restart it automatically when resources become available
        return START_STICKY
    }

    /**
     * Called when the service is being destroyed.
     * Clean up all resources to prevent memory leaks.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy - Cleaning up")

        // Stop the beep loop
        isRunning = false
        handler.removeCallbacks(beepRunnable)

        // Release MediaPlayer resources
        mediaPlayer?.release()
        mediaPlayer = null

        // Cancel all coroutines to prevent leaks
        serviceScope.cancel()
    }

    /**
     * This service does not support binding.
     * It's a "started service" that runs independently.
     *
     * @return null - we don't support binding
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Check the database for unread alerts and play alarm if any exist.
     *
     * This method is called every 60 seconds by the beepRunnable.
     *
     * LOGIC:
     * - If unread > 0: Update notification, play alarm sound
     * - If unread == 0: All alerts acknowledged, stop the service
     */
    private fun checkUnreadAndBeep() {
        serviceScope.launch {
            try {
                // Query database for total unread count
                val database = AlertBuddyDatabase.getDatabase(applicationContext)
                val unreadCount = database.messageDao().getTotalUnreadCount()

                // Switch to main thread for notification and sound
                withContext(Dispatchers.Main) {
                    if (unreadCount > 0) {
                        // Still have unread alerts - BEEP!
                        Log.d(TAG, "Found $unreadCount unread alert(s) - playing alarm")
                        updateNotification("$unreadCount unread alert${if (unreadCount > 1) "s" else ""} - Tap to view")
                        playAlertSound()
                    } else {
                        // All alerts have been read - stop service
                        Log.d(TAG, "All alerts acknowledged - stopping service")
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkUnreadAndBeep", e)
            }
        }
    }

    /**
     * Play the alarm sound to get the user's attention.
     *
     * Uses the device's default alarm ringtone because:
     * - It's the loudest and most attention-grabbing
     * - Users are conditioned to respond to alarm sounds
     * - It plays even if device is in silent mode (USAGE_ALARM)
     */
    private fun playAlertSound() {
        try {
            // Release any existing player before creating new one
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                // Configure audio to play as an ALARM
                // This bypasses silent mode and Do Not Disturb in some cases
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                // Get the device's alarm sound, fall back to notification sound
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                setDataSource(applicationContext, alarmUri)
                prepare()
                start()

                // Release player when sound finishes playing
                setOnCompletionListener { mp ->
                    mp.release()
                }
            }

            Log.d(TAG, "Alert sound played")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alert sound", e)
        }
    }

    /**
     * Create the notification channel for the foreground service.
     *
     * Required for Android O (API 26) and above.
     * Without a channel, notifications won't appear.
     *
     * We use LOW importance because:
     * - This notification just shows the service is running
     * - The actual alert notifications use HIGH importance channels
     * - We don't want this notification to make sounds
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alert Monitoring Service",
                NotificationManager.IMPORTANCE_LOW  // Low = silent, just shows in status bar
            ).apply {
                description = "Shows that Alert Buddy is monitoring for critical alerts"
                setShowBadge(false)  // Don't show badge on app icon for this notification
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Create the foreground service notification.
     *
     * This notification:
     * - Shows "Alert Buddy" in the title
     * - Shows unread count or status in the text
     * - Cannot be swiped away (setOngoing = true)
     * - Opens the app when tapped
     *
     * @param message The text to display (e.g., "3 unread alerts - Tap to view")
     * @return The built Notification object
     */
    private fun createNotification(message: String): Notification {
        // Create intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)  // App icon in status bar
            .setContentTitle("Alert Buddy")       // Notification title
            .setContentText(message)              // Dynamic status message
            .setContentIntent(pendingIntent)      // Open app when tapped
            .setOngoing(true)                     // Cannot be swiped away
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Low = no sound
            .setCategory(NotificationCompat.CATEGORY_SERVICE)  // Categorize as service
            .build()
    }

    /**
     * Update the foreground notification with a new message.
     * Called when unread count changes.
     *
     * @param message The new text to display
     */
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }
}

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
import com.altron.alertbuddy.data.AlertBuddyDatabase
import kotlinx.coroutines.*

/**
 * Foreground Service that runs continuously and beeps every 60 seconds
 * when there are unread alerts.
 *
 * This service ensures alerts are never missed, even when the app is closed.
 */
class AlertService : Service() {

    companion object {
        private const val TAG = "AlertService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alert_buddy_service"
        private const val BEEP_INTERVAL_MS = 60_000L // 60 seconds

        fun startService(context: Context) {
            val intent = Intent(context, AlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AlertService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var isRunning = false

    private val beepRunnable = object : Runnable {
        override fun run() {
            checkUnreadAndBeep()
            if (isRunning) {
                handler.postDelayed(this, BEEP_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Alert Service started")

        startForeground(NOTIFICATION_ID, createNotification("Monitoring for alerts..."))

        isRunning = true
        handler.post(beepRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(beepRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        serviceScope.cancel()
        Log.d(TAG, "Alert Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkUnreadAndBeep() {
        serviceScope.launch {
            try {
                val database = AlertBuddyDatabase.getDatabase(applicationContext)
                val unreadCount = database.messageDao().getTotalUnreadCount()

                withContext(Dispatchers.Main) {
                    if (unreadCount > 0) {
                        Log.d(TAG, "Found $unreadCount unread alerts, beeping...")
                        updateNotification("$unreadCount unread alert${if (unreadCount > 1) "s" else ""}")
                        playAlertSound()
                    } else {
                        Log.d(TAG, "No unread alerts")
                        updateNotification("All alerts acknowledged")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking unread count", e)
            }
        }
    }

    private fun playAlertSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setDataSource(applicationContext, alarmUri)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alert sound", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alert Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for alert monitoring"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
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
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your app icon
            .setContentTitle("Alert Buddy")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }
}

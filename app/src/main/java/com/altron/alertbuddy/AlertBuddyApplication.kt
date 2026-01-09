package com.altron.alertbuddy

import android.app.Application
import android.util.Log
import com.altron.alertbuddy.service.AlertService
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class AlertBuddyApplication : Application() {

    companion object {
        private const val TAG = "AlertBuddyApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Get FCM token for debugging
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "FCM Token: $token")
                // TODO: Send this token to your server for targeting this device
            } else {
                Log.w(TAG, "Failed to get FCM token", task.exception)
            }
        }

        // Subscribe to topics for broadcast alerts
        FirebaseMessaging.getInstance().subscribeToTopic("alerts")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to alerts topic")
                }
            }

        // Start the background alert service
        AlertService.startService(this)
    }
}

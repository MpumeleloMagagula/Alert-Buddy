package com.altron.alertbuddy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver that starts the AlertService when the device boots up.
 * Only starts the service if user was previously logged in.
 * This ensures alerts continue to beep even after device restarts.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "Device boot completed")

            // Only start service if user was previously logged in
            val prefs = context.getSharedPreferences("alert_buddy_prefs", Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)

            if (isLoggedIn) {
                Log.d(TAG, "User is logged in, starting AlertService")
                AlertService.startService(context)
            } else {
                Log.d(TAG, "User not logged in, skipping AlertService start")
            }
        }
    }
}
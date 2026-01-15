package com.altron.alertbuddy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver - Starts AlertService When Device Boots Up
 *
 * PURPOSE:
 * Ensures that alert monitoring continues even after the device is restarted.
 * Without this receiver, users would need to manually open the app after
 * every reboot to resume monitoring for critical infrastructure alerts.
 *
 * HOW IT WORKS:
 * 1. When Android finishes booting, it broadcasts ACTION_BOOT_COMPLETED
 * 2. This receiver catches that broadcast (registered in AndroidManifest.xml)
 * 3. We check SharedPreferences to see if user was logged in before shutdown
 * 4. If logged in, we start AlertService to resume beeping for unread alerts
 *
 * REQUIREMENTS IN AndroidManifest.xml:
 * 1. Permission: <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 * 2. Receiver declaration with intent-filter for BOOT_COMPLETED
 *
 * ANDROID RESTRICTIONS:
 * - User must have opened the app at least once (Android security requirement)
 * - On some devices, user must also enable "auto-start" in settings
 * - The app cannot receive boot broadcasts if force-stopped
 *
 * SECURITY CONSIDERATION:
 * We only start the service if the user was previously logged in.
 * This prevents the service from running for logged-out users,
 * which would be both unnecessary and a potential privacy concern.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

        // SharedPreferences name - must match MainActivity and SettingsScreen
        private const val PREFS_NAME = "alert_buddy_prefs"

        // Key for login state - must match MainActivity and SettingsScreen
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    /**
     * Called when a broadcast is received.
     *
     * Android sends broadcasts for many system events. We only care about:
     * - ACTION_BOOT_COMPLETED: Standard Android boot completed
     * - QUICKBOOT_POWERON: Some manufacturers use this for fast boot
     *
     * @param context The Context in which the receiver is running
     * @param intent The Intent being received (contains the action)
     */
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive - Action: ${intent.action}")

        // Verify this is a boot completed broadcast
        // QUICKBOOT_POWERON is used by some manufacturers (e.g., HTC, some Samsung)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "Device boot completed - checking if user was logged in")

            // Check if user was logged in before device shutdown
            // We stored this in SharedPreferences when they logged in
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

            if (isLoggedIn) {
                // User was logged in - start the AlertService
                // This will check for unread alerts and beep if necessary
                Log.d(TAG, "User was logged in - starting AlertService")
                try {
                    AlertService.startService(context)
                    Log.d(TAG, "AlertService started successfully after boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start AlertService after boot", e)
                }
            } else {
                // User was not logged in - don't start service
                // No point monitoring for alerts if they can't see them
                Log.d(TAG, "User was not logged in - AlertService will not start")
            }
        }
    }
}

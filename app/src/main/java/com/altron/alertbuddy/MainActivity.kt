package com.altron.alertbuddy

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.ui.theme.navigation.AlertBuddyNavigation
import com.altron.alertbuddy.ui.theme.AlertBuddyTheme
import com.google.firebase.messaging.FirebaseMessaging
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.altron.alertbuddy.service.AlertService

/**
 * MainActivity - Main Entry Point for Alert Buddy
 *
 * This is the single Activity in the app (Single Activity Architecture).
 * All screens are Composables managed by Navigation Compose.
 *
 * RESPONSIBILITIES:
 * 1. Initialize the app and check if user is already logged in
 * 2. Request notification permission (required for Android 13+)
 * 3. Log FCM token for push notification testing
 * 4. Handle deep links from notification taps
 * 5. Start/stop AlertService based on login state
 * 6. Persist login state in SharedPreferences for BootReceiver
 *
 * FLOW:
 * App Launch → Check Login State → Show Login or Channel List
 *           ↓
 * If logged in → Start AlertService for persistent beeping
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // Request code for notification permission dialog
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

        // SharedPreferences name and key for login state persistence
        // This must match the keys used in BootReceiver and SettingsScreen
        private const val PREFS_NAME = "alert_buddy_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    // Repository for database operations (Room/SQLite)
    private lateinit var repository: AlertRepository

    /**
     * Called when the activity is first created.
     * This is the main entry point for the app.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        // Enable edge-to-edge display
        // This allows content to draw behind the status bar and navigation bar
        enableEdgeToEdge()

        // Request notification permission for Android 13+ (API 33+)
        // Without this permission, notifications won't appear on newer Android versions
        requestNotificationPermission()

        // Initialize the repository for database access
        // The repository provides methods to interact with Room database
        try {
            repository = AlertRepository(applicationContext)
            Log.d(TAG, "Repository initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
        }

        // Get and log FCM token for push notification testing
        // Copy this token from Logcat and use it in Firebase Console
        logFcmToken()

        // Check for deep link data from notification tap
        handleDeepLink(intent)

        // Set up Compose UI
        // Jetpack Compose uses a declarative approach to build UI
        setContent {
            // Track login state - null means still checking
            // Boolean? allows three states: null (loading), true (logged in), false (not logged in)
            var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }

            // LaunchedEffect runs once when the composable enters composition
            // Used here to check if user is already logged in from a previous session
            LaunchedEffect(Unit) {
                try {
                    // Query database for current user
                    val user = repository.getCurrentUser()
                    isLoggedIn = user != null
                    Log.d(TAG, "Login check complete: isLoggedIn = $isLoggedIn")

                    // If user is logged in, start the AlertService
                    // This ensures beeping continues even if app was closed
                    if (user != null) {
                        AlertService.startService(applicationContext)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking login state", e)
                    isLoggedIn = false
                }
            }

            // Apply the app theme (colors, typography, shapes)
            AlertBuddyTheme {
                // Surface provides the background color from theme
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()  // Add padding for status bar
                ) {
                    // Only show navigation once login state is determined
                    // This prevents flashing the wrong screen
                    if (isLoggedIn != null) {
                        Log.d(TAG, "Showing navigation, isLoggedIn = $isLoggedIn")
                        AlertBuddyNavigation(
                            repository = repository,
                            isLoggedIn = isLoggedIn!!,
                            onLoginSuccess = {
                                // Called when user successfully logs in
                                isLoggedIn = true

                                // Save login state for BootReceiver
                                // This allows AlertService to restart after device reboot
                                saveLoginState(true)

                                // Start AlertService to monitor for alerts
                                AlertService.startService(applicationContext)

                                Log.d(TAG, "Login success - AlertService started")
                            },
                            onLogout = {
                                // Called when user logs out
                                isLoggedIn = false

                                // Clear login state so BootReceiver won't start service
                                saveLoginState(false)

                                // Stop AlertService - no need to beep if logged out
                                AlertService.stopService(applicationContext)

                                Log.d(TAG, "Logout complete - AlertService stopped")
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the activity receives a new intent while already running.
     * This happens when user taps a notification while the app is open.
     *
     * @param intent The new intent that was used to start the activity
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Request POST_NOTIFICATIONS permission for Android 13+ (API 33+).
     *
     * Starting with Android 13, apps must request this permission at runtime
     * before they can show notifications. Without it, all notifications are blocked.
     */
    private fun requestNotificationPermission() {
        // Only needed for Android 13 (TIRAMISU) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "POST_NOTIFICATIONS permission granted: $hasPermission")

            // If permission not granted, request it
            if (!hasPermission) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    /**
     * Handle the result of permission request dialogs.
     *
     * @param requestCode The code passed to requestPermissions()
     * @param permissions Array of permissions that were requested
     * @param grantResults Array of results for each permission (GRANTED or DENIED)
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                val granted = grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Notification permission result: ${if (granted) "GRANTED" else "DENIED"}")

                if (!granted) {
                    // User denied permission - notifications won't work
                    // You could show a message explaining why notifications are important
                    Log.w(TAG, "User denied notification permission - alerts may not be visible")
                }
            }
        }
    }

    /**
     * Log the FCM token for testing push notifications.
     *
     * The FCM (Firebase Cloud Messaging) token uniquely identifies this device.
     * To send a test notification:
     * 1. Copy the token from Logcat (filter by "FCM_TOKEN")
     * 2. Go to Firebase Console → Cloud Messaging → Send test message
     * 3. Paste the token in the "Add an FCM registration token" field
     */
    private fun logFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM_TOKEN", "============================================")
                Log.d("FCM_TOKEN", "FCM Token: $token")
                Log.d("FCM_TOKEN", "============================================")
                Log.d("FCM_TOKEN", "Use this token in Firebase Console to send test messages")
            } else {
                Log.e("FCM_TOKEN", "Failed to get FCM token", task.exception)
            }
        }
    }

    /**
     * Handle deep link data from notification intent.
     *
     * When a user taps an alert notification, the intent contains:
     * - messageId: The ID of the specific message that was tapped
     * - channelId: The ID of the channel containing the message
     *
     * These can be used to navigate directly to the message detail screen.
     *
     * @param intent The intent containing deep link extras
     */
    private fun handleDeepLink(intent: Intent?) {
        intent?.let {
            val messageId = it.getStringExtra("messageId")
            val channelId = it.getStringExtra("channelId")

            if (messageId != null) {
                Log.d(TAG, "Deep link received - messageId: $messageId, channelId: $channelId")
                // TODO: Navigate to message detail screen
                // This would require passing the IDs to Navigation component
                // and setting the initial route to MessageDetail
            }
        }
    }

    /**
     * Save login state to SharedPreferences.
     *
     * This persists the login state so BootReceiver can check it after device reboot.
     * If user was logged in, AlertService will be restarted automatically.
     *
     * @param isLoggedIn true if user just logged in, false if logged out
     */
    private fun saveLoginState(isLoggedIn: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
        Log.d(TAG, "Login state saved to SharedPreferences: $isLoggedIn")
    }
}